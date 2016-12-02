#!/bin/bash
set -e

apt-get update
apt-get -y upgrade
apt-get -y install awscli default-jre

wget -Omkgmap.zip http://www.mkgmap.org.uk/download/mkgmap-r3706.zip
wget -Osplitter.zip http://www.mkgmap.org.uk/download/splitter-r476.zip

unzip -o mkgmap.zip
unzip -o splitter.zip

aws s3 cp s3://kartat-build/all_osm.osm.pbf all_osm.osm.pbf
aws s3 cp s3://kartat-build/mml_tag-mapping_tidy.xml mml_tag-mapping_tidy.xml
aws s3 cp s3://kartat-build/mtk2garmin_style mtk2garmin_style --recursive
aws s3 cp s3://kartat-build/mkgmap_mtk2garmin.args mkgmap_mtk2garmin.args
aws s3 cp s3://kartat-build/peruskartta.typ peruskartta.typ

mkdir splitted

java -jar -Xmx1G splitter-r476/splitter.jar --output-dir=splitted all_osm.osm.pbf > splitter.log
(cat mkgmap_mtk2garmin.args;echo;cat splitted/template.args) > splitted/mkgmap_mtk2garmin.args
java -jar -Xmx1G mkgmap-r3706/mkgmap.jar -c splitted/mkgmap_mtk2garmin.args peruskartta.typ


aws s3 cp mtkgarmin/gmapsupp.img  s3://kartat-build/gmapsupp.img

mkdir osmosis
cd osmosis
wget http://bretth.dev.openstreetmap.org/osmosis-build/osmosis-latest.tgz -Oosmosis-latest.tgz
tar -xvzf osmosis-latest.tgz
export JAVACMD_OPTIONS="-Xmx1G"
cd ../
mkdir plugins
cd plugins
wget http://ci.mapsforge.org/job/0.6.1/lastSuccessfulBuild/artifact/mapsforge-map-writer/build/libs/mapsforge-map-writer-0.6.1-jar-with-dependencies.jar
cd ../



echo "Running osmosis writer!"
echo $JAVACMD_OPTIONS
osmosis/bin/osmosis --rbf all_osm.osm.pbf --mapfile-writer file=all.map bbox=59.4507573,19.0714057,70.1120744,31.6133108 tag-conf-file=mml_tag-mapping_tidy.xml type=hd comment="(c) NLS, Metsahallitus, Liikennevirasto, OpenStreetMap contributors 2016"
aws s3 cp all.map s3://kartat-build/all.map
echo "Done!"
shutdown -h now