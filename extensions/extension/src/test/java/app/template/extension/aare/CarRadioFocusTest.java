package app.template.extension.aare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarRadioFocusTest {
    @Test
    public void holdsOnlyWhileOnAndAareSpeaks() {
        assertTrue(CarRadioFocus.holds(true, true, false));
        assertTrue(CarRadioFocus.holds(true, false, true));
        // Off, or nothing of AARE's speaking (a navigation prompt): as AARE does today.
        assertFalse(CarRadioFocus.holds(false, true, true));
        assertFalse(CarRadioFocus.holds(true, false, false));
    }

    @Test
    public void recordingHoldsOnlyWithBothSwitchesOn() {
        assertTrue(CarRadioFocus.holdsForRecording(true, true, true));
        // Car radio pause off, its sub-option off, or nothing recording: the radio is left alone.
        assertFalse(CarRadioFocus.holdsForRecording(false, true, true));
        assertFalse(CarRadioFocus.holdsForRecording(true, false, true));
        assertFalse(CarRadioFocus.holdsForRecording(true, true, false));
    }

    @Test
    public void theRadioAlwaysComesBack() {
        assertTrue(CarRadioFocus.mustRelease(false, 0));
        assertFalse(CarRadioFocus.mustRelease(true, CarRadioFocus.MAX_HOLD_MS - 1));
        // Still "speaking" after the maximum (a stuck state): given back anyway.
        assertTrue(CarRadioFocus.mustRelease(true, CarRadioFocus.MAX_HOLD_MS));
    }
}
