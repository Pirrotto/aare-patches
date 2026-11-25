#!/usr/bin/env python3
"""
Builds the tunnel maps: the shape of every road tunnel of a country, from OpenStreetMap, for the
tunnel estimate (TunnelNavigation) to follow instead of the gyroscope. Runs on GitHub (see
.github/workflows/tunnel-maps.yml), never on the phone. Only the Python standard library.

File format (README.md beside this script): a gzip of
  b"AARETUN" + version (1 byte, 1), country (2 ASCII bytes), way count (varint), then per way:
  flags (1 byte: bit 0 one way), point count (varint), and its points as zigzag varint deltas of
  latitude and longitude in millionths of a degree, running on from the previous point of the file.
gzip with mtime 0, so the same tunnels give the same bytes and an unchanged country is not
published again.

The index (index.txt) is plain text the phone reads without a JSON parser: the line
"AARETUN-INDEX 1", then one line per country, sorted: "<CC> <sha256> <bytes> <ways>".

Data © OpenStreetMap contributors, ODbL 1.0.
"""
import argparse
import gzip
import hashlib
import json
import math
import re
import sys
import time
import urllib.parse
import urllib.request

MAGIC = b"AARETUN"
VERSION = 1
# Car roads only (no paths, railways or parking garages' service roads).
HIGHWAYS = ("motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|"
            "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link")
# A tunnel (its connected tunnel ways together) shorter than this needs no map: the gyroscope's
# estimate is honest enough for it.
MIN_TUNNEL_M = 200.0
# Points closer than this to the line through their neighbours are dropped (Douglas-Peucker).
SIMPLIFY_M = 2.0
# One way of a few kilometres can't have more than this many points once simplified; more means
# broken data, and the phone refuses such a file rather than trusting it.
MAX_POINTS = 65535
OVERPASS = ("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
EARTH_M = 6371008.8


# region Encoding

def varint(n):
    if n < 0:
        raise ValueError("varint of a negative number")
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def zigzag(n):
    return (n << 1) if n >= 0 else ((-n << 1) - 1)


def encode(country, ways):
    """[ways]: (one_way, [(lat, lon), ...]) in degrees. Returns the gzip bytes."""
    if len(country) != 2 or not country.isascii() or not country.isalpha():
        raise ValueError("country must be a two-letter code")
    body = bytearray(MAGIC)
    body.append(VERSION)
    body += country.upper().encode("ascii")
    body += varint(len(ways))
    plat = plon = 0
    for one_way, points in ways:
        if not 2 <= len(points) <= MAX_POINTS:
            raise ValueError("a way needs 2 to %d points" % MAX_POINTS)
        body.append(1 if one_way else 0)
        body += varint(len(points))
        for lat, lon in points:
            if not (-90 <= lat <= 90 and -180 <= lon <= 180):
                raise ValueError("point out of range")
            ilat, ilon = round(lat * 1e6), round(lon * 1e6)
            body += varint(zigzag(ilat - plat)) + varint(zigzag(ilon - plon))
            plat, plon = ilat, ilon
    return gzip.compress(bytes(body), compresslevel=9, mtime=0)


def decode(data):
    """The reverse of [encode], for the tests and to check every file before it is published."""
    raw = gzip.decompress(data)
    if raw[:7] != MAGIC or raw[7] != VERSION:
        raise ValueError("not a tunnel map of version %d" % VERSION)
    country = raw[8:10].decode("ascii")
    pos = 10

    def read():
        nonlocal pos
        n = shift = 0
        while True:
            b = raw[pos]
            pos += 1
            n |= (b & 0x7F) << shift
            if not b & 0x80:
                return n
            shift += 7

    def unzig(n):
        return (n >> 1) if not n & 1 else -((n + 1) >> 1)

    ways = []
    plat = plon = 0
    for _ in range(read()):
        one_way = bool(raw[pos] & 1)
        pos += 1
        points = []
        for _ in range(read()):
            plat += unzig(read())
            plon += unzig(read())
            points.append((plat / 1e6, plon / 1e6))
        ways.append((one_way, points))
    if pos != len(raw):
        raise ValueError("trailing bytes")
    return country, ways

# endregion

# region Geometry


def distance_m(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * EARTH_M * math.asin(min(1.0, math.sqrt(h)))


def length_m(points):
    return sum(distance_m(points[i], points[i + 1]) for i in range(len(points) - 1))


def simplify(points, tolerance_m=SIMPLIFY_M):
    """Douglas-Peucker in a local flat projection (fine over a tunnel's few kilometres)."""
    if len(points) <= 2:
        return list(points)
    lat0 = math.radians(sum(p[0] for p in points) / len(points))
    xy = [(math.radians(p[1]) * math.cos(lat0) * EARTH_M, math.radians(p[0]) * EARTH_M) for p in points]
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        i, j = stack.pop()
        (x1, y1), (x2, y2) = xy[i], xy[j]
        dx, dy = x2 - x1, y2 - y1
        seg = math.hypot(dx, dy)
        worst, at = 0.0, -1
        for k in range(i + 1, j):
            x, y = xy[k]
            d = abs(dy * (x - x1) - dx * (y - y1)) / seg if seg > 0 else math.hypot(x - x1, y - y1)
            if d > worst:
                worst, at = d, k
        if worst > tolerance_m:
            keep[at] = True
            stack += [(i, at), (at, j)]
    return [p for p, k in zip(points, keep) if k]


def long_tunnels(ways, min_m=MIN_TUNNEL_M):
    """
    Keeps the ways whose tunnel (the tunnel ways joined end to end, sharing an end point) is at
    least [min_m] long. A tunnel is often split into several ways (at a lane change, a bridge
    inside it, a junction), so one short way can belong to a long tunnel.
    """
    parent = list(range(len(ways)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    def key(p):
        return round(p[0] * 1e6), round(p[1] * 1e6)

    at_end = {}
    for i, (_, pts) in enumerate(ways):
        for p in (pts[0], pts[-1]):
            j = at_end.setdefault(key(p), i)
            parent[find(i)] = find(j)
    total = {}
    for i, (_, pts) in enumerate(ways):
        r = find(i)
        total[r] = total.get(r, 0.0) + length_m(pts)
    return [w for i, w in enumerate(ways) if total[find(i)] >= min_m]

# endregion

# region OpenStreetMap


def one_way(tags):
    v = tags.get("oneway", "")
    if v in ("yes", "true", "1", "-1"):
        return True
    if v == "no":
        return False
    return tags.get("highway") in ("motorway", "motorway_link") or tags.get("junction") == "roundabout"


def from_overpass(elements):
    """The tunnel ways of an Overpass "out tags geom" answer, as (one_way, points)."""
    ways = []
    for e in elements:
        if e.get("type") != "way" or "geometry" not in e:
            continue
        tags = e.get("tags", {})
        pts = [(g["lat"], g["lon"]) for g in e["geometry"] if g]
        if len(pts) < 2:
            continue
        if tags.get("oneway") == "-1":
            pts.reverse()  # drawn against the traffic: stored in its direction
        ways.append((one_way(tags), pts))
    return ways


def overpass(query, attempts=4):
    data = urllib.parse.urlencode({"data": query}).encode()
    last = None
    for attempt in range(attempts):
        url = OVERPASS[attempt % len(OVERPASS)]
        try:
            req = urllib.request.Request(url, data=data, headers={"User-Agent": "aare-patches tunnel maps"})
            with urllib.request.urlopen(req, timeout=900) as r:
                return json.load(r)
        except Exception as e:  # network or server busy: wait and try the other server
            last = e
            time.sleep(30 * (attempt + 1))
    raise RuntimeError("Overpass failed: %s" % last)


def countries():
    q = '[out:json][timeout:300];area["ISO3166-1"~"^[A-Z]{2}$"]["admin_level"="2"];out tags;'
    codes = {e["tags"]["ISO3166-1"] for e in overpass(q)["elements"] if "tags" in e}
    return sorted(codes)


def country_ways(code):
    q = ('[out:json][timeout:900];area["ISO3166-1"="%s"]["admin_level"="2"]->.a;'
         'way(area.a)["highway"~"^(%s)$"]["tunnel"="yes"];out tags geom;') % (code, HIGHWAYS)
    return from_overpass(overpass(q)["elements"])

# endregion

# region Index

INDEX_HEADER = "AARETUN-INDEX 1"
_INDEX_LINE = re.compile(r"^([A-Z]{2}) ([0-9a-f]{64}) ([0-9]+) ([0-9]+)$")


def write_index(countries):
    """[countries]: code -> {"sha256", "bytes", "ways"}. Sorted, so the same maps give the same index."""
    lines = [INDEX_HEADER]
    for code in sorted(countries):
        c = countries[code]
        lines.append("%s %s %d %d" % (code, c["sha256"], c["bytes"], c["ways"]))
    return "\n".join(lines) + "\n"


def read_index(text):
    """The reverse of [write_index]; a malformed index raises ValueError."""
    lines = text.splitlines()
    if not lines or lines[0] != INDEX_HEADER:
        raise ValueError("not a tunnel maps index")
    out = {}
    for line in lines[1:]:
        m = _INDEX_LINE.match(line)
        if not m or m.group(1) in out:
            raise ValueError("bad index line")
        out[m.group(1)] = {"sha256": m.group(2), "bytes": int(m.group(3)), "ways": int(m.group(4))}
    return out

# endregion


def build(code, ways):
    """The map file of [code] from its tunnel ways: long tunnels only, simplified."""
    kept = [(ow, simplify(pts)) for ow, pts in long_tunnels(ways)]
    data = encode(code, kept)
    if decode(data)[0] != code.upper():
        raise RuntimeError("the file doesn't read back")
    return data, len(kept)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    ap.add_argument("--countries", default="all", help="comma-separated ISO codes, or all")
    ap.add_argument("--out", required=True, help="folder for <CC>.tun and index.txt")
    ap.add_argument("--previous", help="the index.txt published before: a country that fails now keeps its entry")
    args = ap.parse_args(argv)
    previous = {}
    if args.previous:
        try:
            with open(args.previous) as f:
                previous = read_index(f.read())
        except (OSError, ValueError) as e:
            print("previous index unreadable (%s): starting afresh" % e, file=sys.stderr)
    codes = countries() if args.countries == "all" else [c.strip().upper() for c in args.countries.split(",") if c.strip()]
    found = {}
    failed = []
    for code in codes:
        try:
            data, n = build(code, country_ways(code))
        except Exception as e:
            print("%s: %s" % (code, e), file=sys.stderr)
            failed.append(code)
            continue
        if n == 0:
            continue
        with open("%s/%s.tun" % (args.out, code), "wb") as f:
            f.write(data)
        found[code] = {"bytes": len(data), "ways": n, "sha256": hashlib.sha256(data).hexdigest()}
        print("%s: %d ways, %d bytes" % (code, n, len(data)))
        time.sleep(5)  # Overpass is shared: one country at a time
    if failed:
        print("failed: %s" % ",".join(failed), file=sys.stderr)
        # Overpass busy or a country too big this time: its published file stays as it was.
        for code in failed:
            if code in previous:
                found[code] = previous[code]
    with open("%s/index.txt" % args.out, "w") as f:
        f.write(write_index(found))
    return 1 if failed and not found else 0


if __name__ == "__main__":
    sys.exit(main())
