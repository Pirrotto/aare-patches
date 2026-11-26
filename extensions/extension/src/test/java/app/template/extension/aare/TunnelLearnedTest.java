package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TunnelLearnedTest {
    private static TunnelMath.GainLearner turn() {
        return new TunnelMath.GainLearner(0.5, 3, 20, 0.99);
    }

    private static TunnelMath.GainLearner speed() {
        return new TunnelMath.GainLearner(0.8, 1.25, 30, 0.995);
    }

    private static TunnelMath.ErrorLearner err() {
        return new TunnelMath.ErrorLearner();
    }

    private static TunnelMath.BiasLearner bias() {
        return new TunnelMath.BiasLearner(0.02, 60, 0.995);
    }

    @Test
    public void whatWasLearnedComesBackTheSame() {
        TunnelMath.GainLearner t = turn(), s = speed();
        for (int i = 0; i < 40; i++) t.add(1.6, 1);
        for (int i = 0; i < 40; i++) s.add(1.05, 1);
        TunnelMath.BiasLearner b = bias();
        for (int i = 0; i < 80; i++) b.add(0.003);
        TunnelMath.ErrorLearner e = err();
        for (double v : new double[] {4, 2.5, 3, 5, 3.5, 4.2}) e.add(v);
        TunnelMath.ErrorLearner em = err();
        for (double v : new double[] {1, 1.5, 0.8, 1.2, 2}) em.add(v);
        String saved = TunnelMath.Learned.encode(1234, 17, t, s, b, e, em);

        TunnelMath.GainLearner t2 = turn(), s2 = speed();
        TunnelMath.BiasLearner b2 = bias();
        TunnelMath.ErrorLearner e2 = err();
        TunnelMath.ErrorLearner em2 = err();
        assertEquals(17, TunnelMath.Learned.decode(saved, t2, s2, b2, e2, em2), 0);
        assertEquals(em.rate(), em2.rate(), 1e-12);
        assertEquals(0.03, em2.rate(), 1e-12); // 2 % worst, plus 20 %: under the 3 % floor
        assertEquals(e.samples(), e2.samples());
        assertEquals(e.rate(), e2.rate(), 1e-12);
        assertEquals(b.bias(), b2.bias(), 1e-12);
        assertEquals(t.gain(), t2.gain(), 1e-9);
        assertEquals(s.gain(), s2.gain(), 1e-9);
        assertEquals(t.samples(), t2.samples());
        assertEquals(1234, TunnelMath.Learned.savedAt(saved));
    }

    @Test
    public void somethingUnreadableIsIgnored() {
        String[] bad = {null, "", "garbage", "2;1;3;1,1,1;1,1,1", "1;1;3;1,1", "1;1;NaN;1,1,1;1,1,1",
                "1;1;99;1,1,1;1,1,1", "1;1;3;1,-1,1;1,1,1", "1;1;3;1,1,-4;1,1,1", "1;1;3;1,Infinity,1;1,1,1", "1;x;3;1,1,1;1,1,1",
                "3;1;3;1,1,1;1,1,1;0,0,1", "3;1;3;1,1,1;1,1,1;0,0,1;4,x", "3;1;3;1,1,1;1,1,1;0,0,1;4,-1",
                "3;1;3;1,1,1;1,1,1;0,0,1;1,1,1,1,1,1,1,1,1,1,1", "5;1;3;1,1,1;1,1,1;0,0,1;4;4",
                "4;1;3;1,1,1;1,1,1;0,0,1;4", "4;1;3;1,1,1;1,1,1;0,0,1;4;x", "4;1;3;1,1,1;1,1,1;0,0,1;4;-2"};
        for (String b : bad) {
            TunnelMath.GainLearner t = turn(), s = speed();
            for (int i = 0; i < 40; i++) t.add(2, 1);
            TunnelMath.BiasLearner g = bias();
            TunnelMath.ErrorLearner e = err();
            for (int i = 0; i < 6; i++) e.add(2);
            assertTrue(b, Double.isNaN(TunnelMath.Learned.decode(b, t, s, g, e, err())));
            assertFalse(b, e.ready());
            assertEquals(b, 0, g.bias(), 0);
            // Nothing learned: as sent, as before the feature.
            assertFalse(b, t.ready());
            assertEquals(b, 1, t.gain(), 0);
            assertEquals(b, 1, s.gain(), 0);
        }
    }

    @Test
    public void aRestoredGainStaysWithinItsLimits() {
        TunnelMath.GainLearner t = turn(), s = speed();
        // Written as if the car's gyroscope were 10 times off: the gain is still capped at 3.
        TunnelMath.Learned.decode("1;1;10;1000,100,100;1,1,100", t, s, bias(), err(), err());
        assertEquals(3, t.gain(), 0);
    }

    @Test
    public void theCarKeyIsAHashOfMakeModelAndYear() {
        String octavia = TunnelMath.Learned.carKey("Skoda", "Octavia", "2022");
        assertEquals(16, octavia.length());
        assertFalse(octavia.toLowerCase().contains("skoda"));
        assertEquals(octavia, TunnelMath.Learned.carKey("SKODA", "octavia", "2022"));
        assertNotEquals(octavia, TunnelMath.Learned.carKey("Skoda", "Octavia", "2023"));
        assertNotEquals(octavia, TunnelMath.Learned.carKey("Skoda", "Superb", "2022"));
        assertNull(TunnelMath.Learned.carKey("", "", "2022"));
        assertNull(TunnelMath.Learned.carKey(null, null, null));
    }

    @Test
    public void whatVersion1SavedIsStillRead() {
        // Saved before the gyroscope offset was learned: gains kept, offset learned from now on.
        TunnelMath.GainLearner t = turn(), s = speed();
        TunnelMath.BiasLearner b = bias();
        TunnelMath.ErrorLearner e = err();
        assertEquals(17, TunnelMath.Learned.decode("1;1234;17;40,25,25;40,40,40", t, s, b, e, err()), 0);
        assertEquals(0, e.samples());
        assertEquals(1.6, t.gain(), 1e-9);
        assertEquals(0, b.samples());
        assertEquals(0, b.bias(), 0);
    }

    @Test
    public void aBrokenOffsetDropsEverything() {
        TunnelMath.GainLearner t = turn(), s = speed();
        TunnelMath.BiasLearner b = bias();
        assertTrue(Double.isNaN(TunnelMath.Learned.decode("2;1;17;40,25,25;40,40,40;x,1,1", t, s, b, err(), err())));
        assertEquals(1, t.gain(), 0);
    }

    @Test
    public void theOffsetIsUsedOnlyAfterAMinuteAndStaysBounded() {
        TunnelMath.BiasLearner b = bias();
        for (int i = 0; i < 59; i++) b.add(0.004);
        assertEquals(0, b.bias(), 0);
        b.add(0.004);
        assertEquals(0.004, b.bias(), 1e-9);
        TunnelMath.BiasLearner wild = bias();
        for (int i = 0; i < 100; i++) wild.add(0.5);
        assertEquals(0.02, wild.bias(), 0);
        wild.add(Double.NaN);
        assertEquals(100, wild.samples());
    }

    @Test
    public void whatVersion2SavedIsStillRead() {
        // Saved before the tunnel error was measured: the rest kept, the error learned from now on.
        TunnelMath.GainLearner t = turn(), s = speed();
        TunnelMath.BiasLearner b = bias();
        TunnelMath.ErrorLearner e = err();
        assertEquals(17, TunnelMath.Learned.decode("2;1234;17;40,25,25;40,40,40;0.06,20,61", t, s, b, e, err()), 0);
        assertEquals(1.6, t.gain(), 1e-9);
        assertEquals(61, b.samples());
        assertEquals(0, e.samples());
        assertEquals(TunnelMath.ERROR_PER_M, e.rate(), 0);
    }

    @Test
    public void nothingMeasuredYetIsSavedAndReadBack() {
        TunnelMath.ErrorLearner e = err();
        String saved = TunnelMath.Learned.encode(1, 3, turn(), speed(), bias(), e, err());
        TunnelMath.ErrorLearner e2 = err();
        assertEquals(3, TunnelMath.Learned.decode(saved, turn(), speed(), bias(), e2, err()), 0);
        assertEquals(0, e2.samples());
    }

    @Test
    public void theTunnelErrorIsHonest() {
        TunnelMath.ErrorLearner e = err();
        // Until five tunnels were measured: the default 10 %.
        for (int i = 0; i < 4; i++) e.add(2);
        assertFalse(e.ready());
        assertEquals(TunnelMath.ERROR_PER_M, e.rate(), 0);
        // Then the worst of them plus 20 %, never under 3 %.
        e.add(4);
        assertEquals(0.048, e.rate(), 1e-12);
        TunnelMath.ErrorLearner good = err();
        for (int i = 0; i < 6; i++) good.add(0.5);
        assertEquals(TunnelMath.ErrorLearner.FLOOR, good.rate(), 0);
        // One bad tunnel raises it at once, above the default too if that is what was measured.
        good.add(15);
        assertEquals(0.18, good.rate(), 1e-12);
        // It falls back only once ten newer tunnels pushed the bad one out.
        for (int i = 0; i < 9; i++) good.add(1);
        assertEquals(0.18, good.rate(), 1e-12);
        good.add(1);
        assertEquals(TunnelMath.ErrorLearner.FLOOR, good.rate(), 0);
    }

    @Test
    public void aBrokenMeasureIsNotLearned() {
        TunnelMath.ErrorLearner e = err();
        assertFalse(e.add(Double.NaN));
        assertFalse(e.add(-1));
        assertFalse(e.add(Double.POSITIVE_INFINITY));
        assertFalse(e.add(150));
        assertEquals(0, e.samples());
    }

    @Test
    public void theLearnedErrorSetsTheAccuracy() {
        // 1000 m after a 5 m fix: 105 m at the default 10 %, 35 m at a learned 3 %.
        assertEquals(105, TunnelMath.accuracy(5, 1000), 1e-3);
        assertEquals(35, TunnelMath.accuracy(5, 1000, 0.03), 1e-3);
        assertTrue(TunnelMath.accurateEnough(TunnelMath.accuracy(5, 1000, 0.03)));
    }

    @Test
    public void whatVersion3SavedIsStillRead() {
        // Saved before the tunnel maps: the gyroscope's error kept, the map's learned from now on.
        TunnelMath.ErrorLearner e = err(), em = err();
        assertEquals(17, TunnelMath.Learned.decode("3;1234;17;40,25,25;40,40,40;0.06,20,61;4,5,3,4,6", turn(), speed(), bias(), e, em), 0);
        assertEquals(5, e.samples());
        assertEquals(0, em.samples());
        assertEquals(TunnelMath.ERROR_PER_M, em.rate(), 0);
    }

    @Test
    public void theErrorAddsUpStretchByStretch() {
        // 800 m along a map at 2 %, then 200 m with the gyroscope at 10 %: 16 + 20 m on a 5 m fix.
        assertEquals(41, TunnelMath.withError(5, 0.02 * 800 + 0.10 * 200), 1e-3);
        assertEquals(3, TunnelMath.withError(1, 0), 1e-6);
    }
}
