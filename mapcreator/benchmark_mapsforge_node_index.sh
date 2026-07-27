#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
build_root="${MTK2GARMIN_BUILD_ROOT:-/opt/mtk2garmin-build}"
image_lock="${MTK2GARMIN_IMAGE_LOCK:-${script_dir}/images.lock.env}"
benchmark_id="${MAPSFORGE_BENCHMARK_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
benchmark_relative="benchmarks/node-index-${benchmark_id}"
benchmark_root="${build_root}/output/${benchmark_relative}"

if [[ $# -ne 1 || "$1" != "--full" ]]; then
  echo "Usage: benchmark_mapsforge_node_index.sh --full" >&2
  echo "This runs four production-sized Mapsforge builds." >&2
  exit 2
fi

if [[ ! -s "${build_root}/convertedpbf/all_osm.osm.pbf" ]]; then
  echo "Production merged PBF is missing." >&2
  exit 1
fi
if [[ ! -r "${image_lock}" ]]; then
  echo "Image lock is not readable: ${image_lock}" >&2
  exit 1
fi

mkdir -p "${benchmark_root}"
candidates=(
  disk:4096
  disk:16384
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
  echo "Running Mapsforge benchmark candidate ${name}." >&2
  if ! env \
    MTK2GARMIN_BUILD_ROOT="${build_root}" \
    MTK2GARMIN_IMAGE_LOCK="${image_lock}" \
    MTK2GARMIN_OUTPUT_MAP="${output_path}" \
    MAPSFORGE_RS_LOG="${log_path}" \
    MAPSFORGE_RS_MEMORY_PROFILE=production-high-mem \
    MAPSFORGE_RS_MEMORY_BUDGET_GB=32 \
    MAPSFORGE_RS_TILE_PAYLOAD_THREADS=8 \
    MAPSFORGE_RS_NODE_INDEX_TYPE="${node_index_type}" \
    MAPSFORGE_RS_NODE_INDEX_CACHE_BLOCKS="${cache_blocks}" \
    "${script_dir}/run_mapsforge_rs.sh"; then
    echo "Benchmark candidate ${name} failed; continuing." >&2
  fi
done

python3 "${script_dir}/summarize_mapsforge_benchmark.py" \
  --root "${benchmark_root}" \
  --max-rss-gb 32 \
  --minimum-improvement-percent 15
