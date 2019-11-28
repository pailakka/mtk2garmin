#!/bin/bash
set -euxo pipefail
python3 merge_suomi.py

chmod +x merge_all5.sh

time parallel --eta --progress -a merge_all1.sh
time parallel --eta --progress -a merge_all2.sh
time parallel --eta --progress -a merge_all3.sh
time parallel --eta --progress -a merge_all4.sh
time ./merge_all5.sh

time ./process_osm.sh