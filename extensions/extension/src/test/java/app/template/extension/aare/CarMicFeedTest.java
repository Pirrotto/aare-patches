package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarMicFeedTest {
    @Test
    public void noteHasOnlyByteCounts() {
        assertEquals("car mic: 64 KB to the recognizer, 0 KB dropped", CarMicFeed.note(64 * 1024, 0, false));
        assertEquals("car mic: 1 KB to the recognizer, 2 KB dropped, taken over by a car app",
                CarMicFeed.note(1500, 2048, true));
    }

    @Test
    public void feedEndsOnlyAfterWordsAndAPause() {
        // Nothing heard yet: the no-speech timeout decides, not the pause.
        assertFalse(CarMicFeed.quiet(0, 10_000, 2_500));
        assertFalse(CarMicFeed.quiet(1_000, 3_499, 2_500));
        assertTrue(CarMicFeed.quiet(1_000, 3_500, 2_500));
    }
}
