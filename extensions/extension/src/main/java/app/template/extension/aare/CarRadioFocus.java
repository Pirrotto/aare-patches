package app.template.extension.aare;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * While AARE itself speaks (a message read aloud, the voice reply's questions, listening and
 * read-back), asks the car for a short pause of its own sound (radio) instead of a duck, and keeps
 * it until the speaking is over.
 *
 * AARE asks the car for AUDIO_FOCUS_GAIN_TRANSIENT_MAY_DUCK for anything on the navigation channel,
 * and gives it back 3 s after each phrase: some cars (the owner's Skoda) then lower the radio only a
 * little, and it comes back up between the phrases. Android Auto passes the phone app's own request
 * on instead: an assistant's transient request becomes AUDIO_FOCUS_GAIN_TRANSIENT, which the car
 * answers by pausing its source. Navigation apps' prompts are left as AARE sends them.
 *
 * Hooks in AARE's CarSession guidance focus (CarAudioOut.Focus.request, from the audio thread):
 * {@link #guidanceType} replaces the may-duck type, {@link #holdRelease} keeps the focus between
 * the phrases. The voice reply and the reading player say when they speak ({@link #speaking}).
 */
public final class CarRadioFocus {
    private static final String TAG = "AareRadioFocus";
    static final String KEY = PatchSettings.RADIO_PAUSE;

    /** Never kept longer than this, whatever happens: the radio must come back. */
    static final long MAX_HOLD_MS = 3 * 60_000L;
    private static final long CHECK_MS = 2_000L;

    private static volatile boolean replySpeaking;
    private static volatile boolean playerSpeaking;
    /** The guidance focus whose release is being kept back, or null. */
    private static volatile WeakReference<Object> pending;
    private static volatile long pendingSince;
    private static volatile boolean loggedOnce;
    private static Handler main;

    /** Created on first use, so the pure helpers can be unit-tested without Android. */
    private static synchronized Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private CarRadioFocus() {
    }

    /** Who speaks: the voice reply (question, listening, read-back) or the reading player. */
    enum Source { REPLY, PLAYER }

    /** Main thread: [source] started or stopped speaking. */
    static void speaking(Source source, boolean on) {
        if (source == Source.REPLY) replySpeaking = on;
        else playerSpeaking = on;
        if (!on && !replySpeaking && !playerSpeaking) {
            loggedOnce = false;
            releaseIfDone();
        }
    }

    /** Pure: whether AARE's own speech holds the car's sound now. */
    static boolean holds(boolean enabled, boolean reply, boolean player) {
        return enabled && (reply || player);
    }

    /** Pure: whether a kept-back release must now go to the car. */
    static boolean mustRelease(boolean speaking, long heldMs) {
        return !speaking || heldMs >= MAX_HOLD_MS;
    }

    private static boolean active() {
        return holds(PatchSettings.isEnabled(null, KEY), replySpeaking, playerSpeaking);
    }

    /**
     * Called by AARE's guidance focus with the may-duck type it is about to ask for (audio thread).
     * Returns the type to ask for: GAIN_TRANSIENT while AARE itself speaks, else unchanged.
     */
    public static Object guidanceType(Object mayDuck) {
        try {
            if (mayDuck == null || !active()) return mayDuck;
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object pause = Enum.valueOf((Class) mayDuck.getClass(), "AUDIO_FOCUS_GAIN_TRANSIENT");
            if (!loggedOnce) {
                loggedOnce = true;
                PatchLog.add("radio", "AARE speaks: asking the car to pause its sound (GAIN_TRANSIENT) until it is done");
            }
            return pause;
        } catch (Throwable t) {
            Log.w(TAG, "guidanceType", t);
            return mayDuck;
        }
    }

    /**
     * Called at the start of AARE's guidance focus request(playing) (audio thread). Returns true to
     * skip it: the release after a phrase, while AARE is still speaking. The release is sent later
     * ({@link #releaseIfDone}).
     */
    public static boolean holdRelease(Object focus, boolean playing) {
        try {
            if (playing) {
                // The channel plays again: nothing is kept back any more.
                pending = null;
                return false;
            }
            if (!active()) return false;
            if (pending == null) pendingSince = SystemClock.elapsedRealtime();
            pending = new WeakReference<>(focus);
            main().postDelayed(CarRadioFocus::check, CHECK_MS);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "holdRelease", t);
            return false;
        }
    }

    private static void check() {
        if (pending == null) return;
        if (mustRelease(active(), SystemClock.elapsedRealtime() - pendingSince)) release();
        else main().postDelayed(CarRadioFocus::check, CHECK_MS);
    }

    private static void releaseIfDone() {
        if (pending != null && !active()) release();
    }

    /** Sends the kept-back release, through AARE's own request(false), now that nothing holds it. */
    private static void release() {
        WeakReference<Object> ref = pending;
        pending = null;
        Object focus = ref == null ? null : ref.get();
        if (focus == null) return;
        // Not kept again: replySpeaking/playerSpeaking may still say true if the maximum was reached.
        boolean reply = replySpeaking;
        boolean player = playerSpeaking;
        replySpeaking = false;
        playerSpeaking = false;
        try {
            Method request = Class.forName("net.prototux.aare.audio.CarAudioOut$Focus", false, CarRadioFocus.class.getClassLoader())
                    .getMethod("request", boolean.class);
            request.invoke(focus, false);
            PatchLog.add("radio", "AARE done speaking: car's sound given back");
        } catch (Throwable t) {
            Log.w(TAG, "release", t);
            PatchLog.add("radio", "could not give the car's sound back: " + t.getClass().getSimpleName());
        } finally {
            replySpeaking = reply;
            playerSpeaking = player;
        }
    }
}
