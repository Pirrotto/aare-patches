package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class VoiceNotesRecordsTest {
    private static final String VOICE = "🎤 Messaggio vocale (0:05)";
    private static final String SPOKEN = "Marco. " + VOICE;

    @Before
    public void empty() {
        VoiceNotes.forget();
    }

    private static VoiceNotes.Segment only(String spoken) {
        List<VoiceNotes.Segment> found = VoiceNotes.findVoices(spoken);
        assertEquals(1, found.size());
        return found.get(0);
    }

    @Test
    public void aRecentVoiceMessageIsKnown() {
        VoiceNotes.remember(new VoiceNotes.Record("Marco", VOICE, 1000, 0));
        VoiceNotes.forgetOlderThan(VoiceNotes.KEEP_MS);
        assertNotNull(only(SPOKEN).record);
    }

    @Test
    public void aVoiceMessageSeenMoreThanADayAgoIsForgotten() {
        VoiceNotes.remember(new VoiceNotes.Record("Marco", VOICE, 1000, 0));
        VoiceNotes.forgetOlderThan(VoiceNotes.KEEP_MS + 1);
        // Still found in the text, but no longer tied to a message time: no recording is guessed.
        assertNull(only(SPOKEN).record);
    }

    @Test
    public void anOldOneIsDroppedWhenANewOneArrives() {
        VoiceNotes.remember(new VoiceNotes.Record("Marco", VOICE, 1000, 0));
        VoiceNotes.remember(new VoiceNotes.Record("Anna", "🎤 Messaggio vocale (0:09)", 2000, VoiceNotes.KEEP_MS + 1));
        assertNull(only(SPOKEN).record);
        assertNotNull(only("Anna. 🎤 Messaggio vocale (0:09)").record);
    }

    @Test
    public void forgetDropsEverything() {
        VoiceNotes.remember(new VoiceNotes.Record("Marco", VOICE, 1000, 0));
        VoiceNotes.forget();
        assertNull(only(SPOKEN).record);
    }

    @Test
    public void theSameMessageSeenAgainKeepsItsFirstTime() {
        VoiceNotes.remember(new VoiceNotes.Record("Marco", VOICE, 1000, 0));
        // WhatsApp posts the conversation again with every new message.
        VoiceNotes.remember(new VoiceNotes.Record("Marco", VOICE, 1000, VoiceNotes.KEEP_MS));
        VoiceNotes.forgetOlderThan(VoiceNotes.KEEP_MS + 1);
        assertNull(only(SPOKEN).record);
    }
}
