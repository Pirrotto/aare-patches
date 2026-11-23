package app.template.extension.aare;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * The tones of "Listening beep", on the assistant's channel like the voice reply's speech, in the
 * style of Android Auto's assistant: two short notes going up when listening starts, going down when
 * it stops. Our own tones, not Google's sounds.
 */
final class Beep {
    static final int NOTE_MS = 70;
    static final int LENGTH_MS = 2 * NOTE_MS;
    private static final int RATE = 16000;
    private static short[] up;
    private static short[] down;

    private Beep() {
    }

    /** Listening starts. */
    static void playStart() {
        if (up == null) up = concat(tone(RATE, NOTE_MS, 660), tone(RATE, NOTE_MS, 990));
        play(up);
    }

    /** Listening stopped: what was said is being handled. */
    static void playEnd() {
        if (down == null) down = concat(tone(RATE, NOTE_MS, 990), tone(RATE, NOTE_MS, 660));
        play(down);
    }

    static short[] concat(short[] a, short[] b) {
        short[] out = new short[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void play(short[] samples) {
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.length * 2)
                    .build();
            track.write(samples, 0, samples.length);
            track.play();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    track.release();
                } catch (Throwable ignored) {
                }
            }, LENGTH_MS + 500);
        } catch (Throwable t) {
            Log.w("AarePatches", "beep", t);
        }
    }

    /** A sine tone of [hz] with soft edges, so it doesn't click. */
    static short[] tone(int rate, int lengthMs, int hz) {
        int n = rate * lengthMs / 1000;
        int fade = rate * 15 / 1000;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double edge = Math.min(1.0, Math.min(i, n - 1 - i) / (double) fade);
            out[i] = (short) (Math.sin(2 * Math.PI * hz * i / rate) * 0.4 * Short.MAX_VALUE * edge);
        }
        return out;
    }
}
