#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${script_dir}"

time_stamp="${TIME_STAMP:-$(date +%Y-%m-%d)}"
build_root="${MTK2GARMIN_BUILD_ROOT:-/opt/mtk2garmin-build}"
publish_root="${MTK2GARMIN_PUBLISH_ROOT:-/opt/mtk2garmin-publish}"
rs_ogr2osm_root="${RS_OGR2OSM_ROOT:-/home/teemu/rs-ogr2osm}"
image_lock="${MTK2GARMIN_IMAGE_LOCK:-${script_dir}/images.lock.env}"

run_input_update="${RUN_INPUT_UPDATE:-1}"
run_conversion="${RUN_CONVERSION:-1}"
run_mkgmap="${RUN_MKGMAP:-1}"
run_mapsforge="${RUN_MAPSFORGE:-1}"
run_osx="${RUN_OSX:-1}"
run_nsis="${RUN_NSIS:-1}"
run_amoled_nsis="${RUN_AMOLED_NSIS:-0}"
run_publish="${RUN_PUBLISH:-1}"
run_cleanup="${RUN_CLEANUP:-1}"

include_additional_data="${RS_INCLUDE_ADDITIONAL_DATA:-1}"
additional_data_mount="${ADDITIONAL_DATA_MOUNT:-mapcreator_additional-data}"
splitter_java_heap="${SPLITTER_JAVA_HEAP:-40G}"
mkgmap_java_heap="${MKGMAP_JAVA_HEAP:-40G}"
garmin_splitter_max_nodes="${GARMIN_SPLITTER_MAX_NODES:-800000}"
garmin_max_subfile_mib="${GARMIN_MAX_SUBFILE_MIB:-4}"
garmin_max_img_mib="${GARMIN_MAX_IMG_MIB:-1900}"
garmin_max_tiles="${GARMIN_MAX_TILES:-1000}"
mapsforge_rs_memory_profile="${MAPSFORGE_RS_MEMORY_PROFILE:-production-high-mem}"
mapsforge_rs_memory_budget_gb="${MAPSFORGE_RS_MEMORY_BUDGET_GB:-64}"
mapsforge_rs_staged_store="${MAPSFORGE_RS_STAGED_STORE:-global-encoded}"
mapsforge_rs_tile_payload_threads="${MAPSFORGE_RS_TILE_PAYLOAD_THREADS:-16}"
mapsforge_rs_tile_payload_batch_size="${MAPSFORGE_RS_TILE_PAYLOAD_BATCH_SIZE:-1024}"
mapsforge_rs_way_planner_mode="${MAPSFORGE_RS_WAY_PLANNER_MODE:-multi-interval}"
minimum_free_gb="${MTK2GARMIN_MIN_FREE_GB:-80}"

load_image_lock() {
  if [[ ! -r "${image_lock}" ]]; then
    echo "Image lock is not readable: ${image_lock}" >&2
    echo "Release and lock runtime images with ./release_images.sh." >&2
    return 1
  fi

  set -a
  # shellcheck disable=SC1090
  source "${image_lock}"
  set +a

  local variable
  for variable in \
    MML_OGR_IMAGE MAPSFORGE_RS_IMAGE OGR2OSM_IMAGE OSMIUM_IMAGE \
    OSXCONVERTER_IMAGE ADDITIONAL_DATA_IMAGE MKGMAP_IMAGE MAPSTYLES_IMAGE \
    NSIS_IMAGE SITE_IMAGE; do
    if [[ -z "${!variable:-}" || "${!variable}" != *@sha256:* ]]; then
      echo "Image lock variable ${variable} must contain a full digest reference." >&2
      return 1
    fi
  done
}

run_compose() {
  MTK2GARMIN_BUILD_ROOT="${build_root}" \
  MTK2GARMIN_PUBLISH_ROOT="${publish_root}" \
    docker compose --env-file "${image_lock}" "$@"
}

skip_message() {
  local step="$1"
  local flag="$2"
  echo "Skipping ${step}; set ${flag}=1 to enable it." >&2
}

prepare_build_root() {
  mkdir -p \
    "${build_root}/convertedpbf" \
    "${build_root}/splitted" \
    "${build_root}/output" \
    "${publish_root}"
}

check_free_space() {
  if [[ "${run_conversion}" != "1" && "${run_mkgmap}" != "1" && "${run_mapsforge}" != "1" ]]; then
    return
  fi
  if [[ ! "${minimum_free_gb}" =~ ^[0-9]+$ ]]; then
    echo "MTK2GARMIN_MIN_FREE_GB must be a non-negative integer." >&2
    return 2
  fi

  local available_kb
  local required_kb=$((minimum_free_gb * 1024 * 1024))
  available_kb="$(df -Pk "${build_root}" | awk 'NR == 2 {print $4}')"
  if [[ ! "${available_kb}" =~ ^[0-9]+$ || "${available_kb}" -lt "${required_kb}" ]]; then
    echo "Insufficient free space under ${build_root}: require ${minimum_free_gb} GiB." >&2
    return 1
  fi
}

pull_service() {
  local service="$1"
  if ! run_compose pull "${service}"; then
    echo "Locked image for Compose service '${service}' is unavailable." >&2
    echo "Run ./release_images.sh for the affected service." >&2
    return 1
  fi
}

preflight_images() {
  local -a services=(mapstyles)

  [[ "${run_input_update}" == "1" ]] && services+=(mml-ogr-client)
  if [[ "${include_additional_data}" == "1" &&
        ( "${run_input_update}" == "1" || "${run_conversion}" == "1" ) ]]; then
    services+=(additional-data)
  fi
  [[ "${run_mkgmap}" == "1" ]] && services+=(mkgmap)
  [[ "${run_mapsforge}" == "1" ]] && services+=(mapsforge-rs)
  [[ "${run_osx}" == "1" ]] && services+=(osxconverter)
  [[ "${run_nsis}" == "1" ]] && services+=(nsis)
  [[ "${run_publish}" == "1" ]] && services+=(site)

  run_compose config --quiet
  for service in "${services[@]}"; do
    pull_service "${service}"
  done

  if [[ "${run_conversion}" == "1" ]]; then
    docker pull "${OGR2OSM_IMAGE}"
    docker pull "${OSMIUM_IMAGE}"
    docker run --rm "${OGR2OSM_IMAGE}" --help >/dev/null
    docker run --rm --entrypoint osmium "${OSMIUM_IMAGE}" --version >/dev/null
  fi

  if [[ "${run_mkgmap}" == "1" ]]; then
    docker run --rm "${MKGMAP_IMAGE}" ./run_mkgmap.sh --preflight
  fi

  if [[ "${run_publish}" == "1" ]]; then
    docker run --rm \
      --env-file "${script_dir}/aws-access.env" \
      --entrypoint aws \
      "${SITE_IMAGE}" \
      sts get-caller-identity \
      >/dev/null
  fi
}

prepare_runtime() {
  if [[ "${run_input_update}" == "1" ]]; then
    run_compose down -v --remove-orphans
  else
    run_compose down --remove-orphans
  fi
  run_compose up --no-start mapstyles
}

require_file() {
  local path="$1"
  if [[ ! -s "${path}" ]]; then
    echo "Required file is missing or empty: ${path}" >&2
    return 1
  fi
}

validate_input_data() {
  require_file /opt/mtkdata/mktmaasto.zip
  require_file /opt/mtkdata/mtkkorkeus.zip
  require_file /opt/krkdata/kiinteistorekisterikartta.gpkg

  if [[ "${include_additional_data}" == "1" ]]; then
    run_compose run --rm additional-data --validate-only
  fi
}

update_input_data() {
  if [[ "${run_input_update}" != "1" ]]; then
    skip_message "input refresh" "RUN_INPUT_UPDATE"
    if [[ "${run_conversion}" == "1" ]]; then
      validate_input_data
    fi
    return
  fi

  time run_compose run --rm mml-ogr-client
  if [[ "${include_additional_data}" == "1" ]]; then
    time run_compose run --rm additional-data
  fi
  validate_input_data
}

run_conversion_stage() {
  if [[ "${run_conversion}" != "1" ]]; then
    skip_message "rs-ogr2osm conversion" "RUN_CONVERSION"
    return
  fi

  time env \
    MTK2GARMIN_BUILD_ROOT="${build_root}" \
    MTK2GARMIN_PUBLISH_ROOT="${publish_root}" \
    RS_OGR2OSM_ROOT="${rs_ogr2osm_root}" \
    OGR2OSM_IMAGE="${OGR2OSM_IMAGE}" \
    OSMIUM_IMAGE="${OSMIUM_IMAGE}" \
    ADDITIONAL_DATA_MOUNT="${additional_data_mount}" \
    RS_INCLUDE_ADDITIONAL_DATA="${include_additional_data}" \
    RS_MERGE=1 \
    "${script_dir}/run_rs_ogr2osm.sh"
}

require_merged_pbf() {
  require_file "${build_root}/convertedpbf/all_osm.osm.pbf"
  docker run --rm \
    -v "${build_root}/convertedpbf:/convertedpbf:ro" \
    --entrypoint osmium \
    "${OSMIUM_IMAGE}" \
    check-refs /convertedpbf/all_osm.osm.pbf
}

run_mkgmap_stage() {
  if [[ "${run_mkgmap}" != "1" ]]; then
    skip_message "mkgmap" "RUN_MKGMAP"
    return
  fi

  require_merged_pbf
  time run_compose run --rm \
    -e SPLITTER_JAVA_HEAP="${splitter_java_heap}" \
    -e MKGMAP_JAVA_HEAP="${mkgmap_java_heap}" \
    -e GARMIN_SPLITTER_MAX_NODES="${garmin_splitter_max_nodes}" \
    -e GARMIN_MAX_SUBFILE_MIB="${garmin_max_subfile_mib}" \
    -e GARMIN_MAX_IMG_MIB="${garmin_max_img_mib}" \
    -e GARMIN_MAX_TILES="${garmin_max_tiles}" \
    mkgmap ./run_mkgmap.sh
}

run_mapsforge_stage() {
  if [[ "${run_mapsforge}" != "1" ]]; then
    skip_message "Mapsforge generation" "RUN_MAPSFORGE"
    return
  fi

  require_merged_pbf
  time env \
    MTK2GARMIN_BUILD_ROOT="${build_root}" \
    MTK2GARMIN_PUBLISH_ROOT="${publish_root}" \
    MAPSFORGE_RS_MEMORY_PROFILE="${mapsforge_rs_memory_profile}" \
    MAPSFORGE_RS_MEMORY_BUDGET_GB="${mapsforge_rs_memory_budget_gb}" \
    MAPSFORGE_RS_STAGED_STORE="${mapsforge_rs_staged_store}" \
    MAPSFORGE_RS_TILE_PAYLOAD_THREADS="${mapsforge_rs_tile_payload_threads}" \
    MAPSFORGE_RS_TILE_PAYLOAD_BATCH_SIZE="${mapsforge_rs_tile_payload_batch_size}" \
    MAPSFORGE_RS_WAY_PLANNER_MODE="${mapsforge_rs_way_planner_mode}" \
    "${script_dir}/run_mapsforge_rs.sh"
}

run_osx_stage() {
  if [[ "${run_osx}" != "1" ]]; then
    skip_message "macOS packages" "RUN_OSX"
    return
  fi

  time run_compose run --rm osxconverter
}

run_nsis_stage() {
  if [[ "${run_nsis}" != "1" ]]; then
    skip_message "NSIS installers" "RUN_NSIS"
    return
  fi

  time run_compose run --rm nsis /output/mtkgarmin/osmmap.nsi
  time run_compose run --rm nsis /output/mtkgarmin_noparcel/osmmap.nsi
  if [[ "${run_amoled_nsis}" == "1" ]]; then
    time run_compose run --rm nsis /output/mtkgarmin_amoled/osmmap.nsi
    time run_compose run --rm nsis /output/mtkgarmin_amoled_noparcel/osmmap.nsi
  else
    skip_message "AMOLED NSIS installers" "RUN_AMOLED_NSIS"
  fi
}

run_publish_stage() {
  if [[ "${run_publish}" != "1" ]]; then
    skip_message "publication" "RUN_PUBLISH"
    return
  fi

  time run_compose run --rm -e TIME_STAMP="${time_stamp}" site
  "${script_dir}/verify_release.sh" --live "${time_stamp}"
}

run_success_cleanup() {
  if [[ "${run_cleanup}" != "1" ]]; then
    skip_message "successful-run cleanup" "RUN_CLEANUP"
    return
  fi

  "${script_dir}/cleanup_build_root.sh" "${build_root}"
  run_compose down -v --remove-orphans
}

echo "Running the mtk2garmin production pipeline for ${time_stamp}." >&2
load_image_lock
prepare_build_root
check_free_space
preflight_images
prepare_runtime
update_input_data
run_conversion_stage
run_mkgmap_stage
run_mapsforge_stage
run_osx_stage
run_nsis_stage
run_publish_stage
run_success_cleanup
echo "mtk2garmin production pipeline completed for ${time_stamp}." >&2
