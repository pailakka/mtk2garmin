#!/bin/bash
set -euxo pipefail

date
rm -rf /output/dist
mkdir -p /output/dist

time_stamp="${TIME_STAMP:-$(date +%Y-%m-%d)}"
download_prefix="${DOWNLOAD_PREFIX:-${time_stamp}}"
publish_prefix="${PUBLISH_PREFIX:-new-${time_stamp}}"
index_object="${INDEX_OBJECT:-index.html}"
legacy_index_object="${LEGACY_INDEX_OBJECT:-index_old.html}"

cp /output/mtkgarmin/gmapsupp.img /output/dist/mtk_suomi.img
cp /output/mtkgarmin_noparcel/gmapsupp.img /output/dist/mtk_suomi_noparcel.img

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
aws cloudfront create-invalidation --distribution-id "E2F702Y6HFAYV6" --paths "${invalidation_paths[@]}"
