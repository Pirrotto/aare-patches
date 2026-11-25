# Tunnel maps

The shape of every road tunnel, one file per country, for the tunnel estimate to follow a tunnel
instead of the gyroscope. Built from OpenStreetMap by `tunnelmaps.py` on GitHub
(`.github/workflows/tunnel-maps.yml`), once a month, and published as assets of the `tunnel-maps`
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

- `<CC>.tun`: one country, by its ISO 3166-1 code.
- `index.json`: every country with its size, its number of ways and its SHA-256 (`countries`). It
  is published last, so it never names a file that isn't there yet.
- `<file>.sig`: the ECDSA P-256 / SHA-256 signature (DER) of each file, made with the key in the
  `TUNNEL_MAPS_KEY` secret. The phone accepts a file only if its signature matches the public key
  pinned in the code, and only if the country's SHA-256 matches the signed index.

`<CC>.tun` is a gzip (mtime 0, so the same tunnels give the same bytes) of:

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
