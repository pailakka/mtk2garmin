#!/bin/bash
set -e

./osmconvert /additional-data/finland-latest.osm.pbf -o=/convertedpbf/finland-latest.o5m
./osmfilter /convertedpbf/finland-latest.o5m --keep="highway=track =path =footway =cycleway =trail amenity=shelter =toilets tourism=fireplace =lean_to =wilderness man_made=tower and tower:type=observation" --drop-author --drop-version --verbose --out-o5m > /inputdata/finland-filtered.o5m
./osmconvert /convertedpbf/finland-filtered.o5m --out-o5m | ./osmconvert - /convertedpbf/all.osm.pbf -o=/convertedpbf/all_osm.osm.pbf