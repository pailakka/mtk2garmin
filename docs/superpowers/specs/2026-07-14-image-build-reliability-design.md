# Image build reliability design

> Superseded on 2026-07-27 by
> `2026-07-27-single-production-pipeline-design.md`. Image construction is now
> a separate digest-locked release operation and is no longer a conversion
> stage.

## Context

The scheduled jobs on 2026-07-11 and 2026-07-14 failed before map processing.
The shared `mtk2garmin-ubuntugis-base` image inherited an obsolete Apache Arrow
APT source from `ghcr.io/osgeo/gdal:ubuntu-full-3.10.0`. Its unavailable signing
key caused `apt update` to exit with status 100.

Image rebuilding also runs by default at the start of every scheduled data job.
That couples map production to mutable package repositories and unrelated image
release work.

## Approved behavior

- Upgrade the shared mtk2garmin and MML OGC client bases to
  `ghcr.io/osgeo/gdal:ubuntu-full-3.13.1` and the rs-ogr2osm base to
  `ghcr.io/osgeo/gdal:ubuntu-full-3.12.4`. The Rust GDAL crate currently
  supports pre-generated bindings through GDAL 3.12; compiling against 3.13
  fails on upstream API changes.
- Do not add an Apache Arrow key workaround. The updated GDAL base no longer
  configures that repository, and its normal Ubuntu APT update succeeds.
- Keep the image-build function available through `RUN_IMAGE_BUILD=1`.
- Change the scheduled/default path to `RUN_IMAGE_BUILD=0`, so routine map jobs
  consume previously published images.
- Keep the data pipeline behavior unchanged after image selection.
- Fail early with a clear message when a required image is unavailable rather
  than starting the long conversion and failing later.

## Implementation

The shared mtk2garmin, MML OGC client, and rs-ogr2osm Dockerfiles will use
explicit, compatible GDAL releases. A floating `latest` tag will not be
introduced.
Affected APT layers will use noninteractive `apt-get`, avoid recommended packages
where practical, and remove downloaded package indexes in the same layer.

`convert_docker.sh` will default `RUN_IMAGE_BUILD` to zero. Its existing explicit
build path remains the release mechanism. Before input updates or conversion, a
no-build run will verify that the images needed by the selected pipeline can be
resolved locally or pulled through the existing Compose and registry setup.

This change does not introduce an image manifest, new registry, automated image
release schedule, or full pipeline resume mechanism.

## Error handling

- Upstream image or package incompatibilities fail during the explicit image
  build with the failing Dockerfile and command visible.
- Scheduled runs fail in preflight if a required published image is missing.
- Preflight must not delete images, volumes, build output, or published data.

## Validation

1. Build the updated shared mtk2garmin base image.
2. Confirm GDAL reports version 3.13.1 and APT metadata refresh succeeds.
3. Build rs-ogr2osm against GDAL 3.12.4 and run its help smoke test.
4. Build the affected Compose images and run lightweight command/version checks.
5. Exercise the scheduled no-build path only through preflight; do not start the
   multi-hour national map conversion.
6. Run shell syntax checks and review the final diff for unrelated changes.

## Success criteria

The July 11/14 APT-key failure is no longer reproducible, explicit image builds
work with maintained versioned bases, and scheduled map jobs do not rebuild
images unless `RUN_IMAGE_BUILD=1` is set.
