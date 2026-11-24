package app.template.extension.aare;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Keeps the position moving in tunnels. With "Use the car's GPS", AARE gives the phone the car's GPS
 * fix; in a tunnel the car has no fix and the position stops (or the car repeats the last one), so
 * the navigation app stops too. Meanwhile the car still sends its speed, compass and gyroscope:
 * from the last good fix this estimates where the car is (dead reckoning), once a second, until the
 * GPS is back.
 *
 * Heading: the car's compass while it changes, else the last heading turned by the gyroscope's yaw
 * rate. The gyroscope's sign is learned from the GPS while driving, as cars mount it differently.
 */
public final class TunnelNavigation {
    private static final String TAG = "AareTunnel";
    public static final String KEY = PatchSettings.TUNNEL_NAVIGATION;

    /** No new GPS position for this long while moving: the estimate starts. */
    private static final long STALE_MS = 2500;
    // Limits (5 km, 5 minutes of driving) and honest accuracy: TunnelMath. Other apps get this
    // position too, so past the limits the estimate stops and the position stays put.
    private static final float MOVING_MPS = 2f;
    private static final TunnelMath.SpeedHold speedHold = new TunnelMath.SpeedHold();

    /**
     * How much to scale the car's gyroscope (turn) and speed to match its GPS, learned outside
     * tunnels: in the 24.11 test the estimate turned too little in curves and ended up on their
     * outside. Until enough measures, 1 (as sent).
     */
    private static final TunnelMath.GainLearner turnGain = new TunnelMath.GainLearner(0.5, 3, 20, 0.99);
    private static final TunnelMath.GainLearner speedGain = new TunnelMath.GainLearner(0.8, 1.25, 30, 0.995);
    /** The car's yaw rate at the previous fix, to average it over the interval. */
    private static Float previousYaw;
    /** A fix this precise or better (m) is used to learn the gains. */
    private static final float LEARN_ACCURACY_M = 15f;

    private static WeakReference<Object> feed = new WeakReference<>(null);
    private static Handler worker;

    // Last real fix.
    private static Location lastFix;
    private static long lastFixAt;
    private static long lastMovedAt;

    // The estimate.
    private static Location estimate;
    private static double travelledM;
    private static long movingMs;
    private static float startAccuracy;
    private static boolean limitLogged;
    /** "May be off the road" was logged for this estimate. */
    private static boolean wideLogged;
    private static boolean passLogged;
    // Diagnostics: where the heading came from during the current estimate.
    private static int compassSteps, gyroSteps, straightSteps, noSpeedSteps;
    private static double bearing = Double.NaN;
    private static long estimateAt;
    private static final ThreadLocal<Boolean> pushingOurs = new ThreadLocal<>();

    // Gyroscope sign, learned from GPS bearings: sum of yaw-rate × bearing-rate.
    private static double gyroCorrelation;
    private static Location previousFix;

    private TunnelNavigation() {
    }

    // region Hook

    /**
     * Called at the start of PhoneLocationFeed.push(car fix). Returns true to drop the fix: a repeat
     * of the last position while the estimate runs (some cars send it in tunnels).
     */
    public static boolean onPush(Object locationFeed, Location fix) {
        try {
            if (pushingOurs.get() != null) return false; // our estimate, on its way
            if (!PatchSettings.isEnabled(null, KEY)) return false;
            feed = new WeakReference<>(locationFeed);
            ensureWorker();
            long now = SystemClock.elapsedRealtime();

            boolean stale = false;
            if (lastFix != null) {
                Float v = carSpeed();
                speedHold.update(v, now);
                double expected = v == null ? 0 : v * (now - lastFixAt) / 1000.0;
                stale = TunnelMath.isStale(fix.distanceTo(lastFix), expected);
            }
            switch (TunnelMath.fixAction(stale, moving(), estimate != null)) {
                case DROP: return true; // the car's stale fix: keep the estimate going
                case PASS:
                    if (!passLogged) PatchLog.add("tunnel", "car repeats its fix while driving");
                    passLogged = true;
                    return false; // no GPS yet, no estimate yet: as without the feature
                default: break;
            }
            passLogged = false;
            noSpeedSteps = 0;

            synchronized (learnedLock) {
                followCar(now);
                learnGyroSign(fix);
            }
            carCompass(); // follows the compass outside tunnels too, to tell when it freezes
            if (estimate != null) {
                String back = "GPS back after " + (now - lastFixAt) / 1000 + " s, " + (int) travelledM + " m estimated, "
                        + (int) fix.distanceTo(estimate) + " m from the estimate; heading steps compass=" + compassSteps
                        + " gyro=" + gyroSteps + " straight=" + straightSteps;
                Log.i(TAG, back);
                PatchLog.add("tunnel", back);
            }
            lastFix = new Location(fix);
            lastFixAt = now;
            if (!stale) lastMovedAt = now;
            estimate = null;
            if (fix.hasBearing() && fix.hasSpeed() && fix.getSpeed() > MOVING_MPS) bearing = fix.getBearing();
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "onPush", t);
            return false;
        }
    }

    // endregion

    // region Estimate

    private static synchronized void ensureWorker() {
        if (worker != null) return;
        HandlerThread thread = new HandlerThread("aare-tunnel");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(TunnelNavigation::tick);
    }

    private static void tick() {
        try {
            step();
        } catch (Throwable t) {
            Log.w(TAG, "tick", t);
        }
        worker.postDelayed(TunnelNavigation::tick, 1000);
    }

    private static void step() {
        Object f = feed.get();
        if (f == null || lastFix == null) return;
        long now = SystemClock.elapsedRealtime();
        long sinceFix = now - Math.max(lastFixAt, lastMovedAt);
        if (sinceFix < STALE_MS) return;
        // The car's speed, a short gap filled with the last one.
        Float speed = speedHold.update(carSpeed(), now);
        if (speed == null || !connected()) {
            noSpeedSteps++;
            if (noSpeedSteps == 3) PatchLog.add("tunnel", "no GPS, but " + (speed == null ? "no fresh speed from the car" : "car not connected") + ": no estimate");
            return;
        }

        if (estimate == null) {
            estimate = new Location(lastFix);
            estimateAt = lastMovedAt;
            travelledM = 0;
            movingMs = 0;
            limitLogged = false;
            wideLogged = false;
            startAccuracy = lastFix.hasAccuracy() ? lastFix.getAccuracy() : 10f;
            if (Double.isNaN(bearing) && lastFix.hasBearing()) bearing = lastFix.getBearing();
            Log.i(TAG, "no GPS while moving: estimating from the last fix");
            compassSteps = gyroSteps = straightSteps = noSpeedSteps = 0;
            PatchLog.add("tunnel", "estimate starts; gyro learned=" + (Math.abs(gyroCorrelation) > 2)
                    + " (correlation " + (int) gyroCorrelation + "), compass "
                    + (carCompass() != null ? "live" : "frozen or missing") + ", gyro " + (carYawRate() != null ? "sent" : "missing")
                    + "; turn gain " + String.format(java.util.Locale.ROOT, "%.2f", turnGain.gain()) + " (" + turnGain.samples() + " curves)"
                    + ", speed gain " + String.format(java.util.Locale.ROOT, "%.2f", speedGain.gain()));
        }
        if (Double.isNaN(bearing)) return;

        double dt = (now - estimateAt) / 1000.0;
        estimateAt = now;
        if (dt <= 0 || dt > 5) return;

        // Heading: compass when the car updates it, else turn by the gyroscope.
        Float compass = carCompass();
        Float yaw = carYawRate();
        if (compass != null) {
            bearing = compass;
            compassSteps++;
        } else if (yaw != null && Math.abs(gyroCorrelation) > 2) {
            double sign = Math.signum(gyroCorrelation);
            bearing = TunnelMath.turn(bearing, yaw * turnGain.gain(), sign, dt);
            gyroSteps++;
        } else {
            straightSteps++; // no heading source: straight on
        }

        double speedNow = speed * speedGain.gain();
        double distance = speedNow * dt;
        long moved = speed > MOVING_MPS ? (long) (dt * 1000) : 0;
        if (!TunnelMath.withinLimits(travelledM + distance, movingMs + moved)) {
            if (!limitLogged) {
                String stop = "estimate stopped at the limit after " + (int) travelledM + " m, " + movingMs / 1000 + " s of driving";
                Log.i(TAG, stop);
                PatchLog.add("tunnel", stop);
            }
            limitLogged = true;
            return;
        }
        travelledM += distance;
        movingMs += moved;
        Location next = destination(estimate, bearing, distance);
        next.setSpeed((float) speedNow);
        next.setBearing((float) bearing);
        float accuracy = TunnelMath.accuracy(startAccuracy, travelledM);
        if (!wideLogged && accuracy > TunnelMath.ROUTE_SNAP_M) {
            wideLogged = true;
            PatchLog.add("tunnel", "estimate may now be more than " + (int) TunnelMath.ROUTE_SNAP_M
                    + " m off the road after " + (int) travelledM + " m (apps snap to their route only within that)");
        }
        next.setAccuracy(TunnelMath.reportedAccuracy(accuracy));
        next.setTime(System.currentTimeMillis());
        next.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        estimate = next;

        pushingOurs.set(Boolean.TRUE);
        try {
            Method push = f.getClass().getMethod("push", Location.class);
            push.invoke(f, next);
        } catch (Throwable t) {
            Log.w(TAG, "push", t);
        } finally {
            pushingOurs.remove();
        }
    }

    /**
     * Learns from the GPS while driving which way the gyroscope turns, then how much (turnGain),
     * and how the car's speed compares with the GPS one (speedGain).
     */
    private static void learnGyroSign(Location fix) {
        Location prev = previousFix;
        previousFix = new Location(fix);
        Float yaw = carYawRate();
        Float yawBefore = previousYaw;
        previousYaw = yaw;
        boolean precise = fix.hasAccuracy() && fix.getAccuracy() <= LEARN_ACCURACY_M;
        learnSpeedGain(fix, precise);
        if (prev == null || !fix.hasBearing() || !prev.hasBearing() || fix.getSpeed() < 5) return;
        double dt = (fix.getElapsedRealtimeNanos() - prev.getElapsedRealtimeNanos()) / 1e9;
        if (dt <= 0.2 || dt > 3) return;
        double rate = Math.toRadians(wrap(fix.getBearing() - prev.getBearing())) / dt; // clockwise, rad/s
        if (yaw == null || Math.abs(rate) < 0.03) return;
        boolean before = Math.abs(gyroCorrelation) > 2;
        gyroCorrelation = Math.max(-50, Math.min(50, gyroCorrelation + Math.signum(rate * yaw)));
        if (!before && Math.abs(gyroCorrelation) > 2) {
            PatchLog.add("tunnel", "gyroscope direction learned (" + (gyroCorrelation > 0 ? "as sent" : "reversed") + ")");
            saveLearned();
        }
        // How much it turns, once the direction is known: the GPS turn over the interval against
        // the gyroscope's mean over it.
        if (Math.abs(gyroCorrelation) > 2 && precise && yawBefore != null) {
            boolean ready = turnGain.ready();
            turnGain.add(rate, (yawBefore + yaw) / 2.0 * Math.signum(gyroCorrelation));
            if (!ready && turnGain.ready()) {
                PatchLog.add("tunnel", "turn gain learned: " + String.format(java.util.Locale.ROOT, "%.2f", turnGain.gain()));
                saveLearned();
            }
        }
    }

    /** The car's speed against the GPS one, while driving with a precise fix. */
    private static void learnSpeedGain(Location fix, boolean precise) {
        Float v = carSpeed();
        if (!precise || v == null || v < 5 || !fix.hasSpeed() || fix.getSpeed() < 5) return;
        boolean ready = speedGain.ready();
        speedGain.add(fix.getSpeed(), v);
        if (!ready && speedGain.ready()) saveLearned();
    }

    private static Location destination(Location from, double bearingDeg, double meters) {
        double[] to = TunnelMath.destination(from.getLatitude(), from.getLongitude(), bearingDeg, meters);
        Location out = new Location(from);
        out.setLatitude(to[0]);
        out.setLongitude(to[1]);
        return out;
    }

    private static double wrap(double deg) {
        return TunnelMath.wrap(deg);
    }

    // endregion

    // region Learned values, kept per car

    /** AARE's private storage, excluded from backup (AARE sets allowBackup=false). */
    private static final String LEARNED_PREFS = "aare_patches_tunnel";
    private static final String CAR_PREFIX = "car_";
    /** Cars kept: past it, the one saved longest ago is dropped. */
    private static final int MAX_CARS = 5;
    /** While driving, what was learned is saved this often: the car can disconnect at any time. */
    private static final long SAVE_EVERY_MS = 5 * 60_000L;

    /** Guards the learned values: learnt on the feed's thread, deleted from the Settings screen. */
    private static final Object learnedLock = new Object();
    /** The car whose learned values are in use (see carKey), or null. */
    private static String learnedCar;
    private static long learnedSavedAt;

    /** Uses the connected car's learned values, loading them when the car changes, and saves now and then. */
    private static void followCar(long now) {
        String car = carKey();
        if (car == null) return;
        if (!car.equals(learnedCar)) {
            if (learnedCar != null) saveLearned();
            loadLearned(car);
            learnedSavedAt = now;
        } else if (now - learnedSavedAt >= SAVE_EVERY_MS) {
            saveLearned();
            learnedSavedAt = now;
        }
    }

    private static SharedPreferences learnedPrefs() {
        Context app = PatchSettings.application();
        return app == null ? null : app.getSharedPreferences(LEARNED_PREFS, Context.MODE_PRIVATE);
    }

    private static void loadLearned(String car) {
        learnedCar = car;
        SharedPreferences prefs = learnedPrefs();
        double corr = TunnelMath.Learned.decode(prefs == null ? null : prefs.getString(CAR_PREFIX + car, null), turnGain, speedGain);
        if (Double.isNaN(corr)) {
            gyroCorrelation = 0;
            PatchLog.add("tunnel", "nothing learned yet for this car: learning from the GPS");
            return;
        }
        gyroCorrelation = corr;
        PatchLog.add("tunnel", "learned values for this car restored: gyroscope " + (Math.abs(corr) > 2 ? "known" : "not known yet")
                + ", turn gain " + String.format(java.util.Locale.ROOT, "%.2f", turnGain.gain()) + " (" + turnGain.samples() + " curves)"
                + ", speed gain " + String.format(java.util.Locale.ROOT, "%.2f", speedGain.gain()));
    }

    /** Saves what was learned for the current car, if anything (call with learnedLock held). */
    private static void saveLearned() {
        if (learnedCar == null || (turnGain.samples() == 0 && speedGain.samples() == 0 && gyroCorrelation == 0)) return;
        SharedPreferences prefs = learnedPrefs();
        if (prefs == null) return;
        try {
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(CAR_PREFIX + learnedCar,
                    TunnelMath.Learned.encode(System.currentTimeMillis(), gyroCorrelation, turnGain, speedGain));
            // Keep at most MAX_CARS: drop the ones saved longest ago, never the current one.
            java.util.List<java.util.Map.Entry<String, ?>> others = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                if (e.getKey().startsWith(CAR_PREFIX) && !e.getKey().equals(CAR_PREFIX + learnedCar)) others.add(e);
            }
            java.util.Collections.sort(others, (a, b) -> Long.compare(
                    TunnelMath.Learned.savedAt(String.valueOf(a.getValue())), TunnelMath.Learned.savedAt(String.valueOf(b.getValue()))));
            for (int i = 0; i < others.size() - (MAX_CARS - 1); i++) edit.remove(others.get(i).getKey());
            edit.apply();
        } catch (Throwable t) {
            Log.w(TAG, "saveLearned", t);
        }
    }

    /** Deletes what was learned, for every car (tunnel navigation switched off). */
    static void forgetLearned(Context context) {
        synchronized (learnedLock) {
            turnGain.reset();
            speedGain.reset();
            gyroCorrelation = 0;
            learnedCar = null;
            try {
                context.getApplicationContext().getSharedPreferences(LEARNED_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
                PatchLog.add("tunnel", "switched off: learned values deleted");
            } catch (Throwable t) {
                Log.w(TAG, "forgetLearned", t);
            }
        }
    }

    /**
     * The connected car, as a short hash of the make, model and year it reports: the same for every
     * car of that model, nothing about the phone or its owner. Null if not connected or not reported.
     */
    private static String carKey() {
        try {
            Object s = state();
            if (s == null || !(boolean) s.getClass().getMethod("getConnected").invoke(s)) return null;
            Object id = s.getClass().getMethod("getIdentity").invoke(s);
            if (id == null) return null;
            String make = String.valueOf(id.getClass().getMethod("getMake").invoke(id)).trim();
            String model = String.valueOf(id.getClass().getMethod("getModel").invoke(id)).trim();
            Object year = id.getClass().getMethod("getYear").invoke(id);
            return TunnelMath.Learned.carKey(make, model, year == null ? "" : year.toString());
        } catch (Throwable t) {
            return null;
        }
    }

    // endregion

    // region Car data (AARE's CarData)

    private static Object state() {
        try {
            Class<?> carData = Class.forName("net.prototux.aare.car.CarData", false, TunnelNavigation.class.getClassLoader());
            Object instance = carData.getField("INSTANCE").get(null);
            Object flow = carData.getMethod("getState").invoke(instance);
            return flow.getClass().getMethod("getValue").invoke(flow);
        } catch (Throwable t) {
            return null;
        }
    }

    /** A sensor value of [state] no older than two seconds, or null. */
    private static Object fresh(String getter) {
        try {
            Object s = state();
            if (s == null) return null;
            Object timed = s.getClass().getMethod(getter).invoke(s);
            if (timed == null) return null;
            long at = (long) timed.getClass().getMethod("getAtMillis").invoke(timed);
            if (System.currentTimeMillis() - at > 2000) return null;
            return timed.getClass().getMethod("getValue").invoke(timed);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whether a car is connected now (AARE's CarData). Also used by the voice reply. */
    static boolean connected() {
        try {
            Object s = state();
            return s != null && (boolean) s.getClass().getMethod("getConnected").invoke(s);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean moving() {
        Float v = carSpeed();
        return v != null && v > MOVING_MPS;
    }

    /** m/s: the car's speed, else the mean of its wheel speeds. */
    private static Float carSpeed() {
        try {
            Object speed = fresh("getSpeed");
            if (speed != null) return Math.abs((float) speed.getClass().getMethod("getMetersPerSecond").invoke(speed));
            Object dr = fresh("getDeadReckoning");
            if (dr != null) {
                List<?> wheels = (List<?>) dr.getClass().getMethod("getWheelSpeedsMps").invoke(dr);
                if (wheels != null && !wheels.isEmpty()) {
                    double sum = 0;
                    for (Object w : wheels) sum += ((Number) w).doubleValue();
                    return (float) Math.abs(sum / wheels.size());
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static final TunnelMath.CompassWatch compassWatch = new TunnelMath.CompassWatch();

    /**
     * The car's compass, only while it moves. Many cars derive it from their GPS: in a tunnel it
     * freezes on the last heading, and following it the estimate went straight on in curves, off the
     * outside of the road.
     */
    private static Float carCompass() {
        try {
            Object c = fresh("getCompass");
            if (c == null) return null;
            float b = (float) c.getClass().getMethod("getBearingDeg").invoke(c);
            return compassWatch.update(b, SystemClock.elapsedRealtime());
        } catch (Throwable t) {
            return null;
        }
    }

    /** rad/s around the vertical axis, as the car sends it (sign learned separately). */
    private static Float carYawRate() {
        try {
            Object g = fresh("getGyroscope");
            if (g == null) return null;
            return (Float) g.getClass().getMethod("getZ").invoke(g);
        } catch (Throwable t) {
            return null;
        }
    }

    // endregion
}
