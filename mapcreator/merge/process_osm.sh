#!/bin/bash
set -e

./osmconvert /additional-data/finland-latest.osm.pbf -o=/inputdata/finland-latest.o5m
./osmfilter /inputdata/finland-latest.o5m --keep="highway=track =path =footway =cycleway =trail amenity=shelter =toilets tourism=fireplace =lean_to =wilderness man_made=tower and tower:type=observation" --drop-author --drop-version --verbose --out-o5m > /inputdata/finland-filtered.o5m
./osmconvert /inputdata/finland-filtered.o5m --out-o5m | ./osmconvert - /suomi/all.osm.pbf -o=/inputdata/all_osm.osm.pbf