#!/usr/bin/env bash
set -Eeuo pipefail

time_stamp="${TIME_STAMP:-$(date +%Y-%m-%d)}"
publish_root="${PUBLISH_ROOT:-/publish}"
archive_bucket="${ARCHIVE_BUCKET:-kartat-build}"
site_bucket="${SITE_BUCKET:-kartat.hylly.org}"
distribution_id="${CLOUDFRONT_DISTRIBUTION_ID:-E2F702Y6HFAYV6}"
download_base_url="${DOWNLOAD_BASE_URL:-https://kartat-dl.hylly.org}"
site_url="${SITE_URL:-https://kartat.hylly.org}"

dist_dir="/output/dist"
staging_dir="${publish_root}/.staging-${time_stamp}"
target_dir="${publish_root}/${time_stamp}"
backup_dir="${publish_root}/.previous-${time_stamp}-$$"
previous_index="$(mktemp)"
target_promoted=0
homepage_uploaded=0
committed=0

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

rollback() {
  local status=$?

  if [[ "${committed}" != "1" ]]; then
    if [[ "${homepage_uploaded}" == "1" && -s "${previous_index}" ]]; then
      aws s3 cp "${previous_index}" "s3://${site_bucket}/index.html" || true
      aws cloudfront create-invalidation \
        --distribution-id "${distribution_id}" \
        --paths / /index.html >/dev/null || true
    fi

    if [[ "${target_promoted}" == "1" ]]; then
      rm -rf -- "${target_dir}"
      if [[ -d "${backup_dir}" ]]; then
        mv -- "${backup_dir}" "${target_dir}"
      fi
    fi
  fi

  rm -rf -- "${staging_dir}"
  rm -f -- "${previous_index}"
  exit "${status}"
}
trap rollback EXIT

copy_garmin_img() {
  local named_source="$1"
  local fallback_source="$2"
  local target="$3"

  if [[ -s "${named_source}" ]]; then
    cp "${named_source}" "${target}"
  else
    cp "${fallback_source}" "${target}"
  fi
}

require_release_files() {
  local root="$1"
  local filename

  for filename in "${required_files[@]}"; do
    if [[ ! -s "${root}/${filename}" ]]; then
      echo "Required release artifact is missing or empty: ${root}/${filename}" >&2
      return 1
    fi
  done

  if grep -Eq '/v2/|index_v2|rs-ogr2osm v2|Java/GML' "${root}/site.html"; then
    echo "Generated homepage still contains a legacy pipeline reference." >&2
    return 1
  fi
  if ! grep -Fq "${download_base_url}/${time_stamp}/" "${root}/site.html"; then
    echo "Generated homepage does not contain canonical ${time_stamp} links." >&2
    return 1
  fi
}

verify_public_downloads() {
  local filename

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
      "${download_base_url}/${time_stamp}/${filename}" \
      >/dev/null
  done
}

poll_homepage() {
  local attempt
  local page

  for attempt in $(seq 1 30); do
    if page="$(curl --fail --silent --show-error "${site_url}/index.html")" &&
       grep -Fq "${download_base_url}/${time_stamp}/" <<<"${page}" &&
       ! grep -Eq '/v2/|index_v2|rs-ogr2osm v2|Java/GML' <<<"${page}"; then
      return 0
    fi
    sleep 2
  done

  echo "Published homepage did not converge to release ${time_stamp}." >&2
  return 1
}

rm -rf -- "${dist_dir}" "${staging_dir}" "${backup_dir}"
mkdir -p "${dist_dir}" "${staging_dir}"

copy_garmin_img \
  /output/mtkgarmin/mtk_suomi.img \
  /output/mtkgarmin/gmapsupp.img \
  "${dist_dir}/mtk_suomi.img"
copy_garmin_img \
  /output/mtkgarmin_noparcel/mtk_suomi_noparcel.img \
  /output/mtkgarmin_noparcel/gmapsupp.img \
  "${dist_dir}/mtk_suomi_noparcel.img"
copy_garmin_img \
  /output/mtkgarmin_amoled/mtk_suomi_amoled.img \
  /output/mtkgarmin_amoled/gmapsupp.img \
  "${dist_dir}/mtk_suomi_amoled.img"
copy_garmin_img \
  /output/mtkgarmin_amoled_noparcel/mtk_suomi_amoled_noparcel.img \
  /output/mtkgarmin_amoled_noparcel/gmapsupp.img \
  "${dist_dir}/mtk_suomi_amoled_noparcel.img"

cp /output/mtkgarmin/MTKSuomi.exe "${dist_dir}/mtk_suomi.exe"
cp /output/mtkgarmin_noparcel/MTKSuomi.exe "${dist_dir}/mtk_suomi_noparcel.exe"
cp /output/mtk_suomi_noparcel_osx.zip "${dist_dir}/mtk_suomi_noparcel_osx.zip"
cp /output/mtk_suomi_osx.zip "${dist_dir}/mtk_suomi_osx.zip"
cp /output/mtk_all.map "${dist_dir}/mtk_suomi.map"
cp /mapstyles/peruskartta.zip "${dist_dir}/peruskartta.zip"
cp /mapstyles/tiekartta.zip "${dist_dir}/tiekartta.zip"

python3 generate_site.py "${time_stamp}"
(
  cd "${dist_dir}"
  7z a -tzip mtk_suomi.cpkg \
    mtk_suomi.map \
    peruskartta.zip \
    mapdetails.json \
    >/dev/null
)

require_release_files "${dist_dir}"
rsync -a --delete "${dist_dir}/" "${staging_dir}/"
require_release_files "${staging_dir}"

aws s3 sync \
  --delete \
  "${staging_dir}/" \
  "s3://${archive_bucket}/${time_stamp}/"

if [[ -d "${target_dir}" ]]; then
  mv -- "${target_dir}" "${backup_dir}"
fi
mv -- "${staging_dir}" "${target_dir}"
target_promoted=1

verify_public_downloads
aws s3 cp "s3://${site_bucket}/index.html" "${previous_index}"
aws s3 cp "${target_dir}/site.html" "s3://${site_bucket}/index.html"
homepage_uploaded=1

invalidation_paths=(/ /index.html)
for optional_file in robots.txt sitemap.xml; do
  if aws s3 cp "${target_dir}/${optional_file}" "s3://${site_bucket}/${optional_file}"; then
    invalidation_paths+=("/${optional_file}")
  else
    echo "Warning: optional ${optional_file} publication failed; continuing." >&2
  fi
done

aws cloudfront create-invalidation \
  --distribution-id "${distribution_id}" \
  --paths "${invalidation_paths[@]}" \
  >/dev/null

poll_homepage
verify_public_downloads

find "${publish_root}" \
  -mindepth 1 \
  -maxdepth 1 \
  -type d \
  -regextype posix-extended \
  -regex '.*/[0-9]{4}-[0-9]{2}-[0-9]{2}' \
  ! -name "${time_stamp}" \
  -exec rm -rf -- {} +

rm -rf -- "${backup_dir}"
committed=1
trap - EXIT
rm -f -- "${previous_index}"
echo "Published and verified canonical release ${time_stamp}."
