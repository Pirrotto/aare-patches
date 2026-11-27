package app.template.extension.aare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MicForegroundTest {
    @Test
    public void microphoneTypeIsSeen() {
        // AARE's attempts: connected device (16) with media projection (32), microphone (128), location (8).
        assertTrue(MicForeground.hasMicrophone(16 | 128 | 8));
        assertTrue(MicForeground.hasMicrophone(48 | 128));
        assertFalse(MicForeground.hasMicrophone(16 | 8));
        assertFalse(MicForeground.hasMicrophone(16));
        assertFalse(MicForeground.hasMicrophone(-1));
    }

    @Test
    public void lineSaysWhichOne() {
        assertTrue(MicForeground.line(16 | 128).contains("with the microphone"));
        assertTrue(MicForeground.line(16).contains("without the microphone"));
    }
}
