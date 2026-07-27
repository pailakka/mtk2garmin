#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "${fixture}"' EXIT

snapshot_root="${fixture}/snapshots-root"
incoming="${snapshot_root}/.incoming-fixture"
mkdir -p \
  "${incoming}/mtkdata" \
  "${incoming}/krkdata" \
  "${incoming}/additional-data"

for relative in \
  mtkdata/mktmaasto.zip \
  mtkdata/mtkkorkeus.zip \
  krkdata/kiinteistorekisterikartta.gpkg \
  additional-data/finland-latest.osm.pbf \
  additional-data/syvyyskayra_v.shp \
  additional-data/syvyyskayra_v.dbf \
  additional-data/syvyyskayra_v.shx \
  additional-data/syvyyspiste_p.shp \
  additional-data/syvyyspiste_p.dbf \
  additional-data/syvyyspiste_p.shx
do
  printf 'fixture %s\n' "${relative}" > "${incoming}/${relative}"
done

env \
  MTK2GARMIN_INPUT_SNAPSHOT_ROOT="${snapshot_root}" \
  MTK2GARMIN_SNAPSHOT_ID=fixture \
  "${script_dir}/input_snapshot.sh" --promote-existing "${incoming}"

test -L "${snapshot_root}/current"
test "$(realpath "${snapshot_root}/current")" = \
  "${snapshot_root}/snapshots/fixture"
test -s "${snapshot_root}/snapshots/fixture/manifest.json"

env \
  MTK2GARMIN_INPUT_SNAPSHOT_ROOT="${snapshot_root}" \
  MTK2GARMIN_INPUT_MAX_AGE_HOURS=1 \
  "${script_dir}/input_snapshot.sh" --check-current

mkdir -p "${snapshot_root}/snapshots/old"
printf 'old\n' > "${snapshot_root}/snapshots/old/file"
legacy_mtkdata="${fixture}/legacy-mtkdata"
legacy_krkdata="${fixture}/legacy-krkdata"
mkdir -p "${legacy_mtkdata}" "${legacy_krkdata}"
printf 'legacy\n' > "${legacy_mtkdata}/mktmaasto.zip"
printf 'legacy\n' > "${legacy_krkdata}/kiinteistorekisterikartta.gpkg"
incoming="${snapshot_root}/.incoming-replacement"
mkdir -p \
  "${incoming}/mtkdata" \
  "${incoming}/krkdata" \
  "${incoming}/additional-data"
for relative in \
  mtkdata/mktmaasto.zip \
  mtkdata/mtkkorkeus.zip \
  krkdata/kiinteistorekisterikartta.gpkg \
  additional-data/finland-latest.osm.pbf \
  additional-data/syvyyskayra_v.shp \
  additional-data/syvyyskayra_v.dbf \
  additional-data/syvyyskayra_v.shx \
  additional-data/syvyyspiste_p.shp \
  additional-data/syvyyspiste_p.dbf \
  additional-data/syvyyspiste_p.shx
do
  printf 'replacement %s\n' "${relative}" > "${incoming}/${relative}"
done

env \
  MTK2GARMIN_INPUT_SNAPSHOT_ROOT="${snapshot_root}" \
  MTK2GARMIN_SNAPSHOT_ID=replacement \
  MTK2GARMIN_REPLACE_LEGACY_INPUTS=1 \
  MTK2GARMIN_LEGACY_MTKDATA_ROOT="${legacy_mtkdata}" \
  MTK2GARMIN_LEGACY_KRKDATA_ROOT="${legacy_krkdata}" \
  "${script_dir}/input_snapshot.sh" --promote-existing "${incoming}"

test "$(realpath "${snapshot_root}/current")" = \
  "${snapshot_root}/snapshots/replacement"
test ! -e "${snapshot_root}/snapshots/fixture"
test ! -e "${snapshot_root}/snapshots/old"
test -L "${legacy_mtkdata}"
test -L "${legacy_krkdata}"
test "$(realpath "${legacy_mtkdata}")" = \
  "${snapshot_root}/snapshots/replacement/mtkdata"
test ! -e "${legacy_mtkdata}.retired-replacement"
test ! -e "${legacy_krkdata}.retired-replacement"

echo "input snapshot tests passed"
