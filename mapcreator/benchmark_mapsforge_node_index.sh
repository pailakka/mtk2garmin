#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
build_root="${MTK2GARMIN_BUILD_ROOT:-/opt/mtk2garmin-build}"
image_lock="${MTK2GARMIN_IMAGE_LOCK:-${script_dir}/images.lock.env}"
candidate_image="${MAPSFORGE_RS_CANDIDATE_IMAGE:-}"
benchmark_id="${MAPSFORGE_BENCHMARK_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
benchmark_relative="benchmarks/node-index-${benchmark_id}"
benchmark_root="${build_root}/output/${benchmark_relative}"
baseline_map="${MAPSFORGE_BENCHMARK_BASELINE_MAP:-${build_root}/output/mtk_all.map}"
baseline_log="${MAPSFORGE_BENCHMARK_BASELINE_LOG:-${build_root}/output/mapsforge-rs.log}"
input_pbf="${MAPSFORGE_BENCHMARK_INPUT_PBF:-${build_root}/convertedpbf/all_osm.osm.pbf}"
tag_mapping="${MAPSFORGE_BENCHMARK_TAG_MAPPING:-${script_dir}/../mapstyles/mapsforge_peruskartta/mml_tag-mapping_tidy.xml}"
theme="${MAPSFORGE_BENCHMARK_THEME:-${script_dir}/../mapstyles/mapsforge_peruskartta/Peruskartta.xml}"
creation_date_millis="${MAPSFORGE_BENCHMARK_CREATION_DATE_MILLIS:-1785196800000}"
reader_compare="${MAPSFORGE_READER_COMPARE:-${script_dir}/../../rs-mapsforge-writer/scripts/compare-map-files.sh}"
render_compare="${MAPSFORGE_RENDER_COMPARE:-${script_dir}/../../rs-mapsforge-writer/scripts/compare-rendered-tiles.sh}"
reader_repo="${MAPSFORGE_READER_REPO:-${script_dir}/../../mapsforge}"
reader_java_home="${MAPSFORGE_READER_JAVA_HOME:-}"
if [[ -z "${reader_java_home}" && -x /opt/rs-mapsforge-writer/jdk-21/bin/java ]]; then
  reader_java_home=/opt/rs-mapsforge-writer/jdk-21
fi

if [[ $# -ne 1 || "$1" != "--full" ]]; then
  echo "Usage: benchmark_mapsforge_node_index.sh --full" >&2
  echo "This runs the disk-65536 and memory production candidates." >&2
  exit 2
fi

for required_file in \
  "${input_pbf}" \
  "${baseline_map}" \
  "${baseline_log}" \
  "${image_lock}" \
  "${tag_mapping}" \
  "${theme}" \
  "${reader_compare}" \
  "${render_compare}"; do
  if [[ ! -s "${required_file}" ]]; then
    echo "Required benchmark input is missing or empty: ${required_file}" >&2
    exit 1
  fi
done
if [[ "${candidate_image}" != *@sha256:* ]]; then
  echo "Set MAPSFORGE_RS_CANDIDATE_IMAGE to an immutable digest reference." >&2
  exit 1
fi
if [[ ! -x "${reader_repo}/gradlew" ]]; then
  echo "Mapsforge reader checkout is missing gradlew: ${reader_repo}" >&2
  exit 1
fi
reader_commit="$(git -C "${reader_repo}" rev-parse HEAD)"
if [[ -n "${MAPSFORGE_READER_COMMIT:-}" && "${reader_commit}" != "${MAPSFORGE_READER_COMMIT}" ]]; then
  echo "Mapsforge reader commit mismatch: expected=${MAPSFORGE_READER_COMMIT} actual=${reader_commit}" >&2
  exit 1
fi
reader_env=(
  "MAPSFORGE_REPO_DIR=${reader_repo}"
  "MAPSFORGE_READER_COMMIT=${reader_commit}"
)
if [[ -n "${reader_java_home}" ]]; then
  if [[ ! -x "${reader_java_home}/bin/java" ]]; then
    echo "Invalid Mapsforge reader Java home: ${reader_java_home}" >&2
    exit 1
  fi
  reader_env+=("MAPSFORGE_READER_JAVA_HOME=${reader_java_home}")
  reader_java="${reader_java_home}/bin/java"
else
  reader_java="$(command -v java)"
fi
reader_java_version="$("${reader_java}" -version 2>&1 | head -1)"

mkdir -p "${benchmark_root}"
cp --reflink=auto "${baseline_map}" "${benchmark_root}/baseline.map"
cp "${baseline_log}" "${benchmark_root}/baseline.log"

{
  echo "schema=2"
  echo "benchmark_id=${benchmark_id}"
  echo "candidate_image=${candidate_image}"
  echo "creation_date_millis=${creation_date_millis}"
  echo "input_pbf=${input_pbf}"
  echo "input_pbf_sha256=$(sha256sum "${input_pbf}" | awk '{print $1}')"
  echo "tag_mapping=${tag_mapping}"
  echo "tag_mapping_sha256=$(sha256sum "${tag_mapping}" | awk '{print $1}')"
  echo "theme=${theme}"
  echo "theme_sha256=$(sha256sum "${theme}" | awk '{print $1}')"
  echo "mapsforge_reader_repo=${reader_repo}"
  echo "mapsforge_reader_commit=${reader_commit}"
  echo "mapsforge_reader_java=${reader_java_version}"
  echo "baseline_map_sha256=$(sha256sum "${benchmark_root}/baseline.map" | awk '{print $1}')"
  echo "baseline_log_sha256=$(sha256sum "${benchmark_root}/baseline.log" | awk '{print $1}')"
  echo "image_lock_sha256=$(sha256sum "${image_lock}" | awk '{print $1}')"
} >"${benchmark_root}/manifest.txt"

candidates=(
  disk:65536
  memory:0
)

for candidate in "${candidates[@]}"; do
  node_index_type="${candidate%%:*}"
  cache_blocks="${candidate##*:}"
  name="${node_index_type}-${cache_blocks}"
  if [[ "${node_index_type}" == "memory" ]]; then
    name=memory
  fi
  log_path="${benchmark_root}/${name}.log"
  output_path="/output/${benchmark_relative}/${name}.map"
  host_output_path="${benchmark_root}/${name}.map"
  echo "Running Mapsforge benchmark candidate ${name}." >&2
  if ! env \
    MTK2GARMIN_BUILD_ROOT="${build_root}" \
    MTK2GARMIN_IMAGE_LOCK="${image_lock}" \
    MTK2GARMIN_OUTPUT_MAP="${output_path}" \
    MAPSFORGE_RS_LOG="${log_path}" \
    MAPSFORGE_RS_CANDIDATE_IMAGE="${candidate_image}" \
    MAPSFORGE_RS_CREATION_DATE_MILLIS="${creation_date_millis}" \
    MAPSFORGE_RS_MEMORY_PROFILE=production-high-mem \
    MAPSFORGE_RS_MEMORY_BUDGET_GB=32 \
    MAPSFORGE_RS_STAGED_STORE=global-encoded \
    MAPSFORGE_RS_TILE_PAYLOAD_THREADS=8 \
    MAPSFORGE_RS_TILE_PAYLOAD_BATCH_SIZE=1024 \
    MAPSFORGE_RS_WAY_PLANNER_MODE=multi-interval \
    MAPSFORGE_RS_NODE_INDEX_TYPE="${node_index_type}" \
    MAPSFORGE_RS_NODE_INDEX_CACHE_BLOCKS="${cache_blocks}" \
    "${script_dir}/run_mapsforge_rs.sh"; then
    echo "Benchmark candidate ${name} failed; continuing." >&2
    continue
  fi
  touch "${benchmark_root}/${name}.blocks.ok"

  reader_ok=true
  for zoom in 7 10 12 14; do
    reader_log="${benchmark_root}/${name}.reader-z${zoom}.log"
    if ! env "${reader_env[@]}" \
      LEFT_LABEL=baseline RIGHT_LABEL="${name}" MAX_TILE_DELTAS=20 \
      "${reader_compare}" \
      "${benchmark_root}/baseline.map" \
      "${host_output_path}" \
      "${zoom}" >"${reader_log}" 2>&1; then
      reader_ok=false
      break
    fi
    if ! grep -qx "delta_pois=0" "${reader_log}" ||
      ! grep -qx "delta_ways=0" "${reader_log}" ||
      ! grep -qx "differing_poi_tiles=0" "${reader_log}" ||
      ! grep -qx "differing_way_tiles=0" "${reader_log}"; then
      reader_ok=false
      break
    fi
  done
  if [[ "${reader_ok}" == "true" ]]; then
    touch "${benchmark_root}/${name}.reader.ok"
  fi

  render_log="${benchmark_root}/${name}.render.log"
  if env "${reader_env[@]}" \
    LEFT_LABEL=baseline RIGHT_LABEL="${name}" SCAN_ZOOM=14 SAMPLE_TILES=8 \
    MAX_DIFFERING_PIXELS=0 DOWNLOAD_RENDER_DEPS=true \
    "${render_compare}" \
    "${benchmark_root}/baseline.map" \
    "${host_output_path}" \
    "${theme}" >"${render_log}" 2>&1; then
    touch "${benchmark_root}/${name}.render.ok"
  fi
done

python3 "${script_dir}/summarize_mapsforge_benchmark.py" \
  --root "${benchmark_root}" \
  --baseline baseline \
  --max-rss-gb 28 \
  --max-total-minutes 60 \
  --max-pass4-minutes 40 \
  --max-global-load-seconds 90
