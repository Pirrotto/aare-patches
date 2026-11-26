package app.template.extension.aare;

import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The navigation app's next maneuver, as AARE passes it to the car's cluster (NavStatusBridge.update,
 * the hosted app's Trip): its side and how far it is. The tunnel estimate uses it to take the right
 * branch where a tunnel of the maps splits. Only the latest one is held, in memory, never logged or
 * kept: it is read through the Car App Library's public getters, by reflection (AARE carries the
 * library, the patches don't).
 */
final class TunnelManeuver {
    private static final String TAG = "AarePatchesManeuver";
    /** A maneuver older than this is no longer the app's word: Vela sends one about every second while guiding. */
    static final long FRESH_MS = 5_000;
    /** A maneuver farther than this (m) is past the junction: here the route goes straight on. */
    static final double NEAR_M = 200;

    private static volatile TunnelBranch.Side side = TunnelBranch.Side.UNKNOWN;
    private static volatile double meters = Double.NaN;
    private static volatile long at;
    private static Map<Integer, String> typeNames;
    private static Map<Integer, Double> unitMeters;

    private TunnelManeuver() {
    }

    /** Called at the start of NavStatusBridge.update(Trip): never throws, nothing else changes. */
    public static void onTrip(Object trip) {
        try {
            if (trip == null || !PatchSettings.isEnabled(null, PatchSettings.TUNNEL_NAVIGATION)) return;
            List<?> steps = (List<?>) call(trip, "getSteps");
            if (steps == null || steps.isEmpty()) {
                side = TunnelBranch.Side.UNKNOWN;
                meters = Double.NaN;
                at = SystemClock.elapsedRealtime();
                return;
            }
            Object maneuver = call(steps.get(0), "getManeuver");
            String name = maneuver == null ? null : typeName(maneuver.getClass(), (Integer) call(maneuver, "getType"));
            double m = Double.NaN;
            List<?> estimates = (List<?>) call(trip, "getStepTravelEstimates");
            if (estimates != null && !estimates.isEmpty()) {
                Object distance = call(estimates.get(0), "getRemainingDistance");
                if (distance != null) {
                    double value = (Double) call(distance, "getDisplayDistance");
                    Double perUnit = unitMeters(distance.getClass()).get((Integer) call(distance, "getDisplayUnit"));
                    if (perUnit != null && value >= 0) m = value * perUnit;
                }
            }
            side = TunnelBranch.side(name);
            meters = m;
            at = SystemClock.elapsedRealtime();
        } catch (Throwable t) {
            Log.w(TAG, "onTrip", t);
        }
    }

    /**
     * What to do at a junction now: the maneuver's side if it is fresh and close, STRAIGHT if the
     * app is guiding with its next maneuver farther on, UNKNOWN without guidance.
     */
    static TunnelBranch.Side here() {
        if (SystemClock.elapsedRealtime() - at > FRESH_MS) return TunnelBranch.Side.UNKNOWN;
        double m = meters;
        TunnelBranch.Side s = side;
        if (Double.isNaN(m)) return TunnelBranch.Side.UNKNOWN;
        return m <= NEAR_M ? s : TunnelBranch.Side.STRAIGHT;
    }

    /** Drops the held maneuver (the car disconnected). */
    static void forget() {
        side = TunnelBranch.Side.UNKNOWN;
        meters = Double.NaN;
        at = 0;
    }

    private static Object call(Object o, String method) throws ReflectiveOperationException {
        return o.getClass().getMethod(method).invoke(o);
    }

    /** The Maneuver.TYPE_* name of [type], read from the class's own constants. */
    private static synchronized String typeName(Class<?> maneuverClass, Integer type) {
        if (type == null) return null;
        if (typeNames == null) typeNames = constants(maneuverClass, "TYPE_");
        return typeNames.get(type);
    }

    /** Metres per Distance.UNIT_* (from their names, so no number is assumed). */
    private static synchronized Map<Integer, Double> unitMeters(Class<?> distanceClass) {
        if (unitMeters != null) return unitMeters;
        Map<Integer, Double> out = new HashMap<>();
        for (Map.Entry<Integer, String> e : constants(distanceClass, "UNIT_").entrySet()) {
            String n = e.getValue();
            Double v = n.startsWith("UNIT_METERS") ? Double.valueOf(1) : n.startsWith("UNIT_KILOMETERS") ? Double.valueOf(1000)
                    : n.startsWith("UNIT_MILES") ? Double.valueOf(1609.344) : n.equals("UNIT_FEET") ? Double.valueOf(0.3048)
                    : n.equals("UNIT_YARDS") ? Double.valueOf(0.9144) : null;
            if (v != null) out.put(e.getKey(), v);
        }
        unitMeters = out;
        return out;
    }

    private static Map<Integer, String> constants(Class<?> c, String prefix) {
        Map<Integer, String> out = new HashMap<>();
        for (Field f : c.getFields()) {
            int mod = f.getModifiers();
            if (!Modifier.isStatic(mod) || f.getType() != int.class || !f.getName().startsWith(prefix)) continue;
            try {
                out.put(f.getInt(null), f.getName());
            } catch (IllegalAccessException e) {
                Log.w(TAG, "constant " + f.getName(), e);
            }
        }
        return out;
    }
}
