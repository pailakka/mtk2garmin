#! /bin/bash

DATA_FOLDER=./geodata/

osmconvert ${DATA_FOLDER}finland-latest.osm.pbf -o=${DATA_FOLDER}finland-latest.o5m
osmfilter ${DATA_FOLDER}finland-latest.o5m --keep="highway=track =path =footway amenity=shelter =toilets tourism=fireplace =lean_to =wilderness man_made=tower and tower:type=observation" --drop-author --drop-version --verbose --out-o5m >${DATA_FOLDER}finland-filtered.o5m
osmconvert ${DATA_FOLDER}finland-filtered.o5m --out-o5m | osmconvert - suomi/all.osm.pbf -o=suomi/all_osm.osm.pbf
