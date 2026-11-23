package app.template.extension.aare;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** The short tone of "Listening beep", on the assistant's channel like the voice reply's speech. */
final class Beep {
    static final int LENGTH_MS = 120;
    private static final int RATE = 16000;
    private static short[] samples;

    private Beep() {
    }

    static void play() {
        try {
            if (samples == null) samples = tone(RATE, LENGTH_MS, 880);
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
