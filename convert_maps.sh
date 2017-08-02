#!/bin/bash
set -e

apt-get update
apt-get -y upgrade
apt-get -y install awscli default-jre git python unzip

aws configure

git clone --depth=1 -b master --single-branch https://github.com/pailakka/mtk2garmin.git

cd mtk2garmin

cd mapsforge_peruskartta
python tidy_tag_mapping.py
cd ..

wget -Omkgmap.zip http://www.mkgmap.org.uk/download/mkgmap-r3973.zip
wget -Osplitter.zip http://www.mkgmap.org.uk/download/splitter-r583.zip

unzip -o mkgmap.zip
unzip -o splitter.zip

aws s3 cp s3://kartat-build/all_osm.osm.pbf all_osm.osm.pbf

mkdir splitted

echo "Splitting file..."
java -jar -Xmx5G splitter-r583/splitter.jar --output-dir=splitted all_osm.osm.pbf > splitter.log
echo "Splitting done"
(cat mkgmap_mtk2garmin.args;echo;cat splitted/template.args) > splitted/mkgmap_mtk2garmin.args
echo "Compiling typ"
java -cp "mkgmap-r3973/mkgmap.jar:lib/*jar" uk.me.parabola.mkgmap.main.TypCompiler peruskartta_garmin.txt peruskartta.typ
echo "Compiling typ done"
echo "Compiling garmin img"
java -jar -Xmx1G mkgmap-r3973/mkgmap.jar -c splitted/mkgmap_mtk2garmin.args peruskartta.typ


aws s3 cp mtkgarmin/gmapsupp.img  s3://kartat-build/gmapsupp.img

mkdir osmosis
cd osmosis
wget http://bretth.dev.openstreetmap.org/osmosis-build/osmosis-latest.tgz -Oosmosis-latest.tgz
tar -xvzf osmosis-latest.tgz
export JAVACMD_OPTIONS="-Xmx60G"
cd ../
mkdir plugins
cd plugins
wget "http://search.maven.org/remotecontent?filepath=org/mapsforge/mapsforge-map-writer/0.8.0/mapsforge-map-writer-0.8.0-jar-with-dependencies.jar"
cd ../



echo "Running osmosis writer!"
./mapsforge_convert.sh
aws s3 cp all.map s3://kartat-build/all.map
echo "Done!"
shutdown -h now
