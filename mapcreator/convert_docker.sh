#!/bin/bash
set -euxo pipefail

printf -v date '%(%Y%m%d)T' -1

##docker build --tag mtk2garmin-ubuntugis-base -f ./ubuntugis-base/Dockerfile ./ubuntugis-base
#
#docker-compose build
#
#if docker build --tag "localhost:32000/mtk2garmin-additional-data:$date" -f ../get-additional-data/Dockerfile --no-cache ../get-additional-data; then
#  echo "Succesfully loaded additional data"
#  docker tag "localhost:32000/mtk2garmin-additional-data:$date" localhost:32000/mtk2garmin-additional-data:latest
#  docker push localhost:32000/mtk2garmin-additional-data:latest
#fi
#
#docker-compose pull

docker-compose run mml-client /go/src/app/mml-muutostietopalvelu-client load -p maastotietokanta -t kaikki -f application/gml+xml -d /mtkdata
docker-compose run mml-client /go/src/app/mml-muutostietopalvelu-client load -p kiinteistorekisterikartta -t karttalehdittain -f application/x-shapefile -d /krkdata


docker-compose up --no-start additional-data
docker-compose run mtk2garmin-converter java -jar /opt/mtk2garmin/mtk2garmin-converter/target/mtk2garmin-0.0.1-SNAPSHOT.jar /opt/mtk2garmin/mtk2garmin.conf



