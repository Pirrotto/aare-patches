package app.template.extension.aare;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * The voice button with an assistant whose car app doesn't accept AARE (Google's, which only answers
 * Android Auto): instead of a bar that waits, AARE opens the assistant the way it opens one with no
 * car app, on the car screen ("Open assistants without a car app on the car screen", Shizuku) or on
 * the phone. And, with its own switch, AARE's car-screen assistant also while driving.
 *
 * Called from CarRuntime.assistant() and CarAppConnection.fail(String); AARE's objects are reached
 * through their public methods, by reflection. Only package names and timings are kept, in memory.
 */
// Public: called from AARE's classes in other packages (see HookTargetsTest).
public final class AssistantCarScreen {
    private static final String TAG = "AarePatchesAssistant";
    /** How long a car app may take to come up before the voice button stops waiting for it. */
    static final long WAIT_MS = 5_000;
    /** A car app's failure this soon after the press is an answer to it (AARE gives up after 10 s). */
    static final long PRESS_WINDOW_MS = 15_000;

    /** Made on first use: the decisions above are tested on the JVM, where there is no main looper. */
    private static Handler main;
    /** Car apps that refused AARE in this run of AARE: the voice button goes straight past them. */
    private static final Set<String> refused = new HashSet<>();
    /** The package to go past once, for the press that falls back. */
    private static String skipOnce;
    private static String pressedPkg;
    private static long pressedAt;
    private static int press;
    private static boolean fellBack;
    private static WeakReference<Object> runtime = new WeakReference<>(null);

    private AssistantCarScreen() {
    }

    // region Decisions (no Android, tested)

    /** Whether the voice button goes past the assistant's car app. */
    static boolean skip(boolean enabled, boolean refusedBefore, boolean once) {
        return enabled && (refusedBefore || once);
    }

    /** The driving restriction AARE's car-screen assistant sees: lifted only with both switches on. */
    static boolean restricted(boolean restricted, boolean enabled, boolean whileDriving) {
        return restricted && !(enabled && whileDriving);
    }

    /** Whether the car app of this press is still not up, so the press falls back. */
    static boolean waitedInVain(String status) {
        return "CONNECTING".equals(status) || "FAILED".equals(status);
    }

    /** Whether a failure of [pkg]'s car app answers the last press. */
    static boolean answersPress(String pkg, String pressed, long sincePressMs) {
        return pkg != null && pkg.equals(pressed) && sincePressMs >= 0 && sincePressMs <= PRESS_WINDOW_MS;
    }

    // endregion

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static boolean enabled() {
        return PatchSettings.isEnabled(null, PatchSettings.ASSISTANT_CAR_SCREEN);
    }

    /**
     * In CarRuntime.assistant(), on the assistant's car app connection (null if it has none): the
     * connection to use, or null to open the assistant as one without a car app. Main thread; never throws.
     */
    public static Object carApp(Object connection) {
        try {
            if (connection == null || !enabled()) return connection;
            String pkg = packageOf(connection);
            if (pkg == null) return connection;
            boolean once = pkg.equals(skipOnce);
            skipOnce = null;
            if (skip(true, refused.contains(pkg), once)) {
                PatchLog.add("assistant", "its car app doesn't answer AARE, opened without it");
                return null;
            }
            runtime = new WeakReference<>(activeRuntime());
            pressedPkg = pkg;
            pressedAt = SystemClock.elapsedRealtime();
            fellBack = false;
            final int thisPress = ++press;
            main().postDelayed(() -> {
                if (thisPress != press || fellBack) return;
                String status = statusOf(connection);
                if (waitedInVain(status)) fallBack(pkg, "no answer in " + WAIT_MS / 1000 + " s");
            }, WAIT_MS);
            return connection;
        } catch (Throwable t) {
            Log.w(TAG, "carApp", t);
            return connection;
        }
    }

    /** At the start of CarAppConnection.fail(String): AARE gave up on a car app. Never throws. */
    public static void carAppFailed(Object connection) {
        try {
            if (connection == null || !enabled()) return;
            String pkg = packageOf(connection);
            if (!answersPress(pkg, pressedPkg, SystemClock.elapsedRealtime() - pressedAt)) return;
            if (refused.add(pkg)) PatchLog.add("assistant", "its car app refused AARE; next presses go past it");
            if (!fellBack) main().post(() -> fallBack(pkg, "car app failed"));
        } catch (Throwable t) {
            Log.w(TAG, "carAppFailed", t);
        }
    }

    /**
     * In CarRuntime.assistant() and applyScreenSettings(), on ui.drivingRestricted where it keeps
     * AARE's car-screen assistant parked only. Never throws.
     */
    public static boolean drivingRestricted(boolean restricted) {
        try {
            if (!restricted) return false;
            return restricted(true, enabled(), PatchSettings.isEnabled(null, PatchSettings.ASSISTANT_CAR_SCREEN_DRIVING));
        } catch (Throwable t) {
            Log.w(TAG, "drivingRestricted", t);
            return restricted;
        }
    }

    /** Closes the waiting bar and presses again, past the car app (main thread). */
    private static void fallBack(String pkg, String why) {
        if (fellBack) return;
        Object r = runtime.get();
        if (r == null) return;
        fellBack = true;
        try {
            // The driver closed the bar meanwhile: nothing to reopen.
            Object ui = r.getClass().getMethod("getUi").invoke(r);
            if (ui == null || ui.getClass().getMethod("getAssistantAppId").invoke(ui) == null) return;
            PatchLog.add("assistant", why + ", opened without its car app");
            r.getClass().getMethod("stopAssistant").invoke(r);
            skipOnce = pkg;
            r.getClass().getMethod("assistant").invoke(r);
        } catch (Throwable t) {
            skipOnce = null;
            PatchLog.add("assistant", "couldn't open it without its car app: " + t.getClass().getSimpleName());
            Log.w(TAG, "fallBack", t);
        }
    }

    /** CarRuntime.Companion.getActive(): the car screen's runtime, the one whose button was pressed. */
    private static Object activeRuntime() throws ReflectiveOperationException {
        Class<?> type = Class.forName("net.prototux.aare.host.CarRuntime");
        Object companion = type.getField("Companion").get(null);
        return companion.getClass().getMethod("getActive").invoke(companion);
    }

    private static String packageOf(Object connection) throws ReflectiveOperationException {
        Object info = connection.getClass().getMethod("getInfo").invoke(connection);
        if (info == null) return null;
        Object pkg = info.getClass().getMethod("getPackageName").invoke(info);
        return pkg instanceof String ? (String) pkg : null;
    }

    private static String statusOf(Object connection) {
        try {
            Object s = connection.getClass().getMethod("getStatus").invoke(connection);
            return s instanceof Enum ? ((Enum<?>) s).name() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
