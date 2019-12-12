#!/bin/bash
set -euxo pipefail

date
time_stamp=$(date +%Y-%m-%d)
mkdir -p "/output/${time_stamp}"

#rsync -avP "/output/${time_stamp}" "/var/www/jekku/public_html/kartat/"
aws s3 sync "/output/${time_stamp}" "s3://kartat-build/${time_stamp}"
aws s3 cp "/output/${time_stamp}/site.html" "s3://kartat.hylly.org/index.html"

#rm -rf "/output/${time_stamp}"

echo "Done"
