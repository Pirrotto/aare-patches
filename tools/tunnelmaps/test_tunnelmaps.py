"""Tests of the tunnel maps builder: python3 -m unittest discover tools/tunnelmaps"""
import gzip
import unittest

import tunnelmaps as tm


def line(lat, lon, n, step=0.001):
    """[n] points due north, about 111 m apart for step 0.001."""
    return [(lat + i * step, lon) for i in range(n)]


class EncodingTest(unittest.TestCase):
    def test_roundtrip(self):
        ways = [(True, [(47.3769, 8.5417), (47.3800, 8.5500)]), (False, [(-33.9, 151.2), (-33.8999, 151.2001), (-33.8, 151.3)])]
        country, back = tm.decode(tm.encode("ch", ways))
        self.assertEqual("CH", country)
        self.assertEqual(len(ways), len(back))
        for (ow, pts), (ow2, pts2) in zip(ways, back):
            self.assertEqual(ow, ow2)
            for a, b in zip(pts, pts2):
                self.assertAlmostEqual(a[0], b[0], places=6)
                self.assertAlmostEqual(a[1], b[1], places=6)

    def test_the_same_tunnels_give_the_same_bytes(self):
        ways = [(False, line(46.0, 8.0, 5))]
        self.assertEqual(tm.encode("CH", ways), tm.encode("CH", ways))

    def test_bad_input_is_refused(self):
        for bad in [("C", []), ("C1", []), ("CH", [(False, [(1, 1)])]), ("CH", [(False, [(91, 0), (0, 0)])]),
                    ("CH", [(False, [(0, 181), (0, 0)])])]:
            with self.assertRaises(ValueError, msg=str(bad)):
                tm.encode(*bad)

    def test_a_damaged_file_is_refused(self):
        good = gzip.decompress(tm.encode("CH", [(False, line(46.0, 8.0, 3))]))
        with self.assertRaises(ValueError):
            tm.decode(gzip.compress(b"XXXXXXX" + good[7:]))
        with self.assertRaises(ValueError):
            tm.decode(gzip.compress(good + b"\x00"))
        with self.assertRaises(Exception):
            tm.decode(gzip.compress(good[:-2]))

    def test_varint(self):
        self.assertEqual(b"\x00", tm.varint(0))
        self.assertEqual(b"\xac\x02", tm.varint(300))
        self.assertEqual(1, tm.zigzag(-1))
        self.assertEqual(2, tm.zigzag(1))
        with self.assertRaises(ValueError):
            tm.varint(-1)


class GeometryTest(unittest.TestCase):
    def test_distance(self):
        self.assertAlmostEqual(111.2, tm.distance_m((46.0, 8.0), (46.001, 8.0)), delta=0.5)

    def test_a_straight_tunnel_keeps_its_ends_only(self):
        self.assertEqual(2, len(tm.simplify(line(46.0, 8.0, 20))))

    def test_a_curve_is_kept(self):
        pts = line(46.0, 8.0, 5) + [(46.004, 8.0 + 0.001 * i) for i in range(1, 6)]
        out = tm.simplify(pts)
        self.assertIn((46.004, 8.0), out)
        self.assertEqual(pts[0], out[0])
        self.assertEqual(pts[-1], out[-1])

    def test_short_tunnels_are_dropped_but_a_long_one_split_in_ways_stays(self):
        short = (False, line(45.0, 7.0, 2))  # 111 m
        part1 = (False, line(46.0, 8.0, 2))  # 111 m
        part2 = (False, [line(46.0, 8.0, 2)[-1], (46.002, 8.0)])  # joined to part1: 222 m together
        kept = tm.long_tunnels([short, part1, part2])
        self.assertEqual([part1, part2], kept)


class OverpassTest(unittest.TestCase):
    def test_ways_and_directions(self):
        els = [
            {"type": "way", "tags": {"highway": "motorway"}, "geometry": [{"lat": 1, "lon": 1}, {"lat": 2, "lon": 2}]},
            {"type": "way", "tags": {"highway": "primary", "oneway": "-1"}, "geometry": [{"lat": 1, "lon": 1}, {"lat": 2, "lon": 2}]},
            {"type": "way", "tags": {"highway": "primary"}, "geometry": [{"lat": 1, "lon": 1}, {"lat": 2, "lon": 2}]},
            {"type": "way", "tags": {"highway": "primary"}, "geometry": [{"lat": 1, "lon": 1}]},
            {"type": "node", "lat": 1, "lon": 1},
        ]
        ways = tm.from_overpass(els)
        self.assertEqual(3, len(ways))
        self.assertTrue(ways[0][0])
        self.assertEqual([(2, 2), (1, 1)], ways[1][1])
        self.assertTrue(ways[1][0])
        self.assertFalse(ways[2][0])

    def test_build(self):
        data, n = tm.build("CH", [(False, line(46.0, 8.0, 5))])
        self.assertEqual(1, n)
        self.assertEqual("CH", tm.decode(data)[0])



class IndexTest(unittest.TestCase):
    def test_roundtrip_and_order(self):
        c = {"DE": {"sha256": "b" * 64, "bytes": 2, "ways": 3, "file": "Germany.aaretun"},
             "CH": {"sha256": "a" * 64, "bytes": 23783, "ways": 940, "file": "Switzerland.aaretun"}}
        text = tm.write_index(c)
        self.assertEqual("AARETUN-INDEX 2\nCH " + "a" * 64 + " 23783 940 Switzerland.aaretun\nDE " + "b" * 64
                         + " 2 3 Germany.aaretun\n", text)
        self.assertEqual(c, tm.read_index(text))

    def test_a_bad_index_is_refused(self):
        f = " Switzerland.aaretun"
        for bad in ["", "AARETUN-INDEX 1\n", "AARETUN-INDEX 2\nch " + "a" * 64 + " 1 1" + f,
                    "AARETUN-INDEX 2\nCH " + "a" * 63 + " 1 1" + f, "AARETUN-INDEX 2\nCH " + "a" * 64 + " -1 1" + f,
                    "AARETUN-INDEX 2\nCH " + "a" * 64 + " 1 1" + f + "\nCH " + "a" * 64 + " 1 1" + f,
                    "AARETUN-INDEX 2\nCH " + "a" * 64 + " 1 1 ../x.aaretun", "AARETUN-INDEX 2\nCH " + "a" * 64 + " 1 1"]:
            with self.assertRaises(ValueError, msg=bad):
                tm.read_index(bad)



class PublishEachTest(unittest.TestCase):
    def test_order(self):
        self.assertEqual(["CH", "DE", "AT", "FR"], tm.ordered(["AT", "CH", "DE", "FR"], ["CH", "DE", "XX"]))
        self.assertEqual(["AT", "CH"], tm.ordered(["AT", "CH"], []))

    def test_merge_index(self):
        e = {"sha256": "a" * 64, "bytes": 5, "ways": 1, "file": "Switzerland.aaretun"}
        text = tm.merge_index("", "CH", e)
        self.assertEqual({"CH": e}, tm.read_index(text))
        e2 = {"sha256": "b" * 64, "bytes": 7, "ways": 2, "file": "Austria.aaretun"}
        text = tm.merge_index(text, "AT", e2)
        text = tm.merge_index(text, "CH", e2)
        self.assertEqual({"AT": e2, "CH": e2}, tm.read_index(text))
        self.assertEqual({"CH": e2}, tm.read_index(tm.merge_index(text, "AT", None)))



class PackTest(unittest.TestCase):
    def test_roundtrip(self):
        m = tm.encode("CH", [(False, line(46.0, 8.0, 3))])
        sig = bytes(range(70))
        packed = tm.pack(m, sig)
        self.assertEqual(b"AARETUNS\x01\x00\x46", packed[:11])
        self.assertEqual((m, sig), tm.unpack(packed))

    def test_bad_packs(self):
        for bad in [b"", b"AARETUNX\x01\x00\x01xy", b"AARETUNS\x02\x00\x01xy", b"AARETUNS\x01\x00\x05xy",
                    b"AARETUNS\x01\x00\x00y"]:
            with self.assertRaises(ValueError, msg=bad):
                tm.unpack(bad)
        with self.assertRaises(ValueError):
            tm.pack(b"m", b"")

    def test_file_names(self):
        self.assertEqual("Switzerland.aaretun", tm.file_name("Switzerland", "CH"))
        self.assertEqual("United-States.aaretun", tm.file_name("United States", "US"))
        self.assertEqual("Cote-d-Ivoire.aaretun", tm.file_name("Côte d'Ivoire", "CI"))
        self.assertEqual("XK.aaretun", tm.file_name("", "XK"))
        self.assertEqual("Congo-CG.aaretun", tm.file_name("Congo", "CG", {"Congo.aaretun"}))


if __name__ == "__main__":
    unittest.main()
