package app.template.extension.aare;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * The patches' technical diagnostics, kept in memory only (gone when AARE closes) and copied by
 * "Copy diagnostics" in Settings. Technical state only: never a position, a name, a message or
 * dictated text (CLAUDE.md, security rules) – callers log counts, states and decisions.
 */
final class PatchLog {
    private static final int MAX = 300;
    private static final ArrayDeque<String> lines = new ArrayDeque<>();

    private PatchLog() {
    }

    static void add(String area, String message) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + " " + area + ": " + message;
        synchronized (lines) {
            lines.addLast(line);
            while (lines.size() > MAX) lines.removeFirst();
        }
    }

    static String dump() {
        StringBuilder b = new StringBuilder();
        synchronized (lines) {
            for (String l : lines) b.append(l).append('\n');
        }
        return b.toString();
    }
}
