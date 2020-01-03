#!/bin/bash
set -euxo pipefail

cd mapsforge_peruskartta
python3 tidy_tag_mapping.py
mkdir -p /mapstyles/mapsforge_peruskartta
cp Peruskartta.xml /mapstyles/mapsforge_peruskartta
cp Tiekartta.xml /mapstyles/mapsforge_peruskartta
cp mml_tag-mapping_tidy.xml /mapstyles/mapsforge_peruskartta
cp -r mml /mapstyles/mapsforge_peruskartta

svgo -f /mapstyles/mapsforge_peruskartta/mml

7za a peruskartta.zip Peruskartta.xml mml
7za a tiekartta.zip Tiekartta.xml mml

cp peruskartta.zip /mapstyles/peruskartta.zip
cp tiekartta.zip /mapstyles/tiekartta.zip

cd ..

cp -r mtk2garmin_style /mapstyles
cp -r mtk2garmin_style_noparcel /mapstyles
