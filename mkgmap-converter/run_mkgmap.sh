#!/bin/bash
set -euxo pipefail

splitter_java_heap="${SPLITTER_JAVA_HEAP:-40G}"
mkgmap_java_heap="${MKGMAP_JAVA_HEAP:-40G}"
garmin_splitter_max_nodes="${GARMIN_SPLITTER_MAX_NODES:-800000}"
garmin_max_subfile_mib="${GARMIN_MAX_SUBFILE_MIB:-4}"
garmin_max_img_mib="${GARMIN_MAX_IMG_MIB:-1900}"
garmin_max_tiles="${GARMIN_MAX_TILES:-1000}"

preflight_runtime() {
  local required_file
  local -a required_files=(
    mkgmap.jar
    splitter.jar
    perus.typ
    perus_amoled.typ
    mkgmap_mtk2garmin.args
    mkgmap_mtk2garmin_noparcel.args
    mkgmap_mtk2garmin_amoled.args
    mkgmap_mtk2garmin_amoled_noparcel.args
    check_img_subfiles.py
  )

  for required_file in "${required_files[@]}"; do
    if [[ ! -s "${required_file}" ]]; then
      echo "mkgmap image preflight failed: missing or empty /opt/mkgmap/${required_file}" >&2
      return 1
    fi
  done

  echo "mkgmap image preflight passed"
}

preflight_outputs() {
  local output_dir
  local -a output_dirs=(
    /output/mtkgarmin
    /output/mtkgarmin_noparcel
    /output/mtkgarmin_amoled
    /output/mtkgarmin_amoled_noparcel
  )

  for output_dir in "${output_dirs[@]}"; do
    if [[ ! -s "${output_dir}/perus.typ" ]]; then
      echo "mkgmap output preflight failed: missing or empty ${output_dir}/perus.typ" >&2
      return 1
    fi

    if [[ ! -s "${output_dir}/osmmap.nsi" ]]; then
      echo "mkgmap output preflight failed: missing or empty ${output_dir}/osmmap.nsi" >&2
      return 1
    fi

    if ! grep -Fq 'File "perus.typ"' "${output_dir}/osmmap.nsi"; then
      echo "mkgmap output preflight failed: ${output_dir}/osmmap.nsi does not install perus.typ" >&2
      return 1
    fi
  done

  echo "mkgmap output preflight passed"
}

preflight_runtime

if [[ $# -gt 0 ]]; then
  if [[ "$1" == "--preflight" && $# -eq 1 ]]; then
    exit 0
  fi

  echo "Usage: $0 [--preflight]" >&2
  exit 2
fi

rm -rf /splitted/*
rm -rf /output/mtkgarmin
rm -rf /output/mtkgarmin_noparcel
rm -rf /output/mtkgarmin_amoled
rm -rf /output/mtkgarmin_amoled_noparcel
rm -rf /output/mtkgarmin_mh
mkdir -p \
  /output/mtkgarmin \
  /output/mtkgarmin_noparcel \
  /output/mtkgarmin_amoled \
  /output/mtkgarmin_amoled_noparcel
time java "-Xmx${splitter_java_heap}" -jar splitter.jar --output-dir=/splitted --max-areas=4096 --max-nodes="${garmin_splitter_max_nodes}" --resolution=14 /convertedpbf/all_osm.osm.pbf
(cat mkgmap_mtk2garmin.args;echo;cat /splitted/template.args) > /splitted/mkgmap_mtk2garmin.args
(cat mkgmap_mtk2garmin_noparcel.args;echo;cat /splitted/template.args) > /splitted/mkgmap_mtk2garmin_noparcel.args
(cat mkgmap_mtk2garmin_amoled.args;echo;cat /splitted/template.args) > /splitted/mkgmap_mtk2garmin_amoled.args
(cat mkgmap_mtk2garmin_amoled_noparcel.args;echo;cat /splitted/template.args) > /splitted/mkgmap_mtk2garmin_amoled_noparcel.args
time java "-Xmx${mkgmap_java_heap}" -jar mkgmap.jar -c /splitted/mkgmap_mtk2garmin.args perus.typ
time java "-Xmx${mkgmap_java_heap}" -jar mkgmap.jar -c /splitted/mkgmap_mtk2garmin_noparcel.args perus.typ
cp perus.typ /output/mtkgarmin/perus.typ
cp perus.typ /output/mtkgarmin_noparcel/perus.typ

cp perus_amoled.typ perus.typ
time java "-Xmx${mkgmap_java_heap}" -jar mkgmap.jar -c /splitted/mkgmap_mtk2garmin_amoled.args perus.typ
time java "-Xmx${mkgmap_java_heap}" -jar mkgmap.jar -c /splitted/mkgmap_mtk2garmin_amoled_noparcel.args perus.typ
cp perus.typ /output/mtkgarmin_amoled/perus.typ
cp perus.typ /output/mtkgarmin_amoled_noparcel/perus.typ

preflight_outputs

cp /output/mtkgarmin/gmapsupp.img /output/mtkgarmin/mtk_suomi.img
cp /output/mtkgarmin_noparcel/gmapsupp.img /output/mtkgarmin_noparcel/mtk_suomi_noparcel.img
cp /output/mtkgarmin_amoled/gmapsupp.img /output/mtkgarmin_amoled/mtk_suomi_amoled.img
cp /output/mtkgarmin_amoled_noparcel/gmapsupp.img /output/mtkgarmin_amoled_noparcel/mtk_suomi_amoled_noparcel.img

python3 ./check_img_subfiles.py \
  --max-subfile-mib "$garmin_max_subfile_mib" \
  --max-img-mib "$garmin_max_img_mib" \
  --max-tiles "$garmin_max_tiles" \
  /output/mtkgarmin/mtk_suomi.img \
  /output/mtkgarmin_noparcel/mtk_suomi_noparcel.img \
  /output/mtkgarmin_amoled/mtk_suomi_amoled.img \
  /output/mtkgarmin_amoled_noparcel/mtk_suomi_amoled_noparcel.img
