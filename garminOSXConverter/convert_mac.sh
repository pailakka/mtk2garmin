#!/bin/bash
set -e

curl --fail -O 'http://www.javawa.nl/downloads/divers/jmc_cli_linux.tar.gz' -H 'Connection: keep-alive' -H 'Pragma: no-cache' -H 'Cache-Control: no-cache' -H 'Origin: http://www.javawa.nl' -H 'Upgrade-Insecure-Requests: 1' -H 'Content-Type: application/x-www-form-urlencoded' -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.139 Safari/537.36' -H 'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8' -H 'Referer: http://www.javawa.nl/downloads/divers/jmc_cli_linux.tar.gz' -H 'Accept-Encoding: gzip, deflate' -H 'Accept-Language: en-GB,en;q=0.9,en-US;q=0.8,fi;q=0.7' --data 'download=Downloaden' --compressed
tar -xvzf jmc_cli_linux.tar.gz

rm -rf /opt/mtk2garmin_build/mtk2garmin/mtkgarmin_osx
mkdir /opt/mtk2garmin_build/mtk2garmin/mtkgarmin_osx
./jmc_cli -src=/opt/mtk2garmin_build/mtk2garmin/mtkgarmin/ -dest=/opt/mtk2garmin_build/mtk2garmin/mtkgarmin_osx/ -gmap=mtk_suomi.gmap
./jmc_cli -src=/opt/mtk2garmin_build/mtk2garmin/mtkgarmin_noparcel/ -dest=/opt/mtk2garmin_build/mtk2garmin/mtkgarmin_noparcel_osx/ -gmap=mtk_suomi_noparcel.gmap

7z a /opt/mtk2garmin_build/mtk2garmin/mtk_suomi_osx.zip /opt/mtk2garmin_build/mtk2garmin/mtkgarmin_osx/mtk_suomi.gmap
7z a /opt/mtk2garmin_build/mtk2garmin/mtk_suomi_noparcel_osx.zip /opt/mtk2garmin_build/mtk2garmin/mtkgarmin_osx/mtk_suomi_noparcel.gmap

exit 0