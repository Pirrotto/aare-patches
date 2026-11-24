package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReplyWordsTest {
    @Test
    public void sendItAnswersAsBefore() {
        assertEquals(Boolean.TRUE, ReplyWords.yesOrNo("Sì, mandalo"));
        assertEquals(Boolean.TRUE, ReplyWords.yesOrNo("ja schick"));
        assertEquals(Boolean.FALSE, ReplyWords.yesOrNo("No, annulla"));
        assertNull(ReplyWords.yesOrNo("forse"));
        assertNull(ReplyWords.yesOrNo("sì no"));
        assertNull(ReplyWords.yesOrNo(null));
        // "Reply" isn't a yes to sending.
        assertNull(ReplyWords.yesOrNo("rispondi"));
    }

    @Test
    public void doYouWantToReply() {
        assertEquals(Boolean.TRUE, ReplyWords.askAnswer("Sì"));
        assertEquals(Boolean.TRUE, ReplyWords.askAnswer("rispondi"));
        assertEquals(Boolean.TRUE, ReplyWords.askAnswer("Va bene"));
        assertEquals(Boolean.TRUE, ReplyWords.askAnswer("ok"));
        assertEquals(Boolean.TRUE, ReplyWords.askAnswer("Ja, antworten"));
        assertEquals(Boolean.FALSE, ReplyWords.askAnswer("no"));
        assertEquals(Boolean.FALSE, ReplyWords.askAnswer("lascia stare"));
        assertEquals(Boolean.FALSE, ReplyWords.askAnswer("niente"));
        assertEquals(Boolean.FALSE, ReplyWords.askAnswer("nein, später"));
        assertNull(ReplyWords.askAnswer("che ore sono"));
        assertNull(ReplyWords.askAnswer(""));
    }

    @Test
    public void moreWaysToDictateAgain() {
        String[] again = {"riprova", "Riprovare", "rifai", "rifallo", "ricomincia", "da capo, ricominciare", "daccapo",
                "ridetta", "no, è sbagliato", "retry", "redo", "again please", "that's wrong", "nochmal", "falsch", "neu"};
        for (String a : again) {
            assertTrue(a, ReplyWords.wantsChange(a));
            assertTrue(a, ReplyWords.actEarlyOnConfirmation(a));
        }
    }

    @Test
    public void anOrdinaryAnswerDoesNotDictateAgain() {
        String[] plain = {"sì", "sì mandalo", "ok", "no", "annulla", "ripeti", "di nuovo", "nuovo", "yes send", "ja", "nein"};
        for (String p : plain) assertFalse(p, ReplyWords.wantsChange(p));
    }
}
