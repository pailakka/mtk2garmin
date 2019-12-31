#!/bin/bash
set -euxo pipefail

date
rm -rf /output/dist
mkdir -p /output/dist

time_stamp=$(date +%Y-%m-%d)

cp /output/mtkgarmin/gmapsupp.img /output/dist/mtk_suomi.img
cp /output/mtkgarmin_noparcel/gmapsupp.img /output/dist/mtk_suomi_noparcel.img

cp /output/mtkgarmin/MTK\ Suomi.exe /output/dist/mtk_suomi.exe
cp /output/mtkgarmin_noparcel/MTK\ Suomi.exe /output/dist/mtk_suomi_noparcel.exe

cp /output/mtk_suomi_noparcel_osx.zip /output/dist/mtk_suomi_noparcel_osx.zip
cp /output/mtk_suomi_osx.zip /output/dist/mtk_suomi_osx.zip
cp /output/mtk_all.map /output/dist/mtk_suomi.map

cp /mapstyles/peruskartta.zip /output/dist/peruskartta.zip
cp /mapstyles/tiekartta.zip /output/dist/tiekartta.zip

python3 generate_site.py "${time_stamp}"

mkdir -p "/publish/${time_stamp}"

rsync -avP "/output/dist/" "/publish/${time_stamp}/"
aws2 s3 sync "/publish/${time_stamp}" "s3://kartat-build/new-${time_stamp}"
aws2 s3 cp "/publish/${time_stamp}/site.html" "s3://kartat.hylly.org/index.html"
