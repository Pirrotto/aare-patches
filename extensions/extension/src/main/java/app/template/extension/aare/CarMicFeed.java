package app.template.extension.aare;

import android.annotation.TargetApi;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.speech.RecognizerIntent;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * "Car microphone for dictation" (Voice reply, test): the voice reply's speech recognition hears
 * the car's microphone directly. AARE's own microphone for car apps (CarRuntime.microphone: the car's
 * mic when the car has one, else the phone's; always 16 kHz mono 16-bit PCM) is written into a pipe
 * given to the recognizer as RecognizerIntent.EXTRA_AUDIO_SOURCE (Android 13+).
 *
 * The car's PCM arrives on AARE's car reader thread: it is only queued there (dropped if the queue is
 * full, so that thread never waits) and written to the pipe by a thread of its own. With an audio
 * source the recognition ends only when the audio is closed ({@link #finish}). Nothing is kept: the
 * audio goes only to the recognizer, and only counters (bytes, never sound) reach the diagnostics.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
final class CarMicFeed {
    private static final String TAG = "AarePatchesCarMic";
    static final int SAMPLE_RATE = 16_000;
    /** About 2 s of the car's audio (it comes in chunks of about 20-60 ms). */
    static final int QUEUE_CHUNKS = 64;

    private final ParcelFileDescriptor read;
    private final ParcelFileDescriptor write;
    private final OutputStream out;
    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CHUNKS);
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final Thread writer;
    private volatile boolean finished;
    private volatile boolean closed;
    /** AARE's MicHandle: closing it gives the car's microphone back. */
    private volatile Object handle;
    private volatile boolean takenOver;

    private CarMicFeed(ParcelFileDescriptor[] pipe) {
        read = pipe[0];
        write = pipe[1];
        out = new ParcelFileDescriptor.AutoCloseOutputStream(write);
        writer = new Thread(this::pump, "aare-patches-car-mic");
    }

    /** Opens AARE's microphone into a new pipe, or returns null (logged) if it can't. Main thread. */
    static CarMicFeed open(Object carRuntime) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || carRuntime == null) return null;
        CarMicFeed feed = null;
        try {
            Object microphone = carRuntime.getClass().getMethod("getMicrophone").invoke(carRuntime);
            if (microphone == null) {
                PatchLog.add("reply", "car mic: AARE has no microphone now, the recognizer uses its own");
                return null;
            }
            feed = new CarMicFeed(ParcelFileDescriptor.createPipe());
            feed.writer.start();
            final CarMicFeed f = feed;
            Object sink = kotlinFunction("kotlin.jvm.functions.Function3", args -> {
                f.offer((byte[]) args[0], (Integer) args[1], (Integer) args[2]);
                return null;
            });
            Object stopped = kotlinFunction("kotlin.jvm.functions.Function0", args -> {
                // Another listener (a car app) took the microphone: the recognition ends with what it heard.
                f.takenOver = true;
                f.finish();
                return null;
            });
            // Through the public interfaces: the lambdas' own classes aren't accessible.
            Method invoke = Class.forName("kotlin.jvm.functions.Function2", false, CarMicFeed.class.getClassLoader())
                    .getMethod("invoke", Object.class, Object.class);
            feed.handle = invoke.invoke(microphone, sink, stopped);
            if (feed.handle == null) {
                PatchLog.add("reply", "car mic: AARE didn't open it, the recognizer uses its own");
                feed.close();
                return null;
            }
            return feed;
        } catch (Throwable t) {
            Log.w(TAG, "open", t);
            PatchLog.add("reply", "car mic can't open: " + t.getClass().getSimpleName() + "; the recognizer uses its own");
            if (feed != null) feed.close();
            return null;
        }
    }

    /** The recognizer's audio source: the pipe's read end and its format. */
    void putExtras(Intent intent) {
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE);
    }

    /** AARE's car reader thread: queued, never waited for. */
    private void offer(byte[] buf, int off, int len) {
        if (finished || buf == null || len <= 0 || off < 0 || off + len > buf.length) return;
        byte[] copy = new byte[len];
        System.arraycopy(buf, off, copy, 0, len);
        if (!queue.offer(copy)) dropped.addAndGet(len);
    }

    private void pump() {
        try {
            while (!finished) {
                byte[] b = queue.poll(200, TimeUnit.MILLISECONDS);
                if (b == null) continue;
                out.write(b);
                written.addAndGet(b.length);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // The recognizer closed its end (it ended, or doesn't read an audio source): nothing more to write.
            finished = true;
        }
        closeQuietly(out);
    }

    /** Ends the audio: the recognizer then gives its result. The microphone goes back to AARE. */
    void finish() {
        if (finished && handle == null) return;
        finished = true;
        writer.interrupt();
        Object h = handle;
        handle = null;
        if (h != null) {
            try {
                Class.forName("net.prototux.aare.host.MicHandle", false, CarMicFeed.class.getClassLoader())
                        .getMethod("close").invoke(h);
            } catch (Throwable t) {
                Log.w(TAG, "mic close", t);
            }
        }
    }

    /** Finishes and frees the pipe; notes what went through (byte counts only). Idempotent. */
    void close() {
        if (closed) return;
        closed = true;
        finish();
        try {
            writer.join(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeQuietly(out);
        closeQuietly(read);
        PatchLog.add("reply", note(written.get(), dropped.get(), takenOver));
    }

    /** Pure: words came ([lastChangeAt] set) and nothing new for [pauseMs]: the audio can end. */
    static boolean quiet(long lastChangeAt, long now, long pauseMs) {
        return lastChangeAt > 0 && now - lastChangeAt >= pauseMs;
    }

    /** "car mic: 64 KB to the recognizer, 0 KB dropped": dropped audio means the recognizer didn't read it. */
    static String note(long writtenBytes, long droppedBytes, boolean takenOver) {
        return "car mic: " + writtenBytes / 1024 + " KB to the recognizer, " + droppedBytes / 1024 + " KB dropped"
                + (takenOver ? ", taken over by a car app" : "");
    }

    private static void closeQuietly(java.io.Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            Log.w(TAG, "close", e);
        }
    }

    private interface Body {
        Object run(Object[] args) throws Exception;
    }

    /** A kotlin.jvm.functions.FunctionN made without Kotlin on the classpath (as PatchSettings does). */
    private static Object kotlinFunction(String name, Body body) throws ClassNotFoundException {
        ClassLoader loader = CarMicFeed.class.getClassLoader();
        Class<?> type = Class.forName(name, false, loader);
        Object unit = null;
        try {
            unit = Class.forName("kotlin.Unit", true, loader).getField("INSTANCE").get(null);
        } catch (Throwable ignored) {
            // No kotlin.Unit: null is returned, which AARE ignores for these callbacks.
        }
        final Object result = unit;
        return Proxy.newProxyInstance(loader, new Class<?>[]{type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "invoke":
                    try {
                        body.run(args == null ? new Object[0] : args);
                    } catch (Throwable t) {
                        Log.w(TAG, "callback", t);
                    }
                    return result;
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    return "AarePatchesCarMic";
            }
        });
    }
}
