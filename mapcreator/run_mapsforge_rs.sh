#!/usr/bin/env bash
set -euo pipefail

build_root="${MTK2GARMIN_BUILD_ROOT:-/opt/mtk2garmin-build}"
publish_root="${MTK2GARMIN_PUBLISH_ROOT:-/opt/mtk2garmin-publish}"
mapsforge_rs_log="${MAPSFORGE_RS_LOG:-${build_root}/output/mapsforge-rs.log}"

input_pbf="${MTK2GARMIN_INPUT_PBF:-/convertedpbf/all_osm.osm.pbf}"
output_map="${MTK2GARMIN_OUTPUT_MAP:-/output/mtk_all_rs.map}"
bbox="${BBOX:-${MTK2GARMIN_BBOX:-59.4507573,19.0714057,70.1120744,31.6133108}}"
zoom_interval_conf="${ZOOM_INTERVAL_CONF:-7,0,7,10,8,11,12,12,13,14,14,21}"
tag_mapping="${MTK2GARMIN_TAG_MAPPING:-/mapstyles/mapsforge_peruskartta/mml_tag-mapping_tidy.xml}"
writer_type="${TYPE:-hd}"
tag_values="${TAG_VALUES:-false}"
comment="${COMMENT:-(c) NLS, Metsahallitus, Liikennevirasto, OpenStreetMap contributors 2026}"
memory_profile="${MAPSFORGE_RS_MEMORY_PROFILE:-production-high-mem}"
memory_budget_gb="${MAPSFORGE_RS_MEMORY_BUDGET_GB:-64}"
staged_store="${MAPSFORGE_RS_STAGED_STORE:-global-encoded}"
tile_payload_threads="${MAPSFORGE_RS_TILE_PAYLOAD_THREADS:-16}"
tile_payload_batch_size="${MAPSFORGE_RS_TILE_PAYLOAD_BATCH_SIZE:-1024}"
way_planner_mode="${MAPSFORGE_RS_WAY_PLANNER_MODE:-multi-interval}"

run_compose() {
  MTK2GARMIN_BUILD_ROOT="${build_root}" \
  MTK2GARMIN_PUBLISH_ROOT="${publish_root}" \
    docker compose "$@"
}

mkdir -p "${build_root}/output"
rm -f "${mapsforge_rs_log}"

run_compose up --no-start --remove-orphans mapstyles

if ! { time run_compose run --rm \
           -e MTK2GARMIN_INPUT_PBF="${input_pbf}" \
           -e MTK2GARMIN_OUTPUT_MAP="${output_map}" \
           -e BBOX="${bbox}" \
           -e ZOOM_INTERVAL_CONF="${zoom_interval_conf}" \
           -e MTK2GARMIN_TAG_MAPPING="${tag_mapping}" \
           -e TYPE="${writer_type}" \
           -e COMMENT="${comment}" \
           -e TAG_VALUES="${tag_values}" \
           -e MAPSFORGE_RS_MEMORY_PROFILE="${memory_profile}" \
           -e MAPSFORGE_RS_MEMORY_BUDGET_GB="${memory_budget_gb}" \
           -e MAPSFORGE_RS_STAGED_STORE="${staged_store}" \
           -e MAPSFORGE_RS_TILE_PAYLOAD_THREADS="${tile_payload_threads}" \
           -e MAPSFORGE_RS_TILE_PAYLOAD_BATCH_SIZE="${tile_payload_batch_size}" \
           -e MAPSFORGE_RS_WAY_PLANNER_MODE="${way_planner_mode}" \
           mapsforge-rs; } 2>&1 | tee "${mapsforge_rs_log}"; then
  exit 1
fi

python3 "$(dirname "${BASH_SOURCE[0]}")/check_mapsforge_blocks.py" \
  "${build_root}/output/$(basename "${output_map}")"

echo "mapsforge-rs log: ${mapsforge_rs_log}"
