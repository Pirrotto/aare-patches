package app.template.extension.aare;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * The tunnel maps' files as tools/tunnelmaps/tunnelmaps.py writes them, read and checked. Everything
 * here comes from the network, so nothing is trusted: sizes are bounded, every field is checked, and
 * a file is used only once its signature matches the key pinned in TunnelMaps. Plain Java, for tests.
 */
final class TunnelMapFile {
    private TunnelMapFile() {
    }

    static final String INDEX_HEADER = "AARETUN-INDEX 2";
    private static final byte[] MAGIC = "AARETUN".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    /** Largest file accepted, compressed and not: far above the biggest country (a few MB). */
    static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    static final int MAX_RAW_BYTES = 128 * 1024 * 1024;
    static final int MAX_INDEX_BYTES = 64 * 1024;
    static final int MAX_POINTS = 65535;
    private static final Pattern INDEX_LINE =
            Pattern.compile("^([A-Z]{2}) ([0-9a-f]{64}) ([0-9]{1,10}) ([0-9]{1,10}) ([A-Za-z0-9-]{1,60}\\.aaretun)$");
    private static final byte[] PACK_MAGIC = "AARETUNS".getBytes(StandardCharsets.US_ASCII);

    /** One tunnel way: its points in degrees, in the direction of travel when it is one way. */
    static final class Way {
        final boolean oneWay;
        final double[] lat;
        final double[] lon;

        Way(boolean oneWay, double[] lat, double[] lon) {
            this.oneWay = oneWay;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** One country of the index: its map's SHA-256, its published file's size and name. */
    static final class Entry {
        final String sha256;
        final long bytes;
        final long ways;
        final String file;

        Entry(String sha256, long bytes, long ways, String file) {
            this.sha256 = sha256;
            this.bytes = bytes;
            this.ways = ways;
            this.file = file;
        }
    }

    /**
     * A published file ("Switzerland.aaretun"): {map, signature}. Format: "AARETUNS", version 1,
     * the signature's length (2 bytes, big-endian), the signature, then the map. Nothing checked yet
     * but the layout: the caller verifies the signature and reads the map.
     */
    static byte[][] unpack(byte[] packed) throws IOException {
        if (packed == null || packed.length < 12 || packed.length > MAX_FILE_BYTES) throw new IOException("not a tunnel map file");
        for (int i = 0; i < PACK_MAGIC.length; i++) if (packed[i] != PACK_MAGIC[i]) throw new IOException("not a tunnel map file");
        if (packed[8] != 1) throw new IOException("unknown tunnel map file version");
        int n = ((packed[9] & 0xFF) << 8) | (packed[10] & 0xFF);
        if (n == 0 || n > 1024 || packed.length < 11 + n + 1) throw new IOException("bad tunnel map file");
        return new byte[][] {java.util.Arrays.copyOfRange(packed, 11 + n, packed.length),
                java.util.Arrays.copyOfRange(packed, 11, 11 + n)};
    }

    // region Signature

    /**
     * The pinned key, from its X.509 DER form in hex (hex rather than Base64: java.util.Base64 needs
     * Android 8, and android.util.Base64 isn't there in unit tests).
     */
    static PublicKey publicKey(String derHex) throws GeneralSecurityException {
        if (derHex == null || derHex.length() % 2 != 0) throw new GeneralSecurityException("bad key");
        byte[] der = new byte[derHex.length() / 2];
        for (int i = 0; i < der.length; i++) {
            int hi = Character.digit(derHex.charAt(2 * i), 16), lo = Character.digit(derHex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) throw new GeneralSecurityException("bad key");
            der[i] = (byte) (hi << 4 | lo);
        }
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
    }

    /** Whether [signature] (ECDSA P-256 over SHA-256, DER, as openssl dgst writes it) signs [data]. */
    static boolean verify(byte[] data, byte[] signature, PublicKey key) {
        if (data == null || signature == null || key == null) return false;
        try {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initVerify(key);
            s.update(data);
            return s.verify(signature);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false; // a malformed signature is a wrong one
        }
    }

    static String sha256(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format(Locale.ROOT, "%02x", x));
            return b.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // endregion

    // region Index

    /** The countries of an index, in its order; throws IOException if it is malformed in any way. */
    static Map<String, Entry> readIndex(byte[] data) throws IOException {
        if (data == null || data.length > MAX_INDEX_BYTES) throw new IOException("index too big");
        String text = new String(data, StandardCharsets.US_ASCII);
        String[] lines = text.split("\n", -1);
        if (lines.length < 2 || !INDEX_HEADER.equals(lines[0]) || !lines[lines.length - 1].isEmpty()) {
            throw new IOException("not a tunnel maps index");
        }
        Map<String, Entry> out = new LinkedHashMap<>();
        for (int i = 1; i < lines.length - 1; i++) {
            Matcher m = INDEX_LINE.matcher(lines[i]);
            if (!m.matches() || out.containsKey(m.group(1))) throw new IOException("bad index line " + i);
            long bytes = Long.parseLong(m.group(3));
            if (bytes <= 0 || bytes > MAX_FILE_BYTES) throw new IOException("bad size on line " + i);
            out.put(m.group(1), new Entry(m.group(2), bytes, Long.parseLong(m.group(4)), m.group(5)));
        }
        return Collections.unmodifiableMap(out);
    }

    // endregion

    // region Map file

    /** The country a map file says it is (its header), without trusting the rest: [read] checks that. */
    static String countryOf(byte[] gz) throws IOException {
        if (gz == null || gz.length > MAX_FILE_BYTES) throw new IOException("file too big");
        Reader r = new Reader(gunzip(gz));
        for (byte b : MAGIC) if (r.u8() != (b & 0xFF)) throw new IOException("not a tunnel map");
        if (r.u8() != VERSION) throw new IOException("unknown tunnel map version");
        int a = r.u8(), b = r.u8();
        if (a < 'A' || a > 'Z' || b < 'A' || b > 'Z') throw new IOException("bad country");
        return new String(new char[] {(char) a, (char) b});
    }

    /** The ways of a country's file; throws IOException if it isn't one of [country], or is damaged. */
    static List<Way> read(byte[] gz, String country) throws IOException {
        if (gz == null || gz.length > MAX_FILE_BYTES) throw new IOException("file too big");
        byte[] raw = gunzip(gz);
        Reader r = new Reader(raw);
        for (byte b : MAGIC) if (r.u8() != (b & 0xFF)) throw new IOException("not a tunnel map");
        if (r.u8() != VERSION) throw new IOException("unknown tunnel map version");
        String cc = new String(new byte[] {(byte) r.u8(), (byte) r.u8()}, StandardCharsets.US_ASCII);
        if (!cc.equals(country)) throw new IOException("the file is of another country");
        long count = r.varint();
        // Each way takes at least 1 + 1 + 2 * 2 bytes: a count above that is a lie.
        if (count > raw.length / 6) throw new IOException("bad way count");
        List<Way> ways = new ArrayList<>((int) count);
        long plat = 0, plon = 0;
        for (long w = 0; w < count; w++) {
            boolean oneWay = (r.u8() & 1) != 0;
            long n = r.varint();
            if (n < 2 || n > MAX_POINTS) throw new IOException("bad point count");
            double[] lat = new double[(int) n], lon = new double[(int) n];
            for (int i = 0; i < n; i++) {
                plat += unzig(r.varint());
                plon += unzig(r.varint());
                if (plat < -90_000_000L || plat > 90_000_000L || plon < -180_000_000L || plon > 180_000_000L) {
                    throw new IOException("point out of range");
                }
                lat[i] = plat / 1e6;
                lon[i] = plon / 1e6;
            }
            ways.add(new Way(oneWay, lat, lon));
        }
        if (!r.atEnd()) throw new IOException("trailing bytes");
        return ways;
    }

    private static byte[] gunzip(byte[] gz) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(gz.length * 4);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > MAX_RAW_BYTES) throw new IOException("file too big once unpacked");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static long unzig(long n) {
        return (n >>> 1) ^ -(n & 1);
    }

    private static final class Reader {
        private final byte[] b;
        private int pos;

        Reader(byte[] b) {
            this.b = b;
        }

        int u8() throws IOException {
            if (pos >= b.length) throw new IOException("file cut short");
            return b[pos++] & 0xFF;
        }

        /** An unsigned varint of at most 5 bytes (35 bits: more than any field needs). */
        long varint() throws IOException {
            long n = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int x = u8();
                n |= (long) (x & 0x7F) << shift;
                if ((x & 0x80) == 0) return n;
            }
            throw new IOException("bad number");
        }

        boolean atEnd() {
            return pos == b.length;
        }
    }

    // endregion
}
