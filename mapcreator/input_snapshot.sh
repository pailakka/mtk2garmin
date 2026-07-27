#!/usr/bin/env bash
set -Eeuo pipefail

runtime_script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script_dir="${MTK2GARMIN_SNAPSHOT_SOURCE_DIR:-${runtime_script_dir}}"
script_dir="$(realpath -e -- "${script_dir}")"
snapshot_root="${MTK2GARMIN_INPUT_SNAPSHOT_ROOT:-/opt/mtk2garmin-inputs}"
image_lock="${MTK2GARMIN_IMAGE_LOCK:-${script_dir}/images.lock.env}"
max_age_hours="${MTK2GARMIN_INPUT_MAX_AGE_HOURS:-6}"
verify_snapshot_hashes="${MTK2GARMIN_VERIFY_INPUT_HASHES:-1}"
minimum_free_gb="${MTK2GARMIN_SNAPSHOT_MIN_FREE_GB:-120}"
snapshot_id="${MTK2GARMIN_SNAPSHOT_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
lock_file="${MTK2GARMIN_SNAPSHOT_LOCK:-${snapshot_root}/snapshot.lock}"
replace_legacy_inputs="${MTK2GARMIN_REPLACE_LEGACY_INPUTS:-0}"
legacy_mtkdata_root="${MTK2GARMIN_LEGACY_MTKDATA_ROOT:-/opt/mtkdata}"
legacy_krkdata_root="${MTK2GARMIN_LEGACY_KRKDATA_ROOT:-/opt/krkdata}"

mode="refresh"
existing_incoming=""

usage() {
  cat >&2 <<'EOF'
Usage:
  input_snapshot.sh
  input_snapshot.sh --check-current
  input_snapshot.sh --promote-existing INCOMING_DIRECTORY
EOF
}

case "${1:-}" in
  "")
    ;;
  --check-current)
    mode="check"
    shift
    ;;
  --promote-existing)
    if [[ $# -ne 2 ]]; then
      usage
      exit 2
    fi
    mode="promote"
    existing_incoming="$2"
    shift 2
    ;;
  --help | -h)
    usage
    exit 0
    ;;
  *)
    usage
    exit 2
    ;;
esac

if [[ $# -ne 0 ]]; then
  usage
  exit 2
fi

snapshot_root="$(realpath -m -- "${snapshot_root}")"
if [[ "${snapshot_root}" == "/" ]]; then
  echo "Snapshot root must not be /." >&2
  exit 2
fi

required_files=(
  mtkdata/mktmaasto.zip
  mtkdata/mtkkorkeus.zip
  krkdata/kiinteistorekisterikartta.gpkg
  additional-data/finland-latest.osm.pbf
  additional-data/syvyyskayra_v.shp
  additional-data/syvyyskayra_v.dbf
  additional-data/syvyyskayra_v.shx
  additional-data/syvyyspiste_p.shp
  additional-data/syvyyspiste_p.dbf
  additional-data/syvyyspiste_p.shx
)

check_snapshot() {
  local root="$1"
  local manifest="${root}/manifest.json"
  local -a hash_argument=()

  if [[ "${verify_snapshot_hashes}" == "1" ]]; then
    hash_argument+=(--verify-hashes)
  fi

  if [[ ! -s "${manifest}" ]]; then
    echo "Snapshot manifest is missing: ${manifest}" >&2
    return 1
  fi
  python3 "${script_dir}/pipeline_state.py" snapshot-check \
    --root "${root}" \
    --manifest "${manifest}" \
    --max-age-hours "${max_age_hours}" \
    "${hash_argument[@]}"
}

replace_legacy_input() {
  local legacy_path="$1"
  local required_relative="$2"
  local snapshot_path="$3"
  local next_link
  local retired_path=""

  legacy_path="$(
    realpath -m -- "$(dirname -- "${legacy_path}")"
  )/$(basename -- "${legacy_path}")"
  if [[ "${legacy_path}" == "/" || "${legacy_path}" == "${snapshot_path}" ]]; then
    echo "Refusing unsafe legacy input replacement: ${legacy_path}" >&2
    return 2
  fi
  if [[ ! -L "${legacy_path}" && -e "${legacy_path}" ]]; then
    if [[ ! -s "${legacy_path}/${required_relative}" ]]; then
      echo "Legacy input root lacks ${required_relative}: ${legacy_path}" >&2
      return 1
    fi
    retired_path="${legacy_path}.retired-${snapshot_id}"
    if [[ -e "${retired_path}" || -L "${retired_path}" ]]; then
      echo "Legacy input retirement target already exists: ${retired_path}" >&2
      return 1
    fi
    mv -- "${legacy_path}" "${retired_path}"
  fi

  next_link="${legacy_path}.next-${snapshot_id}"
  ln -s "${snapshot_path}" "${next_link}"
  if ! mv -Tf -- "${next_link}" "${legacy_path}"; then
    rm -f -- "${next_link}"
    if [[ -n "${retired_path}" && -d "${retired_path}" ]]; then
      mv -- "${retired_path}" "${legacy_path}"
    fi
    return 1
  fi

  if [[ -n "${retired_path}" && -d "${retired_path}" ]] &&
     ! rm -rf -- "${retired_path}"; then
    echo "Warning: retired legacy input cleanup failed: ${retired_path}" >&2
  fi
}

if [[ "${mode}" == "check" ]]; then
  if [[ ! -L "${snapshot_root}/current" ]]; then
    echo "Current input snapshot link is missing: ${snapshot_root}/current" >&2
    exit 1
  fi
  current_root="$(realpath -e -- "${snapshot_root}/current")"
  case "${current_root}" in
    "${snapshot_root}/snapshots/"*)
      ;;
    *)
      echo "Current snapshot resolves outside ${snapshot_root}/snapshots." >&2
      exit 1
      ;;
  esac
  check_snapshot "${current_root}"
  exit
fi

mkdir -p -- "${snapshot_root}/snapshots"
exec 9>"${lock_file}"
if ! flock -n 9; then
  echo "Another input snapshot refresh is already running." >&2
  exit 75
fi

incoming="${snapshot_root}/.incoming-${snapshot_id}"
cleanup_incoming=1
cleanup() {
  if [[ "${cleanup_incoming}" == "1" && -n "${incoming:-}" ]]; then
    rm -rf -- "${incoming}"
  fi
}
trap cleanup EXIT

if [[ "${mode}" == "promote" ]]; then
  incoming="$(realpath -e -- "${existing_incoming}")"
  case "${incoming}" in
    "${snapshot_root}/.incoming-"*)
      ;;
    *)
      echo "Existing incoming directory must be under ${snapshot_root}." >&2
      exit 2
      ;;
  esac
else
  if [[ ! "${minimum_free_gb}" =~ ^[0-9]+$ ]]; then
    echo "MTK2GARMIN_SNAPSHOT_MIN_FREE_GB must be a non-negative integer." >&2
    exit 2
  fi
  available_kb="$(df -Pk "${snapshot_root}" | awk 'NR == 2 {print $4}')"
  required_kb=$((minimum_free_gb * 1024 * 1024))
  if [[ ! "${available_kb}" =~ ^[0-9]+$ || "${available_kb}" -lt "${required_kb}" ]]; then
    echo "Input refresh requires ${minimum_free_gb} GiB free under ${snapshot_root}." >&2
    exit 1
  fi

  if [[ ! -r "${image_lock}" ]]; then
    echo "Image lock is not readable: ${image_lock}" >&2
    exit 1
  fi

  set -a
  # shellcheck disable=SC1090
  source "${image_lock}"
  set +a

  rm -rf -- "${incoming}"
  mkdir -p \
    "${incoming}/mtkdata" \
    "${incoming}/krkdata" \
    "${incoming}/additional-data"

  compose_environment=(
    "MTK2GARMIN_MTKDATA_ROOT=${incoming}/mtkdata"
    "MTK2GARMIN_KRKDATA_ROOT=${incoming}/krkdata"
    "ADDITIONAL_DATA_MOUNT=${incoming}/additional-data"
  )
  (
    cd "${script_dir}"
    env "${compose_environment[@]}" \
      docker compose --env-file "${image_lock}" config --quiet
    env "${compose_environment[@]}" \
      docker compose --env-file "${image_lock}" pull \
        mml-ogr-client additional-data
    env "${compose_environment[@]}" \
      docker compose --env-file "${image_lock}" run --rm --no-deps \
        mml-ogr-client
    env "${compose_environment[@]}" \
      docker compose --env-file "${image_lock}" run --rm --no-deps \
        additional-data
    env "${compose_environment[@]}" \
      docker compose --env-file "${image_lock}" run --rm --no-deps \
        additional-data --validate-only
  )
fi

manifest_arguments=()
for relative in "${required_files[@]}"; do
  manifest_arguments+=(--required "${relative}")
done
python3 "${script_dir}/pipeline_state.py" snapshot-create \
  --root "${incoming}" \
  --manifest "${incoming}/manifest.json" \
  --snapshot-id "${snapshot_id}" \
  "${manifest_arguments[@]}"

python3 "${script_dir}/pipeline_state.py" snapshot-check \
  --root "${incoming}" \
  --manifest "${incoming}/manifest.json" \
  --max-age-hours "${max_age_hours}" \
  --verify-hashes

target="${snapshot_root}/snapshots/${snapshot_id}"
if [[ -e "${target}" ]]; then
  echo "Snapshot target already exists: ${target}" >&2
  exit 1
fi
mv -- "${incoming}" "${target}"
cleanup_incoming=0

next_link="${snapshot_root}/.current-${snapshot_id}"
ln -s "snapshots/${snapshot_id}" "${next_link}"
mv -Tf -- "${next_link}" "${snapshot_root}/current"

if [[ "${replace_legacy_inputs}" == "1" ]]; then
  replace_legacy_input \
    "${legacy_mtkdata_root}" \
    mktmaasto.zip \
    "${snapshot_root}/current/mtkdata"
  replace_legacy_input \
    "${legacy_krkdata_root}" \
    kiinteistorekisterikartta.gpkg \
    "${snapshot_root}/current/krkdata"
fi

if ! find "${snapshot_root}/snapshots" \
  -mindepth 1 -maxdepth 1 -type d \
  ! -name "${snapshot_id}" \
  -exec rm -rf -- {} +; then
  echo "Warning: superseded input snapshot cleanup failed." >&2
fi

echo "Promoted input snapshot ${snapshot_id}: ${target}"
