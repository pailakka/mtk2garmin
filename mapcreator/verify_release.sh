#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
publish_root="${MTK2GARMIN_PUBLISH_ROOT:-/opt/mtk2garmin-publish}"
download_base_url="${DOWNLOAD_BASE_URL:-https://kartat-dl.hylly.org}"
site_url="${SITE_URL:-https://kartat.hylly.org}"

usage() {
  cat >&2 <<'EOF'
Usage: verify_release.sh --staged YYYY-MM-DD
       verify_release.sh --live YYYY-MM-DD
EOF
}

if [[ $# -ne 2 || ( "$1" != "--staged" && "$1" != "--live" ) ]]; then
  usage
  exit 2
fi

mode="$1"
release_date="$2"
if [[ ! "${release_date}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "Release date must use YYYY-MM-DD." >&2
  exit 2
fi

release_root="${publish_root}/${release_date}"
required_files=(
  mapdetails.json
  mtk_suomi.cpkg
  mtk_suomi.exe
  mtk_suomi.img
  mtk_suomi.map
  mtk_suomi_amoled.img
  mtk_suomi_amoled_noparcel.img
  mtk_suomi_locus.xml
  mtk_suomi_noparcel.exe
  mtk_suomi_noparcel.img
  mtk_suomi_noparcel_osx.zip
  mtk_suomi_osx.zip
  peruskartta.zip
  peruskartta_locus.xml
  site.html
  tiekartta.zip
  tiekartta_locus.xml
)

for filename in "${required_files[@]}"; do
  if [[ ! -s "${release_root}/${filename}" ]]; then
    echo "Missing or empty release artifact: ${release_root}/${filename}" >&2
    exit 1
  fi
done

if grep -Eq '/v2/|index_v2|rs-ogr2osm v2|Java/GML' "${release_root}/site.html"; then
  echo "Release homepage contains a legacy pipeline reference." >&2
  exit 1
fi
grep -Fq "${download_base_url}/${release_date}/" "${release_root}/site.html"

python3 "${repo_root}/mkgmap-converter/check_img_subfiles.py" \
  --max-subfile-mib "${GARMIN_MAX_SUBFILE_MIB:-4}" \
  --max-img-mib "${GARMIN_MAX_IMG_MIB:-1900}" \
  --max-tiles "${GARMIN_MAX_TILES:-1000}" \
  --required-typ-name perus.typ \
  "${release_root}/mtk_suomi.img" \
  "${release_root}/mtk_suomi_noparcel.img" \
  "${release_root}/mtk_suomi_amoled.img" \
  "${release_root}/mtk_suomi_amoled_noparcel.img"

python3 "${script_dir}/check_mapsforge_blocks.py" \
  "${release_root}/mtk_suomi.map"

if command -v 7z >/dev/null 2>&1; then
  for installer in mtk_suomi.exe mtk_suomi_noparcel.exe; do
    if ! 7z l "${release_root}/${installer}" |
      grep -Ei '(^|[[:space:]\\/])perus\.typ([[:space:]]|$)' >/dev/null; then
      echo "${installer} does not contain perus.typ." >&2
      exit 1
    fi
  done
fi

if [[ "${mode}" == "--live" ]]; then
  page="$(curl --fail --silent --show-error --location "${site_url}/index.html")"
  grep -Fq "${download_base_url}/${release_date}/" <<<"${page}"
  if grep -Eq '/v2/|index_v2|rs-ogr2osm v2|Java/GML' <<<"${page}"; then
    echo "Live homepage contains a legacy pipeline reference." >&2
    exit 1
  fi

  for filename in "${required_files[@]}"; do
    [[ "${filename}" == "site.html" ]] && continue
    curl \
      --fail \
      --silent \
      --show-error \
      --location \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 2 \
      --head \
      "${download_base_url}/${release_date}/${filename}" \
      >/dev/null
  done
fi

echo "Release verification passed: mode=${mode} date=${release_date}"
