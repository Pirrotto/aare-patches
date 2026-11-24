package app.template.extension.aare;

/**
 * The tunnel estimate's maths and decisions, with no Android in it so it can be unit-tested
 * (TunnelMathTest). TunnelNavigation feeds it the car's data and pushes the result.
 */
final class TunnelMath {
    /** Past this distance without GPS an estimate is too far off to help: it stops. */
    static final double MAX_DISTANCE_M = 5000;
    /** Past this much driving (not standing) without GPS it stops too. */
    static final long MAX_MOVING_MS = 5 * 60_000;
    /**
     * Honest accuracy: the error grows by about 6 % of the distance driven without GPS (heading a
     * few degrees off, speed a few percent), on top of the last fix's own accuracy.
     */
    static final double ERROR_PER_M = 0.06;
    /** A compass that hasn't moved for this long is frozen (cars that derive it from their GPS). */
    static final long COMPASS_FROZEN_MS = 2500;

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private TunnelMath() {
    }

    /** What to do with a fix the car sends. */
    enum FixAction {
        /** A real fix: record it, and drop any estimate. */
        USE,
        /** A repeat of the last fix while driving, before any estimate: let it through, don't record it. */
        PASS,
        /** A repeat while estimating, moving or standing (a queue in the tunnel): keep the estimate. */
        DROP,
    }

    /**
     * Whether a fix [movedM] from the last one is the car's stale position, when driving should have
     * moved it [expectedM] (speed × time since the last fix). In tunnels some cars repeat their last
     * fix with a few metres of jitter: a fixed 1 m threshold took those as real and froze the
     * position. A real fix moves at least a third of the expected distance.
     */
    static boolean isStale(double movedM, double expectedM) {
        if (movedM < 1) return true;
        return expectedM >= 3 && movedM < expectedM / 3;
    }

    /**
     * [stale]: see isStale. While estimating, a stale fix is always dropped: standing in a queue in
     * the tunnel it used to reset the estimate back to the tunnel's entrance.
     */
    static FixAction fixAction(boolean stale, boolean moving, boolean estimating) {
        if (!stale) return FixAction.USE;
        if (estimating) return FixAction.DROP;
        return moving ? FixAction.PASS : FixAction.USE;
    }

    static boolean withinLimits(double travelledM, long movingMs) {
        return travelledM <= MAX_DISTANCE_M && movingMs <= MAX_MOVING_MS;
    }

    /** The accuracy of an estimate [travelledM] metres from a fix of accuracy [start]. */
    static float accuracy(float start, double travelledM) {
        return (float) (Math.max(start, 3f) + ERROR_PER_M * travelledM);
    }

    /**
     * The accuracy reported to the apps, at most this. Navigation apps ignore fixes worse than 50 m
     * (Vela on the car screen does: VelaCarSession, CarMapRenderer), so past about 750 m of tunnel
     * the estimate was dropped and the arrow stopped. The estimate still stops at its limits
     * (MAX_DISTANCE_M, MAX_MOVING_MS).
     */
    static final float REPORTED_ACCURACY_MAX_M = 45f;

    /** The accuracy to report for [accuracy]: never worse than [REPORTED_ACCURACY_MAX_M]. */
    static float reportedAccuracy(float accuracy) {
        return Math.min(accuracy, REPORTED_ACCURACY_MAX_M);
    }

    /** A navigation app keeps the position on its route only within about this distance of it. */
    static final float ROUTE_SNAP_M = 40f;

    /** The heading after [dtS] seconds turning at [yawRadS], the gyroscope's sign learned as [sign]. */
    static double turn(double bearingDeg, double yawRadS, double sign, double dtS) {
        return normalize(bearingDeg + sign * Math.toDegrees(yawRadS) * dtS);
    }

    /** {latitude, longitude} [meters] from (lat, lon) towards [bearingDeg] on a sphere. */
    static double[] destination(double lat, double lon, double bearingDeg, double meters) {
        double lat1 = Math.toRadians(lat);
        double lon1 = Math.toRadians(lon);
        double b = Math.toRadians(bearingDeg);
        double d = meters / EARTH_RADIUS_M;
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(b));
        double lon2 = lon1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(lat1), Math.cos(d) - Math.sin(lat1) * Math.sin(lat2));
        return new double[] {Math.toDegrees(lat2), Math.toDegrees(lon2)};
    }

    /** -180..180 */
    static double wrap(double deg) {
        double d = deg % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    /** 0..360 */
    static double normalize(double deg) {
        double d = deg % 360;
        return d < 0 ? d + 360 : d;
    }

    /** Tells a live compass from one frozen on the tunnel's entrance heading. */
    static final class CompassWatch {
        private Float last;
        private long changedAt;

        /** The compass reading if it is live, null if it hasn't moved for COMPASS_FROZEN_MS. */
        Float update(float bearingDeg, long nowMs) {
            Float prev = last;
            last = bearingDeg;
            if (prev == null || Math.abs(wrap(bearingDeg - prev)) >= 0.01) changedAt = nowMs;
            return nowMs - changedAt > COMPASS_FROZEN_MS ? null : bearingDeg;
        }
    }

    /**
     * The car's speed with short gaps filled: cars don't send it at a fixed rate, and a missing
     * reading for a moment stopped the estimate in a tunnel. The last speed is kept [HOLD_MS].
     */
    static final class SpeedHold {
        static final long HOLD_MS = 5_000L;
        private Float last;
        private long lastAt;

        /** [speed] (m/s, or null if none fresh) at [nowMs]: it, else the last one if recent, else null. */
        Float update(Float speed, long nowMs) {
            if (speed != null) {
                last = speed;
                lastAt = nowMs;
                return speed;
            }
            if (last != null && nowMs - lastAt <= HOLD_MS) return last;
            return null;
        }
    }

    /**
     * Learns how much a car reading must be scaled to match the GPS (least squares through zero,
     * recent measures weighing more): measured ≈ gain × reported. Until [minSamples] measures it
     * answers 1, i.e. the reading as the car sends it; the gain stays within [min, max].
     */
    static final class GainLearner {
        private final double min;
        private final double max;
        private final int minSamples;
        private final double forget;
        private double sxy;
        private double sxx;
        private int samples;

        GainLearner(double min, double max, int minSamples, double forget) {
            this.min = min;
            this.max = max;
            this.minSamples = minSamples;
            this.forget = forget;
        }

        void add(double measured, double reported) {
            if (Double.isNaN(measured) || Double.isNaN(reported) || Double.isInfinite(measured) || Double.isInfinite(reported)) return;
            sxy = sxy * forget + measured * reported;
            sxx = sxx * forget + reported * reported;
            samples++;
        }

        boolean ready() {
            return samples >= minSamples && sxx > 1e-9;
        }

        int samples() {
            return samples;
        }

        double gain() {
            if (!ready()) return 1;
            return Math.max(min, Math.min(max, sxy / sxx));
        }

        /** Back to nothing learned. */
        void reset() {
            sxy = 0;
            sxx = 0;
            samples = 0;
        }

        /** What it learned, as text (see [restore]). */
        String save() {
            return sxy + "," + sxx + "," + samples;
        }

        /**
         * Takes back what [save] wrote. Anything malformed, not finite or negative leaves it at
         * nothing learned and returns false. The gain stays within min and max as always.
         */
        boolean restore(String saved) {
            reset();
            if (saved == null) return false;
            String[] p = saved.split(",", -1);
            if (p.length != 3) return false;
            try {
                double xy = Double.parseDouble(p[0]);
                double xx = Double.parseDouble(p[1]);
                int n = Integer.parseInt(p[2]);
                if (!finite(xy) || !finite(xx) || xx < 0 || n < 0) return false;
                sxy = xy;
                sxx = xx;
                samples = n;
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    /**
     * How the car's GPS behaves at the start of a session, for the diagnostics: how many fixes,
     * how precise, the longest gap. Only numbers, never a position. Navigation apps such as Vela
     * ignore fixes worse than [APP_LIMIT_M], which can leave the map black or off-centre.
     */
    static final class FixStats {
        static final float APP_LIMIT_M = 50f;
        private final java.util.List<Float> accuracies = new java.util.ArrayList<>();
        private int fixes;
        private int withoutAccuracy;
        private long firstAt = -1;
        private long lastAt = -1;
        private long longestGapMs;

        void reset() {
            accuracies.clear();
            fixes = 0;
            withoutAccuracy = 0;
            firstAt = -1;
            lastAt = -1;
            longestGapMs = 0;
        }

        /** A fix at [atMs] (elapsed time) with this accuracy, or NaN if it has none. */
        void add(long atMs, float accuracy) {
            if (firstAt < 0) firstAt = atMs;
            if (lastAt >= 0) longestGapMs = Math.max(longestGapMs, atMs - lastAt);
            lastAt = atMs;
            fixes++;
            if (Float.isNaN(accuracy)) withoutAccuracy++;
            else accuracies.add(accuracy);
        }

        int fixes() {
            return fixes;
        }

        /** Milliseconds since the first fix, or -1 before it. */
        long sinceFirst(long nowMs) {
            return firstAt < 0 ? -1 : nowMs - firstAt;
        }

        /** E.g. "28 fixes, accuracy 8-65 m (median 12), 3 worse than 50 m, 0 without accuracy, longest gap 2 s". */
        String summary() {
            StringBuilder b = new StringBuilder().append(fixes).append(fixes == 1 ? " fix" : " fixes");
            if (!accuracies.isEmpty()) {
                java.util.List<Float> sorted = new java.util.ArrayList<>(accuracies);
                java.util.Collections.sort(sorted);
                int worse = 0;
                for (float a : sorted) if (a > APP_LIMIT_M) worse++;
                b.append(", accuracy ").append(Math.round(sorted.get(0))).append('-').append(Math.round(sorted.get(sorted.size() - 1)))
                        .append(" m (median ").append(Math.round(sorted.get(sorted.size() / 2))).append("), ")
                        .append(worse).append(" worse than ").append((int) APP_LIMIT_M).append(" m");
            }
            b.append(", ").append(withoutAccuracy).append(" without accuracy, longest gap ").append(longestGapMs / 1000).append(" s");
            return b.toString();
        }
    }

    private static boolean finite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    /**
     * What the tunnel estimate learned about one car, kept between trips: the gyroscope's direction
     * (its correlation with GPS turns) and the two gains. No position, only the car's own scales.
     */
    static final class Learned {
        /** Format of [encode]: a different one is ignored, so an update can change it safely. */
        static final String VERSION = "1";
        /** Same bound as while learning. */
        static final double MAX_CORRELATION = 50;

        static String encode(long savedAtMs, double gyroCorrelation, GainLearner turn, GainLearner speed) {
            return VERSION + ";" + savedAtMs + ";" + gyroCorrelation + ";" + turn.save() + ";" + speed.save();
        }

        /**
         * Restores [turn] and [speed] from [saved] and returns the gyroscope correlation, or NaN
         * (with both learners at nothing learned) if [saved] is missing, malformed or of another
         * version.
         */
        static double decode(String saved, GainLearner turn, GainLearner speed) {
            turn.reset();
            speed.reset();
            if (saved == null) return Double.NaN;
            String[] p = saved.split(";", -1);
            if (p.length != 5 || !VERSION.equals(p[0])) return Double.NaN;
            try {
                Long.parseLong(p[1]);
                double corr = Double.parseDouble(p[2]);
                if (!finite(corr) || Math.abs(corr) > MAX_CORRELATION) return Double.NaN;
                if (!turn.restore(p[3]) || !speed.restore(p[4])) {
                    turn.reset();
                    speed.reset();
                    return Double.NaN;
                }
                return corr;
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }

        /**
         * A short key for the car with this make, model and year, or null if make and model are both
         * unknown (nothing is kept then). A hash, so the stored names say nothing readable.
         */
        static String carKey(String make, String model, String year) {
            if ((make == null || make.isEmpty()) && (model == null || model.isEmpty())) return null;
            String id = (make == null ? "" : make.toLowerCase(java.util.Locale.ROOT)) + "|"
                    + (model == null ? "" : model.toLowerCase(java.util.Locale.ROOT)) + "|" + (year == null ? "" : year);
            try {
                byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < 8; i++) b.append(String.format(java.util.Locale.ROOT, "%02x", d[i]));
                return b.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                return null;
            }
        }

        /** When [saved] was written (ms since 1970), or 0 if it can't be read. */
        static long savedAt(String saved) {
            if (saved == null) return 0;
            String[] p = saved.split(";", -1);
            if (p.length < 2) return 0;
            try {
                return Long.parseLong(p[1]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
