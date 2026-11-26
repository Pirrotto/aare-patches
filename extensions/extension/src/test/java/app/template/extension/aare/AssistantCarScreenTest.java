package app.template.extension.aare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AssistantCarScreenTest {
    @Test
    public void carAppKeptWhileOff() {
        // Off (the default): AARE's own behaviour, the car app is always tried.
        assertFalse(AssistantCarScreen.skip(false, true, true));
        assertFalse(AssistantCarScreen.skip(true, false, false));
    }

    @Test
    public void carAppSkippedAfterRefusalOrOnce() {
        assertTrue(AssistantCarScreen.skip(true, true, false));
        assertTrue(AssistantCarScreen.skip(true, false, true));
    }

    @Test
    public void drivingRestrictionLiftedOnlyWithBothSwitches() {
        assertTrue(AssistantCarScreen.restricted(true, false, false));
        assertTrue(AssistantCarScreen.restricted(true, false, true));
        assertTrue(AssistantCarScreen.restricted(true, true, false));
        assertFalse(AssistantCarScreen.restricted(true, true, true));
        // Parked stays parked whatever the switches.
        assertFalse(AssistantCarScreen.restricted(false, false, false));
        assertFalse(AssistantCarScreen.restricted(false, true, true));
    }

    @Test
    public void fallsBackOnlyWhenTheCarAppIsNotUp() {
        assertTrue(AssistantCarScreen.waitedInVain("CONNECTING"));
        assertTrue(AssistantCarScreen.waitedInVain("FAILED"));
        assertFalse(AssistantCarScreen.waitedInVain("READY"));
        assertFalse(AssistantCarScreen.waitedInVain("IDLE"));
        assertFalse(AssistantCarScreen.waitedInVain(null));
    }

    @Test
    public void failureAnswersOnlyThePressedAppSoonAfter() {
        assertTrue(AssistantCarScreen.answersPress("com.google.android.googlequicksearchbox", "com.google.android.googlequicksearchbox", 10_000));
        assertFalse(AssistantCarScreen.answersPress("com.waze", "com.google.android.googlequicksearchbox", 10_000));
        assertFalse(AssistantCarScreen.answersPress("a", "a", AssistantCarScreen.PRESS_WINDOW_MS + 1));
        assertFalse(AssistantCarScreen.answersPress("a", "a", -1));
        assertFalse(AssistantCarScreen.answersPress(null, null, 0));
    }
}
