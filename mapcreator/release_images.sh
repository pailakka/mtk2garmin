#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
rs_ogr2osm_root="${RS_OGR2OSM_ROOT:-/home/teemu/rs-ogr2osm}"
mapsforge_root="${MAPSFORGE_RS_ROOT:-/home/teemu/rs-mapsforge-writer}"
lock_file="${MTK2GARMIN_IMAGE_LOCK:-${script_dir}/images.lock.env}"
release_id="${IMAGE_RELEASE_ID:-$(date -u +%Y%m%d%H%M%S)-$(git -C "${repo_root}" rev-parse --short=12 HEAD)}"
cd "${script_dir}"

owned_services=(
  base
  mml-ogr-client
  rs-ogr2osm
  mapsforge-rs
  osxconverter
  additional-data
  mkgmap
  mapstyles
  site
)

declare -A variable_for_service=(
  [base]=OSMIUM_IMAGE
  [mml-ogr-client]=MML_OGR_IMAGE
  [rs-ogr2osm]=OGR2OSM_IMAGE
  [mapsforge-rs]=MAPSFORGE_RS_IMAGE
  [osxconverter]=OSXCONVERTER_IMAGE
  [additional-data]=ADDITIONAL_DATA_IMAGE
  [mkgmap]=MKGMAP_IMAGE
  [mapstyles]=MAPSTYLES_IMAGE
  [site]=SITE_IMAGE
)

declare -A repository_for_variable=(
  [MML_OGR_IMAGE]=localhost:5000/mtk2garmin-mml-ogr-client
  [MAPSFORGE_RS_IMAGE]=teemupel/mapsforge-rs
  [OGR2OSM_IMAGE]=localhost:5000/rs-ogr2osm
  [OSMIUM_IMAGE]=teemupel/mtk2garmin-ubuntugis-base
  [OSXCONVERTER_IMAGE]=localhost:5000/osxconverter
  [ADDITIONAL_DATA_IMAGE]=localhost:5000/mtk2garmin-additional-data
  [MKGMAP_IMAGE]=teemupel/mkgmap
  [MAPSTYLES_IMAGE]=teemupel/mtk2garmin-mapstyles
  [NSIS_IMAGE]=wheatstalk/makensis
  [SITE_IMAGE]=localhost:5000/mtk2garmin-site
)

usage() {
  cat >&2 <<'EOF'
Usage: release_images.sh --all
       release_images.sh SERVICE [SERVICE...]

Services:
  base mml-ogr-client rs-ogr2osm mapsforge-rs osxconverter
  additional-data mkgmap mapstyles site
EOF
}

contains_service() {
  local wanted="$1"
  local item
  for item in "${selected_services[@]}"; do
    [[ "${item}" == "${wanted}" ]] && return 0
  done
  return 1
}

validate_service() {
  local wanted="$1"
  local item
  for item in "${owned_services[@]}"; do
    [[ "${item}" == "${wanted}" ]] && return 0
  done
  echo "Unknown image service: ${wanted}" >&2
  usage
  return 2
}

require_clean_source() {
  local root="$1"
  local label="$2"
  if [[ -n "$(git -C "${root}" status --short --untracked-files=no)" ]]; then
    echo "Refusing to release ${label} from a dirty tracked worktree: ${root}" >&2
    return 1
  fi
}

resolve_digest() {
  local reference="$1"
  local repository="$2"
  local digest_reference

  docker pull "${reference}" >/dev/null
  digest_reference="$(
    docker image inspect "${reference}" \
      --format '{{range .RepoDigests}}{{println .}}{{end}}' |
      awk -v prefix="${repository}@sha256:" 'index($0, prefix) == 1 {print; exit}'
  )"
  if [[ -z "${digest_reference}" ]]; then
    echo "Could not resolve registry digest for ${reference}." >&2
    return 1
  fi
  printf '%s\n' "${digest_reference}"
}

if [[ $# -eq 0 ]]; then
  usage
  exit 2
fi

if [[ "$1" == "--all" ]]; then
  [[ $# -eq 1 ]] || { usage; exit 2; }
  selected_services=("${owned_services[@]}")
else
  selected_services=("$@")
fi

for service in "${selected_services[@]}"; do
  validate_service "${service}"
done

require_clean_source "${repo_root}" mtk2garmin
if contains_service rs-ogr2osm; then
  require_clean_source "${rs_ogr2osm_root}" rs-ogr2osm
fi
if contains_service mapsforge-rs; then
  require_clean_source "${mapsforge_root}" rs-mapsforge-writer
fi

work_dir="$(mktemp -d)"
candidate_env="${work_dir}/candidate.env"
next_lock="${work_dir}/images.lock.env"
trap 'rm -rf -- "${work_dir}"' EXIT

declare -A current_reference=()
if [[ -r "${lock_file}" ]]; then
  while IFS='=' read -r key value; do
    [[ -n "${key}" && "${key}" != \#* ]] || continue
    current_reference["${key}"]="${value}"
  done < "${lock_file}"
fi

for variable in "${!repository_for_variable[@]}"; do
  repository="${repository_for_variable[${variable}]}"
  current_reference["${variable}"]="${current_reference[${variable}]:-${repository}:latest}"
done

for service in "${selected_services[@]}"; do
  variable="${variable_for_service[${service}]}"
  repository="${repository_for_variable[${variable}]}"
  current_reference["${variable}"]="${repository}:${release_id}"
done

for variable in \
  MML_OGR_IMAGE MAPSFORGE_RS_IMAGE OGR2OSM_IMAGE OSMIUM_IMAGE \
  OSXCONVERTER_IMAGE ADDITIONAL_DATA_IMAGE MKGMAP_IMAGE MAPSTYLES_IMAGE \
  NSIS_IMAGE SITE_IMAGE; do
  printf '%s=%s\n' "${variable}" "${current_reference[${variable}]}" >> "${candidate_env}"
done

if contains_service base; then
  base_repository="${repository_for_variable[OSMIUM_IMAGE]}"
  base_candidate="${current_reference[OSMIUM_IMAGE]}"
  docker pull ghcr.io/osgeo/gdal:ubuntu-full-3.13.1
  docker build \
    --build-arg GDAL_BASE_IMAGE=ghcr.io/osgeo/gdal:ubuntu-full-3.13.1 \
    --tag "${base_candidate}" \
    -f "${script_dir}/ubuntugis-base/Dockerfile" \
    "${script_dir}/ubuntugis-base"
  docker push "${base_candidate}"
  docker tag "${base_candidate}" "${base_repository}:latest"
  docker push "${base_repository}:latest"
fi

if contains_service rs-ogr2osm; then
  docker build \
    --build-arg GDAL_BASE_IMAGE=ghcr.io/osgeo/gdal:ubuntu-full-3.12.4 \
    --tag "${current_reference[OGR2OSM_IMAGE]}" \
    "${rs_ogr2osm_root}"
  docker push "${current_reference[OGR2OSM_IMAGE]}"
fi

for service in \
  mml-ogr-client mapsforge-rs osxconverter additional-data mkgmap mapstyles site; do
  if contains_service "${service}"; then
    MTK2GARMIN_BUILD_ROOT=/opt/mtk2garmin-build \
    MTK2GARMIN_PUBLISH_ROOT=/opt/mtk2garmin-publish \
      docker compose --env-file "${candidate_env}" build "${service}"
    MTK2GARMIN_BUILD_ROOT=/opt/mtk2garmin-build \
    MTK2GARMIN_PUBLISH_ROOT=/opt/mtk2garmin-publish \
      docker compose --env-file "${candidate_env}" push "${service}"
  fi
done

for variable in \
  MML_OGR_IMAGE MAPSFORGE_RS_IMAGE OGR2OSM_IMAGE OSMIUM_IMAGE \
  OSXCONVERTER_IMAGE ADDITIONAL_DATA_IMAGE MKGMAP_IMAGE MAPSTYLES_IMAGE \
  NSIS_IMAGE SITE_IMAGE; do
  repository="${repository_for_variable[${variable}]}"
  digest_reference="$(resolve_digest "${current_reference[${variable}]}" "${repository}")"
  printf '%s=%s\n' "${variable}" "${digest_reference}" >> "${next_lock}"
done

set -a
# shellcheck disable=SC1090
source "${next_lock}"
set +a

docker run --rm --entrypoint osmium "${OSMIUM_IMAGE}" --version >/dev/null
docker run --rm "${OGR2OSM_IMAGE}" --help >/dev/null
docker run --rm "${ADDITIONAL_DATA_IMAGE}" --help >/dev/null
docker run --rm "${MKGMAP_IMAGE}" ./run_mkgmap.sh --preflight >/dev/null
docker run --rm --entrypoint python3 "${SITE_IMAGE}" \
  -m py_compile /opt/mtkgarmin-site/generate_site.py
docker run --rm --entrypoint python3 "${SITE_IMAGE}" \
  -m py_compile \
    /opt/mtkgarmin-site/generate_artifact_manifest.py \
    /opt/mtkgarmin-site/verify_artifact_manifest.py

MTK2GARMIN_BUILD_ROOT=/opt/mtk2garmin-build \
MTK2GARMIN_PUBLISH_ROOT=/opt/mtk2garmin-publish \
  docker compose --env-file "${next_lock}" config --quiet

mv -- "${next_lock}" "${lock_file}"
echo "Released images and updated ${lock_file}."
