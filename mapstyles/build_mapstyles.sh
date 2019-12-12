#!/bin/bash
set -euxo pipefail

cd mapsforge_peruskartta
python3 tidy_tag_mapping.py
mkdir -p /mapstyles/mapsforge_peruskartta
cp Peruskartta.xml /mapstyles/mapsforge_peruskartta
cp Tiekartta.xml /mapstyles/mapsforge_peruskartta
cp mml_tag-mapping_tidy.xml /mapstyles/mapsforge_peruskartta
cp -r mml /mapstyles/mapsforge_peruskartta

cd ..

cp -r mtk2garmin_style /mapstyles
cp -r mtk2garmin_style_noparcel /mapstyles
