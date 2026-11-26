package app.template.extension.aare;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The diagnostics' note on a listening: technical state only. */
public class ListenNoteTest {
    @Test
    public void notes() {
        assertEquals("never started", ListenNote.of(false, -1, false, Float.NEGATIVE_INFINITY));
        assertEquals("started after 420 ms, voice heard, loudest 8 dB", ListenNote.of(true, 420, true, 7.6f));
        assertEquals("started after 300 ms, no voice heard, loudest -2 dB", ListenNote.of(true, 300, false, -2.2f));
        assertEquals("started after 0 ms, no voice heard, no level", ListenNote.of(true, -5, false, Float.NEGATIVE_INFINITY));
        assertEquals("started after 10 ms, no voice heard, no level", ListenNote.of(true, 10, false, Float.NaN));
    }
}
