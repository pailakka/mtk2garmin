#!/bin/bash
set -euxo pipefail

# curl 'https://www.javawa.nl/downloads/software/jmc_cli_linux.tar.gz?lang=en'  --output jmc_cli_linux.tar.gz \
#   -H 'authority: www.javawa.nl' \
#   -H 'accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7' \
#   -H 'accept-language: en-GB,en;q=0.9,en-US;q=0.8,fi;q=0.7' \
#   -H 'cache-control: no-cache' \
#   -H 'content-type: application/x-www-form-urlencoded' \
#   -H 'origin: https://www.javawa.nl' \
#   -H 'pragma: no-cache' \
#   -H 'referer: https://www.javawa.nl/downloads/software/jmc_cli_linux.tar.gz?lang=en' \
#   -H 'sec-ch-ua: "Google Chrome";v="119", "Chromium";v="119", "Not?A_Brand";v="24"' \
#   -H 'sec-ch-ua-mobile: ?0' \
#   -H 'sec-ch-ua-platform: "Windows"' \
#   -H 'sec-fetch-dest: document' \
#   -H 'sec-fetch-mode: navigate' \
#   -H 'sec-fetch-site: same-origin' \
#   -H 'sec-fetch-user: ?1' \
#   -H 'upgrade-insecure-requests: 1' \
#   -H 'user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36' \
#   --data-raw 'download=' \
#   --compressed

tar -xvzf jmc_cli_linux.tar.gz

rm -rf /output/mtkgarmin_osx
mkdir /output/mtkgarmin_osx

./jmc_cli -src=/output/mtkgarmin/ -dest=/output/mtkgarmin_osx/ -gmap=mtk_suomi.gmap -bmap=osmmap.img
./jmc_cli -src=/output/mtkgarmin_noparcel/ -dest=/output/mtkgarmin_osx/ -gmap=mtk_suomi_noparcel.gmap -bmap=osmmap.img

7z a /output/mtk_suomi_osx.zip /output/mtkgarmin_osx/mtk_suomi.gmap
7z a /output/mtk_suomi_noparcel_osx.zip /output/mtkgarmin_osx/mtk_suomi_noparcel.gmap

exit 0
