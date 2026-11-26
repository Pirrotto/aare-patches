package app.template.extension.aare;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PatchSettingsTest {
    @Test
    public void everyFeatureStartsOff() {
        // AARE wants new features opt-in: none of the patches' switches may start on.
        assertTrue(PatchSettings.offByDefault(PatchSettings.TUNNEL_NAVIGATION));
        assertTrue(PatchSettings.offByDefault(PatchSettings.VOICE_MESSAGES));
        assertTrue(PatchSettings.offByDefault(PatchSettings.VOICE_REPLY));
        assertTrue(PatchSettings.offByDefault(PatchSettings.VOICE_REPLY_ASK));
        assertTrue(PatchSettings.offByDefault(PatchSettings.VOICE_REPLY_BEEP));
        assertTrue(PatchSettings.offByDefault(PatchSettings.RADIO_PAUSE));
        assertTrue(PatchSettings.offByDefault(PatchSettings.RADIO_PAUSE_RECORDING));
        assertTrue(PatchSettings.offByDefault(PatchSettings.LOCATION_CHECK));
        assertTrue(PatchSettings.offByDefault(PatchSettings.LOCATION_FIX));
        assertTrue(PatchSettings.offByDefault(PatchSettings.TUNNEL_MAPS_AUTO));
        assertTrue(PatchSettings.offByDefault(PatchSettings.TUNNEL_MAPS_HERE));
        assertTrue(PatchSettings.offByDefault(PatchSettings.ASSISTANT_CAR_SCREEN));
        assertTrue(PatchSettings.offByDefault(PatchSettings.ASSISTANT_CAR_SCREEN_DRIVING));
        assertTrue(PatchSettings.offByDefault(PatchSettings.MAP_AREA_NOTE));
    }
}
