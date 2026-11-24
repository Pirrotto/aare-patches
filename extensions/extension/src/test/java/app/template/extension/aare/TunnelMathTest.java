package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TunnelMathTest {
    /** Distance in metres between two points (equirectangular, fine for a few km). */
    private static double distance(double[] a, double[] b) {
        double dy = (b[0] - a[0]) * 111_195;
        double dx = (b[1] - a[1]) * 111_195 * Math.cos(Math.toRadians(a[0]));
        return Math.hypot(dx, dy);
    }

    @Test
    public void destinationGoesTheRightWayAndDistance() {
        double[] start = {46.0, 8.0};
        double[] north = TunnelMath.destination(46.0, 8.0, 0, 1000);
        assertEquals(1000, distance(start, north), 2);
        assertTrue(north[0] > 46.0);
        double[] east = TunnelMath.destination(46.0, 8.0, 90, 1000);
        assertEquals(1000, distance(start, east), 2);
        assertTrue(east[1] > 8.0);
    }

    @Test
    public void anglesWrap() {
        assertEquals(-10, TunnelMath.wrap(350), 1e-9);
        assertEquals(10, TunnelMath.wrap(-350), 1e-9);
        assertEquals(350, TunnelMath.normalize(-10), 1e-9);
        assertEquals(5, TunnelMath.turn(355, Math.toRadians(5), 1, 2), 1e-9);
        // A gyroscope mounted the other way round: its learned sign flips the turn.
        assertEquals(345, TunnelMath.turn(355, Math.toRadians(5), -1, 2), 1e-9);
    }

    @Test
    public void accuracyIsHonestAndGrowsWithTheDistance() {
        assertEquals(5, TunnelMath.accuracy(5, 0), 1e-6);
        assertEquals(65, TunnelMath.accuracy(5, 1000), 1e-3);
        // A fix claiming better than 3 m doesn't make the estimate look better.
        assertEquals(3, TunnelMath.accuracy(1, 0), 1e-6);
    }

    @Test
    public void theEstimateStopsAfter5KmOr5MinutesOfDriving() {
        assertTrue(TunnelMath.withinLimits(4999, 60_000));
        assertFalse(TunnelMath.withinLimits(5001, 60_000));
        assertTrue(TunnelMath.withinLimits(1000, 5 * 60_000));
        assertFalse(TunnelMath.withinLimits(1000, 5 * 60_000 + 1));
    }

    @Test
    public void aStaleFixNeverResetsTheEstimateEvenInAQueue() {
        // A real new position: always used.
        assertEquals(TunnelMath.FixAction.USE, TunnelMath.fixAction(false, true, true));
        // The car repeating its tunnel-entrance fix while estimating, driving or standing in a queue.
        assertEquals(TunnelMath.FixAction.DROP, TunnelMath.fixAction(true, true, true));
        assertEquals(TunnelMath.FixAction.DROP, TunnelMath.fixAction(true, false, true));
        // Before any estimate: let it through as without the feature.
        assertEquals(TunnelMath.FixAction.PASS, TunnelMath.fixAction(true, true, false));
        // Parked with GPS: the same spot is a real fix.
        assertEquals(TunnelMath.FixAction.USE, TunnelMath.fixAction(true, false, false));
    }

    @Test
    public void aRepeatedFixWithJitterIsStale() {
        // Driving 20 m/s, 1 s since the last fix: a real one moved ~20 m.
        assertFalse(TunnelMath.isStale(19, 20));
        // The car's tunnel fix repeated with a few metres of jitter: stale (it used to freeze the position).
        assertTrue(TunnelMath.isStale(4, 20));
        assertTrue(TunnelMath.isStale(0.5, 20));
        // Standing still or crawling: only a fix that didn't move at all is the same spot.
        assertFalse(TunnelMath.isStale(2, 1));
        assertTrue(TunnelMath.isStale(0.2, 0));
    }

    @Test
    public void aFrozenCompassIsIgnored() {
        TunnelMath.CompassWatch watch = new TunnelMath.CompassWatch();
        assertEquals(100f, watch.update(100f, 0), 0);
        assertEquals(101f, watch.update(101f, 1000), 0);
        assertEquals(101f, watch.update(101f, 3000), 0);
        assertNull(watch.update(101f, 4000)); // 3 s unchanged
        assertEquals(102f, watch.update(102f, 5000), 0); // moves again: live
    }

    @Test
    public void aCurvedTunnelWithAFrozenCompassStaysOnTheCurve() {
        // 72 km/h, a right-hand curve turning 2.5°/s for 60 s, the compass frozen at the entrance
        // heading, the gyroscope mounted reversed (sign learned as -1, so it reads -yaw).
        double v = 20, turnDegS = 2.5, sign = -1;
        double[] truth = {46.0, 8.0};
        double heading = 30;
        double[] estimate = truth.clone();
        double bearing = heading;
        TunnelMath.CompassWatch watch = new TunnelMath.CompassWatch();
        // The compass froze when the GPS went; the estimate starts 2.5 s later (TunnelNavigation's
        // STALE_MS), the compass followed meanwhile.
        watch.update(30f, -2500);
        long now = 0;
        for (int t = 0; t < 60; t++) {
            now += 1000;
            heading = TunnelMath.normalize(heading + turnDegS);
            truth = TunnelMath.destination(truth[0], truth[1], heading - turnDegS / 2, v);
            double gyroReads = -Math.toRadians(turnDegS); // reversed mounting
            Float compass = watch.update(30f, now);
            bearing = compass != null ? compass : TunnelMath.turn(bearing, gyroReads, sign, 1);
            estimate = TunnelMath.destination(estimate[0], estimate[1], bearing, v);
        }
        double error = distance(truth, estimate);
        assertTrue("error " + error + " m", error < 60);
        // Following the frozen compass instead would have been hundreds of metres off.
        double[] straight = TunnelMath.destination(46.0, 8.0, 30, 1200);
        assertTrue(distance(truth, straight) > 400);
    }

    @Test
    public void aShortGapInTheCarSpeedIsFilled() {
        TunnelMath.SpeedHold hold = new TunnelMath.SpeedHold();
        assertNull(hold.update(null, 0));
        assertEquals(15f, hold.update(15f, 1_000), 0f);
        assertEquals(15f, hold.update(null, 3_000), 0f);
        assertEquals(15f, hold.update(null, 6_000), 0f);
        assertNull(hold.update(null, 6_001));
        assertEquals(12f, hold.update(12f, 7_000), 0f);
    }

    @Test
    public void aGyroscopeSendingTooLittleIsScaledUp() {
        TunnelMath.GainLearner g = new TunnelMath.GainLearner(0.5, 3, 20, 0.99);
        // Until enough curves: as sent.
        for (int i = 0; i < 19; i++) g.add(0.1, 0.07);
        assertFalse(g.ready());
        assertEquals(1.0, g.gain(), 0);
        // The car sends 70 % of the real turn, with noise.
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < 200; i++) {
            double real = 0.05 + r.nextDouble() * 0.3;
            g.add(real + r.nextGaussian() * 0.01, real * 0.7);
        }
        assertTrue(g.ready());
        assertEquals(1 / 0.7, g.gain(), 0.05);
    }

    @Test
    public void aLearnedGainStaysWithinItsLimits() {
        TunnelMath.GainLearner g = new TunnelMath.GainLearner(0.8, 1.25, 30, 0.995);
        for (int i = 0; i < 50; i++) g.add(30, 10); // 3x: out of range
        assertEquals(1.25, g.gain(), 0);
        g = new TunnelMath.GainLearner(0.8, 1.25, 30, 0.995);
        for (int i = 0; i < 50; i++) g.add(5, 10);
        assertEquals(0.8, g.gain(), 0);
        g.add(Double.NaN, 1);
        assertEquals(0.8, g.gain(), 0);
    }

    @Test
    public void theTurnWithTheGainCoversTheWholeCurve() {
        // A 90 degree curve in 10 s; the car reports 70 %, the learned gain 1/0.7 restores it.
        double bearing = 0;
        double realRate = Math.toRadians(9);
        for (int i = 0; i < 10; i++) bearing = TunnelMath.turn(bearing, realRate * 0.7 * (1 / 0.7), 1, 1);
        assertEquals(90, bearing, 0.001);
    }
}
