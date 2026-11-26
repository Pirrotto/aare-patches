package app.template.extension.aare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Follows a tunnel of the tunnel maps: found from the last fix before the GPS was lost, then walked
 * along by the distance the car drives. The heading comes from the tunnel's shape, so the only error
 * left is how far along it the car is (its speed). Where the tunnel splits, ends or the match is in
 * doubt it says so and the estimate goes back to the gyroscope. No position is kept or logged.
 * Plain Java, for tests.
 */
final class TunnelMatch {
    /** The last fix must be this close to a tunnel's line (m) to follow it. */
    static final double MATCH_M = 25;
    /** ...or this far before its entrance, on its line (m): the GPS often drops just before the portal. */
    static final double BEFORE_ENTRY_M = 150;
    /** ...heading the tunnel's way within this (degrees). */
    static final double MAX_HEADING_DEG = 35;
    /** Two fits whose scores differ by less than this (m) are a doubt, if they are in different places. */
    static final double AMBIGUOUS_M = 5;
    /** Fits closer than this (m) are the same place. */
    static final double SAME_PLACE_M = 8;
    /** A turn onto the next way sharper than this (degrees) isn't the road going on: back to the gyroscope. */
    static final double MAX_JOIN_TURN_DEG = 60;

    private static final double M_PER_DEG_LAT = 111_132.0;

    /** Why following stopped. */
    enum Stop {
        /** The tunnel's last way ended with nothing after it: the car is leaving the map. */
        END,
        /** Several ways go on from here: which one the car takes isn't known. */
        JUNCTION
    }

    /** The ways of the maps on the phone, with which way ends meet which. */
    static final class Map {
        final List<TunnelMapFile.Way> ways;
        /** For each way, its box (min lat, min lon, max lat, max lon), to skip the far ones fast. */
        private final double[][] boxes;
        /** Way ends by their exact point (millionths of a degree): {way, 0 for its start or 1 for its end}. */
        private final java.util.Map<Long, List<int[]>> ends = new HashMap<>();

        Map(List<TunnelMapFile.Way> ways) {
            this.ways = ways;
            boxes = new double[ways.size()][];
            for (int i = 0; i < ways.size(); i++) {
                TunnelMapFile.Way w = ways.get(i);
                double a = 90, b = 180, c = -90, d = -180;
                for (int k = 0; k < w.lat.length; k++) {
                    a = Math.min(a, w.lat[k]);
                    b = Math.min(b, w.lon[k]);
                    c = Math.max(c, w.lat[k]);
                    d = Math.max(d, w.lon[k]);
                }
                boxes[i] = new double[] {a, b, c, d};
                end(key(w.lat[0], w.lon[0])).add(new int[] {i, 0});
                end(key(w.lat[w.lat.length - 1], w.lon[w.lon.length - 1])).add(new int[] {i, 1});
            }
        }

        private List<int[]> end(long key) {
            List<int[]> l = ends.get(key);
            if (l == null) {
                l = new ArrayList<>(2);
                ends.put(key, l);
            }
            return l;
        }

        private static long key(double lat, double lon) {
            return Math.round(lat * 1e6) * 400_000_000L + Math.round(lon * 1e6);
        }

        /**
         * The tunnel the car is entering from its last fix ([lat], [lon], heading [bearing] degrees),
         * or null when none fits, or when two fit about as well (never a guess).
         */
        TunnelMatch match(double lat, double lon, double bearing) {
            double margin = (BEFORE_ENTRY_M + MATCH_M) / M_PER_DEG_LAT;
            double lonMargin = margin / Math.max(0.01, Math.cos(Math.toRadians(lat)));
            TunnelMatch best = null;
            double bestScore = Double.MAX_VALUE;
            // Every fit, to tell a real doubt (another tube) from the same place on a split way.
            List<TunnelMatch> fits = new ArrayList<>();
            List<Double> scores = new ArrayList<>();
            for (int i = 0; i < ways.size(); i++) {
                double[] box = boxes[i];
                if (lat < box[0] - margin || lat > box[2] + margin || lon < box[1] - lonMargin || lon > box[3] + lonMargin) continue;
                TunnelMapFile.Way w = ways.get(i);
                for (int dir = 1; dir >= -1; dir -= 2) {
                    if (dir < 0 && w.oneWay) continue;
                    int n = w.lat.length;
                    for (int s = 0; s < n - 1; s++) {
                        // Segment s in the direction of travel: from point a to point b.
                        int a = dir > 0 ? s : n - 1 - s, b = dir > 0 ? s + 1 : n - 2 - s;
                        double len = dist(w.lat[a], w.lon[a], w.lat[b], w.lon[b]);
                        if (len < 0.5) continue;
                        if (Math.abs(TunnelMath.wrap(bearing - heading(w.lat[a], w.lon[a], w.lat[b], w.lon[b]))) > MAX_HEADING_DEG) continue;
                        double[] p = project(lat, lon, w.lat[a], w.lon[a], w.lat[b], w.lon[b]);
                        double along = p[0], side = Math.abs(p[1]);
                        boolean entry = s == 0;
                        double score;
                        if (along >= 0 && along <= len) {
                            if (side > MATCH_M) continue;
                            score = side;
                        } else if (entry && along < 0 && -along <= BEFORE_ENTRY_M && side <= MATCH_M) {
                            score = side + 0.05 * -along; // before the portal, on its line
                        } else {
                            continue;
                        }
                        TunnelMatch m = new TunnelMatch(this, i, dir, s, along);
                        fits.add(m);
                        scores.add(score);
                        if (score < bestScore) {
                            bestScore = score;
                            best = m;
                        }
                    }
                }
            }
            if (best == null) return null;
            // Another fit almost as good but somewhere else (a parallel tube, another tunnel): not sure
            // enough to follow either. Fits at the same place (the next segment at a bend, the next way
            // of a split tunnel) are no doubt.
            double[] at = best.position();
            for (int k = 0; k < fits.size(); k++) {
                if (scores.get(k) - bestScore >= AMBIGUOUS_M) continue;
                double[] p = fits.get(k).position();
                if (dist(at[0], at[1], p[0], p[1]) > SAME_PLACE_M) return null;
            }
            return best;
        }
    }

    private final Map map;
    private int way;
    /** +1 along the way's points, -1 against them (two-way tunnels only). */
    private int dir;
    /** Segment index in the direction of travel, and metres along it (negative before the entrance). */
    private int seg;
    private double along;
    /** At a stop: how far the car went past the point it stopped at (m), to go on from there. */
    private double overshoot;
    private int branches;

    private TunnelMatch(Map map, int way, int dir, int seg, double along) {
        this.map = map;
        this.way = way;
        this.dir = dir;
        this.seg = seg;
        this.along = along;
    }

    private TunnelMapFile.Way w() {
        return map.ways.get(way);
    }

    private int a() {
        return dir > 0 ? seg : w().lat.length - 1 - seg;
    }

    private int b() {
        return dir > 0 ? seg + 1 : w().lat.length - 2 - seg;
    }

    private int segments() {
        return w().lat.length - 1;
    }

    /**
     * Moves [meters] further along the tunnel. Where it splits, [side] (the navigation's maneuver
     * here, TunnelBranch.Side) picks the branch when the choice is clear. Null if it went on; else why
     * it stopped, with the position left at the last point it could follow.
     */
    Stop advance(double meters, TunnelBranch.Side side) {
        along += Math.max(0, meters);
        while (true) {
            TunnelMapFile.Way w = w();
            double len = dist(w.lat[a()], w.lon[a()], w.lat[b()], w.lon[b()]);
            if (along <= len) return null;
            if (seg + 1 < segments()) {
                along -= len;
                seg++;
                continue;
            }
            // The end of this way: on to a way that can be driven on from here.
            int endPoint = b();
            double inHeading = bearing();
            List<int[]> ends = map.ends.get(Map.key(w.lat[endPoint], w.lon[endPoint]));
            List<int[]> ways = new ArrayList<>();
            List<Double> turns = new ArrayList<>();
            boolean sharp = false;
            if (ends != null) {
                for (int[] e : ends) {
                    if (e[0] == way) continue;
                    TunnelMapFile.Way o = map.ways.get(e[0]);
                    int d = e[1] == 0 ? 1 : -1;
                    if (d < 0 && o.oneWay) continue; // that one comes in here: can't be driven out
                    int p0 = d > 0 ? 0 : o.lat.length - 1, p1 = d > 0 ? 1 : o.lat.length - 2;
                    double turn = TunnelMath.wrap(heading(o.lat[p0], o.lon[p0], o.lat[p1], o.lon[p1]) - inHeading);
                    if (Math.abs(turn) > MAX_JOIN_TURN_DEG) {
                        sharp = true; // not the road going on
                        continue;
                    }
                    ways.add(new int[] {e[0], d});
                    turns.add(turn);
                }
            }
            int pick = -1;
            if (ways.size() == 1) {
                pick = 0;
            } else if (ways.size() > 1) {
                double[] t = new double[turns.size()];
                for (int i = 0; i < t.length; i++) t[i] = turns.get(i);
                pick = TunnelBranch.choose(t, side);
            }
            if (pick < 0) {
                overshoot = along - len;
                along = len;
                return ways.isEmpty() && !sharp ? Stop.END : Stop.JUNCTION;
            }
            along -= len;
            way = ways.get(pick)[0];
            dir = ways.get(pick)[1];
            seg = 0;
            if (ways.size() > 1) branches++;
        }
    }

    /** How many times a branch was chosen at a junction (for the diagnostics). */
    int branches() {
        return branches;
    }

    /** After a stop: the metres driven past the point where it stopped. */
    double overshoot() {
        return overshoot;
    }

    /** Where the car is now: {lat, lon}. */
    double[] position() {
        TunnelMapFile.Way w = w();
        double la = w.lat[a()], lo = w.lon[a()], lb = w.lat[b()], ob = w.lon[b()];
        double len = dist(la, lo, lb, ob);
        double t = len > 0 ? along / len : 0;
        return new double[] {la + (lb - la) * t, lo + (ob - lo) * t};
    }

    /** The tunnel's heading here, degrees clockwise from north. */
    double bearing() {
        TunnelMapFile.Way w = w();
        return heading(w.lat[a()], w.lon[a()], w.lat[b()], w.lon[b()]);
    }

    // region Flat geometry (fine over a tunnel's few kilometres)

    static double dist(double lat1, double lon1, double lat2, double lon2) {
        double dy = (lat2 - lat1) * M_PER_DEG_LAT;
        double dx = (lon2 - lon1) * M_PER_DEG_LAT * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        return Math.hypot(dx, dy);
    }

    static double heading(double lat1, double lon1, double lat2, double lon2) {
        double dy = (lat2 - lat1) * M_PER_DEG_LAT;
        double dx = (lon2 - lon1) * M_PER_DEG_LAT * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        double deg = Math.toDegrees(Math.atan2(dx, dy));
        return deg < 0 ? deg + 360 : deg;
    }

    /** The point ([lat], [lon]) against the segment from 1 to 2: {metres along it, metres to its side}. */
    static double[] project(double lat, double lon, double lat1, double lon1, double lat2, double lon2) {
        double c = Math.cos(Math.toRadians(lat1)) * M_PER_DEG_LAT;
        double sx = (lon2 - lon1) * c, sy = (lat2 - lat1) * M_PER_DEG_LAT;
        double px = (lon - lon1) * c, py = (lat - lat1) * M_PER_DEG_LAT;
        double len = Math.hypot(sx, sy);
        if (len == 0) return new double[] {0, Math.hypot(px, py)};
        return new double[] {(px * sx + py * sy) / len, (sx * py - sy * px) / len};
    }

    // endregion
}
