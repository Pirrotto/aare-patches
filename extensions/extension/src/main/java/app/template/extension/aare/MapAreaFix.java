package app.template.extension.aare;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The free part of the map given to the navigation app (onVisibleAreaChanged), as the largest
 * rectangle the cards leave free.
 *
 * AARE's MapGeometry.unobstructed cuts the map card by card, largest card first, keeping each time
 * the largest of the four slices beside that card. With Vela's left column split in two (the
 * manoeuvre at the top, the arrival at the bottom) that keeps the strip between them, as wide as the
 * screen, instead of the taller area to their right: Vela centres the car on the whole width and
 * the route moves towards the cards. Here every rectangle bounded by the map's and the cards' edges
 * is tried, so the result is the largest free one whatever the order of the cards.
 */
// Public: called from AARE's MapGeometry (another package).
public final class MapAreaFix {
    private static final String TAG = "AarePatchesMapArea";

    private MapAreaFix() {
    }

    /**
     * The largest rectangle inside [map] that overlaps none of [cards] (each {left, top, right,
     * bottom}, right/bottom exclusive). Ties go to the wider one, then the first found. Null if
     * nothing of the map is free.
     */
    static float[] largestFree(float[] map, List<float[]> cards) {
        if (map == null || !(map[2] > map[0]) || !(map[3] > map[1])) return null;
        List<float[]> inside = new ArrayList<>();
        for (float[] c : cards) {
            float[] in = intersect(c, map);
            if (in != null) inside.add(in);
        }
        // A free rectangle as large as possible has each side on the map's edge or on a card's edge.
        TreeSet<Float> xs = new TreeSet<>();
        TreeSet<Float> ys = new TreeSet<>();
        xs.add(map[0]);
        xs.add(map[2]);
        ys.add(map[1]);
        ys.add(map[3]);
        for (float[] c : inside) {
            xs.add(c[0]);
            xs.add(c[2]);
            ys.add(c[1]);
            ys.add(c[3]);
        }
        Float[] x = xs.toArray(new Float[0]);
        Float[] y = ys.toArray(new Float[0]);
        float[] best = null;
        float bestArea = 0;
        for (int l = 0; l < x.length; l++) {
            for (int r = l + 1; r < x.length; r++) {
                for (int t = 0; t < y.length; t++) {
                    for (int b = t + 1; b < y.length; b++) {
                        float[] box = {x[l], y[t], x[r], y[b]};
                        float area = (box[2] - box[0]) * (box[3] - box[1]);
                        if (area < bestArea) continue;
                        if (area == bestArea && best != null && box[2] - box[0] <= best[2] - best[0]) continue;
                        if (overlapsAny(box, inside)) continue;
                        best = box;
                        bestArea = area;
                    }
                }
            }
        }
        return best;
    }

    private static boolean overlapsAny(float[] box, List<float[]> cards) {
        for (float[] c : cards) {
            if (intersect(box, c) != null) return true;
        }
        return false;
    }

    /** The overlap of two rectangles, or null if they only touch or are apart (as AARE's Box.intersect). */
    static float[] intersect(float[] a, float[] b) {
        float left = Math.max(a[0], b[0]);
        float top = Math.max(a[1], b[1]);
        float right = Math.min(a[2], b[2]);
        float bottom = Math.min(a[3], b[3]);
        if (right <= left || bottom <= top) return null;
        return new float[]{left, top, right, bottom};
    }

    /**
     * At the start of MapGeometry.unobstructed([map], [obstacles]): the largest free rectangle as an
     * AARE Box, or null to let AARE work it out (switch off, nothing free, or an error). Never throws.
     */
    public static Object unobstructed(Object map, Object obstacles) {
        try {
            if (map == null || !(obstacles instanceof List)) return null;
            if (!PatchSettings.isEnabled(null, PatchSettings.MAP_AREA_FIX)) return null;
            List<float[]> cards = new ArrayList<>();
            for (Object o : (List<?>) obstacles) {
                if (o != null) cards.add(floats(o));
            }
            float[] free = largestFree(floats(map), cards);
            if (free == null) return null;
            Constructor<?> box = map.getClass().getConstructor(float.class, float.class, float.class, float.class);
            Object result = box.newInstance(free[0], free[1], free[2], free[3]);
            MapAreaNote.noted(map, obstacles, result, true);
            return result;
        } catch (Throwable t) {
            Log.w(TAG, "unobstructed", t);
            return null;
        }
    }

    /** A MapGeometry.Box as {left, top, right, bottom}, through its public getters. */
    static float[] floats(Object b) throws ReflectiveOperationException {
        Class<?> c = b.getClass();
        return new float[]{(Float) c.getMethod("getLeft").invoke(b), (Float) c.getMethod("getTop").invoke(b),
                (Float) c.getMethod("getRight").invoke(b), (Float) c.getMethod("getBottom").invoke(b)};
    }
}
