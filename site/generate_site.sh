#!/bin/bash
set -euxo pipefail

date
rm -rf /output/dist
mkdir -p /output/dist

time_stamp="${TIME_STAMP:-$(date +%Y-%m-%d)}"
download_prefix="${DOWNLOAD_PREFIX:-${time_stamp}}"
publish_prefix="${PUBLISH_PREFIX:-new-${time_stamp}}"
index_object="${INDEX_OBJECT:-index.html}"
legacy_index_object="${LEGACY_INDEX_OBJECT-index_old.html}"

copy_garmin_img() {
  local named_source="$1"
  local legacy_source="$2"
  local target="$3"

  if [[ -f "${named_source}" ]]; then
    cp "${named_source}" "${target}"
  else
    cp "${legacy_source}" "${target}"
  fi
}

copy_garmin_img /output/mtkgarmin/mtk_suomi.img /output/mtkgarmin/gmapsupp.img /output/dist/mtk_suomi.img
copy_garmin_img /output/mtkgarmin_noparcel/mtk_suomi_noparcel.img /output/mtkgarmin_noparcel/gmapsupp.img /output/dist/mtk_suomi_noparcel.img

copy_garmin_img /output/mtkgarmin_amoled/mtk_suomi_amoled.img /output/mtkgarmin_amoled/gmapsupp.img /output/dist/mtk_suomi_amoled.img
copy_garmin_img /output/mtkgarmin_amoled_noparcel/mtk_suomi_amoled_noparcel.img /output/mtkgarmin_amoled_noparcel/gmapsupp.img /output/dist/mtk_suomi_amoled_noparcel.img

cp /output/mtkgarmin/MTKSuomi.exe /output/dist/mtk_suomi.exe
cp /output/mtkgarmin_noparcel/MTKSuomi.exe /output/dist/mtk_suomi_noparcel.exe

cp /output/mtk_suomi_noparcel_osx.zip /output/dist/mtk_suomi_noparcel_osx.zip
cp /output/mtk_suomi_osx.zip /output/dist/mtk_suomi_osx.zip
cp /output/mtk_all.map /output/dist/mtk_suomi.map

cp /mapstyles/peruskartta.zip /output/dist/peruskartta.zip
cp /mapstyles/tiekartta.zip /output/dist/tiekartta.zip

python3 generate_site.py "${time_stamp}"

7z a -tzip /output/dist/mtk_suomi.cpkg /output/dist/mtk_suomi.map /output/dist/peruskartta.zip /output/dist/mapdetails.json
mkdir -p "/publish/${download_prefix}"

rsync -avP "/output/dist/" "/publish/${download_prefix}/"
aws s3 sync "/publish/${download_prefix}" "s3://kartat-build/${publish_prefix}"

if [[ -n "${legacy_index_object}" ]]; then
  aws s3 cp "/publish/${download_prefix}/site.html" "s3://kartat.hylly.org/${legacy_index_object}"
fi
aws s3 cp "/publish/${download_prefix}/site2.html" "s3://kartat.hylly.org/${index_object}"

invalidation_paths=("/${index_object}")
if [[ -n "${legacy_index_object}" ]]; then
  invalidation_paths+=("/${legacy_index_object}")
fi

publish_optional_live_object() {
  local filename="$1"

  if aws s3 cp "/publish/${download_prefix}/${filename}" "s3://kartat.hylly.org/${filename}"; then
    invalidation_paths+=("/${filename}")
  else
    echo "Warning: could not publish optional live object ${filename}; continuing map publication." >&2
  fi
}

publish_optional_live_object robots.txt
publish_optional_live_object sitemap.xml
aws cloudfront create-invalidation --distribution-id "E2F702Y6HFAYV6" --paths "${invalidation_paths[@]}"
