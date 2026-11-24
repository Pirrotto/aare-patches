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

    @Test
    public void whatWasLearnedComesBackTheSame() {
        TunnelMath.GainLearner t = turn(), s = speed();
        for (int i = 0; i < 40; i++) t.add(1.6, 1);
        for (int i = 0; i < 40; i++) s.add(1.05, 1);
        String saved = TunnelMath.Learned.encode(1234, 17, t, s);

        TunnelMath.GainLearner t2 = turn(), s2 = speed();
        assertEquals(17, TunnelMath.Learned.decode(saved, t2, s2), 0);
        assertEquals(t.gain(), t2.gain(), 1e-9);
        assertEquals(s.gain(), s2.gain(), 1e-9);
        assertEquals(t.samples(), t2.samples());
        assertEquals(1234, TunnelMath.Learned.savedAt(saved));
    }

    @Test
    public void somethingUnreadableIsIgnored() {
        String[] bad = {null, "", "garbage", "2;1;3;1,1,1;1,1,1", "1;1;3;1,1", "1;1;NaN;1,1,1;1,1,1",
                "1;1;99;1,1,1;1,1,1", "1;1;3;1,-1,1;1,1,1", "1;1;3;1,1,-4;1,1,1", "1;1;3;1,Infinity,1;1,1,1", "1;x;3;1,1,1;1,1,1"};
        for (String b : bad) {
            TunnelMath.GainLearner t = turn(), s = speed();
            for (int i = 0; i < 40; i++) t.add(2, 1);
            assertTrue(b, Double.isNaN(TunnelMath.Learned.decode(b, t, s)));
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
        TunnelMath.Learned.decode("1;1;10;1000,100,100;1,1,100", t, s);
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
}
