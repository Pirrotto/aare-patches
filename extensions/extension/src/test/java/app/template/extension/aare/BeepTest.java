package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BeepTest {
    @Test
    public void toneHasSoftEdgesAndStaysBelowFullScale() {
        short[] t = Beep.tone(16000, 120, 880);
        assertEquals(1920, t.length);
        assertEquals(0, t[0]);
        assertTrue(Math.abs(t[t.length - 1]) < 200);
        int max = 0;
        for (short v : t) max = Math.max(max, Math.abs(v));
        assertTrue(max > 10000 && max <= 0.41 * Short.MAX_VALUE);
    }

    @Test
    public void twoNotesMakeOneTone() {
        short[] a = Beep.tone(16000, 70, 660);
        short[] b = Beep.tone(16000, 70, 990);
        short[] both = Beep.concat(a, b);
        assertEquals(a.length + b.length, both.length);
        assertEquals(b[5], both[a.length + 5]);
    }
}
