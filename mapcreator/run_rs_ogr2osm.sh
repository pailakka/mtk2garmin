#!/usr/bin/env bash
set -euo pipefail

build_root="${MTK2GARMIN_BUILD_ROOT:-/opt/mtk2garmin-build}"
publish_root="${MTK2GARMIN_PUBLISH_ROOT:-/opt/mtk2garmin-publish}"
rs_ogr2osm_root="${RS_OGR2OSM_ROOT:-/home/teemu/rs-ogr2osm}"
ogr2osm_image="${OGR2OSM_IMAGE:?OGR2OSM_IMAGE is required}"
osmium_image="${OSMIUM_IMAGE:?OSMIUM_IMAGE is required}"
include_additional_data="${RS_INCLUDE_ADDITIONAL_DATA:-1}"
additional_data_mount="${ADDITIONAL_DATA_MOUNT:-mapcreator_additional-data}"
merge_outputs="${RS_MERGE:-1}"
output_root="${OUTPUT_ROOT:-${build_root}/convertedpbf}"
rs_ogr2osm_log="${RS_OGR2OSM_LOG:-${build_root}/output/rs-ogr2osm.log}"

run_compose() {
  MTK2GARMIN_BUILD_ROOT="${build_root}" \
  MTK2GARMIN_PUBLISH_ROOT="${publish_root}" \
    docker compose "$@"
}

mkdir -p \
  "${build_root}/convertedpbf" \
  "${build_root}/output" \
  "${build_root}/splitted"

rm -f "${rs_ogr2osm_log}"

time env \
  OUTPUT_ROOT="${output_root}" \
  OGR2OSM_IMAGE="${ogr2osm_image}" \
  OSMIUM_IMAGE="${osmium_image}" \
  ADDITIONAL_DATA_MOUNT="${additional_data_mount}" \
  RS_INCLUDE_ADDITIONAL_DATA="${include_additional_data}" \
  RS_MERGE="${merge_outputs}" \
  RS_OGR2OSM_LOG="${rs_ogr2osm_log}" \
  "${rs_ogr2osm_root}/scripts/mtk/run-rs-full.sh"

echo "rs-ogr2osm log: ${rs_ogr2osm_log}"
echo "rs-ogr2osm output root: ${output_root}"
