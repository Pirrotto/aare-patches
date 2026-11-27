package app.template.extension.aare;

import android.app.Service;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * Whether AARE's projection service runs in the foreground with the microphone type.
 *
 * AARE asks for its foreground service with the microphone type and, if Android refuses it, starts
 * without it and only says so in logcat (ProjectionService.goForeground). Android refuses it when
 * AARE starts by itself (a wireless car, the phone locked in a pocket). Without it, while the phone
 * is locked, apps listening for AARE (the voice reply's speech recognition) hear nothing. The voice
 * reply's diagnostics say so, so a "never started" listening can be told apart.
 *
 * Hook at the start of ProjectionService.goForeground$start(service, types), one attempt: a moment
 * after the last attempt, the service's own foreground types (Service.getForegroundServiceType,
 * Android 10+) say which one Android accepted.
 */
// Public: called from AARE's ProjectionService (another package).
public final class MicForeground {
    private static final String TAG = "AarePatchesMic";
    /** android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE (API 30). */
    static final int TYPE_MICROPHONE = 128;

    /** After the last attempt, when the service's types are read. */
    private static final long CHECK_MS = 1_000;
    /** The types AARE's service runs with, or -1 before the first start. */
    private static volatile int running = -1;
    private static Handler main;
    private static WeakReference<Service> service = new WeakReference<>(null);
    /** The service instance the last line was about: a new one (AARE restarted) is noted again. */
    private static WeakReference<Service> noted = new WeakReference<>(null);
    private static final Runnable check = MicForeground::check;

    private MicForeground() {
    }

    /** Whether [types] holds the microphone. */
    static boolean hasMicrophone(int types) {
        return types >= 0 && (types & TYPE_MICROPHONE) != 0;
    }

    /** The diagnostics line for a start with [types]. */
    static String line(int types) {
        return hasMicrophone(types)
                ? "AARE runs in the foreground with the microphone"
                : "AARE runs in the foreground without the microphone (Android refused it, as when AARE starts by itself with the phone locked); with the phone locked, speech recognition may hear nothing";
    }

    /** At the start of goForeground$start: AARE tries to run in the foreground. Never throws. */
    public static void trying(Object projectionService) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !(projectionService instanceof Service)) return;
            service = new WeakReference<>((Service) projectionService);
            if (main == null) main = new Handler(Looper.getMainLooper());
            main.removeCallbacks(check);
            main.postDelayed(check, CHECK_MS);
        } catch (Throwable t) {
            Log.w(TAG, "trying", t);
        }
    }

    /** Main thread, after the attempts: the types Android accepted; noted when the microphone changed. */
    private static void check() {
        try {
            Service s = service.get();
            if (s == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
            int types = s.getForegroundServiceType();
            boolean changed = running < 0 || hasMicrophone(running) != hasMicrophone(types) || s != noted.get();
            noted = new WeakReference<>(s);
            running = types;
            if (changed) PatchLog.add("mic", line(types));
        } catch (Throwable t) {
            Log.w(TAG, "check", t);
        }
    }

    /** For the voice reply's diagnostics: "", or why the phone may not hear. */
    static String note() {
        int types = running;
        return types >= 0 && !hasMicrophone(types) ? "; AARE has no microphone in the background" : "";
    }
}
