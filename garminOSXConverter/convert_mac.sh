#!/bin/bash
set -e

curl 'https://www.javawa.nl/index.php?download=divers/jmc_cli_linux.tar.gz'  --output jmc_cli_linux.tar.gz \
  -H 'authority: www.javawa.nl' \
  -H 'cache-control: max-age=0' \
  -H 'upgrade-insecure-requests: 1' \
  -H 'origin: https://www.javawa.nl' \
  -H 'content-type: application/x-www-form-urlencoded' \
  -H 'user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.89 Safari/537.36' \
  -H 'accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9' \
  -H 'sec-fetch-site: same-origin' \
  -H 'sec-fetch-mode: navigate' \
  -H 'sec-fetch-user: ?1' \
  -H 'sec-fetch-dest: document' \
  -H 'referer: https://www.javawa.nl/index.php?download=divers/jmc_cli_linux.tar.gz' \
  -H 'accept-language: en-GB,en;q=0.9,en-US;q=0.8,fi;q=0.7' \
  --data-raw 'download=' \
  --compressed
tar -xvzf jmc_cli_linux.tar.gz

rm -rf /output/mtkgarmin_osx
mkdir /output/mtkgarmin_osx

./jmc_cli -src=/output/mtkgarmin/ -dest=/output/mtkgarmin_osx/ -gmap=mtk_suomi.gmap -bmap=osmmap.img
./jmc_cli -src=/output/mtkgarmin_noparcel/ -dest=/output/mtkgarmin_osx/ -gmap=mtk_suomi_noparcel.gmap -bmap=osmmap.img

7z a /output/mtk_suomi_osx.zip /output/mtkgarmin_osx/mtk_suomi.gmap
7z a /output/mtk_suomi_noparcel_osx.zip /output/mtkgarmin_osx/mtk_suomi_noparcel.gmap

exit 0
