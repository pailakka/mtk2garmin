#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "${fixture}"' EXIT

export MTK2GARMIN_BUILD_ROOT="${fixture}/build"
export MTK2GARMIN_PUBLISH_ROOT="${fixture}/publish"
# shellcheck disable=SC1091
source "${script_dir}/convert_docker.sh"

mkdir -p "${build_root}/output"
run_mkgmap=1
run_mapsforge=1
run_osx=0
run_nsis=0

run_mkgmap_recorded() {
  touch "${fixture}/garmin.started"
  for _ in $(seq 1 100); do
    if [[ -e "${fixture}/mapsforge.started" ]]; then
      touch "${fixture}/branches.overlapped"
      break
    fi
    sleep 0.01
  done
}

run_mapsforge_recorded() {
  touch "${fixture}/mapsforge.started"
  sleep 0.05
  touch "${fixture}/mapsforge.finished"
}

run_parallel_build_branches
test -e "${fixture}/branches.overlapped"
test -e "${fixture}/mapsforge.finished"

run_mkgmap_recorded() {
  return 17
}

run_mapsforge_recorded() {
  sleep 0.1
  touch "${fixture}/healthy-sibling.finished"
}

set +e
run_parallel_build_branches
branch_status=$?
set -e
test "${branch_status}" -eq 1
test -e "${fixture}/healthy-sibling.finished"

run_conversion_stage() {
  return 7
}
require_merged_pbf() {
  touch "${fixture}/stale-output-was-accepted"
}
set +e
run_conversion_and_validate
conversion_status=$?
set -e
test "${conversion_status}" -eq 1
test ! -e "${fixture}/stale-output-was-accepted"

echo "pipeline orchestration tests passed"
