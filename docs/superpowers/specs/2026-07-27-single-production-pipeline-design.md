# Single Production Pipeline Design

## Status

Approved for implementation on 2026-07-27.

## Goal

Make the current Rust-based map conversion flow the only production pipeline.
Remove the old Java/GML implementation and every active v1/v2 selector,
namespace, comparison, and publication path.

The production contract is:

- one build root: `/opt/mtk2garmin-build`;
- one dated release namespace: `<YYYY-MM-DD>/`;
- one public homepage: `index.html`;
- rs-ogr2osm for source conversion and merge;
- mkgmap for Garmin outputs;
- rs-mapsforge-writer for Mapsforge output;
- no supported path back to the Java/GML pipeline.

## Orchestration

`mapcreator/convert_docker.sh` always runs the production flow:

1. preflight locked runtime images and required host state;
2. refresh MML GeoPackages and additional public datasets when enabled;
3. run rs-ogr2osm and merge a validated `all_osm.osm.pbf`;
4. build and validate Garmin IMG outputs;
5. build Mapsforge, macOS, and NSIS outputs;
6. stage and validate the dated release;
7. publish the homepage only after every required download is live;
8. clean intermediates only after successful publication.

The supported recovery controls are `RUN_INPUT_UPDATE`, `RUN_CONVERSION`,
`RUN_MKGMAP`, `RUN_MAPSFORGE`, `RUN_OSX`, `RUN_NSIS`,
`RUN_AMOLED_NSIS`, `RUN_PUBLISH`, and `RUN_CLEANUP`. Pipeline selectors,
legacy-stage selectors, and image building inside the conversion command are
removed.

The existing `mml-client` GML/shapefile loads are removed because the production
Rust configurations consume only the GeoPackages produced by `mml-ogr-client`.
The additional-data image becomes a stable fetcher: downloads happen during
input refresh, are validated in temporary paths, and replace the named-volume
dataset atomically.

## Image Release Contract

Image construction is a separate operator action. `release_images.sh` builds
selected owned images or all owned images, pushes versioned candidates,
resolves full registry digests, tests the exact digest references, and only
then replaces `images.lock.env`.

Compose services and rs-ogr2osm helper scripts consume the lock file. Scheduled
conversions never build images or rely on mutable `latest` tags. A failed image
release leaves the previous lock unchanged.

## Publication Contract

The newer `site/index2.html` content becomes the sole `site/index.html`
template. Dual rendering, the v2 banner, `site2.html`, `index_v2.html`,
`index_old.html`, and variant/prefix environment controls are removed.

Each release is first assembled under a local staging directory and uploaded
to the canonical S3 archive prefix without changing the homepage. The
publisher validates the required artifact manifest, sizes, metadata, Garmin
compatibility, and every public download URL. It then exposes the local dated
directory, uploads the new root homepage, invalidates `/` and `/index.html`,
and polls until the page and all links are correct.

Failures before the homepage swap leave the previous release live. Failures
retain diagnostics. `robots.txt` and `sitemap.xml` remain optional, non-fatal
publication objects.

## One-Time Cutover

The verified 2026-07-26 release is newer than the 2026-07-07 main release.
Promote its artifacts from `v2/2026-07-26` to canonical `2026-07-26`, regenerate
the homepage with canonical links, validate every download, and then replace
the root homepage.

Only after the canonical release is live:

- remove the local `v2/` and `2026-07-07/` releases;
- remove the archive bucket's `v2/` and `new-*` namespaces;
- remove `index_v2.html`, `index_old.html`, and `index2.html`;
- require those obsolete public paths to return 404.

Future dated S3 releases remain archived. Local publication retains the current
release only. Rollback during validation means restoring the previous
unversioned homepage or image lock, not restoring the Java/GML pipeline.

## Verification

Tests cover shell syntax, Compose resolution, runtime data refresh, image-lock
atomicity, notification behavior, cleanup retention, canonical site generation,
and failure-before-index-swap behavior.

A production release is accepted only when:

- `osmium check-refs` passes for the merged PBF;
- all four Garmin IMG files pass the configured tile, subfile, and total-size
  limits;
- every IMG and installer uses `perus.typ` with the aligned FID;
- the Mapsforge output passes the existing reader/render smoke;
- required IMG, EXE, ZIP, MAP, style, package, metadata, and Locus objects are
  present and downloadable;
- the root homepage contains only canonical dated links and every link returns
  HTTP 200;
- the scheduled wrapper records the run and the failure-notification path is
  proven with a synthetic test.
