#!/bin/bash
set -euxo pipefail

time ./osmconvert /additional-data/finland-latest.osm.pbf -o=/convertedpbf/finland-latest.o5m
time ./osmfilter /convertedpbf/finland-latest.o5m --keep="highway=track =path =footway =cycleway =trail amenity=shelter =toilets tourism=fireplace =lean_to =wilderness man_made=tower and tower:type=observation" --drop-author --drop-version --verbose --out-o5m > /convertedpbf/finland-filtered.o5m
time ./osmconvert /convertedpbf/finland-filtered.o5m --out-o5m | ./osmconvert - /convertedpbf/all_direct.osm.pbf -o=/convertedpbf/all_osm.osm.pbf
time ./osmconvert /additional-data/finland-latest.osm.pbf --out-statistics
time ./osmconvert /convertedpbf/finland-filtered.o5m --out-statistics
time ./osmconvert /convertedpbf/all.osm.pbf --out-statistics
time ./osmconvert /convertedpbf/all_osm.osm.pbf --out-statistics