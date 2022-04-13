#!/bin/bash
set -euxo pipefail

time ./osmconvert syvyyspisteet.osm.pbf --out-o5m | ./osmconvert - syvyyskayrat.osm.pbf --out-o5m | ./osmconvert - krk.osm.pbf --out-o5m | ./osmconvert - mtkmaasto.osm.pbf --out-o5m | osmconvert - mtkkorkeus.osm.pbff -o=all.pbf
time ./process_osm.sh
