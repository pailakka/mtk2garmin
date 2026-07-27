#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "${fixture}"' EXIT

fake_bin="${fixture}/bin"
target="${fixture}/data"
mkdir -p "${fake_bin}" "${target}"

cat > "${fake_bin}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
output=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "--output" ]]; then
    output="$2"
    shift 2
  else
    shift
  fi
done
[[ "${FAKE_CURL_FAIL:-0}" != "1" ]] || exit 22
printf 'fresh pbf\n' > "${output}"
EOF

cat > "${fake_bin}/ogr2ogr" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
for argument in "$@"; do
  if [[ "${argument}" == *.shp ]]; then
    base="${argument%.shp}"
    printf 'shape\n' > "${base}.shp"
    printf 'dbf\n' > "${base}.dbf"
    printf 'index\n' > "${base}.shx"
    exit 0
  fi
done
exit 2
EOF

for command in osmium ogrinfo; do
  cat > "${fake_bin}/${command}" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
done
chmod +x "${fake_bin}"/*

PATH="${fake_bin}:${PATH}" \
ADDITIONAL_DATA_DIR="${target}" \
  "${script_dir}/get_additional_data.sh"

for required in \
  grid.zip finland-latest.osm.pbf \
  syvyyskayra_v.shp syvyyskayra_v.dbf syvyyskayra_v.shx \
  syvyyspiste_p.shp syvyyspiste_p.dbf syvyyspiste_p.shx; do
  test -s "${target}/${required}"
done

printf 'known-good pbf\n' > "${target}/finland-latest.osm.pbf"
set +e
PATH="${fake_bin}:${PATH}" \
FAKE_CURL_FAIL=1 \
ADDITIONAL_DATA_DIR="${target}" \
  "${script_dir}/get_additional_data.sh"
refresh_status=$?
set -e

[[ "${refresh_status}" -ne 0 ]]
grep -Fq 'known-good pbf' "${target}/finland-latest.osm.pbf"

PATH="${fake_bin}:${PATH}" \
ADDITIONAL_DATA_DIR="${target}" \
  "${script_dir}/get_additional_data.sh" --validate-only

echo "additional-data refresh tests passed"
