#!/usr/bin/env bash
set -e

git clone --single-branch https://github.com/pailakka/mapsforge.git
cd mapsforge
docker build -t mapsforge-writer --no-cache -f Dockerfile .
docker run -e JAVACMD_OPTIONS=-Xmx25G \
           -v /opt/mtk2garmin_build/mtk2garmin:/mapdata \
           mapsforge-writer \
           /app/bin/osmosis \
           --rbf file=/mapdata/all_osm.osm.pbf workers=2 \
           --mapfile-writer file=/mapdata/all.map bbox=59.4507573,19.0714057,70.1120744,31.6133108 \
           simplification-max-zoom=11 simplification-factor=8 threads=2 \
           zoom-interval-conf=6,0,7,10,8,11,12,12,13,14,14,21 \
           label-position=true polylabel=true \
           tag-conf-file=/mapdata/mapsforge_peruskartta/mml_tag-mapping_tidy.xml type=hd comment="(c) NLS, Metsahallitus, Liikennevirasto, OpenStreetMap contributors 2017"
cd ..