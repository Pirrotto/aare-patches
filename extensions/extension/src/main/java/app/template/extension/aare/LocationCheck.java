package app.template.extension.aare;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/**
 * Test diagnostics for the black or off-centre map at the start of a drive: Vela got no location
 * until AARE or Vela was opened on the phone (24.11 test), although the car's fixes reached the
 * phone. For the first minutes of each car session it notes, every 15 s, whether AARE's and the
 * navigation apps' processes may read the location (their capabilities, through Shizuku's shell
 * rights and {@link LocationCheckShell}), and logs it when it changes. Only process states: no
 * position, nothing else of the phone.
 */
final class LocationCheck {
    private static final String TAG = "AareLocationCheck";
    static final String KEY = PatchSettings.LOCATION_CHECK;

    /** Navigation apps that run on the car screen through AARE (car app library), besides AARE. */
    private static final String[] NAVIGATION_APPS = {
            "app.vela", "app.organicmaps", "app.comaps", "net.osmand", "net.osmand.plus",
    };
    private static final long EVERY_MS = 15_000L;
    private static final long FOR_MS = 5 * 60_000L;

    private static Handler worker;
    private static long startedAt;
    private static int session;
    private static String lastLine;
    private static final Object lock = new Object();
    private static Connection connection;
    private static IBinder binder;

    private LocationCheck() {
    }

    /** A new car session began (its first car fix): checks for the next minutes. Any thread. */
    static void sessionStarted() {
        if (!PatchSettings.isEnabled(null, KEY)) return;
        Handler h = worker();
        h.post(() -> {
            session++;
            startedAt = SystemClock.elapsedRealtime();
            lastLine = null;
            final int mine = session;
            h.removeCallbacksAndMessages(null);
            h.post(new Runnable() {
                @Override
                public void run() {
                    if (mine != session) return;
                    check();
                    if (SystemClock.elapsedRealtime() - startedAt < FOR_MS) h.postDelayed(this, EVERY_MS);
                }
            });
        });
    }

    private static synchronized Handler worker() {
        if (worker == null) {
            HandlerThread t = new HandlerThread("aare-location-check");
            t.start();
            worker = new Handler(t.getLooper());
        }
        return worker;
    }

    private static void check() {
        Context app = PatchSettings.application();
        if (app == null) return;
        long sinceS = (SystemClock.elapsedRealtime() - startedAt) / 1000;
        IBinder b = helper(app);
        if (b == null) {
            log(sinceS, "Shizuku not running or not allowed for AARE: no check");
            return;
        }
        // All asked for: Android may hide whether another app is installed, the helper is not limited by that.
        List<String> packages = new ArrayList<>();
        packages.add(app.getPackageName());
        for (String p : NAVIGATION_APPS) packages.add(p);
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(LocationCheckShell.DESCRIPTOR);
            data.writeInt(packages.size());
            for (String p : packages) data.writeString(p);
            b.transact(LocationCheckShell.TRANSACTION_CHECK, data, reply, 0);
            reply.readException();
            Map<String, String> states = new LinkedHashMap<>();
            int n = reply.readInt();
            for (int i = 0; i < n; i++) {
                String p = reply.readString();
                String procState = reply.readString();
                String capability = reply.readString();
                ProcessCaps.State s = new ProcessCaps.State();
                s.capability = capability == null ? "?" : capability;
                Boolean loc = s.location();
                states.put(p, "state " + procState + ", location " + (loc == null ? "?" : loc ? "yes" : "NO") + " (" + s.capability + ")");
            }
            StringBuilder line = new StringBuilder();
            for (Map.Entry<String, String> e : states.entrySet()) {
                if (line.length() > 0) line.append("; ");
                line.append(e.getKey().equals(app.getPackageName()) ? "AARE" : e.getKey()).append(": ").append(e.getValue());
            }
            if (!states.containsKey(app.getPackageName())) line.insert(0, "AARE not found; ");
            if (states.size() <= 1) line.append(" (no navigation app running)");
            log(sinceS, line.toString());
        } catch (Throwable t) {
            Log.w(TAG, "check", t);
            forget();
            log(sinceS, "check failed: " + t.getClass().getSimpleName());
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /** Logs [line] when it differs from the last one, so the moment it changes (an app opened) shows. */
    private static void log(long sinceS, String line) {
        if (line.equals(lastLine)) return;
        lastLine = line;
        PatchLog.add("location", "+" + sinceS + " s: " + line);
    }

    // region Shizuku helper (as in VoiceNotesAccess)

    private static final class Connection implements ServiceConnection {
        final Shizuku.UserServiceArgs args;
        final CountDownLatch bound = new CountDownLatch(1);

        Connection(Shizuku.UserServiceArgs args) {
            this.args = args;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (lock) {
                if (connection == this && service != null && service.pingBinder()) binder = service;
            }
            bound.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (lock) {
                if (connection == this) binder = null;
            }
        }
    }

    private static IBinder helper(Context context) {
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
        Connection c;
        synchronized (lock) {
            if (binder != null && binder.pingBinder()) return binder;
            binder = null;
            if (connection != null && connection.bound.getCount() == 0) forget();
            if (connection == null) {
                Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                        new ComponentName(context.getPackageName(), LocationCheckShell.class.getName()))
                        .daemon(false)
                        .processNameSuffix("locationcheck")
                        .debuggable(false)
                        .version(1);
                Connection nc = new Connection(args);
                connection = nc;
                try {
                    Shizuku.bindUserService(args, nc);
                } catch (Throwable t) {
                    Log.w(TAG, "Shizuku bind", t);
                    connection = null;
                    return null;
                }
            }
            c = connection;
        }
        try {
            if (!c.bound.await(5, TimeUnit.SECONDS)) {
                forget();
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        synchronized (lock) {
            return binder;
        }
    }

    private static void forget() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    Shizuku.unbindUserService(connection.args, connection, true);
                } catch (Throwable t) {
                    Log.w(TAG, "Shizuku unbind", t);
                }
            }
            connection = null;
            binder = null;
        }
    }

    // endregion
}
