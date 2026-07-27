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

Successful conversions retain only publishable `output/dist` files. Failed
runs preserve intermediate files and logs. Set `RUN_CLEANUP=0` for a successful
diagnostic run that must retain intermediates.

Useful recovery controls are `RUN_INPUT_UPDATE`, `RUN_CONVERSION`,
`RUN_MKGMAP`, `RUN_MAPSFORGE`, `RUN_OSX`, `RUN_NSIS`, `RUN_AMOLED_NSIS`,
`RUN_PUBLISH`, and `RUN_CLEANUP`.
