#!/bin/bash
set -e
python3 merge_suomi.py

parallel --eta --progress -a merge_all1.sh
parallel --eta --progress -a merge_all2.sh
parallel --eta --progress -a merge_all3.sh
parallel --eta --progress -a merge_all4.sh

./process_osm.sh