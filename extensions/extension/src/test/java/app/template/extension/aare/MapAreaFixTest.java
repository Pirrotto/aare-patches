package app.template.extension.aare;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class MapAreaFixTest {
    private static final float[] MAP = {310, 248, 1734, 831};

    private static List<float[]> cards(float[]... c) {
        return new ArrayList<>(Arrays.asList(c));
    }

    @Test
    public void splitLeftColumnKeepsTheTallAreaNotTheStripBetween() {
        // The owner's drive, 27.11 06:42:41: AARE kept 310,475-1630,703, the strip between Vela's two left cards.
        List<float[]> c = cards(
                new float[]{1630, 623, 1718, 815},
                new float[]{326, 264, 911, 475},
                new float[]{326, 703, 911, 815},
                new float[]{1526, 264, 1718, 352});
        assertArrayEquals(new float[]{911, 248, 1526, 831}, MapAreaFix.largestFree(MAP, c), 0f);
    }

    @Test
    public void sameResultWhateverTheOrderOfTheCards() {
        List<float[]> c = cards(
                new float[]{326, 703, 911, 815},
                new float[]{1526, 264, 1718, 352},
                new float[]{1630, 623, 1718, 815},
                new float[]{326, 264, 911, 475});
        float[] first = MapAreaFix.largestFree(MAP, c);
        Collections.reverse(c);
        assertArrayEquals(first, MapAreaFix.largestFree(MAP, c), 0f);
        assertArrayEquals(new float[]{911, 248, 1526, 831}, first, 0f);
    }

    @Test
    public void fullHeightLeftCardLeavesTheRight() {
        // 06:42:33: one card on the left, AARE's result was already right.
        assertArrayEquals(new float[]{911, 248, 1734, 831},
                MapAreaFix.largestFree(MAP, cards(new float[]{326, 264, 911, 815})), 0f);
    }

    @Test
    public void noCardsGivesTheWholeMap() {
        assertArrayEquals(MAP, MapAreaFix.largestFree(MAP, cards()), 0f);
    }

    @Test
    public void cardsOutsideTheMapAreIgnored() {
        assertArrayEquals(MAP, MapAreaFix.largestFree(MAP, cards(new float[]{0, 0, 300, 240}, new float[]{1734, 248, 1900, 831})), 0f);
    }

    @Test
    public void noFreeAreaGivesNull() {
        assertNull(MapAreaFix.largestFree(MAP, cards(new float[]{0, 0, 2000, 1000})));
        assertNull(MapAreaFix.largestFree(new float[]{10, 10, 10, 20}, cards()));
        assertNull(MapAreaFix.largestFree(null, cards()));
    }

    @Test
    public void touchingCardsDoNotOverlap() {
        assertNull(MapAreaFix.intersect(new float[]{0, 0, 10, 10}, new float[]{10, 0, 20, 10}));
        assertArrayEquals(new float[]{5, 5, 10, 10}, MapAreaFix.intersect(new float[]{0, 0, 10, 10}, new float[]{5, 5, 20, 20}), 0f);
    }
}
