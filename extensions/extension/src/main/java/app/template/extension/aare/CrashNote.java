package app.template.extension.aare;

import android.util.Log;

/**
 * When AARE stops because of an error nobody caught, notes in the patches' diagnostics what it was
 * and where: the error's class and the code it went through (classes, methods, lines), so a restart
 * in the car can be traced. Never the error's message, which may hold data (a name, a text, a
 * path): only those names. Then Android's own handling goes on as before (the app still stops).
 */
public final class CrashNote {
    private static final String TAG = "AarePatchesCrash";
    /** Frames noted per error, and causes followed. */
    static final int FRAMES = 8;
    static final int CAUSES = 3;
    private static boolean installed;

    private CrashNote() {
    }

    /** Called at the start of AareApp.onCreate. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                PatchLog.addAndWait("crash", "AARE stopped by an error on the " + (thread == null ? "?" : kind(thread))
                        + " thread: " + describe(error), 1000);
            } catch (Throwable t) {
                Log.w(TAG, "note", t);
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** "main" or "background": a thread's name can carry an app's own words, so only this. */
    private static String kind(Thread t) {
        return t == android.os.Looper.getMainLooper().getThread() ? "main" : "background";
    }

    /** The error's class and frames, and its causes': names only, never a message. */
    static String describe(Throwable error) {
        StringBuilder b = new StringBuilder();
        Throwable e = error;
        for (int c = 0; e != null && c <= CAUSES; c++, e = e.getCause()) {
            if (c > 0) b.append(" | caused by ");
            b.append(e.getClass().getName());
            StackTraceElement[] st = e.getStackTrace();
            for (int i = 0; i < Math.min(FRAMES, st.length); i++) {
                StackTraceElement f = st[i];
                b.append(i == 0 ? " at " : " < ").append(f.getClassName()).append('.').append(f.getMethodName())
                        .append(':').append(f.getLineNumber());
            }
        }
        return b.toString();
    }
}
