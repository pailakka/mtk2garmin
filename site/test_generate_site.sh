#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "${fixture}"' EXIT
site_image="${SITE_TEST_IMAGE:-$(
  awk -F= '$1 == "SITE_IMAGE" {print $2}' "${repo_root}/mapcreator/images.lock.env"
)}"

for filename in \
  mtk_suomi.img \
  mtk_suomi_noparcel.img \
  mtk_suomi_amoled.img \
  mtk_suomi_amoled_noparcel.img \
  mtk_suomi.exe \
  mtk_suomi_noparcel.exe \
  mtk_suomi_osx.zip \
  mtk_suomi_noparcel_osx.zip \
  mtk_suomi.map \
  peruskartta.zip \
  tiekartta.zip; do
  printf 'fixture %s\n' "${filename}" > "${fixture}/${filename}"
done

docker run --rm \
  -v "${script_dir}:/opt/mtkgarmin-site:ro" \
  -v "${fixture}:/fixture" \
  -w /opt/mtkgarmin-site \
  -e OUTPUT_DIST_DIR=/fixture \
  --entrypoint python3 \
  "${site_image}" \
  generate_site.py 2026-07-26

test -s "${fixture}/site.html"
test ! -e "${fixture}/site2.html"
grep -Fq 'https://kartat-dl.hylly.org/2026-07-26/' "${fixture}/site.html"
if grep -Eq '/v2/|index_v2|rs-ogr2osm v2|Java/GML' "${fixture}/site.html"; then
  echo "generated site contains a legacy pipeline reference" >&2
  exit 1
fi
test -s "${fixture}/mtk_suomi_locus.xml"
test -s "${fixture}/peruskartta_locus.xml"
test -s "${fixture}/sitemap.xml"
test -s "${fixture}/robots.txt"

echo "site generation tests passed"
