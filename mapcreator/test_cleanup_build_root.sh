#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "${fixture}"' EXIT

mkdir -p \
  "${fixture}/convertedpbf/nested" \
  "${fixture}/splitted" \
  "${fixture}/output/dist/nested" \
  "${fixture}/output/mtkgarmin" \
  "${fixture}/output/mtkgarmin_osx"
printf 'merged pbf\n' > "${fixture}/convertedpbf/all_osm.osm.pbf"
printf 'staged data\n' > "${fixture}/convertedpbf/nested/writer.stage"
printf 'split tile\n' > "${fixture}/splitted/63240001.osm.pbf"
printf 'published map\n' > "${fixture}/output/dist/nested/mtk_suomi.map"
printf 'published site\n' > "${fixture}/output/dist/site.html"
printf 'writer log\n' > "${fixture}/output/mapsforge-rs.log"
printf 'garmin work\n' > "${fixture}/output/mtkgarmin/gmapsupp.img"
printf 'osx work\n' > "${fixture}/output/mtkgarmin_osx/mtk_suomi.gmap"

"${script_dir}/cleanup_build_root.sh" "${fixture}"

test -f "${fixture}/output/dist/nested/mtk_suomi.map"
test "$(cat "${fixture}/output/dist/nested/mtk_suomi.map")" = "published map"
test -f "${fixture}/output/dist/site.html"
test -d "${fixture}/convertedpbf"
test -d "${fixture}/splitted"
test -z "$(find "${fixture}/convertedpbf" -mindepth 1 -print -quit)"
test -z "$(find "${fixture}/splitted" -mindepth 1 -print -quit)"
test ! -e "${fixture}/output/mapsforge-rs.log"
test ! -e "${fixture}/output/mtkgarmin"
test ! -e "${fixture}/output/mtkgarmin_osx"

if "${script_dir}/cleanup_build_root.sh" / >/dev/null 2>&1; then
  echo "cleanup accepted unsafe root /" >&2
  exit 1
fi

echo "cleanup build-root test passed"
