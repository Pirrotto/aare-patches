package app.template.extension.aare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

/** The tunnel maps as the phone reads them: what comes from the network is checked before any use. */
public class TunnelMapFileTest {
    /**
     * Written by tools/tunnelmaps/tunnelmaps.py: encode("CH", [(True, [(47.3769, 8.5417), (47.38, 8.55)]),
     * (False, [(46.0, 8.0), (46.001, 8.0005), (-46.002, -8.0)])]), so both sides read the same format.
     */
    private static final String FIXTURE = "1f8b08000000000002ff012f00d0ff4141524554554e01434802010288a8972d88d89208b830d8810100"
            + "03bfbaa801df9143d00fe807efeade57e797a10f9527fb292f000000";

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(out)) {
            z.write(raw);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] gz) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void refused(byte[] gz, String country) {
        try {
            TunnelMapFile.read(gz, country);
            fail("accepted a bad file");
        } catch (IOException expected) {
            // refused, as it should be
        }
    }

    @Test
    public void thePythonFileIsReadTheSame() throws IOException {
        List<TunnelMapFile.Way> ways = TunnelMapFile.read(hex(FIXTURE), "CH");
        assertEquals(2, ways.size());
        assertTrue(ways.get(0).oneWay);
        assertEquals(47.3769, ways.get(0).lat[0], 1e-9);
        assertEquals(8.55, ways.get(0).lon[1], 1e-9);
        assertFalse(ways.get(1).oneWay);
        assertEquals(3, ways.get(1).lat.length);
        assertEquals(-46.002, ways.get(1).lat[2], 1e-9);
        assertEquals(-8.0, ways.get(1).lon[2], 1e-9);
    }

    @Test
    public void aFileOfAnotherCountryOrDamagedIsRefused() throws IOException {
        byte[] good = hex(FIXTURE);
        refused(good, "DE");
        byte[] raw = gunzip(good);
        byte[] magic = raw.clone();
        magic[0] = 'X';
        refused(gzip(magic), "CH");
        byte[] version = raw.clone();
        version[7] = 2;
        refused(gzip(version), "CH");
        refused(gzip(java.util.Arrays.copyOf(raw, raw.length - 1)), "CH");
        byte[] longer = java.util.Arrays.copyOf(raw, raw.length + 1);
        refused(gzip(longer), "CH");
        refused(new byte[] {1, 2, 3}, "CH");
        refused(null, "CH");
    }

    @Test
    public void liesInTheFileAreRefused() throws IOException {
        // A huge way count in a tiny file.
        byte[] head = "AARETUN".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(head);
        b.write(1);
        b.write('C');
        b.write('H');
        b.write(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F});
        refused(gzip(b.toByteArray()), "CH");
        // A way of one point.
        b.reset();
        b.write(head);
        b.write(1);
        b.write('C');
        b.write('H');
        b.write(new byte[] {1, 0, 1, 0, 0});
        refused(gzip(b.toByteArray()), "CH");
        // A point outside the Earth (latitude 100).
        b.reset();
        b.write(head);
        b.write(1);
        b.write('C');
        b.write('H');
        b.write(new byte[] {1, 0, 2, (byte) 0x80, (byte) 0x84, (byte) 0xAF, 0x5F, 0, 0, 0});
        refused(gzip(b.toByteArray()), "CH");
    }

    @Test
    public void theIndexIsReadStrictly() throws IOException {
        String a = "a".repeat(64);
        Map<String, TunnelMapFile.Entry> index = TunnelMapFile.readIndex(
                ("AARETUN-INDEX 1\nCH " + a + " 23783 940\nDE " + a + " 2 3\n").getBytes(StandardCharsets.US_ASCII));
        assertEquals(2, index.size());
        assertEquals(23783, index.get("CH").bytes);
        assertEquals(a, index.get("DE").sha256);
        String[] bad = {"", "AARETUN-INDEX 2\n", "AARETUN-INDEX 1", "AARETUN-INDEX 1\nch " + a + " 1 1\n",
                "AARETUN-INDEX 1\nCH " + a.substring(1) + " 1 1\n", "AARETUN-INDEX 1\nCH " + a + " 0 1\n",
                "AARETUN-INDEX 1\nCH " + a + " 99999999999 1\n", "AARETUN-INDEX 1\nCH " + a + " 1 1\nCH " + a + " 1 1\n",
                "AARETUN-INDEX 1\nCH " + a + " 1 1\n\n", "AARETUN-INDEX 1\nCH ../" + a + " 1 1\n"};
        for (String s : bad) {
            try {
                TunnelMapFile.readIndex(s.getBytes(StandardCharsets.US_ASCII));
                fail("accepted: " + s);
            } catch (IOException expected) {
                // refused
            }
        }
    }

    @Test
    public void onlyTheRightSignatureIsAccepted() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = g.generateKeyPair(), other = g.generateKeyPair();
        byte[] data = hex(FIXTURE);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(pair.getPrivate());
        s.update(data);
        byte[] sig = s.sign();
        assertTrue(TunnelMapFile.verify(data, sig, pair.getPublic()));
        // Another key, a changed file, a damaged or missing signature: refused.
        assertFalse(TunnelMapFile.verify(data, sig, other.getPublic()));
        byte[] changed = data.clone();
        changed[20] ^= 1;
        assertFalse(TunnelMapFile.verify(changed, sig, pair.getPublic()));
        assertFalse(TunnelMapFile.verify(data, new byte[] {1, 2, 3}, pair.getPublic()));
        assertFalse(TunnelMapFile.verify(data, null, pair.getPublic()));
        assertFalse(TunnelMapFile.verify(data, sig, null));
    }

    @Test
    public void thePinnedKeyIsAP256Key() throws Exception {
        PublicKey key = TunnelMapFile.publicKey(TunnelMaps.PUBLIC_KEY);
        assertEquals("EC", key.getAlgorithm());
        assertEquals(256, ((ECPublicKey) key).getParams().getOrder().bitLength());
        try {
            TunnelMapFile.publicKey("zz");
            fail("accepted a broken key");
        } catch (java.security.GeneralSecurityException expected) {
            // refused
        }
    }

    @Test
    public void sha256() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", TunnelMapFile.sha256(new byte[0]));
    }
}
