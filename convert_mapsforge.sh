#!/bin/bash

apt-get update
apt-get -y upgrade
apt-get -y install awscli default-jre

mkdir osmosis
cd osmosis
wget http://bretth.dev.openstreetmap.org/osmosis-build/osmosis-latest.tgz -Oosmosis-latest.tgz
tar -xvzf osmosis-latest.tgz
export JAVACMD_OPTIONS="-Xmx117G"
cd ~
mkdir plugins
cd plugins
wget http://ci.mapsforge.org/job/0.6.1/lastSuccessfulBuild/artifact/mapsforge-map-writer/build/libs/mapsforge-map-writer-0.6.1-jar-with-dependencies.jar
cd ~

aws s3 cp s3://kartat.hylly.org/all_osm.osm.pbf all_osm.osm.pbf
aws s3 cp s3://kartat.hylly.org/mml_tag-mapping_tidy.xml mml_tag-mapping_tidy.xml

echo "Running osmosis writer!"
echo $JAVACMD_OPTIONS
osmosis/bin/osmosis --rbf all_osm.osm.pbf --mapfile-writer file=all.map bbox=59.4507573,19.0714057,70.1120744,31.6133108 tag-conf-file=mml_tag-mapping_tidy.xml type=hd comment="(c) NLS, Metsahallitus, Liikennevirasto, OpenStreetMap contributors 2016"
aws s3 cp all.map s3://kartat.hylly.org/all.map --acl public-read
echo "Done!"
shutdown -h now