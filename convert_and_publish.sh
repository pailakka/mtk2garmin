#!/bin/bash
set -e
date
time_stamp=$(date +%Y-%m-%d)
mkdir -p "/opt/mtk2garmin_build/output/${time_stamp}"

./convert_maps.sh > "/opt/mtk2garmin_build/output/${time_stamp}/build_log.txt" 2>&1
cd /opt/mtk2garmin_build/mtk2garmin/
python3 publish_map.py "/opt/mtk2garmin_build/output/${time_stamp}" > "/opt/mtk2garmin_build/output/${time_stamp}/publish_log.txt" 2>&1

aws s3 cp "/opt/mtk2garmin_build/output/${time_stamp}/site.html" "s3://kartat-build/${time_stamp}/site.html"
cp "/opt/mtk2garmin_build/output/${time_stamp}/site.html" "/var/www/jekku/public_html/kartat/${time_stamp}/site.html"


aws s3 cp "/opt/mtk2garmin_build/output/${time_stamp}/build_log.txt" "s3://kartat-build/${time_stamp}/build_log.txt"
cp "/opt/mtk2garmin_build/output/${time_stamp}/build_log.txt" "/var/www/jekku/public_html/kartat/${time_stamp}/build_log.txt"

aws s3 cp "/opt/mtk2garmin_build/output/${time_stamp}/publish_log.txt" "s3://kartat-build/${time_stamp}/publish_log.txt"
cp "/opt/mtk2garmin_build/output/${time_stamp}/publish_log.txt" "/var/www/jekku/public_html/kartat/${time_stamp}/publish_log.txt"

echo "Done"