#!/bin/bash
set -euxo pipefail

time osmium sort -s multipass /convertedpbf/all_direct.osm.pbf -o /convertedpbf/all_direct_sorted.osm.pbf  --overwrite
time osmium tags-filter  --overwrite -o /convertedpbf/finland-filtered.osm.pbf /additional-data/finland-latest.osm.pbf w/highway=track,path,footway,cycleway,trail n/amenity=shelter,toilets n/tourism=fireplace,lean_to,wilderness n/man_made=tower n/tower:type=observation
time osmium merge /convertedpbf/finland-filtered.osm.pbf /convertedpbf/all_direct_sorted.osm.pbf -o /convertedpbf/all_osm.osm.pbf --overwrite
osmium fileinfo /additional-data/finland-latest.osm.pbf
osmium fileinfo /convertedpbf/finland-filtered.osm.pbf
osmium fileinfo /convertedpbf/all_direct.osm.pbf
osmium fileinfo /convertedpbf/all_osm.osm.pbf
