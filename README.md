Converts Finnish national topographic GeoPackages to OSM PBF and produces
Mapsforge and Garmin map packages published at https://kartat.hylly.org/.

The production entrypoint is:

```bash
cd mapcreator
./convert_docker.sh
```

There is one production pipeline and one build root,
`/opt/mtk2garmin-build`. Releases use canonical `YYYY-MM-DD/` paths and the
public homepage is always `index.html`.

Tool images are released separately and pinned by registry digest:

```bash
cd mapcreator
./release_images.sh additional-data site
```

Scheduled conversions use `run_scheduled_conversion.sh`, which writes a dated
log under `/home/teemu` and sends the configured SNS notification when a run
fails. Use `run_scheduled_conversion.sh --test-notification` to test delivery
without starting a conversion.

Source refresh is a separate scheduled step:

```bash
cd mapcreator
./run_scheduled_snapshot.sh
```

It downloads into `/opt/mtk2garmin-inputs/.incoming-*`, hashes and validates
the required inputs, and atomically promotes an immutable production choice via
`/opt/mtk2garmin-inputs/current`. The conversion rejects snapshots older than
six hours and does not fall back to a previous snapshot. The intended schedule
is refresh at 01:00 and conversion at 03:00 on build days.

Successful conversions retain only publishable `output/dist` files. Failed
runs preserve intermediate files and logs. Set `RUN_CLEANUP=0` for a successful
diagnostic run that must retain intermediates.

Useful recovery controls are `RUN_INPUT_UPDATE`, `RUN_CONVERSION`,
`RUN_MKGMAP`, `RUN_MAPSFORGE`, `RUN_OSX`, `RUN_NSIS`, `RUN_AMOLED_NSIS`,
`RUN_PUBLISH`, and `RUN_CLEANUP`. Successful expensive stages are fingerprinted
under `/opt/mtk2garmin-build/state/stages` and reused automatically when every
recorded output still matches. Set `MTK2GARMIN_RESUME=0` for a full rerun or
list comma-separated stage names in `MTK2GARMIN_FORCE_STAGES`.

After conversion, Garmin and Mapsforge run concurrently. The Garmin branch
runs macOS packaging and the standard and no-parcel NSIS installers
concurrently after mkgmap. Initial overlap limits are 48 GiB and eight mkgmap
jobs for Garmin, and 32 GiB and eight tile workers for Mapsforge.

Every published release contains `artifact-manifest.json`. Archive upload
retries compare its SHA-256 metadata and reuse matching objects, while the
homepage remains the final cutover object. Publication and housekeeping status
are independent: a cleanup failure sends a distinct notification but does not
invalidate or rebuild a verified release.
