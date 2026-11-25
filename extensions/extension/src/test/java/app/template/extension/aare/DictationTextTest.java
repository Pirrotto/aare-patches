package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DictationTextTest {
    @Test
    public void hesitationsAreDropped() {
        assertEquals("arrivo tra dieci minuti", DictationText.clean("eee arrivo ehm tra mmm dieci minuti"));
        assertEquals("Ciao, ci vediamo dopo", DictationText.clean("Ciao, ehm, ci vediamo dopo"));
        assertEquals("ich komme später", DictationText.clean("äh ich ähm komme später"));
        assertEquals("", DictationText.clean("eee mmm"));
        assertEquals("", DictationText.clean(null));
    }

    @Test
    public void ordinaryWordsStay() {
        // "e" (and), "ah", "ehi", "em" in words are words.
        assertEquals("pane e latte", DictationText.clean("pane e latte"));
        assertEquals("ah ok ehi Emma", DictationText.clean("ah ok ehi Emma"));
        assertEquals("mamma", DictationText.clean("mamma hmm"));
    }

    @Test
    public void piecesAreJoined() {
        assertEquals("Arrivo tra poco ci vediamo", DictationText.join("Arrivo tra poco", "ci vediamo"));
        assertEquals("Arrivo", DictationText.join("", "Arrivo"));
        assertEquals("Arrivo", DictationText.join("Arrivo", ""));
        assertEquals("", DictationText.join(null, null));
    }

    @Test
    public void aPieceAfterAFullStopStartsWithACapital() {
        assertEquals("Arrivo tra poco. Ci vediamo", DictationText.join("Arrivo tra poco.", "ci vediamo"));
        assertEquals("Ci sei? Sono qui", DictationText.join("Ci sei?", "sono qui"));
        assertEquals("Che bello! Grazie", DictationText.join("Che bello!", "grazie"));
        assertEquals("Allora\u2026 Domani", DictationText.join("Allora\u2026", "domani"));
        // No full stop is made up between pieces: a pause can come mid-sentence.
        assertEquals("Arrivo tra dieci minuti", DictationText.join("Arrivo tra", "dieci minuti"));
        assertEquals("Arrivo, poi ti chiamo", DictationText.join("Arrivo,", "poi ti chiamo"));
        // A piece already capitalised, or starting with a digit, stays as it is.
        assertEquals("Ok. Domani", DictationText.join("Ok.", "Domani"));
        assertEquals("Ok. 10 minuti", DictationText.join("Ok.", "10 minuti"));
    }

    @Test
    public void punctuationIsOnlyDetected() {
        assertTrue(DictationText.punctuated("Arrivo tra poco."));
        assertTrue(DictationText.punctuated("Ciao, ci sei"));
        assertFalse(DictationText.punctuated("arrivo tra poco"));
        assertFalse(DictationText.punctuated(""));
        assertFalse(DictationText.punctuated(null));
        assertFalse(DictationText.endsSentence(null));
        assertFalse(DictationText.endsSentence("  "));
        assertTrue(DictationText.endsSentence("Ci sei? "));
    }
}
