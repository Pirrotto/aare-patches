package app.template.extension.aare;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MapAreaNoteTest {
    @Test
    public void boxIsLeftTopRightBottomInWholePixels() {
        assertEquals("0,0-1920,720", MapAreaNote.box(0f, 0f, 1920f, 720f));
        assertEquals("641,0-1920,719", MapAreaNote.box(640.6f, 0.2f, 1920f, 719.4f));
    }

    @Test
    public void lineNamesMapCardsAndFreeArea() {
        assertEquals("map 0,0-1920,720; cards [0,0-640,400]; free 640,0-1920,720",
                MapAreaNote.line("0,0-1920,720", "0,0-640,400", "640,0-1920,720"));
    }
}
