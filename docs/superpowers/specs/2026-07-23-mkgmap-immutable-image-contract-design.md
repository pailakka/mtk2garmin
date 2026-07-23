# Mkgmap immutable image contract design

## Context

The scheduled runs on 2026-07-18 and 2026-07-21 pulled
`teemupel/mkgmap` while Compose bind-mounted the checkout's
`run_mkgmap.sh` over the script packaged in that image. The published image
contained `perus.typ` and `perus_amoled.typ`, but the newer host script expected
`peruskar.typ` and `perusamo.typ`. Mkgmap spent nearly an hour or more processing
the national input before the missing TYP files caused the run to fail.

Scheduled map production already defaults to `RUN_IMAGE_BUILD=0` to avoid
coupling data updates to mutable package repositories. That separation remains
the correct model, but a pulled image must not be combined with executable code
from a potentially different checkout revision.

## Approved behavior

- Keep scheduled runs on published images with `RUN_IMAGE_BUILD=0`.
- Make the mkgmap image the sole owner of its executable script, compiled TYP
  files, argument templates, validation script, mkgmap JAR, and splitter JAR.
- Do not bind-mount `run_mkgmap.sh` from the host into scheduled containers.
- Preserve the short compiled TYP basenames `peruskar.typ` and `perusamo.typ`.
- Validate the complete mkgmap runtime contract before national data processing.
- Keep the existing explicit full image-release path through
  `RUN_IMAGE_BUILD=1`.
- Build and publish a corrected `teemupel/mkgmap` image once as part of this
  repair.

## Runtime contract

`run_mkgmap.sh` will support a `--preflight` mode. It will verify that these
image-owned inputs exist and are non-empty:

- `mkgmap.jar`
- `splitter.jar`
- `peruskar.typ`
- `perusamo.typ`
- the four mkgmap argument templates
- `check_img_subfiles.py`

Preflight performs no national-map conversion, writes no output artifacts, and
does not require the converted PBF. A normal invocation will run the same
preflight before deleting or creating build output, so direct use of the image
also fails safely.

After pulling required images, `convert_docker.sh` will invoke the mkgmap
preflight when the selected pipeline enables Garmin output. A missing or
inconsistent image therefore stops before input refresh, conversion, splitting,
or publication.

## Image release flow

The corrected image will be built from the current `mkgmap-converter`
Dockerfile. The build itself compiles the two TYP files and runs the runtime
preflight before completing. The image will then be pushed through the existing
Compose image configuration.

The published image will be pulled again and tested through Compose. This
registry-backed check proves that the artifact available to the next cron run,
not merely a local build layer, satisfies the contract.

Routine cron runs will continue pulling published images. Source changes to
`run_mkgmap.sh`, TYP sources, argument templates, or validation tooling take
effect only after an explicit image release, preventing partial source/image
updates.

## Error handling

- Docker image construction fails if either TYP compilation fails or the
  resulting runtime contract is incomplete.
- Scheduled preflight fails with the exact missing image-owned path before
  expensive data processing.
- A normal mkgmap invocation repeats preflight before clearing output
  directories.
- Pull or registry failures remain visible through the existing required-image
  checks.

## Validation

1. Run shell syntax checks for the changed scripts.
2. Render the Compose configuration and confirm `run_mkgmap.sh` is no longer a
   host volume.
3. Build the mkgmap image from the current Dockerfile.
4. Run `run_mkgmap.sh --preflight` in the local image.
5. Inspect the container to confirm `peruskar.typ` and `perusamo.typ` exist and
   the obsolete names are not required.
6. Push the image using the existing Compose image name.
7. Remove or bypass the local image, pull the published image, and run the same
   preflight through Compose.
8. Exercise the pipeline's image/preflight path without starting the multi-hour
   national conversion.
9. Review the final diff and preserve unrelated worktree changes.

## Success criteria

- The pulled mkgmap image and its runtime script cannot come from different
  source revisions.
- The published image contains and uses `peruskar.typ` and `perusamo.typ`.
- Missing image-owned runtime inputs stop the pipeline before national data
  processing.
- Scheduled runs remain independent from package repositories and full image
  rebuilds.
- The explicit image release path remains available for future source changes.
