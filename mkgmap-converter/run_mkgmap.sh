#!/bin/bash
set -euxo pipefail

rm -rf /splitted/*
rm -rf /output/mtkgarmin
rm -rf /output/mtkgarmin_noparcel
rm -rf /output/mtkgarmin_amoled
rm -rf /output/mtkgarmin_amoled_noparcel
rm -rf /output/mtkgarmin_mh
time java -jar -Xmx30G splitter.jar --output-dir=/splitted --max-areas=4096 --max-nodes=3200000 --resolution=14 /convertedpbf/all_osm.osm.pbf
(cat mkgmap_mtk2garmin.args;echo;cat /splitted/template.args) > /splitted/mkgmap_mtk2garmin.args
(cat mkgmap_mtk2garmin_noparcel.args;echo;cat /splitted/template.args) > /splitted/mkgmap_mtk2garmin_noparcel.args
(cat mkgmap_mtk2garmin_amoled.args;echo;cat /splitted/template.args) > /splitted/mkgmap_mtk2garmin_amoled.args
(cat mkgmap_mtk2garmin_amoled_noparcel.args;echo;cat /splitted/template.args) > /splitted/mkgmap_mtk2garmin_amoled_noparcel.args
time java -jar -Xmx30G mkgmap.jar -c /splitted/mkgmap_mtk2garmin.args perus.typ
time java -jar -Xmx30G mkgmap.jar -c /splitted/mkgmap_mtk2garmin_noparcel.args perus.typ
time java -jar -Xmx30G mkgmap.jar -c /splitted/mkgmap_mtk2garmin_amoled.args perus_amoled.typ
time java -jar -Xmx30G mkgmap.jar -c /splitted/mkgmap_mtk2garmin_amoled_noparcel.args perus_amoled.typ

cp perus.typ /output/mtkgarmin/perus.typ
cp perus.typ /output/mtkgarmin_noparcel/perus.typ
cp perus_amoled.typ /output/mtkgarmin_amoled/perus.typ
cp perus_amoled.typ /output/mtkgarmin_amoled_noparcel/perus.typ
