package app.template.extension.aare;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;

/**
 * "While an app records" (Car radio pause): while a car is connected and a phone app records (a
 * WhatsApp voice message, a recorder, dictation, an assistant listening), the car's own radio pauses,
 * as Android Auto does, through {@link CarRadioFocus}. Android tells every app about recordings, with
 * their source but without the app's name, so this needs no permission and keeps nothing.
 *
 * Calls (VOICE_COMMUNICATION) are left to the car, and recordings Android silences (an app in the
 * background) don't count. The end waits [END_HOLD_MS], so a recognizer that restarts doesn't make
 * the radio come and go, and the radio always comes back after [MAX_MS].
 *
 * Hooks: CarSession.onRunning (a car connected) and CarSession.onClosed (it went).
 */
// Public: called from AARE's CarSession (another package). Android 8+, as the other voice features
// (onSession returns below it), for AudioManager's recording callback and the focus requests.
@android.annotation.TargetApi(Build.VERSION_CODES.O)
public final class RecordingPause {
    private static final String TAG = "AarePatchesRecPause";
    static final long END_HOLD_MS = 1_500;
    static final long MAX_MS = 10 * 60_000L;

    private static Handler main;
    private static WeakReference<Object> session = new WeakReference<>(null);
    private static AudioManager audio;
    private static AudioManager.AudioRecordingCallback callback;
    private static boolean paused;
    private static final Runnable end = RecordingPause::endNow;
    private static final Runnable cap = () -> {
        if (paused) PatchLog.add("radio", "recording longer than " + MAX_MS / 60_000 + " min: car's sound given back");
        endNow();
    };

    private RecordingPause() {
    }

    /** Pure: whether a recording from [source] makes the radio pause. */
    static boolean counts(int source, boolean silenced) {
        if (silenced) return false;
        return source == MediaRecorder.AudioSource.DEFAULT
                || source == MediaRecorder.AudioSource.MIC
                || source == MediaRecorder.AudioSource.CAMCORDER
                || source == MediaRecorder.AudioSource.VOICE_RECOGNITION
                || source == MediaRecorder.AudioSource.UNPROCESSED;
    }

    private static synchronized Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    /** At the start of CarSession.onRunning: a car is connected. Any thread; never throws. */
    public static void onSession(Object carSession) {
        try {
            if (carSession == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
            main().post(() -> start(carSession));
        } catch (Throwable t) {
            Log.w(TAG, "onSession", t);
        }
    }

    /** At the start of CarSession.onClosed: the car went. Any thread; never throws. */
    public static void onClosed(Object carSession) {
        try {
            main().post(() -> {
                if (session.get() != carSession) return;
                stop();
            });
        } catch (Throwable t) {
            Log.w(TAG, "onClosed", t);
        }
    }

    private static void start(Object carSession) {
        try {
            stop();
            Context app = PatchSettings.application();
            if (app == null) return;
            audio = app.getSystemService(AudioManager.class);
            if (audio == null) return;
            session = new WeakReference<>(carSession);
            callback = new AudioManager.AudioRecordingCallback() {
                @Override
                public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                    changed(configs);
                }
            };
            // Registered for the whole connection: the switch is read at each change, so turning it on
            // or off works at once.
            audio.registerAudioRecordingCallback(callback, main());
            changed(audio.getActiveRecordingConfigurations());
        } catch (Throwable t) {
            Log.w(TAG, "start", t);
            PatchLog.add("radio", "can't watch recordings: " + t.getClass().getSimpleName());
        }
    }

    private static void stop() {
        main().removeCallbacks(end);
        main().removeCallbacks(cap);
        if (audio != null && callback != null) {
            try {
                audio.unregisterAudioRecordingCallback(callback);
            } catch (Throwable t) {
                Log.w(TAG, "unregister", t);
            }
        }
        callback = null;
        audio = null;
        session = new WeakReference<>(null);
        if (paused) PatchLog.add("radio", "car disconnected during a recording");
        paused = false;
        CarRadioFocus.sessionClosed();
    }

    private static void changed(List<AudioRecordingConfiguration> configs) {
        try {
            boolean any = false;
            if (configs != null) {
                for (AudioRecordingConfiguration c : configs) {
                    boolean silenced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && c.isClientSilenced();
                    if (counts(c.getClientAudioSource(), silenced)) any = true;
                }
            }
            if (any) {
                main().removeCallbacks(end);
                if (paused) return;
                if (!PatchSettings.isEnabled(null, PatchSettings.RADIO_PAUSE)
                        || !PatchSettings.isEnabled(null, PatchSettings.RADIO_PAUSE_RECORDING)) return;
                Object focus = guidanceFocus();
                if (focus == null) return;
                paused = true;
                CarRadioFocus.recording(true, focus);
                main().postDelayed(cap, MAX_MS);
            } else if (paused) {
                main().removeCallbacks(end);
                main().postDelayed(end, END_HOLD_MS);
            }
        } catch (Throwable t) {
            Log.w(TAG, "changed", t);
        }
    }

    private static void endNow() {
        main().removeCallbacks(end);
        main().removeCallbacks(cap);
        if (!paused) return;
        paused = false;
        try {
            CarRadioFocus.recording(false, guidanceFocus());
        } catch (Throwable t) {
            Log.w(TAG, "end", t);
        }
    }

    /** CarSession's guidanceFocus (CarAudioOut.Focus): the request AARE makes for its navigation channel. */
    private static Object guidanceFocus() {
        Object s = session.get();
        if (s == null) return null;
        try {
            Field f = s.getClass().getDeclaredField("guidanceFocus");
            f.setAccessible(true);
            return f.get(s);
        } catch (Throwable t) {
            Log.w(TAG, "guidanceFocus", t);
            PatchLog.add("radio", "AARE's guidance focus not found: " + t.getClass().getSimpleName());
            return null;
        }
    }
}
