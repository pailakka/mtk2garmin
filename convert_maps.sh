#!/bin/bash
set -e

apt-get update
apt-get -y upgrade
apt-get -y install awscli default-jre git python

aws configure

git clone --depth=1 -b master --single-branch https://github.com/pailakka/mtk2garmin.git

cd mtk2garmin

cd mapsforge_peruskartta
python tidy_tag_mapping.py
cd ..

wget -Omkgmap.zip http://www.mkgmap.org.uk/download/mkgmap-r3972.zip
wget -Osplitter.zip http://www.mkgmap.org.uk/download/splitter-r583.zip

unzip -o mkgmap.zip
unzip -o splitter.zip

aws s3 cp s3://kartat-build/all_osm.osm.pbf all_osm.osm.pbf

mkdir splitted

java -jar -Xmx1G splitter-r548/splitter.jar --output-dir=splitted all_osm.osm.pbf > splitter.log
(cat mkgmap_mtk2garmin.args;echo;cat splitted/template.args) > splitted/mkgmap_mtk2garmin.args
java -cp "mkgmap-r3972/mkgmap.jar:lib/*jar" uk.me.parabola.mkgmap.main.TypCompiler peruskartta_garmin.txt peruskartta.typ
java -jar -Xmx1G mkgmap-r3972/mkgmap.jar -c splitted/mkgmap_mtk2garmin.args peruskartta.typ


aws s3 cp mtkgarmin/gmapsupp.img  s3://kartat-build/gmapsupp.img

mkdir osmosis
cd osmosis
wget http://bretth.dev.openstreetmap.org/osmosis-build/osmosis-latest.tgz -Oosmosis-latest.tgz
tar -xvzf osmosis-latest.tgz
export JAVACMD_OPTIONS="-Xmx1G"
cd ../
mkdir plugins
cd plugins
wget "http://search.maven.org/remotecontent?filepath=org/mapsforge/mapsforge-map-writer/0.8.0/mapsforge-map-writer-0.8.0-jar-with-dependencies.jar"
cd ../



echo "Running osmosis writer!"
echo $JAVACMD_OPTIONS
osmosis/bin/osmosis --rbf all_osm.osm.pbf --mapfile-writer file=all.map bbox=59.4507573,19.0714057,70.1120744,31.6133108 tag-conf-file=mapsforge_peruskartta/mml_tag-mapping_tidy.xml type=hd comment="(c) NLS, Metsahallitus, Liikennevirasto, OpenStreetMap contributors 2017"
aws s3 cp all.map s3://kartat-build/all.map
echo "Done!"
shutdown -h now