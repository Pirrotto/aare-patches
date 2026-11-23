package app.template.extension.aare;

import static org.junit.Assert.assertEquals;

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
}
