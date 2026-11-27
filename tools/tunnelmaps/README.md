# Tunnel maps

The shape of every road tunnel, one file per country, for the tunnel estimate to follow a tunnel
instead of the gyroscope. Built from OpenStreetMap by `tunnelmaps.py` on GitHub
(`.github/workflows/tunnel-maps.yml`), every day for the countries that are due, and published as assets of the `tunnel-maps`
release. That release is a pre-release and never "latest", so Morphe and the patch releases
ignore it. The phone never builds anything: it only downloads a country's file, when the user
asks, and checks its signature.

Data © OpenStreetMap contributors, [ODbL 1.0](https://opendatacommons.org/licenses/odbl/).

## What is kept

- Ways tagged `tunnel=yes` on car roads, from motorways down to residential streets and their
  links. Service roads, paths and railways are left out.
- Only tunnels at least 200 m long, counting the tunnel ways that join end to end. A shorter tunnel
  is followed well enough by the gyroscope.
- The points simplified to within 2 m, and one-way tunnels stored in their direction of travel.
- No names and no other tags: only the shapes and whether each one is one way.

## Files

- `<Country>.aaretun`, named in English (`Switzerland.aaretun`): one country, the map with its
  signature, so it can also be loaded by hand («Tunnel maps» → "From file…"). Layout: `AARETUNS`,
  version (1 byte, 1), the signature's length (2 bytes, big-endian), the signature (ECDSA P-256 /
  SHA-256, DER, of the map, made with the key in the `TUNNEL_MAPS_KEY` secret), then the map below.
- `index.txt` and its signature `index.txt.sig`: the line `AARETUN-INDEX 2`, then one line per
  country, sorted: `<CC> <map's sha256> <file's bytes> <ways> <file name> <YYYYMMDD built>`. The
  SHA-256 is the map's: a signature differs at every signing, the map only when the tunnels changed.
  The date makes the runs resumable: every day the countries due (missing, or built more than 30
  days ago) are built, the owner's country and its neighbours first, until GitHub's time limit
  nears; the rest is done the next day, so each country is rebuilt about once a month. Plain text,
  so the phone reads it without a JSON parser.
  Countries are built and published one at a time (Switzerland and its neighbours first): each
  country's file goes up first, then the index naming it, so a phone never sees an index naming a
  file that isn't there yet, and a run stopped by GitHub's time limit loses nothing already done.
  A country the shared Overpass servers can't answer whole (Germany and France got a 502 every
  time) is asked for in tiles of its bounding box, each tile split in four again when it fails, two
  more times at most (64 tiles), with the ways merged by their id. It gets 45 minutes; past that it
  fails and is tried again at the next run.
- `countries.txt`: the countries' English names (`CC Name` per line) of the last run that could
  list them, kept so a run still starts when the shared servers can't answer that big request.

The phone accepts a map only if its signature matches the public key pinned in the code, and, for a
download, only if its size and SHA-256 match the signed index.

The map inside is a gzip (mtime 0, so the same tunnels give the same bytes) of:

| Field | Size |
|---|---|
| `AARETUN` | 7 bytes |
| format version (1) | 1 byte |
| country, ASCII | 2 bytes |
| way count | varint |
| per way: flags (bit 0: one way) | 1 byte |
| per way: point count (2–65535) | varint |
| per point: latitude, longitude | zigzag varint each, in millionths of a degree, as the difference from the previous point of the file |

## Tests

`python3 -m unittest discover -s tools/tunnelmaps` runs them on every pull request that touches
the builder, together with a dry run for Switzerland that publishes nothing.
