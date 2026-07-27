#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${script_dir}"

time_stamp="${TIME_STAMP:-$(date +%Y-%m-%d)}"
build_root="${MTK2GARMIN_BUILD_ROOT:-/opt/mtk2garmin-build}"
publish_root="${MTK2GARMIN_PUBLISH_ROOT:-/opt/mtk2garmin-publish}"
rs_ogr2osm_root="${RS_OGR2OSM_ROOT:-/home/teemu/rs-ogr2osm}"
image_lock="${MTK2GARMIN_IMAGE_LOCK:-${script_dir}/images.lock.env}"

run_input_update="${RUN_INPUT_UPDATE:-0}"
run_conversion="${RUN_CONVERSION:-1}"
run_mkgmap="${RUN_MKGMAP:-1}"
run_mapsforge="${RUN_MAPSFORGE:-1}"
run_osx="${RUN_OSX:-1}"
run_nsis="${RUN_NSIS:-1}"
run_amoled_nsis="${RUN_AMOLED_NSIS:-0}"
run_publish="${RUN_PUBLISH:-1}"
run_cleanup="${RUN_CLEANUP:-1}"

include_additional_data="${RS_INCLUDE_ADDITIONAL_DATA:-1}"
rs_ogr2osm_workers="${RS_OGR2OSM_WORKERS:-2}"
additional_data_mount="${ADDITIONAL_DATA_MOUNT:-mapcreator_additional-data}"
input_snapshot_root="${MTK2GARMIN_INPUT_SNAPSHOT_ROOT:-/opt/mtk2garmin-inputs}"
input_snapshot_max_age_hours="${MTK2GARMIN_INPUT_MAX_AGE_HOURS:-6}"
use_input_snapshot="${MTK2GARMIN_USE_INPUT_SNAPSHOT:-1}"
mtkdata_root="${MTK2GARMIN_MTKDATA_ROOT:-/opt/mtkdata}"
krkdata_root="${MTK2GARMIN_KRKDATA_ROOT:-/opt/krkdata}"
splitter_java_heap="${SPLITTER_JAVA_HEAP:-40G}"
mkgmap_java_heap="${MKGMAP_JAVA_HEAP:-40G}"
mkgmap_max_jobs="${MKGMAP_MAX_JOBS:-8}"
mkgmap_container_memory_limit="${MKGMAP_CONTAINER_MEMORY_LIMIT:-48g}"
garmin_splitter_max_nodes="${GARMIN_SPLITTER_MAX_NODES:-800000}"
garmin_max_subfile_mib="${GARMIN_MAX_SUBFILE_MIB:-4}"
garmin_max_img_mib="${GARMIN_MAX_IMG_MIB:-1900}"
garmin_max_tiles="${GARMIN_MAX_TILES:-1000}"
mapsforge_rs_memory_profile="${MAPSFORGE_RS_MEMORY_PROFILE:-production-high-mem}"
mapsforge_rs_memory_budget_gb="${MAPSFORGE_RS_MEMORY_BUDGET_GB:-32}"
mapsforge_rs_staged_store="${MAPSFORGE_RS_STAGED_STORE:-global-encoded}"
mapsforge_rs_tile_payload_threads="${MAPSFORGE_RS_TILE_PAYLOAD_THREADS:-8}"
mapsforge_rs_tile_payload_batch_size="${MAPSFORGE_RS_TILE_PAYLOAD_BATCH_SIZE:-1024}"
mapsforge_rs_way_planner_mode="${MAPSFORGE_RS_WAY_PLANNER_MODE:-multi-interval}"
mapsforge_rs_node_index_type="${MAPSFORGE_RS_NODE_INDEX_TYPE:-disk}"
mapsforge_rs_node_index_cache_blocks="${MAPSFORGE_RS_NODE_INDEX_CACHE_BLOCKS:-65536}"
mapsforge_rs_container_memory_limit="${MAPSFORGE_RS_CONTAINER_MEMORY_LIMIT:-32g}"
minimum_free_gb="${MTK2GARMIN_MIN_FREE_GB:-80}"
resume_stages="${MTK2GARMIN_RESUME:-1}"
force_stages=",${MTK2GARMIN_FORCE_STAGES:-},"
state_root="${MTK2GARMIN_STATE_ROOT:-${build_root}/state}"
stage_manifest_root="${state_root}/stages"
run_status_file="${MTK2GARMIN_RUN_STATUS_FILE:-${state_root}/run-status-${time_stamp}.json}"
run_summary_file="${MTK2GARMIN_RUN_SUMMARY_FILE:-${state_root}/run-summary-${time_stamp}.json}"
input_snapshot_manifest=""

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
  MTK2GARMIN_MTKDATA_ROOT="${mtkdata_root}" \
  MTK2GARMIN_KRKDATA_ROOT="${krkdata_root}" \
  ADDITIONAL_DATA_MOUNT="${additional_data_mount}" \
  MKGMAP_CONTAINER_MEMORY_LIMIT="${mkgmap_container_memory_limit}" \
  MAPSFORGE_RS_CONTAINER_MEMORY_LIMIT="${mapsforge_rs_container_memory_limit}" \
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
    "${stage_manifest_root}" \
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
  run_compose down --remove-orphans
  run_compose up --no-start mapstyles
}

require_file() {
  local path="$1"
  if [[ ! -s "${path}" ]]; then
    echo "Required file is missing or empty: ${path}" >&2
    return 1
  fi
}

write_run_status() {
  local release_status="$1"
  local housekeeping_status="$2"
  local housekeeping_exit_code="${3:-}"
  local failure_type="${4:-none}"
  local -a arguments=(
    status-write
    --status-file "${run_status_file}"
    --release "${release_status}"
    --housekeeping "${housekeeping_status}"
    --failure-type "${failure_type}"
  )

  if [[ -n "${housekeeping_exit_code}" ]]; then
    arguments+=(--housekeeping-exit-code "${housekeeping_exit_code}")
  fi
  python3 "${script_dir}/pipeline_state.py" "${arguments[@]}"
}

write_run_summary() {
  python3 "${script_dir}/pipeline_state.py" run-summary \
    --stage-root "${stage_manifest_root}" \
    --status-file "${run_status_file}" \
    --release-date "${time_stamp}" \
    --output "${run_summary_file}"
}

finalize_run_summary() {
  local status=$?
  trap - EXIT
  write_run_summary || true
  exit "${status}"
}

stage_manifest_file() {
  local stage="$1"
  printf '%s/%s.json\n' "${stage_manifest_root}" "${stage}"
}

stage_fingerprint() {
  local stage="$1"
  local version="$2"
  shift 2

  python3 "${script_dir}/pipeline_state.py" fingerprint \
    --stage "${stage}" \
    --version "${version}" \
    "$@"
}

stage_is_forced() {
  local stage="$1"
  [[ "${force_stages}" == *",${stage},"* ]]
}

stage_can_be_reused() {
  local stage="$1"
  local fingerprint="$2"
  local manifest
  local -a hash_argument=()

  if [[ "${resume_stages}" != "1" ]] || stage_is_forced "${stage}"; then
    return 1
  fi
  if [[ "${MTK2GARMIN_RESUME_VERIFY_HASHES:-1}" != "1" ]]; then
    hash_argument+=(--skip-hashes)
  fi
  manifest="$(stage_manifest_file "${stage}")"
  python3 "${script_dir}/pipeline_state.py" check \
    --manifest "${manifest}" \
    --fingerprint "${fingerprint}" \
    "${hash_argument[@]}"
}

run_recorded_stage() {
  local stage="$1"
  local fingerprint="$2"
  shift 2

  local -a output_arguments=()
  while [[ $# -gt 0 && "$1" != "--" ]]; do
    output_arguments+=("$1")
    shift
  done
  if [[ $# -eq 0 ]]; then
    echo "Internal error: stage ${stage} has no command separator." >&2
    return 2
  fi
  shift

  if stage_can_be_reused "${stage}" "${fingerprint}"; then
    echo "Reusing verified ${stage} outputs." >&2
    return
  fi

  local started_at
  local started_epoch
  local ended_at
  local ended_epoch
  local duration_seconds
  local stage_status

  started_at="$(date --iso-8601=seconds)"
  started_epoch="$(date +%s)"
  if "$@"; then
    stage_status=0
  else
    stage_status=$?
  fi
  ended_epoch="$(date +%s)"
  ended_at="$(date --iso-8601=seconds)"
  duration_seconds=$((ended_epoch - started_epoch))

  if [[ "${stage_status}" -eq 0 ]]; then
    python3 "${script_dir}/pipeline_state.py" record \
      --manifest "$(stage_manifest_file "${stage}")" \
      --stage "${stage}" \
      --fingerprint "${fingerprint}" \
      --status success \
      --started-at "${started_at}" \
      --ended-at "${ended_at}" \
      --duration-seconds "${duration_seconds}" \
      --exit-code 0 \
      "${output_arguments[@]}"
  else
    python3 "${script_dir}/pipeline_state.py" record \
      --manifest "$(stage_manifest_file "${stage}")" \
      --stage "${stage}" \
      --fingerprint "${fingerprint}" \
      --status failed \
      --started-at "${started_at}" \
      --ended-at "${ended_at}" \
      --duration-seconds "${duration_seconds}" \
      --exit-code "${stage_status}" || true
    return "${stage_status}"
  fi
}

validate_input_data() {
  require_file "${mtkdata_root}/mktmaasto.zip"
  require_file "${mtkdata_root}/mtkkorkeus.zip"
  require_file "${krkdata_root}/kiinteistorekisterikartta.gpkg"

  if [[ "${include_additional_data}" == "1" ]]; then
    run_compose run --rm --no-deps additional-data --validate-only
  fi
}

update_input_data() {
  if [[ "${run_input_update}" != "1" ]]; then
    skip_message "input snapshot refresh" "RUN_INPUT_UPDATE"
    return
  fi

  time "${script_dir}/input_snapshot.sh"
}

resolve_input_snapshot() {
  local current_root

  if [[ "${use_input_snapshot}" != "1" ]]; then
    echo "Using explicitly configured legacy input directories." >&2
    validate_input_data
    return
  fi

  MTK2GARMIN_INPUT_SNAPSHOT_ROOT="${input_snapshot_root}" \
  MTK2GARMIN_INPUT_MAX_AGE_HOURS="${input_snapshot_max_age_hours}" \
    "${script_dir}/input_snapshot.sh" --check-current

  current_root="$(realpath -e -- "${input_snapshot_root}/current")"
  mtkdata_root="${current_root}/mtkdata"
  krkdata_root="${current_root}/krkdata"
  additional_data_mount="${current_root}/additional-data"
  input_snapshot_manifest="${current_root}/manifest.json"
  validate_input_data
  echo "Using immutable input snapshot: ${current_root}" >&2
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
    MTKDATA_ROOT="${mtkdata_root}" \
    KRKDATA_ROOT="${krkdata_root}" \
    ADDITIONAL_DATA_MOUNT="${additional_data_mount}" \
    RS_INCLUDE_ADDITIONAL_DATA="${include_additional_data}" \
    RS_OGR2OSM_WORKERS="${rs_ogr2osm_workers}" \
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

  time run_compose run --rm \
    -e SPLITTER_JAVA_HEAP="${splitter_java_heap}" \
    -e MKGMAP_JAVA_HEAP="${mkgmap_java_heap}" \
    -e MKGMAP_MAX_JOBS="${mkgmap_max_jobs}" \
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

  time env \
    MTK2GARMIN_BUILD_ROOT="${build_root}" \
    MTK2GARMIN_PUBLISH_ROOT="${publish_root}" \
    MAPSFORGE_RS_MEMORY_PROFILE="${mapsforge_rs_memory_profile}" \
    MAPSFORGE_RS_MEMORY_BUDGET_GB="${mapsforge_rs_memory_budget_gb}" \
    MAPSFORGE_RS_STAGED_STORE="${mapsforge_rs_staged_store}" \
    MAPSFORGE_RS_TILE_PAYLOAD_THREADS="${mapsforge_rs_tile_payload_threads}" \
    MAPSFORGE_RS_TILE_PAYLOAD_BATCH_SIZE="${mapsforge_rs_tile_payload_batch_size}" \
    MAPSFORGE_RS_WAY_PLANNER_MODE="${mapsforge_rs_way_planner_mode}" \
    MAPSFORGE_RS_NODE_INDEX_TYPE="${mapsforge_rs_node_index_type}" \
    MAPSFORGE_RS_NODE_INDEX_CACHE_BLOCKS="${mapsforge_rs_node_index_cache_blocks}" \
    "${script_dir}/run_mapsforge_rs.sh"
}

run_osx_stage() {
  if [[ "${run_osx}" != "1" ]]; then
    skip_message "macOS packages" "RUN_OSX"
    return
  fi

  time run_compose run --rm osxconverter
}

run_nsis_standard_stage() {
  time run_compose run --rm nsis /output/mtkgarmin/osmmap.nsi
}

run_nsis_noparcel_stage() {
  time run_compose run --rm nsis /output/mtkgarmin_noparcel/osmmap.nsi
}

run_nsis_amoled_stage() {
  if [[ "${run_amoled_nsis}" == "1" ]]; then
    if ! time run_compose run --rm nsis /output/mtkgarmin_amoled/osmmap.nsi; then
      return 1
    fi
    time run_compose run --rm nsis /output/mtkgarmin_amoled_noparcel/osmmap.nsi
  fi
}

run_nsis_stage() {
  if [[ "${run_nsis}" != "1" ]]; then
    skip_message "NSIS installers" "RUN_NSIS"
    return
  fi

  run_nsis_standard_stage
  run_nsis_noparcel_stage
  if [[ "${run_amoled_nsis}" == "1" ]]; then
    run_nsis_amoled_stage
  else
    skip_message "AMOLED NSIS installers" "RUN_AMOLED_NSIS"
  fi
}

run_publish_stage() {
  if [[ "${run_publish}" != "1" ]]; then
    skip_message "publication" "RUN_PUBLISH"
    return
  fi

  if ! time run_compose run --rm -e TIME_STAMP="${time_stamp}" site; then
    return 1
  fi
  "${script_dir}/verify_release.sh" --live "${time_stamp}"
}

run_success_cleanup() {
  local cleanup_status=0

  if [[ "${run_cleanup}" != "1" ]]; then
    skip_message "successful-run cleanup" "RUN_CLEANUP"
    return
  fi

  "${script_dir}/cleanup_build_root.sh" "${build_root}" "${OSMIUM_IMAGE}" ||
    cleanup_status=$?
  run_compose down -v --remove-orphans || cleanup_status=$?
  return "${cleanup_status}"
}

run_conversion_and_validate() {
  if ! run_conversion_stage; then
    return 1
  fi
  require_merged_pbf
}

run_conversion_recorded() {
  local fingerprint
  local -a inputs=()

  if [[ "${run_conversion}" != "1" ]]; then
    skip_message "rs-ogr2osm conversion" "RUN_CONVERSION"
    require_merged_pbf
    return
  fi

  if [[ -n "${input_snapshot_manifest}" ]]; then
    inputs+=(--file "input-snapshot=${input_snapshot_manifest}")
  else
    inputs+=(
      --file "mktmaasto=${mtkdata_root}/mktmaasto.zip"
      --file "mtkkorkeus=${mtkdata_root}/mtkkorkeus.zip"
      --file "property=${krkdata_root}/kiinteistorekisterikartta.gpkg"
    )
  fi
  fingerprint="$(stage_fingerprint conversion 2 \
    "${inputs[@]}" \
    --file "maasto-config=${rs_ogr2osm_root}/mtk_config/mtk2garmin_maasto.toml" \
    --file "elevation-config=${rs_ogr2osm_root}/mtk_config/mtk2garmin_korkeus.toml" \
    --file "property-config=${rs_ogr2osm_root}/mtk_config/mtk2garmin_kiinteistorajat.toml" \
    --file "depth-contour-config=${rs_ogr2osm_root}/mtk_config/mtk2garmin_syvyyskayrat.toml" \
    --file "depth-point-config=${rs_ogr2osm_root}/mtk_config/mtk2garmin_syvyyspisteet.toml" \
    --file "tag-script=${rs_ogr2osm_root}/mtk_config/mtk2garmin_mml.lua" \
    --file "depth-tag-script=${rs_ogr2osm_root}/mtk_config/mtk2garmin_syvyys.lua" \
    --file "conversion-runner=${rs_ogr2osm_root}/scripts/mtk/run-rs-full.sh" \
    --file "merge-runner=${rs_ogr2osm_root}/scripts/mtk/merge-rs-full.sh" \
    --value "ogr-image=${OGR2OSM_IMAGE}" \
    --value "osmium-image=${OSMIUM_IMAGE}" \
    --value "include-additional-data=${include_additional_data}" \
    --value "workers=${rs_ogr2osm_workers}")"

  run_recorded_stage conversion "${fingerprint}" \
    --output "${build_root}/convertedpbf/all_osm.osm.pbf" \
    -- \
    run_conversion_and_validate
  require_merged_pbf
}

converted_input_arguments() {
  local conversion_manifest
  conversion_manifest="$(stage_manifest_file conversion)"
  if [[ "${run_conversion}" == "1" && -s "${conversion_manifest}" ]]; then
    printf '%s\n%s\n' --file "conversion=${conversion_manifest}"
  else
    printf '%s\n%s\n' --file \
      "merged-pbf=${build_root}/convertedpbf/all_osm.osm.pbf"
  fi
}

run_mkgmap_recorded() {
  local fingerprint
  local -a inputs=()

  if [[ "${run_mkgmap}" != "1" ]]; then
    skip_message "mkgmap" "RUN_MKGMAP"
    return
  fi
  mapfile -t inputs < <(converted_input_arguments)
  fingerprint="$(stage_fingerprint mkgmap 3 \
    "${inputs[@]}" \
    --value "image=${MKGMAP_IMAGE}" \
    --value "max-jobs=${mkgmap_max_jobs}" \
    --value "splitter-max-nodes=${garmin_splitter_max_nodes}" \
    --value "max-subfile-mib=${garmin_max_subfile_mib}" \
    --value "max-img-mib=${garmin_max_img_mib}" \
    --value "max-tiles=${garmin_max_tiles}")"
  run_recorded_stage mkgmap "${fingerprint}" \
    --output-tree "${build_root}/output/mtkgarmin" \
    --output-tree "${build_root}/output/mtkgarmin_noparcel" \
    --output-tree "${build_root}/output/mtkgarmin_amoled" \
    --output-tree "${build_root}/output/mtkgarmin_amoled_noparcel" \
    -- \
    run_mkgmap_stage
}

run_mapsforge_recorded() {
  local fingerprint
  local -a inputs=()

  if [[ "${run_mapsforge}" != "1" ]]; then
    skip_message "Mapsforge generation" "RUN_MAPSFORGE"
    return
  fi
  mapfile -t inputs < <(converted_input_arguments)
  fingerprint="$(stage_fingerprint mapsforge 3 \
    "${inputs[@]}" \
    --value "writer-image=${MAPSFORGE_RS_IMAGE}" \
    --value "mapstyles-image=${MAPSTYLES_IMAGE}" \
    --value "profile=${mapsforge_rs_memory_profile}" \
    --value "budget-gb=${mapsforge_rs_memory_budget_gb}" \
    --value "staged-store=${mapsforge_rs_staged_store}" \
    --value "tile-threads=${mapsforge_rs_tile_payload_threads}" \
    --value "tile-batch=${mapsforge_rs_tile_payload_batch_size}" \
    --value "way-planner=${mapsforge_rs_way_planner_mode}" \
    --value "node-index=${mapsforge_rs_node_index_type}" \
    --value "node-cache-blocks=${mapsforge_rs_node_index_cache_blocks}")"
  run_recorded_stage mapsforge "${fingerprint}" \
    --output "${build_root}/output/mtk_all.map" \
    -- \
    run_mapsforge_stage
}

garmin_stage_input_arguments() {
  local mkgmap_manifest
  mkgmap_manifest="$(stage_manifest_file mkgmap)"
  if [[ -s "${mkgmap_manifest}" ]]; then
    printf '%s\n%s\n' --file "mkgmap=${mkgmap_manifest}"
  else
    printf '%s\n%s\n%s\n%s\n' \
      --file "garmin=${build_root}/output/mtkgarmin/mtk_suomi.img" \
      --file "garmin-noparcel=${build_root}/output/mtkgarmin_noparcel/mtk_suomi_noparcel.img"
  fi
}

run_osx_recorded() {
  local fingerprint
  local -a inputs=()
  mapfile -t inputs < <(garmin_stage_input_arguments)
  fingerprint="$(stage_fingerprint osx-packages 2 \
    "${inputs[@]}" \
    --value "image=${OSXCONVERTER_IMAGE}")"
  run_recorded_stage osx-packages "${fingerprint}" \
    --output "${build_root}/output/mtk_suomi_osx.zip" \
    --output "${build_root}/output/mtk_suomi_noparcel_osx.zip" \
    -- \
    run_osx_stage
}

run_nsis_standard_recorded() {
  local fingerprint
  local -a inputs=()
  mapfile -t inputs < <(garmin_stage_input_arguments)
  fingerprint="$(stage_fingerprint nsis-standard 2 \
    "${inputs[@]}" \
    --value "image=${NSIS_IMAGE}")"
  run_recorded_stage nsis-standard "${fingerprint}" \
    --output "${build_root}/output/mtkgarmin/MTKSuomi.exe" \
    -- \
    run_nsis_standard_stage
}

run_nsis_noparcel_recorded() {
  local fingerprint
  local -a inputs=()
  mapfile -t inputs < <(garmin_stage_input_arguments)
  fingerprint="$(stage_fingerprint nsis-noparcel 2 \
    "${inputs[@]}" \
    --value "image=${NSIS_IMAGE}")"
  run_recorded_stage nsis-noparcel "${fingerprint}" \
    --output "${build_root}/output/mtkgarmin_noparcel/MTKSuomi.exe" \
    -- \
    run_nsis_noparcel_stage
}

run_nsis_amoled_recorded() {
  local fingerprint
  local -a inputs=()
  mapfile -t inputs < <(garmin_stage_input_arguments)
  fingerprint="$(stage_fingerprint nsis-amoled 2 \
    "${inputs[@]}" \
    --value "image=${NSIS_IMAGE}")"
  run_recorded_stage nsis-amoled "${fingerprint}" \
    --output "${build_root}/output/mtkgarmin_amoled/MTKSuomi.exe" \
    --output "${build_root}/output/mtkgarmin_amoled_noparcel/MTKSuomi.exe" \
    -- \
    run_nsis_amoled_stage
}

run_garmin_packaging() {
  local -a labels=()
  local -a pids=()
  local packaging_status=0
  local index

  if [[ "${run_osx}" == "1" ]]; then
    (run_osx_recorded) \
      > >(tee "${build_root}/output/osx-packages.log") 2>&1 &
    pids+=("$!")
    labels+=(osx-packages)
  else
    skip_message "macOS packages" "RUN_OSX"
  fi

  if [[ "${run_nsis}" == "1" ]]; then
    (run_nsis_standard_recorded) \
      > >(tee "${build_root}/output/nsis-standard.log") 2>&1 &
    pids+=("$!")
    labels+=(nsis-standard)
    (run_nsis_noparcel_recorded) \
      > >(tee "${build_root}/output/nsis-noparcel.log") 2>&1 &
    pids+=("$!")
    labels+=(nsis-noparcel)
    if [[ "${run_amoled_nsis}" == "1" ]]; then
      (run_nsis_amoled_recorded) \
        > >(tee "${build_root}/output/nsis-amoled.log") 2>&1 &
      pids+=("$!")
      labels+=(nsis-amoled)
    else
      skip_message "AMOLED NSIS installers" "RUN_AMOLED_NSIS"
    fi
  else
    skip_message "NSIS installers" "RUN_NSIS"
  fi

  for index in "${!pids[@]}"; do
    if wait "${pids[index]}"; then
      echo "Packaging stage ${labels[index]} completed." >&2
    else
      echo "Packaging stage ${labels[index]} failed." >&2
      packaging_status=1
    fi
  done
  return "${packaging_status}"
}

run_garmin_branch() {
  if ! run_mkgmap_recorded; then
    echo "mkgmap failed; Garmin packaging cannot start." >&2
    return 1
  fi
  run_garmin_packaging
}

run_parallel_build_branches() {
  local -a labels=()
  local -a pids=()
  local branch_status=0
  local index

  if [[ "${run_mkgmap}" == "1" || "${run_osx}" == "1" || "${run_nsis}" == "1" ]]; then
    (run_garmin_branch) \
      > >(tee "${build_root}/output/garmin-branch.log") 2>&1 &
    pids+=("$!")
    labels+=(garmin)
  fi
  if [[ "${run_mapsforge}" == "1" ]]; then
    (run_mapsforge_recorded) \
      > >(tee "${build_root}/output/mapsforge-branch.log") 2>&1 &
    pids+=("$!")
    labels+=(mapsforge)
  fi

  for index in "${!pids[@]}"; do
    if wait "${pids[index]}"; then
      echo "Build branch ${labels[index]} completed." >&2
    else
      echo "Build branch ${labels[index]} failed; waiting for healthy siblings completed." >&2
      branch_status=1
    fi
  done
  return "${branch_status}"
}

run_publish_recorded() {
  local fingerprint
  local stage
  local manifest
  local -a inputs=()

  if [[ "${run_publish}" != "1" ]]; then
    skip_message "publication" "RUN_PUBLISH"
    return
  fi

  for stage in mapsforge mkgmap osx-packages nsis-standard nsis-noparcel; do
    manifest="$(stage_manifest_file "${stage}")"
    if [[ -s "${manifest}" ]]; then
      inputs+=(--file "${stage}=${manifest}")
    fi
  done
  fingerprint="$(stage_fingerprint publication 3 \
    "${inputs[@]}" \
    --value "site-image=${SITE_IMAGE}" \
    --value "mapstyles-image=${MAPSTYLES_IMAGE}" \
    --value "release-date=${time_stamp}")"
  run_recorded_stage publication "${fingerprint}" \
    --output-tree "${build_root}/output/dist" \
    -- \
    run_publish_stage
}

main() {
  local release_status="pending"
  local housekeeping_status="pending"
  local cleanup_status

  [[ "${run_publish}" == "1" ]] || release_status="not-requested"
  [[ "${run_cleanup}" == "1" ]] || housekeeping_status="not-requested"

  echo "Running the mtk2garmin production pipeline for ${time_stamp}." >&2
  load_image_lock
  prepare_build_root
  write_run_status "${release_status}" "${housekeeping_status}"
  check_free_space
  if ! update_input_data || ! resolve_input_snapshot; then
    write_run_status "${release_status}" "${housekeeping_status}" "" snapshot
    return 1
  fi
  preflight_images
  prepare_runtime
  if ! run_conversion_recorded || ! run_parallel_build_branches; then
    write_run_status "${release_status}" "${housekeeping_status}" "" conversion
    return 1
  fi

  if [[ "${run_publish}" == "1" ]]; then
    if run_publish_recorded; then
      release_status="verified"
      write_run_status "${release_status}" "${housekeeping_status}"
    else
      release_status="failed"
      write_run_status "${release_status}" "${housekeeping_status}" "" publication
      return 1
    fi
  fi

  if [[ "${run_cleanup}" == "1" ]]; then
    if run_success_cleanup; then
      housekeeping_status="succeeded"
    else
      cleanup_status=$?
      housekeeping_status="failed"
      write_run_status \
        "${release_status}" "${housekeeping_status}" "${cleanup_status}" housekeeping
      echo "Release status is ${release_status}; housekeeping failed with exit ${cleanup_status}." >&2
      echo "The verified release will not be rebuilt because of housekeeping failure." >&2
      return 0
    fi
  fi

  write_run_status "${release_status}" "${housekeeping_status}"
  echo "mtk2garmin production pipeline completed for ${time_stamp}." >&2
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  trap finalize_run_summary EXIT
  main "$@"
fi
