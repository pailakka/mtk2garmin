#!/usr/bin/env bash
set -Eeuo pipefail

target_dir="${ADDITIONAL_DATA_DIR:-/additional-data}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'EOF'
Usage: get_additional_data.sh [--validate-only]

Refreshes the production Finland OSM extract and Traficom bathymetry data.
Downloads are validated in a staging directory before replacing current data.
EOF
}

require_file() {
  local path="$1"
  if [[ ! -s "${path}" ]]; then
    echo "Missing or empty additional-data file: ${path}" >&2
    return 1
  fi
}

validate_dataset() {
  local root="$1"

  require_file "${root}/grid.zip"
  require_file "${root}/finland-latest.osm.pbf"
  require_file "${root}/syvyyskayra_v.shp"
  require_file "${root}/syvyyskayra_v.dbf"
  require_file "${root}/syvyyskayra_v.shx"
  require_file "${root}/syvyyspiste_p.shp"
  require_file "${root}/syvyyspiste_p.dbf"
  require_file "${root}/syvyyspiste_p.shx"

  osmium fileinfo "${root}/finland-latest.osm.pbf" >/dev/null
  ogrinfo -ro -so "${root}/syvyyskayra_v.shp" syvyyskayra_v >/dev/null
  ogrinfo -ro -so "${root}/syvyyspiste_p.shp" syvyyspiste_p >/dev/null
}

case "${1:-}" in
  "")
    ;;
  --validate-only)
    validate_dataset "${target_dir}"
    echo "Additional data validation passed: ${target_dir}"
    exit 0
    ;;
  --help | -h)
    usage
    exit 0
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac

mkdir -p "${target_dir}"
staging_dir="$(mktemp -d "${target_dir}/.refresh.XXXXXX")"
trap 'rm -rf -- "${staging_dir}"' EXIT

cp "${script_dir}/grid.zip" "${staging_dir}/grid.zip"

curl \
  --fail \
  --location \
  --retry 5 \
  --retry-all-errors \
  --retry-delay 30 \
  --output "${staging_dir}/finland-latest.osm.pbf" \
  "https://download.geofabrik.de/europe/finland-latest.osm.pbf"

ogr2ogr \
  --config OGR_WFS_PAGE_SIZE 10000 \
  -nln syvyyskayra_v \
  -f "ESRI Shapefile" \
  "${staging_dir}/syvyyskayra_v.shp" \
  "WFS:https://julkinen.traficom.fi/inspirepalvelu/rajoitettu/wfs?typeName=rajoitettu:DepthContour_L"

ogr2ogr \
  --config OGR_WFS_PAGE_SIZE 10000 \
  -nln syvyyspiste_p \
  -f "ESRI Shapefile" \
  "${staging_dir}/syvyyspiste_p.shp" \
  "WFS:https://julkinen.traficom.fi/inspirepalvelu/rajoitettu/wfs?typeName=rajoitettu:Sounding_P"

validate_dataset "${staging_dir}"

find "${target_dir}" -maxdepth 1 -type f \
  \( \
    -name 'grid.zip' -o \
    -name 'finland-latest.osm.pbf' -o \
    -name 'syvyyskayra_v.*' -o \
    -name 'syvyyspiste_p.*' \
  \) \
  -delete

find "${staging_dir}" -mindepth 1 -maxdepth 1 -type f -exec mv -- {} "${target_dir}/" \;
validate_dataset "${target_dir}"
echo "Additional data refresh completed: ${target_dir}"
