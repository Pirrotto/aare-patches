package app.template.extension.aare;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The patches' technical diagnostics, copied by "Copy diagnostics" in Settings. Technical state
 * only: never a position, a name, a message or dictated text (CLAUDE.md, security rules) – callers
 * log counts, states and decisions.
 *
 * Kept in a file in AARE's own private storage (no other app can read it, not backed up), so it is
 * still there after AARE closed: the newest [MAX] lines, across drives, oldest dropped first. In each
 * run of AARE a "patch log opened" line comes before the first one, so the drives can be told apart:
 * it marks when the patches first logged something, not when AARE started.
 */
final class PatchLog {
    private static final String TAG = "AarePatchLog";
    static final int MAX = 1000;
    private static final String FILE = "aare-patches-log.txt";

    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    /** Writes the file off the calling thread, in order. */
    private static final ExecutorService writer = Executors.newSingleThreadExecutor();
    private static boolean loaded;
    private static File file;
    private static int appendedSinceTrim;

    private PatchLog() {
    }

    static void add(String area, String message) {
        String line = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT).format(new Date()) + " " + area + ": " + message;
        synchronized (lines) {
            load();
            keep(line);
            final File f = file;
            if (f == null) return;
            if (++appendedSinceTrim >= MAX / 4) {
                appendedSinceTrim = 0;
                final List<String> all = new ArrayList<>(lines);
                writer.execute(() -> write(f, all, false));
            } else {
                final List<String> one = java.util.Collections.singletonList(line);
                writer.execute(() -> write(f, one, true));
            }
        }
    }

    static String dump() {
        StringBuilder b = new StringBuilder();
        synchronized (lines) {
            load();
            for (String l : lines) b.append(l).append('\n');
        }
        return b.toString();
    }

    /** On first use in this run of AARE: the lines kept from earlier runs, then a start line. */
    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Context app = application();
            if (app == null) return;
            file = new File(app.getNoBackupFilesDir(), FILE);
            if (file.exists()) {
                try (BufferedReader r = new BufferedReader(new FileReader(file))) {
                    for (String l = r.readLine(); l != null; l = r.readLine()) keep(l);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "load", t);
        }
        String start = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT).format(new Date()) + " === patch log opened";
        keep(start);
        final File f = file;
        if (f != null) {
            final List<String> all = new ArrayList<>(lines);
            writer.execute(() -> write(f, all, false));
        }
    }

    private static void keep(String line) {
        lines.addLast(line);
        while (lines.size() > MAX) lines.removeFirst();
    }

    private static void write(File f, List<String> content, boolean append) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f, append), StandardCharsets.UTF_8)) {
            for (String l : content) w.write(l + "\n");
        } catch (IOException t) {
            Log.w(TAG, "write", t);
        }
    }

    /** AARE's application: the log is written from places that have no Context at hand. */
    private static Context application() {
        try {
            Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            return app instanceof Context ? (Context) app : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
