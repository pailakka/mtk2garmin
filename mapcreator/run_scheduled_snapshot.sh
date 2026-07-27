#!/usr/bin/env bash
set -uo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
snapshot_command="${MTK2GARMIN_SNAPSHOT_COMMAND:-${script_dir}/input_snapshot.sh}"
notifier="${MTK2GARMIN_NOTIFIER:-${script_dir}/notify_conversion_failure.sh}"
log_dir="${MTK2GARMIN_LOG_DIR:-/home/teemu}"
logger_command="${MTK2GARMIN_LOGGER:-logger}"

if [[ $# -gt 0 ]]; then
  if [[ $# -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
    echo "Usage: run_scheduled_snapshot.sh"
    exit 0
  fi
  echo "Usage: run_scheduled_snapshot.sh" >&2
  exit 2
fi

log_syslog_error() {
  local message="$1"
  if command -v "${logger_command}" >/dev/null 2>&1; then
    "${logger_command}" -p user.err -t mtk2garmin -- "${message}"
  fi
}

if ! mkdir -p -- "${log_dir}"; then
  log_syslog_error "Unable to create mtk2garmin log directory: ${log_dir}"
  exit 73
fi

timestamp="$(date +%Y%m%d%H%M%S)"
log_path="${log_dir}/mtk2garmin_snapshot_${timestamp}-cron.log"
started_epoch="$(date +%s)"
started_at="$(date --iso-8601=seconds)"
snapshot_started=0

if ! : > "${log_path}"; then
  log_syslog_error "Unable to create mtk2garmin snapshot log: ${log_path}"
  exit 73
fi

notify_on_exit() {
  local original_status=$?
  local ended_epoch
  local ended_at
  local duration_seconds
  local notify_output
  local notify_status

  trap - EXIT HUP INT TERM
  if [[ "${original_status}" -ne 0 && "${snapshot_started}" -eq 1 ]]; then
    ended_epoch="$(date +%s)"
    ended_at="$(date --iso-8601=seconds)"
    duration_seconds=$((ended_epoch - started_epoch))
    printf '\nScheduled input snapshot failed: exit=%s ended=%s duration=%ss\n' \
      "${original_status}" "${ended_at}" "${duration_seconds}" >> "${log_path}"

    notify_output="$("${notifier}" \
      --exit-code "${original_status}" \
      --log "${log_path}" \
      --started-at "${started_at}" \
      --ended-at "${ended_at}" \
      --duration-seconds "${duration_seconds}" \
      --failure-type snapshot 2>&1)"
    notify_status=$?
    if [[ -n "${notify_output}" ]]; then
      printf '%s\n' "${notify_output}" >> "${log_path}"
    fi
    if [[ "${notify_status}" -ne 0 ]]; then
      printf 'NOTIFICATION_FAILED: notifier exited %s\n' "${notify_status}" \
        >> "${log_path}"
      log_syslog_error \
        "mtk2garmin snapshot failed with exit ${original_status}; notification failed with exit ${notify_status}; log=${log_path}"
    fi
  fi
  exit "${original_status}"
}

trap notify_on_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

printf 'Scheduled input snapshot started: time=%s command=%s\n' \
  "${started_at}" "${snapshot_command}" >> "${log_path}"
snapshot_started=1
(cd "${script_dir}" && "${snapshot_command}") >> "${log_path}" 2>&1
snapshot_status=$?

if [[ "${snapshot_status}" -eq 0 ]]; then
  ended_epoch="$(date +%s)"
  ended_at="$(date --iso-8601=seconds)"
  duration_seconds=$((ended_epoch - started_epoch))
  printf 'Scheduled input snapshot succeeded: ended=%s duration=%ss\n' \
    "${ended_at}" "${duration_seconds}" >> "${log_path}"
fi

exit "${snapshot_status}"
