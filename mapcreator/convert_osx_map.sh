#!/bin/bash
set -e

docker build --tag garmin-osx-converter -f ./garminOSXConverter/Dockerfile ./garminOSXConverter
docker run -v /opt/mtk2garmin_build/mtk2garmin:/opt/mtk2garmin_build/mtk2garmin garmin-osx-converter