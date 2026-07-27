#!/usr/bin/env bash
set -uo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
converter="${MTK2GARMIN_CONVERSION_COMMAND:-${script_dir}/convert_docker.sh}"
notifier="${MTK2GARMIN_NOTIFIER:-${script_dir}/notify_conversion_failure.sh}"
log_dir="${MTK2GARMIN_LOG_DIR:-/home/teemu}"
logger_command="${MTK2GARMIN_LOGGER:-logger}"

usage() {
  cat >&2 <<'EOF'
Usage: run_scheduled_conversion.sh [--test-notification]
EOF
}

log_syslog_error() {
  local message="$1"

  if command -v "${logger_command}" >/dev/null 2>&1; then
    "${logger_command}" -p user.err -t mtk2garmin -- "${message}"
  fi
}

send_test_notification() {
  local now
  local status
  local test_log

  now="$(date --iso-8601=seconds)"
  test_log="$(mktemp)"
  printf 'Synthetic mtk2garmin failure-notification test; no conversion was started.\n' > "${test_log}"

  "${notifier}" \
    --exit-code 0 \
    --log "${test_log}" \
    --started-at "${now}" \
    --ended-at "${now}" \
    --duration-seconds 0 \
    --test-notification
  status=$?

  rm -f -- "${test_log}"
  return "${status}"
}

case "${1:-}" in
  "")
    ;;
  --test-notification)
    if [[ $# -ne 1 ]]; then
      usage
      exit 2
    fi
    send_test_notification
    exit $?
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

if ! mkdir -p -- "${log_dir}"; then
  log_syslog_error "Unable to create mtk2garmin log directory: ${log_dir}"
  exit 73
fi

timestamp="$(date +%Y%m%d%H%M%S)"
log_path="${log_dir}/mtk2garmin_${timestamp}-cron.log"
status_path="${log_dir}/mtk2garmin_${timestamp}-status.json"
started_epoch="$(date +%s)"
started_at="$(date --iso-8601=seconds)"
conversion_started=0

read_status_field() {
  local field="$1"
  python3 "${script_dir}/pipeline_state.py" status-get \
    --status-file "${status_path}" \
    --field "${field}" \
    2>/dev/null
}

if ! : > "${log_path}"; then
  log_syslog_error "Unable to create mtk2garmin conversion log: ${log_path}"
  exit 73
fi

# shellcheck disable=SC2329
notify_on_exit() {
  local original_status=$?
  local duration_seconds
  local ended_at
  local ended_epoch
  local notify_output
  local notify_status
  local failure_type="conversion"
  local recorded_failure_type

  trap - EXIT HUP INT TERM

  if [[ "${original_status}" -ne 0 && "${conversion_started}" -eq 1 ]]; then
    ended_epoch="$(date +%s)"
    ended_at="$(date --iso-8601=seconds)"
    duration_seconds=$((ended_epoch - started_epoch))
    recorded_failure_type="$(read_status_field failure_type || true)"
    if [[ "${recorded_failure_type}" =~ ^(snapshot|conversion|publication)$ ]]; then
      failure_type="${recorded_failure_type}"
    elif [[ "$(read_status_field release || true)" == "failed" ]]; then
      failure_type="publication"
    fi

    {
      printf '\nScheduled conversion failed: exit=%s ended=%s duration=%ss\n' \
        "${original_status}" "${ended_at}" "${duration_seconds}"
    } >> "${log_path}"

    notify_output="$("${notifier}" \
      --exit-code "${original_status}" \
      --log "${log_path}" \
      --started-at "${started_at}" \
      --ended-at "${ended_at}" \
      --duration-seconds "${duration_seconds}" \
      --failure-type "${failure_type}" 2>&1)"
    notify_status=$?

    if [[ -n "${notify_output}" ]]; then
      printf '%s\n' "${notify_output}" >> "${log_path}"
    fi

    if [[ "${notify_status}" -ne 0 ]]; then
      printf 'NOTIFICATION_FAILED: notifier exited %s\n' "${notify_status}" >> "${log_path}"
      log_syslog_error \
        "mtk2garmin conversion failed with exit ${original_status}; notification failed with exit ${notify_status}; log=${log_path}"
    fi
  fi

  exit "${original_status}"
}

trap notify_on_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

{
  printf 'Scheduled conversion started: time=%s command=%s\n' \
    "${started_at}" "${converter}"
} >> "${log_path}"

conversion_started=1
(cd "${script_dir}" && \
  MTK2GARMIN_RUN_STATUS_FILE="${status_path}" "${converter}") \
  >> "${log_path}" 2>&1
conversion_status=$?

if [[ "${conversion_status}" -eq 0 ]]; then
  ended_epoch="$(date +%s)"
  ended_at="$(date --iso-8601=seconds)"
  duration_seconds=$((ended_epoch - started_epoch))

  if [[ "$(read_status_field housekeeping || true)" == "failed" ]]; then
    housekeeping_exit_code="$(read_status_field housekeeping_exit_code || true)"
    if [[ ! "${housekeeping_exit_code}" =~ ^[0-9]+$ ]]; then
      housekeeping_exit_code=1
    fi
    printf 'Scheduled release succeeded but housekeeping failed: exit=%s\n' \
      "${housekeeping_exit_code}" >> "${log_path}"

    notify_output="$("${notifier}" \
      --exit-code "${housekeeping_exit_code}" \
      --log "${log_path}" \
      --started-at "${started_at}" \
      --ended-at "${ended_at}" \
      --duration-seconds "${duration_seconds}" \
      --failure-type housekeeping 2>&1)"
    notify_status=$?
    if [[ -n "${notify_output}" ]]; then
      printf '%s\n' "${notify_output}" >> "${log_path}"
    fi
    if [[ "${notify_status}" -ne 0 ]]; then
      printf 'NOTIFICATION_FAILED: housekeeping notifier exited %s\n' \
        "${notify_status}" >> "${log_path}"
      log_syslog_error \
        "mtk2garmin release succeeded but housekeeping notification failed with exit ${notify_status}; log=${log_path}"
    fi
  fi

  printf 'Scheduled conversion succeeded: ended=%s duration=%ss\n' \
    "${ended_at}" "${duration_seconds}" >> "${log_path}"
fi

exit "${conversion_status}"
