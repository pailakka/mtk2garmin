#!/bin/bash
set -euxo pipefail

printf -v date '%(%Y%m%d)T' -1

docker build --tag mtk2garmin-ubuntugis-base -f ./ubuntugis-base/Dockerfile ./ubuntugis-base
docker build --tag mtk2garmin-converter -f ../mtk2garmin-converter/Dockerfile ../mtk2garmin-converter

if docker build --tag "mtk2garmin-additional-data:$date" -f ../get-additional-data/Dockerfile ../get-additional-data; then
  echo "Succesfully loaded additional data"
  docker tag "mtk2garmin-additional-data:$date" mtk2garmin-additional-data:latest
fi

docker pull teemupel/mml-muutostietopalvelu-client
docker pull teemupel/mapsforge





