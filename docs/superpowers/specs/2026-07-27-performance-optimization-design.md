# MTK2Garmin production performance design

Date: 2026-07-27

## Goal

Reduce the normal production run from roughly five hours to less than three
hours while preserving the existing Garmin, Mapsforge, installer, and website
compatibility contracts. A failed housekeeping task must never turn an already
published release into a failed conversion that is rebuilt from scratch.

## Baseline

The latest complete production run took 4 h 51 min:

- source refresh: about 36 min
- rs-ogr2osm conversion and merge: about 93 min
- mkgmap: about 53 min
- Mapsforge writer: about 75 min
- NSIS installers: about 23 min
- publication and verification: about 10 min

The release was published successfully, but cleanup then failed on root-owned
files. The wrapper reported the whole run as failed and a targeted retry spent
another 2 h 50 min. This is the first reliability and performance issue to
remove.

## Production flow

### Input snapshot

A separate 01:00 job refreshes all source data into an incoming directory. It
validates the required files, records their sizes and SHA-256 hashes, writes an
immutable snapshot manifest, and atomically promotes the snapshot as `current`.

The 03:00 production job accepts only a complete snapshot no more than six
hours old. It never falls back to an older snapshot. A snapshot failure is
reported independently and does not destroy the last complete snapshot.

### Resumable stages

Every expensive stage records:

- stage name and implementation version
- canonical input fingerprint
- image digest and relevant configuration values
- start, end, duration, and peak RSS when available
- output paths, sizes, and SHA-256 hashes
- validator result

A stage is reusable only when its fingerprint matches and every recorded output
still exists with the recorded size and hash. Manifests are written atomically.
This permits a publication or packaging retry without rerunning conversion.

### Parallel critical path

The conversion starts two rs-ogr2osm lanes:

1. `maasto`
2. elevation, property, optional bathymetric contours, and points

After the merged OSM file is validated, the Garmin and Mapsforge branches run
in parallel. The Garmin branch runs mkgmap and then runs macOS conversion and
the two NSIS installers concurrently. An ordinary branch failure does not
cancel a healthy sibling; its completed output remains reusable.

Initial overlap limits are:

- mkgmap: 8 jobs and a 48 GiB memory ceiling
- Mapsforge writer: 8 tile workers and a 32 GiB memory ceiling
- aggregate memory ceiling: 80 GiB

The limits are configuration values so a paired production-sized benchmark can
change them without changing the orchestration.

### Mapsforge hot path

The first writer optimization packs each fixed 38-byte staged-way summary into
one buffer and writes it through a buffered index stream. The currently exposed
node-index type and cache options must control the actual implementation.

Production-sized benchmarks compare disk-index cache sizes of 4k, 16k, and 65k
blocks with the memory index. The selected mode must improve pass 4 by at least
15 percent, stay below 32 GiB RSS, and pass strict map and metadata parity
checks. Relation-member ID tracking is limited to the IDs that are actually
queried.

### Publication

One artifact manifest is the publication contract. Immutable artifacts are
hard-linked into local staging where possible, and uploaded objects carry their
SHA-256 metadata. Upload retries reuse verified objects. The homepage is
uploaded last, after every dated artifact URL passes the public verifier.

Publication success and housekeeping success are separate states. Cleanup,
retention, or Compose teardown failures are reported as housekeeping failures
without relabelling or rebuilding a verified release.

## Compatibility invariants

- There is one production pipeline; no `v1`/`v2` split is reintroduced.
- Garmin IMG and installer content keeps the embedded `perus.typ` name and
  aligned FIDs.
- Existing mkgmap options and immutable image digests remain explicit.
- Mapsforge output must pass the block-size gate and strict semantic parity
  checks before production selection.
- Public dated URLs and the homepage-last cutover contract remain intact.

## Acceptance targets

- input snapshot is ready before the production job and is at most six hours old
- conversion phase is at most 2 h 45 min
- post-conversion branches and publication are at most 2 h 30 min
- a publish-only retry performs no conversion
- cleanup failure leaves the verified release successful and sends a distinct
  alert
- stage summaries make the critical path and cache reuse visible
- failure notification test reaches the configured SNS topic once IAM permits
  `sns:Publish`

