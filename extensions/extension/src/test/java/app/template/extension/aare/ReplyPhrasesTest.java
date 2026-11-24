package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReplyPhrasesTest {
    @Test
    public void aNewMessageIsReadAsGoogleReadsIt() {
        assertEquals("Marco dice: arrivo tra poco", ReplyPhrases.withSays("Marco. arrivo tra poco", "Marco", true));
        assertEquals("Marco says: on my way", ReplyPhrases.withSays("Marco. on my way", "Marco", false));
        // A sentence of the message keeps its full stop.
        assertEquals("Marco dice: Ok. A dopo", ReplyPhrases.withSays("Marco. Ok. A dopo", "Marco", true));
    }

    @Test
    public void groupsAndOtherTextsStayAsAareReadsThem() {
        assertEquals("Calcio. Anna: domani alle 8", ReplyPhrases.withSays("Calcio. Anna: domani alle 8", "Calcio", true));
        assertEquals("Calcio. Anna: ok. Luca: sì", ReplyPhrases.withSays("Calcio. Anna: ok. Luca: sì", "Calcio", true));
        assertEquals("Anna. ciao", ReplyPhrases.withSays("Anna. ciao", "Calcio", true));
        assertEquals("Marco. ", ReplyPhrases.withSays("Marco. ", "Marco", true));
        assertEquals(null, ReplyPhrases.withSays(null, "Marco", true));
    }

    @Test
    public void googlePhrases() {
        assertEquals("Ecco il tuo messaggio per Marco: Arrivo. Vuoi mandarlo?", ReplyPhrases.readBack("Marco", "Arrivo", true));
        assertEquals("Messaggio inviato a Marco.", ReplyPhrases.sent("Marco", true));
        assertEquals("Qual è il messaggio?", ReplyPhrases.whatMessage(true));
    }

    @Test
    public void aNoWaitsForTheEndOfTheAnswer() {
        assertFalse(ReplyWords.actEarlyOnConfirmation("No"));
        assertTrue(ReplyWords.actEarlyOnConfirmation("No, cambia messaggio"));
        assertTrue(ReplyWords.actEarlyOnConfirmation("Sì"));
        assertFalse(ReplyWords.actEarlyOnConfirmation("sì no"));
        assertFalse(ReplyWords.actEarlyOnConfirmation(null));
        // The final answer: "no, change" changes, a plain "no" cancels.
        assertTrue(ReplyWords.wantsChange("no, cambia messaggio"));
        assertEquals(Boolean.FALSE, ReplyWords.yesOrNo("no"));
    }

    @Test
    public void changeMessage() {
        assertTrue(ReplyWords.wantsChange("Cambia messaggio"));
        assertTrue(ReplyWords.wantsChange("no, modifica"));
        assertTrue(ReplyWords.wantsChange("ändern"));
        assertFalse(ReplyWords.wantsChange("sì mandalo"));
        assertFalse(ReplyWords.wantsChange(null));
    }
}
