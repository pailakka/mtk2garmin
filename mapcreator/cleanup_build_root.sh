#!/usr/bin/env bash
set -euo pipefail

build_root="${1:-}"

if [[ -z "${build_root}" ]]; then
  echo "cleanup: build root is required" >&2
  exit 2
fi

build_root="$(realpath -m -- "${build_root}")"
if [[ "${build_root}" == "/" ]]; then
  echo "cleanup: refusing to clean unsafe build root: ${build_root}" >&2
  exit 2
fi

echo "Cleaning build artifacts under ${build_root}; retaining output/dist" >&2

for working_dir in convertedpbf splitted; do
  rm -rf -- "${build_root:?}/${working_dir}"
  mkdir -p -- "${build_root}/${working_dir}"
done

mkdir -p -- "${build_root}/output"
find "${build_root}/output" \
  -mindepth 1 -maxdepth 1 \
  ! -name dist \
  -exec rm -rf -- {} +
