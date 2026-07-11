# Output Retention Cleanup Design

## Goal

After a successful mtk2garmin pipeline run, retain only publishable artifacts
under `<build-root>/output/dist`. Remove conversion inputs, sorted and unsorted
intermediates, split files, generated working directories, logs, and obsolete
artifacts that otherwise accumulate in the build root.

Failed or interrupted runs retain their working files for diagnosis. Operators
can also set `RUN_CLEANUP=0` to retain all files from a successful run.

## Scope

The cleanup applies to every build root selected by `PIPELINE`: the v1 root,
the v2 root, or both. It is constrained to those configured build roots and
must not follow cleanup paths outside them.

The retained set after cleanup is exactly:

- `<build-root>/output/dist/**`
- the empty working directories `<build-root>/convertedpbf` and
  `<build-root>/splitted`, ready for later runs

Everything else under `<build-root>/output` is disposable after successful
publication, including logs and Garmin, Mapsforge, OSX, installer, and site
generation working artifacts.

## Lifecycle

`convert_docker.sh` owns the retention policy. It records which build roots
actually ran, executes the selected conversion and downstream stages, performs
an optional v1/v2 comparison, and then cleans each completed build root. Docker
Compose teardown remains a separate final operation.

Because the script runs with `set -e`, cleanup is reached only when all enabled
pipeline stages and comparisons have succeeded. No exit trap performs artifact
cleanup; therefore a failing or interrupted run preserves diagnostic state.

## Cleanup Operation

For each completed build root, the script will:

1. Validate that the path is a non-empty build-root path and is not `/`.
2. Remove and recreate `convertedpbf` and `splitted`.
3. Remove every direct child of `output` except `dist`.
4. Leave all content within `output/dist` unchanged.

The implementation will use explicit root-relative paths and guarded `find`
operations. It will not use age-based deletion or scan unrelated directories.

## Configuration and Observability

`RUN_CLEANUP=1` remains the default and means both build-artifact cleanup and
Docker Compose teardown are enabled. `RUN_CLEANUP=0` retains successful-run
artifacts and skips teardown, preserving the current diagnostic escape hatch.

The script prints each build root before cleaning it and states that
`output/dist` is retained. Project documentation will describe the resulting
retention contract.

## Verification

A shell test creates a temporary fixture build root containing representative
files in `convertedpbf`, `splitted`, `output/dist`, output logs, and generated
output subdirectories. It invokes the cleanup logic and verifies:

- every file below `output/dist` remains byte-for-byte present;
- all other fixture artifacts are absent;
- `convertedpbf` and `splitted` exist and are empty;
- an unsafe root such as `/` is rejected;
- cleanup is not called by the failure path.

The script will also receive a syntax check with `bash -n`.
