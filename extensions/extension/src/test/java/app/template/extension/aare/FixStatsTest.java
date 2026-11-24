package app.template.extension.aare;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FixStatsTest {
    @Test
    public void summarisesAccuracyAndGaps() {
        TunnelMath.FixStats s = new TunnelMath.FixStats();
        s.add(0, 65);
        s.add(1000, 12);
        s.add(2000, Float.NaN);
        s.add(6000, 8);
        assertEquals(4, s.fixes());
        assertEquals(6000, s.sinceFirst(6000));
        assertEquals("4 fixes, accuracy 8-65 m (median 12), 1 worse than 50 m, 1 without accuracy, longest gap 4 s", s.summary());
    }

    @Test
    public void withoutAnyAccuracy() {
        TunnelMath.FixStats s = new TunnelMath.FixStats();
        assertEquals(-1, s.sinceFirst(5));
        s.add(10, Float.NaN);
        assertEquals("1 fix, 1 without accuracy, longest gap 0 s", s.summary());
        s.reset();
        assertEquals(0, s.fixes());
        assertEquals(-1, s.sinceFirst(5));
    }
}
