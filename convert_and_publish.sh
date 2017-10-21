#!/bin/bash
set -e
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:/usr/local/games:/usr/local/go/bin
date
time_stamp=$(date +%Y-%m-%d)
mkdir -p "/opt/mtk2garmin_build/output/${time_stamp}"

./convert_maps.sh 2>&1 | tee "/opt/mtk2garmin_build/output/${time_stamp}/build_log.txt"
cd /opt/mtk2garmin_build/mtk2garmin/
python3 publish_map.py "/opt/mtk2garmin_build/output/${time_stamp}" 2>&1 |tee "/opt/mtk2garmin_build/output/${time_stamp}/publish_log.txt"

rsync -avP "/opt/mtk2garmin_build/output/${time_stamp}" "/var/www/jekku/public_html/kartat/"
aws s3 sync "/opt/mtk2garmin_build/output/${time_stamp}" "s3://kartat-build/${time_stamp}"
aws s3 cp "/opt/mtk2garmin_build/output/${time_stamp}/site.html" "s3://kartat.hylly.org/index.html"

rm -rf "/opt/mtk2garmin_build/output/${time_stamp}"

echo "Done"
