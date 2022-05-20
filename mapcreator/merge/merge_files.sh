#!/bin/bash
set -euxo pipefail

python3 merge_suomi.py
chmod +x merge_all.sh
time ./merge_all.sh || true
time ./process_osm.sh
