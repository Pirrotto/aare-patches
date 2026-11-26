package app.template.extension.aare;

import android.os.SystemClock;
import android.util.Log;

import java.util.List;
import java.util.Locale;

/**
 * Test diagnostics for the map that moves aside on the car screen: each free area AARE works out for
 * the navigation app (MapGeometry.unobstructed: the map, the cards over it, the area left free) is
 * noted in pixels. Only sizes, never a position or what the cards show. At most [MAX_PER_MINUTE]
 * lines a minute, and the same layout only once in a row.
 */
// Public: called from AARE's MapGeometry (another package).
public final class MapAreaNote {
    private static final String TAG = "AarePatchesMapArea";
    static final int MAX_PER_MINUTE = 20;
    private static final long MINUTE_MS = 60_000;

    private static String last;
    private static long windowStart;
    private static int inWindow;

    private MapAreaNote() {
    }

    /** Set while [note] asks MapGeometry itself for the result (main thread: Compose's effect). */
    private static boolean inside;

    /**
     * At the start of MapGeometry.unobstructed([map], [obstacles]) on [geometry]: works the result out
     * with the same method (the parameters' registers may be reused before it returns) and notes it.
     * Never throws.
     */
    public static void note(Object geometry, Object map, Object obstacles) {
        if (inside) return;
        try {
            if (geometry == null || !PatchSettings.isEnabled(null, PatchSettings.MAP_AREA_NOTE)) return;
            Object free;
            inside = true;
            try {
                free = geometry.getClass().getMethod("unobstructed", map.getClass(), List.class).invoke(geometry, map, obstacles);
            } finally {
                inside = false;
            }
            StringBuilder cards = new StringBuilder();
            if (obstacles instanceof List) {
                for (Object o : (List<?>) obstacles) {
                    if (cards.length() > 0) cards.append(", ");
                    cards.append(box(o));
                }
            }
            String line = line(box(map), cards.toString(), box(free));
            long now = SystemClock.elapsedRealtime();
            synchronized (MapAreaNote.class) {
                if (line.equals(last)) return;
                if (now - windowStart > MINUTE_MS) {
                    windowStart = now;
                    inWindow = 0;
                }
                if (++inWindow > MAX_PER_MINUTE) return;
                last = line;
            }
            PatchLog.add("map area", line);
        } catch (Throwable t) {
            Log.w(TAG, "note", t);
        }
    }

    /** "map 0,0-1920,720; cards [0,0-640,720]; free 640,0-1920,720": left,top-right,bottom in px. */
    static String line(String map, String cards, String free) {
        return "map " + map + "; cards [" + cards + "]; free " + free;
    }

    static String box(float left, float top, float right, float bottom) {
        return String.format(Locale.ROOT, "%d,%d-%d,%d", Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
    }

    /** A MapGeometry.Box through its public getters. */
    private static String box(Object b) throws ReflectiveOperationException {
        if (b == null) return "none";
        Class<?> c = b.getClass();
        return box((Float) c.getMethod("getLeft").invoke(b), (Float) c.getMethod("getTop").invoke(b),
                (Float) c.getMethod("getRight").invoke(b), (Float) c.getMethod("getBottom").invoke(b));
    }
}
