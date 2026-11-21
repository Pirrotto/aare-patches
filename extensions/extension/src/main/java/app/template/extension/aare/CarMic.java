package app.template.extension.aare;

import android.content.Intent;
import android.media.AudioFormat;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.speech.RecognizerIntent;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;

/**
 * The car's microphone for speech recognition (dictated replies, AARE's assistant): AARE's own
 * microphone route (CarRuntime.microphone, the car's AAP microphone channel, or the phone's when the
 * car has none) piped into the recognizer with RecognizerIntent.EXTRA_AUDIO_SOURCE (Android 13+).
 *
 * A recognizer that doesn't take an audio source just opens the phone's microphone as before.
 */
final class CarMic {
    private static final String TAG = "AareCarMic";
    static final String KEY = "car_microphone";

    /** An open car microphone; close it when the recognition ends. */
    interface Handle {
        void close();
    }

    private CarMic() {
    }

    static boolean enabled(android.content.Context context) {
        return Build.VERSION.SDK_INT >= 33 && PatchSettings.isRegistered(KEY) && PatchSettings.isEnabled(context, KEY);
    }

    /**
     * Opens the car's microphone and adds it to the recognizer [intent] as its audio source.
     * Returns null (the recognizer then uses the phone's microphone) if it isn't available.
     */
    @SuppressWarnings("unchecked")
    static Handle attach(Object carRuntime, Intent intent) {
        if (carRuntime == null || Build.VERSION.SDK_INT < 33) return null;
        ParcelFileDescriptor[] pipe = null;
        try {
            Object route = carRuntime.getClass().getMethod("getMicrophone").invoke(carRuntime);
            if (!(route instanceof Function2)) return null;
            pipe = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor read = pipe[0];
            final OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);

            // The car's reader thread hands the sound over: it must never wait on the pipe.
            final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(256);
            final byte[] end = new byte[0];
            Thread writer = new Thread(() -> {
                try {
                    while (true) {
                        byte[] chunk = queue.take();
                        if (chunk == end) break;
                        out.write(chunk);
                    }
                } catch (InterruptedException | IOException ignored) {
                    // Recognition over: the recognizer closed its end.
                } finally {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }, "aare-car-mic");
            writer.start();

            Function3<byte[], Integer, Integer, Unit> sink = (data, offset, length) -> {
                byte[] copy = new byte[length];
                System.arraycopy(data, offset, copy, 0, length);
                queue.offer(copy);
                return Unit.INSTANCE;
            };
            Function0<Unit> stopped = () -> {
                queue.offer(end);
                return Unit.INSTANCE;
            };
            Object micHandle = ((Function2<Object, Object, Object>) route).invoke(sink, stopped);
            if (micHandle == null) {
                queue.offer(end);
                read.close();
                return null;
            }

            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000);
            setListening(carRuntime, true);
            Log.i(TAG, "speech recognition on the car's microphone");

            final Object h = micHandle;
            return () -> {
                try {
                    Method close = h.getClass().getMethod("close");
                    close.setAccessible(true);
                    close.invoke(h);
                } catch (Throwable t) {
                    Log.w(TAG, "close", t);
                }
                queue.offer(end);
                try {
                    read.close();
                } catch (IOException ignored) {
                }
                setListening(carRuntime, false);
            };
        } catch (Throwable t) {
            Log.w(TAG, "car microphone unavailable", t);
            if (pipe != null) {
                for (ParcelFileDescriptor p : pipe) {
                    try {
                        p.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            return null;
        }
    }

    /** The rail's mic shows that the car listens. */
    private static void setListening(Object carRuntime, boolean listening) {
        try {
            Object ui = carRuntime.getClass().getMethod("getUi").invoke(carRuntime);
            ui.getClass().getMethod("setListening", boolean.class).invoke(ui, listening);
        } catch (Throwable ignored) {
        }
    }
}
