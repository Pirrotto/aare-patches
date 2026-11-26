package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Following a tunnel of the maps: matched only when sure, walked along, stopped where in doubt. */
public class TunnelMatchTest {
    private static final double M = 111_132.0; // metres per degree of latitude

    /** A way from (lat, lon) pairs in degrees. */
    private static TunnelMapFile.Way way(boolean oneWay, double... latLon) {
        double[] lat = new double[latLon.length / 2], lon = new double[latLon.length / 2];
        for (int i = 0; i < lat.length; i++) {
            lat[i] = latLon[2 * i];
            lon[i] = latLon[2 * i + 1];
        }
        return new TunnelMapFile.Way(oneWay, lat, lon);
    }

    private static double eastDeg(double meters) {
        return meters / (M * Math.cos(Math.toRadians(46.0)));
    }

    /** 1100 m due north from 46.0, 8.0. */
    private static final TunnelMapFile.Way NORTH = way(true, 46.0, 8.0, 46.0 + 1100 / M, 8.0);

    private static TunnelMatch.Map map(TunnelMapFile.Way... ways) {
        return new TunnelMatch.Map(Arrays.asList(ways));
    }

    @Test
    public void aFixBeforeThePortalFollowsTheTunnel() {
        TunnelMatch m = map(NORTH).match(46.0 - 55 / M, 8.0 + eastDeg(5), 3);
        assertNotNull(m);
        assertNull(m.advance(600, TunnelBranch.Side.UNKNOWN));
        double[] p = m.position();
        assertEquals(545, (p[0] - 46.0) * M, 1);
        assertEquals(8.0, p[1], 1e-9); // on the tunnel's line, not 5 m beside it
        assertEquals(0, TunnelMath.wrap(m.bearing()), 0.5);
    }

    @Test
    public void noMatchWhenUnsure() {
        // Too far to the side, too far before the portal, heading the other way on a one-way tube.
        assertNull(map(NORTH).match(46.0 + 300 / M, 8.0 + eastDeg(40), 0));
        assertNull(map(NORTH).match(46.0 - 200 / M, 8.0, 0));
        assertNull(map(NORTH).match(46.0 + 300 / M, 8.0, 180));
        assertNull(map(NORTH).match(46.0 + 300 / M, 8.0, 50));
        // Two two-way tubes 15 m apart, the car between them: which one isn't known.
        TunnelMapFile.Way a = way(false, 46.0, 8.0, 46.0 + 1000 / M, 8.0);
        TunnelMapFile.Way b = way(false, 46.0, 8.0 + eastDeg(15), 46.0 + 1000 / M, 8.0 + eastDeg(15));
        assertNull(map(a, b).match(46.0 + 100 / M, 8.0 + eastDeg(7), 0));
        assertNull(map().match(46.0, 8.0, 0));
    }

    @Test
    public void aTwoWayTunnelIsFollowedEitherWay() {
        TunnelMapFile.Way twoWay = way(false, 46.0, 8.0, 46.0 + 1100 / M, 8.0);
        TunnelMatch m = map(twoWay).match(46.0 + 1150 / M, 8.0, 182);
        assertNotNull(m);
        assertNull(m.advance(150, TunnelBranch.Side.UNKNOWN));
        assertEquals(1000, (m.position()[0] - 46.0) * M, 1);
        assertEquals(180, m.bearing(), 0.5);
    }

    @Test
    public void curvesAndSplitWaysAreFollowed() {
        // North 500 m, then a way of its own bending north-east (100 m north and east) and on east.
        double turnLat = 46.0 + 500 / M;
        TunnelMapFile.Way first = way(true, 46.0, 8.0, turnLat, 8.0);
        TunnelMapFile.Way second = way(true, turnLat, 8.0, turnLat + 100 / M, 8.0 + eastDeg(100),
                turnLat + 100 / M, 8.0 + eastDeg(500));
        TunnelMatch m = map(first, second).match(46.0 + 10 / M, 8.0, 0);
        assertNotNull(m);
        // 490 m to the end of the first way, 141.4 m of the bend, 68.6 m east.
        assertNull(m.advance(700, TunnelBranch.Side.UNKNOWN));
        assertEquals(600, (m.position()[0] - 46.0) * M, 1);
        assertEquals(168.6, (m.position()[1] - 8.0) * M * Math.cos(Math.toRadians(46.0)), 1.5);
        assertEquals(90, m.bearing(), 0.5);
    }

    @Test
    public void theEndOfTheMapStops() {
        TunnelMatch m = map(NORTH).match(46.0 + 10 / M, 8.0, 0);
        assertNotNull(m);
        assertEquals(TunnelMatch.Stop.END, m.advance(2000, TunnelBranch.Side.UNKNOWN));
        assertEquals(1100, (m.position()[0] - 46.0) * M, 1); // left at the exit, not beyond
        assertEquals(910, m.overshoot(), 1); // 2000 m from 10 m in: 910 m past the exit
    }

    @Test
    public void aJunctionStops() {
        double end = 46.0 + 1100 / M;
        TunnelMapFile.Way left = way(true, end, 8.0, end + 300 / M, 8.0 - eastDeg(100));
        TunnelMapFile.Way right = way(true, end, 8.0, end + 300 / M, 8.0 + eastDeg(100));
        TunnelMatch m = map(NORTH, left, right).match(46.0 + 10 / M, 8.0, 0);
        assertEquals(TunnelMatch.Stop.JUNCTION, m.advance(1200, TunnelBranch.Side.UNKNOWN));
        assertEquals(1100, (m.position()[0] - 46.0) * M, 1);
    }

    @Test
    public void onlyAWayThatCanBeDrivenOutGoesOn() {
        double end = 46.0 + 1100 / M;
        // A one-way ramp coming in at the exit: not a way out.
        TunnelMapFile.Way rampIn = way(true, end + 300 / M, 8.0 + eastDeg(50), end, 8.0);
        TunnelMatch m = map(NORTH, rampIn).match(46.0 + 10 / M, 8.0, 0);
        assertEquals(TunnelMatch.Stop.END, m.advance(1200, TunnelBranch.Side.UNKNOWN));
        // A sharp turn back: not the road going on either.
        TunnelMapFile.Way back = way(true, end, 8.0, end - 300 / M, 8.0 + eastDeg(60));
        TunnelMatch n = map(NORTH, back).match(46.0 + 10 / M, 8.0, 0);
        assertEquals(TunnelMatch.Stop.JUNCTION, n.advance(1200, TunnelBranch.Side.UNKNOWN));
    }

    @Test
    public void flatGeometry() {
        assertEquals(1000, TunnelMatch.dist(46.0, 8.0, 46.0 + 1000 / M, 8.0), 1e-6);
        assertEquals(90, TunnelMatch.heading(46.0, 8.0, 46.0, 8.1), 1e-6);
        double[] p = TunnelMatch.project(46.0 + 100 / M, 8.0 + eastDeg(10), 46.0, 8.0, 46.0 + 1000 / M, 8.0);
        assertEquals(100, p[0], 0.1);
        assertEquals(-10, p[1], 0.1); // to the right of a northward line
        List<TunnelMapFile.Way> none = Arrays.asList();
        assertNull(new TunnelMatch.Map(none).match(46.0, 8.0, 0));
    }

    @Test
    public void theManeuverPicksTheBranch() {
        double end = 46.0 + 1100 / M;
        TunnelMapFile.Way left = way(true, end, 8.0, end + 300 / M, 8.0 - eastDeg(100));
        TunnelMapFile.Way right = way(true, end, 8.0, end + 300 / M, 8.0 + eastDeg(100));
        TunnelMatch r = map(NORTH, left, right).match(46.0 + 10 / M, 8.0, 0);
        assertNull(r.advance(1200, TunnelBranch.Side.RIGHT));
        assertEquals(1, r.branches());
        assertTrue(r.position()[1] > 8.0); // went right
        TunnelMatch l = map(NORTH, left, right).match(46.0 + 10 / M, 8.0, 0);
        assertNull(l.advance(1200, TunnelBranch.Side.LEFT));
        assertTrue(l.position()[1] < 8.0);
        // Two branches equally straight: no maneuver here doesn't tell which.
        TunnelMatch s = map(NORTH, left, right).match(46.0 + 10 / M, 8.0, 0);
        assertEquals(TunnelMatch.Stop.JUNCTION, s.advance(1200, TunnelBranch.Side.STRAIGHT));
    }

    @Test
    public void withoutAManeuverHereTheRoadGoesStraightOn() {
        double end = 46.0 + 1100 / M;
        TunnelMapFile.Way on = way(true, end, 8.0, end + 500 / M, 8.0 + eastDeg(10));
        TunnelMapFile.Way exit = way(true, end, 8.0, end + 200 / M, 8.0 + eastDeg(120));
        TunnelMatch m = map(NORTH, on, exit).match(46.0 + 10 / M, 8.0, 0);
        assertNull(m.advance(1300, TunnelBranch.Side.STRAIGHT));
        assertEquals(1310, (m.position()[0] - 46.0) * M, 2); // 10 m in, 1300 m on
        // Unknown (no guidance): no choice made.
        TunnelMatch u = map(NORTH, on, exit).match(46.0 + 10 / M, 8.0, 0);
        assertEquals(TunnelMatch.Stop.JUNCTION, u.advance(1300, TunnelBranch.Side.UNKNOWN));
    }
}
