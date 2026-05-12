#!/bin/bash
set -euxo pipefail

printf -v image_date '%(%Y%m%d)T' -1

time_stamp="${TIME_STAMP:-$(date +%Y-%m-%d)}"
rs_ogr2osm_root="${RS_OGR2OSM_ROOT:-/home/teemu/rs-ogr2osm}"
ogr2osm_image="${OGR2OSM_IMAGE:-rs-ogr2osm}"
v1_build_root="${V1_BUILD_ROOT:-/opt/mtk2garmin-build}"
v2_build_root="${V2_BUILD_ROOT:-/opt/mtk2garmin-build/v2}"
publish_root="${MTK2GARMIN_PUBLISH_ROOT:-/opt/mtk2garmin-publish}"

run_image_build="${RUN_IMAGE_BUILD:-1}"
run_input_update="${RUN_INPUT_UPDATE:-1}"
run_old="${RUN_OLD:-1}"
run_v2="${RUN_V2:-1}"
run_publish="${RUN_PUBLISH:-1}"
run_cleanup="${RUN_CLEANUP:-1}"
include_rs_additional_data="${RS_INCLUDE_ADDITIONAL_DATA:-1}"
additional_data_mount="${ADDITIONAL_DATA_MOUNT:-mapcreator_additional-data}"

run_compose() {
  local build_root="$1"
  shift
  MTK2GARMIN_BUILD_ROOT="${build_root}" \
  MTK2GARMIN_PUBLISH_ROOT="${publish_root}" \
    docker compose "$@"
}

prepare_build_root() {
  local build_root="$1"
  mkdir -p \
    "${build_root}/convertedpbf" \
    "${build_root}/splitted" \
    "${build_root}/output"
}

prepare_shared_volumes() {
  local build_root="$1"
  run_compose "${build_root}" up --no-start additional-data
  run_compose "${build_root}" up --no-start mapstyles
}

run_downstream_outputs() {
  local build_root="$1"
  local download_prefix="$2"
  local publish_prefix="$3"
  local index_object="$4"
  local legacy_index_object="$5"
  local site_variant="$6"

  prepare_build_root "${build_root}"
  prepare_shared_volumes "${build_root}"

  time run_compose "${build_root}" run merger ./process_osm.sh

  time run_compose "${build_root}" run mkgmap ./run_mkgmap.sh

  time run_compose "${build_root}" run mapsforge /app/bin/osmosis \
             --rbf file=/convertedpbf/all_osm.osm.pbf workers=4 \
             --mapfile-writer file=/output/mtk_all.map bbox=59.4507573,19.0714057,70.1120744,31.6133108 \
             simplification-max-zoom=12 simplification-factor=8 threads=8 \
             zoom-interval-conf=5,0,7,10,8,11,12,12,13,14,14,21 \
             label-position=true polylabel=true \
             tag-conf-file=/mapstyles/mapsforge_peruskartta/mml_tag-mapping_tidy.xml type=hd comment="(c) NLS, Metsahallitus, Liikennevirasto, OpenStreetMap contributors 2026"

  time run_compose "${build_root}" run osxconverter

  time run_compose "${build_root}" run nsis /output/mtkgarmin/osmmap.nsi
  time run_compose "${build_root}" run nsis /output/mtkgarmin_noparcel/osmmap.nsi
  time run_compose "${build_root}" run nsis /output/mtkgarmin_amoled/osmmap.nsi
  time run_compose "${build_root}" run nsis /output/mtkgarmin_amoled_noparcel/osmmap.nsi

  if [[ "${run_publish}" == "1" ]]; then
    time run_compose "${build_root}" run \
      -e TIME_STAMP="${time_stamp}" \
      -e DOWNLOAD_PREFIX="${download_prefix}" \
      -e PUBLISH_PREFIX="${publish_prefix}" \
      -e INDEX_OBJECT="${index_object}" \
      -e LEGACY_INDEX_OBJECT="${legacy_index_object}" \
      -e SITE_VARIANT="${site_variant}" \
      site
  fi
}

docker compose down -v --remove-orphans

if [[ "${run_image_build}" == "1" ]]; then
  docker pull ghcr.io/osgeo/gdal:ubuntu-full-3.10.0
  docker build --tag teemupel/mtk2garmin-ubuntugis-base -f ./ubuntugis-base/Dockerfile ./ubuntugis-base
  docker push teemupel/mtk2garmin-ubuntugis-base

  docker compose build --parallel
  docker compose push
  docker build --tag "${ogr2osm_image}" "${rs_ogr2osm_root}"

  if [[ -z "$(docker images -q "localhost:5000/mtk2garmin-additional-data:${image_date}" 2> /dev/null)" ]]; then
    if docker build --tag "localhost:5000/mtk2garmin-additional-data:${image_date}" -f ../get-additional-data/Dockerfile --no-cache ../get-additional-data; then
      echo "Succesfully loaded additional data"
      docker tag "localhost:5000/mtk2garmin-additional-data:${image_date}" localhost:5000/mtk2garmin-additional-data:latest
      docker push localhost:5000/mtk2garmin-additional-data:latest
    fi
  fi
fi

docker compose pull

if [[ "${run_input_update}" == "1" ]]; then
  time run_compose "${v1_build_root}" run mml-client /go/src/app/mml-muutostietopalvelu-client load -p maastotietokanta -t avoin -f application/gml+xml -d /mtkdata
  time run_compose "${v1_build_root}" run mml-client /go/src/app/mml-muutostietopalvelu-client load -p kiinteistorekisterikartta -t karttalehdittain -f application/x-shapefile -d /krkdata

  time run_compose "${v1_build_root}" run mml-ogr-client
fi

prepare_shared_volumes "${v1_build_root}"

if [[ "${run_old}" == "1" ]]; then
  prepare_build_root "${v1_build_root}"
  time run_compose "${v1_build_root}" run mtk2garmin-converter java -jar /opt/mtk2garmin/target/mtk2garmin-0.0.2.jar /opt/mtk2garmin/mtk2garmin.conf
  run_downstream_outputs "${v1_build_root}" "${time_stamp}" "new-${time_stamp}" "index.html" "index_old.html" ""
fi

if [[ "${run_v2}" == "1" ]]; then
  prepare_build_root "${v2_build_root}"
  prepare_shared_volumes "${v2_build_root}"

  time env \
    OUTPUT_ROOT="${v2_build_root}/convertedpbf" \
    OGR2OSM_IMAGE="${ogr2osm_image}" \
    ADDITIONAL_DATA_MOUNT="${additional_data_mount}" \
    RS_INCLUDE_ADDITIONAL_DATA="${include_rs_additional_data}" \
    "${rs_ogr2osm_root}/scripts/mtk/run-rs-full.sh"

  run_downstream_outputs "${v2_build_root}" "v2/${time_stamp}" "v2/${time_stamp}" "index_v2.html" "" "v2"

  time env \
    V1_BUILD_ROOT="${v1_build_root}" \
    V2_BUILD_ROOT="${v2_build_root}" \
    "${rs_ogr2osm_root}/scripts/mtk/compare-v1-v2.sh"
fi

if [[ "${run_cleanup}" == "1" ]]; then
  docker compose down -v
fi
