#!/usr/bin/env bash
set -euo pipefail

build_root="${1:-}"
cleanup_image="${2:-}"

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

cleanup_contents() {
  local root="$1"

  rm -rf -- "${root:?}/convertedpbf" "${root}/splitted"
  mkdir -p -- "${root}/output"
  find "${root}/output" \
    -mindepth 1 -maxdepth 1 \
    ! -name dist \
    -exec rm -rf -- {} +
}

if [[ -n "${cleanup_image}" ]]; then
  command -v docker >/dev/null 2>&1 || {
    echo "cleanup: docker is required when a cleanup image is supplied" >&2
    exit 2
  }
  docker run --rm \
    --mount "type=bind,src=${build_root},dst=/build" \
    --entrypoint /bin/bash \
    "${cleanup_image}" \
    -euo pipefail -c '
      rm -rf -- /build/convertedpbf /build/splitted
      mkdir -p -- /build/output
      find /build/output \
        -mindepth 1 -maxdepth 1 \
        ! -name dist \
        -exec rm -rf -- {} +
    '
else
  cleanup_contents "${build_root}"
fi

mkdir -p -- "${build_root}/convertedpbf" "${build_root}/splitted"
