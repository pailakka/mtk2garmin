#!/bin/bash
set -e
echo "Installing packages"
add-apt-repository -y ppa:ubuntugis/ubuntugis-unstable
apt-get update
apt-get -y upgrade
apt-get -y install default-jre git python unzip default-jdk maven python3 libgdal-java

echo "Make /opt/mtk2garmin_build"
mkdir -p /opt/mtk2garmin_build
cd /opt/mtk2garmin_build

rm -rf mtk2garmin

git clone --depth=1 -b master --single-branch https://github.com/pailakka/mtk2garmin.git

cd mtk2garmin
mkdir suomi
mkdir inputdata
./get_additional_data.sh

ln -s /home/teemu/mtk/tuotteet/maastotietokanta/kaikki/etrs89/gml inputdata/mtks

mvn install
java -jar -Xmx5G target/mtk2garmin-0.0.1-SNAPSHOT.jar mtk2garmin.conf 
wget -O - http://m.m.i24.cc/osmconvert.c | cc -x c - -lz -O3 -o osmconvert
wget -O - http://m.m.i24.cc/osmfilter.c |cc -x c - -O3 -o osmfilter

chmod +x osmconvert
chmod +x osmfilter

python3 merge_suomi.py

parallel --eta --progress merge_all1.sh
parallel --eta --progress merge_all2.sh
parallel --eta --progress merge_all3.sh
parallel --eta --progress merge_all4.sh

./process_osm.sh

cd mapsforge_peruskartta
python3 tidy_tag_mapping.py
cd ..

wget -Omkgmap.zip http://www.mkgmap.org.uk/download/mkgmap-r3977.zip
wget -Osplitter.zip http://www.mkgmap.org.uk/download/splitter-r584.zip

unzip -o mkgmap.zip
unzip -o splitter.zip


mkdir splitted

echo "Splitting file..."
java -jar -Xmx5G splitter-r584/splitter.jar --output-dir=splitted all_osm.osm.pbf > splitter.log
echo "Splitting done"
(cat mkgmap_mtk2garmin.args;echo;cat splitted/template.args) > splitted/mkgmap_mtk2garmin.args
echo "Compiling typ"
java -cp "mkgmap-r3977/mkgmap.jar:lib/*jar" uk.me.parabola.mkgmap.main.TypCompiler peruskartta_garmin.txt peruskartta.typ
echo "Compiling typ done"
echo "Compiling garmin img"
java -jar -Xmx1G mkgmap-r3977/mkgmap.jar -c splitted/mkgmap_mtk2garmin.args peruskartta.typ


aws s3 cp mtkgarmin/gmapsupp.img  s3://kartat-build/gmapsupp.img

mkdir osmosis
cd osmosis
wget http://bretth.dev.openstreetmap.org/osmosis-build/osmosis-latest.tgz -Oosmosis-latest.tgz
tar -xvzf osmosis-latest.tgz
cd ../
mkdir plugins
cd plugins
wget "https://jekku.hylly.org/mapsforge-map-writer-MTK2GARMIN-jar-with-dependencies.jar"
cd ../



echo "Running osmosis writer!"
./mapsforge_convert.sh
aws s3 cp all.map s3://kartat-build/all.map
echo "Done!"
