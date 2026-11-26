package app.template.extension.aare;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The tunnel maps on the phone, built like AARE's phone certificate download
 * (cert/CertificateDownload and CertificateUpdates): a country's file is downloaded when the user
 * asks for it; with "Update the tunnel maps" on (off by default) the index is checked at most once
 * a day and never while a car is connected, and a country whose file changed is downloaded again.
 * With "Add the country I'm in" (off by default) the country of the mobile network is added too.
 *
 * Only this repository's "tunnel-maps" release is contacted, over HTTPS, and a request names only
 * a file (the index or a country): never a position. A file is kept only if its signature matches
 * the key pinned below and its SHA-256 the signed index's. The files live in AARE's no-backup
 * storage, readable by no other app. The diagnostics get counts only, no country.
 */
final class TunnelMaps {
    private static final String TAG = "AarePatchesMaps";
    private static final String BASE = "https://github.com/Pirrotto/aare-patches/releases/download/tunnel-maps/";
    /** The public half of the TUNNEL_MAPS_KEY secret (ECDSA P-256, X.509 DER in hex). */
    static final String PUBLIC_KEY = "3059301306072a8648ce3d020106082a8648ce3d03010703420004eccfbbbc3b965e723c78dcf8d1"
            + "31565a42bf64e048307ef2475006868c016f85269b872796a5c237fbfae03b6b7f669e7b1b362e9588ce3fcd36e90e2bc1adf9";
    private static final String PREFS = "aare_patches_tunnel_maps";
    private static final String LAST_CHECK = "last_check_ms";
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    private static final int TIMEOUT_MS = 10_000;

    /** One download or check at a time. */
    private static final AtomicBoolean busy = new AtomicBoolean();

    private TunnelMaps() {
    }

    /** Why a download or a check didn't work, for the user's message. */
    static final class Failure extends Exception {
        final boolean signature;

        Failure(String message, boolean signature) {
            super(message);
            this.signature = signature;
        }
    }

    // region Files

    static File dir(Context context) {
        File d = new File(context.getApplicationContext().getNoBackupFilesDir(), "tunnel_maps");
        if (!d.isDirectory() && !d.mkdirs()) Log.w(TAG, "can't create the maps folder");
        return d;
    }

    private static File file(Context context, String country) {
        return new File(dir(context), country + ".tun");
    }

    /** The countries on the phone, sorted. */
    static List<String> installed(Context context) {
        TreeSet<String> out = new TreeSet<>();
        File[] files = dir(context).listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.length() == 6 && n.endsWith(".tun")) out.add(n.substring(0, 2));
            }
        }
        return new ArrayList<>(out);
    }

    static long size(Context context, String country) {
        return file(context, country).length();
    }

    static boolean delete(Context context, String country) {
        File f = file(context, country);
        return !f.exists() || f.delete();
    }

    /** The country's name in the phone's language, for the list. */
    static String name(String country) {
        String n = new Locale("", country).getDisplayCountry();
        return n == null || n.isEmpty() ? country : n;
    }

    // region For the estimate

    private static TunnelMatch.Map cached;
    private static String cachedKey;

    /**
     * The maps on the phone, read once and kept while they don't change, or null with none. A file
     * that can't be read is left out (it was checked when downloaded: it would be a damaged disk).
     */
    static synchronized TunnelMatch.Map loaded(Context context) {
        List<String> countries = installed(context);
        if (countries.isEmpty()) {
            cached = null;
            cachedKey = null;
            return null;
        }
        StringBuilder key = new StringBuilder();
        for (String c : countries) {
            File f = file(context, c);
            key.append(c).append(':').append(f.length()).append(':').append(f.lastModified()).append(';');
        }
        if (key.toString().equals(cachedKey)) return cached;
        List<TunnelMapFile.Way> ways = new ArrayList<>();
        int bad = 0;
        for (String c : countries) {
            try {
                ways.addAll(TunnelMapFile.read(readFile(file(context, c)), c));
            } catch (IOException e) {
                bad++;
            }
        }
        if (bad > 0) PatchLog.add("maps", bad + " map files on the phone can't be read: left out");
        cached = ways.isEmpty() ? null : new TunnelMatch.Map(ways);
        cachedKey = key.toString();
        return cached;
    }

    // endregion

    // region Download

    private static PublicKey key() throws Failure {
        try {
            return TunnelMapFile.publicKey(PUBLIC_KEY);
        } catch (GeneralSecurityException e) {
            throw new Failure("the pinned key can't be read", true);
        }
    }

    /** The signed index. Blocking: call it off the main thread. */
    static Map<String, TunnelMapFile.Entry> index() throws Failure {
        byte[] data = get("index.txt", TunnelMapFile.MAX_INDEX_BYTES);
        byte[] sig = get("index.txt.sig", 1024);
        if (!TunnelMapFile.verify(data, sig, key())) throw new Failure("the index's signature is wrong", true);
        try {
            return TunnelMapFile.readIndex(data);
        } catch (IOException e) {
            throw new Failure("the index can't be read", false);
        }
    }

    /** Downloads [country] as the signed [index] describes it, and keeps it only if every check passes. Blocking. */
    static void download(Context context, String country, Map<String, TunnelMapFile.Entry> index) throws Failure {
        TunnelMapFile.Entry entry = index.get(country);
        if (entry == null) throw new Failure("no map for this country", false);
        byte[] data = get(country + ".tun", (int) entry.bytes);
        byte[] sig = get(country + ".tun.sig", 1024);
        if (data.length != entry.bytes || !entry.sha256.equals(TunnelMapFile.sha256(data))
                || !TunnelMapFile.verify(data, sig, key())) {
            throw new Failure("the file's signature is wrong", true);
        }
        save(context, country, data);
    }

    /**
     * A map file the user picked on the phone (with its signature file): kept only if the signature
     * matches the pinned key, like a downloaded one. Returns its country. Blocking.
     */
    static String install(Context context, byte[] data, byte[] sig) throws Failure {
        if (!TunnelMapFile.verify(data, sig, key())) throw new Failure("the file's signature is wrong", true);
        String country;
        try {
            country = TunnelMapFile.countryOf(data);
        } catch (IOException e) {
            throw new Failure("not a tunnel map", false);
        }
        save(context, country, data);
        return country;
    }

    /** Checks [data] is a map of [country] and writes it (signature already checked). */
    private static void save(Context context, String country, byte[] data) throws Failure {
        try {
            TunnelMapFile.read(data, country);
        } catch (IOException e) {
            throw new Failure("the file can't be read", false);
        }
        // Written beside it, then moved over it: a half-written map is never read.
        File target = file(context, country);
        File tmp = new File(target.getPath() + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
        } catch (IOException e) {
            tmp.delete();
            throw new Failure("the file can't be saved", false);
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new Failure("the file can't be saved", false);
        }
    }

    /** Whether the copy of [country] on the phone is the one the index names. */
    static boolean current(Context context, String country, Map<String, TunnelMapFile.Entry> index) {
        TunnelMapFile.Entry entry = index.get(country);
        if (entry == null) return true; // no newer one to get
        try {
            return entry.sha256.equals(TunnelMapFile.sha256(readFile(file(context, country))));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** A map file of the phone (java.nio's Files needs Android 8). */
    static byte[] readFile(File f) throws IOException {
        if (f.length() > TunnelMapFile.MAX_FILE_BYTES) throw new IOException("file too big");
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static byte[] get(String name, int maxBytes) throws Failure {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BASE + name).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "AARE");
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new Failure("HTTP " + code, false);
            // GitHub redirects to its file host; only HTTPS is followed (never down to HTTP).
            if (!"https".equals(c.getURL().getProtocol())) throw new Failure("not HTTPS", false);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (out.size() + n > maxBytes) throw new Failure("bigger than announced", true);
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new Failure("no connection", false);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // endregion

    // region Automatic updates

    /**
     * Runs the automatic update if it is switched on and due: at most once a day, never while a car
     * is connected, on a background thread. Returns at once; safe to call on every Settings open and
     * when a car disconnects.
     */
    static void refresh(Context context) {
        Context app = context.getApplicationContext();
        if (!PatchSettings.isEnabled(app, PatchSettings.TUNNEL_NAVIGATION)) return;
        boolean update = PatchSettings.isEnabled(app, PatchSettings.TUNNEL_MAPS_AUTO);
        boolean here = PatchSettings.isEnabled(app, PatchSettings.TUNNEL_MAPS_HERE);
        if (!update && !here) return;
        if (TunnelNavigation.connected()) return;
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - prefs.getLong(LAST_CHECK, 0) < DAY_MS) return;
        if (!busy.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                check(app, prefs, update, here);
            } finally {
                busy.set(false);
            }
        }, "aare-tunnel-maps").start();
    }

    private static void check(Context app, SharedPreferences prefs, boolean update, boolean here) {
        int got = 0, failed = 0;
        try {
            Map<String, TunnelMapFile.Entry> index = index();
            prefs.edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply();
            List<String> wanted = new ArrayList<>();
            if (update) {
                for (String c : installed(app)) if (!current(app, c, index)) wanted.add(c);
            }
            String country = here ? networkCountry(app) : null;
            if (country != null && index.containsKey(country) && !installed(app).contains(country)) wanted.add(country);
            for (String c : wanted) {
                if (TunnelNavigation.connected()) break; // a car came: the rest tomorrow
                try {
                    download(app, c, index);
                    got++;
                } catch (Failure f) {
                    failed++;
                    PatchLog.add("maps", "automatic update: a map failed (" + f.getMessage() + ")");
                }
            }
            if (got > 0 || failed > 0) PatchLog.add("maps", "automatic update: " + got + " maps downloaded, " + failed + " failed");
        } catch (Failure f) {
            // Silent like AARE's certificate check: tried again at the next Settings open or car disconnect.
            PatchLog.add("maps", "automatic update: index not loaded (" + f.getMessage() + ")");
        }
    }

    /** The mobile network's country (no permission, no position), or null. */
    private static String networkCountry(Context app) {
        try {
            TelephonyManager tm = app.getSystemService(TelephonyManager.class);
            String iso = tm == null ? null : tm.getNetworkCountryIso();
            if (iso == null || iso.length() != 2) return null;
            return iso.toUpperCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Takes the "busy" flag for a download the user asked for; false if one already runs. */
    static boolean begin() {
        return busy.compareAndSet(false, true);
    }

    static void end() {
        busy.set(false);
    }

    // endregion
}
