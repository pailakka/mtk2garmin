#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "${fixture}"' EXIT

fake_converter="${fixture}/fake-converter"
fake_aws="${fixture}/fake-aws"
fake_logger="${fixture}/fake-logger"
logs_dir="${fixture}/logs"
aws_count="${fixture}/aws-count"
aws_message="${fixture}/aws-message"
aws_subject="${fixture}/aws-subject"
syslog_capture="${fixture}/syslog"

mkdir -p "${logs_dir}"

cat > "${fake_converter}" <<'EOF'
#!/usr/bin/env bash
printf 'converter fixture started\n'
printf 'AWS_ACCESS_KEY_ID=AKIAABCDEFGHIJKLMNOP\n'
printf 'AWS_SECRET_ACCESS_KEY=super-secret-value\n'
printf 'Authorization: Bearer ultra-secret-value\n'
printf 'request?X-Amz-Signature=deadbeef&safe=yes\n'
printf 'TOKEN = fixture-token\n'
if [[ -n "${FAKE_EXECUTABLE_CAPTURE:-}" ]]; then
  printf '%s\n' "$0" > "${FAKE_EXECUTABLE_CAPTURE}"
fi
printf 'long-line='
head -c 5000 /dev/zero | tr '\0' x
printf '\n'
if [[ "${FAKE_HOUSEKEEPING_FAIL:-0}" == "1" ]]; then
  printf '%s\n' \
    '{"schema":1,"kind":"run-status","release":"verified","housekeeping":"failed","housekeeping_exit_code":13,"failure_type":"housekeeping"}' \
    > "${MTK2GARMIN_RUN_STATUS_FILE}"
fi
if [[ -n "${FAKE_CONVERSION_SIGNAL:-}" ]]; then
  kill "-${FAKE_CONVERSION_SIGNAL}" "$$"
fi
exit "${FAKE_CONVERSION_EXIT:-0}"
EOF

cat > "${fake_aws}" <<'EOF'
#!/usr/bin/env bash
message_uri=""
subject=""

if [[ -n "${FAKE_AWS_SLEEP:-}" ]]; then
  sleep "${FAKE_AWS_SLEEP}"
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --message)
      message_uri="$2"
      shift 2
      ;;
    --subject)
      subject="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

count=0
if [[ -r "${FAKE_AWS_COUNT}" ]]; then
  count="$(<"${FAKE_AWS_COUNT}")"
fi
printf '%s\n' "$((count + 1))" > "${FAKE_AWS_COUNT}"
cp -- "${message_uri#file://}" "${FAKE_AWS_MESSAGE}"
printf '%s\n' "${subject}" > "${FAKE_AWS_SUBJECT}"
exit "${FAKE_AWS_EXIT:-0}"
EOF

cat > "${fake_logger}" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${FAKE_SYSLOG_CAPTURE}"
EOF

chmod +x "${fake_converter}" "${fake_aws}" "${fake_logger}"

common_env=(
  "MTK2GARMIN_CONVERSION_COMMAND=${fake_converter}"
  "MTK2GARMIN_AWS_CLI=${fake_aws}"
  "MTK2GARMIN_AWS_ENV_FILE=/dev/null"
  "MTK2GARMIN_FAILURE_TOPIC_ARN=arn:aws:sns:eu-west-1:123456789012:test"
  "MTK2GARMIN_LOG_DIR=${logs_dir}"
  "MTK2GARMIN_LOGGER=${fake_logger}"
  "MTK2GARMIN_NOTIFY_HOST=test-host"
  "MTK2GARMIN_NOTIFY_LINE_LIMIT=1000"
  "AWS_ACCESS_KEY_ID=fixture-access-key"
  "AWS_SECRET_ACCESS_KEY=fixture-secret-key"
  "FAKE_AWS_COUNT=${aws_count}"
  "FAKE_AWS_MESSAGE=${aws_message}"
  "FAKE_AWS_SUBJECT=${aws_subject}"
  "FAKE_SYSLOG_CAPTURE=${syslog_capture}"
)

reset_fixture() {
  rm -f -- \
    "${aws_count}" \
    "${aws_message}" \
    "${aws_subject}" \
    "${syslog_capture}"
  find "${logs_dir}" -type f -delete
}

latest_log() {
  find "${logs_dir}" -type f -name 'mtk2garmin_*-cron.log' -print -quit
}

latest_snapshot_log() {
  find "${logs_dir}" -type f -name 'mtk2garmin_snapshot_*-cron.log' -print -quit
}

assert_absent() {
  local pattern="$1"
  local file="$2"

  if grep -q "${pattern}" "${file}"; then
    echo "Unexpected sensitive pattern in ${file}: ${pattern}" >&2
    return 1
  fi
}

reset_fixture
env "${common_env[@]}" FAKE_CONVERSION_EXIT=0 \
  "${script_dir}/run_scheduled_conversion.sh"
[[ ! -e "${aws_count}" ]]
grep -q 'Scheduled conversion succeeded' "$(latest_log)"

reset_fixture
env "${common_env[@]}" FAKE_CONVERSION_EXIT=0 FAKE_HOUSEKEEPING_FAIL=1 \
  "${script_dir}/run_scheduled_conversion.sh"
[[ "$(<"${aws_count}")" -eq 1 ]]
grep -q 'Release succeeded, but housekeeping failed' "${aws_message}"
grep -q 'Failure type: housekeeping' "${aws_message}"
grep -q 'Scheduled conversion succeeded' "$(latest_log)"

reset_fixture
set +e
env "${common_env[@]}" FAKE_CONVERSION_EXIT=23 \
  "${script_dir}/run_scheduled_conversion.sh"
conversion_status=$?
set -e
[[ "${conversion_status}" -eq 23 ]]
[[ "$(<"${aws_count}")" -eq 1 ]]
grep -q 'Scheduled conversion failed' "${aws_message}"
grep -q 'Host: test-host' "${aws_message}"
grep -q 'Exit status: 23' "${aws_message}"
grep -q 'Log: .*/mtk2garmin_.*-cron.log' "${aws_message}"
grep -q '\[REDACTED' "${aws_message}"
assert_absent 'AKIAABCDEFGHIJKLMNOP' "${aws_message}"
assert_absent 'super-secret-value' "${aws_message}"
assert_absent 'ultra-secret-value' "${aws_message}"
assert_absent 'deadbeef' "${aws_message}"
assert_absent 'fixture-token' "${aws_message}"
[[ "$(wc -c < "${aws_message}")" -lt 60000 ]]

reset_fixture
set +e
env "${common_env[@]}" FAKE_CONVERSION_EXIT=23 FAKE_AWS_EXIT=42 \
  "${script_dir}/run_scheduled_conversion.sh"
conversion_status=$?
set -e
[[ "${conversion_status}" -eq 23 ]]
grep -q 'NOTIFICATION_FAILED: notifier exited 42' "$(latest_log)"
grep -q 'notification failed with exit 42' "${syslog_capture}"

reset_fixture
set +e
env "${common_env[@]}" FAKE_CONVERSION_SIGNAL=TERM \
  "${script_dir}/run_scheduled_conversion.sh"
conversion_status=$?
set -e
[[ "${conversion_status}" -eq 143 ]]
[[ "$(<"${aws_count}")" -eq 1 ]]
grep -q 'Exit status: 143' "${aws_message}"

reset_fixture
set +e
env "${common_env[@]}" FAKE_CONVERSION_EXIT=23 FAKE_AWS_SLEEP=2 \
  MTK2GARMIN_NOTIFY_TIMEOUT_SECONDS=1 \
  "${script_dir}/run_scheduled_conversion.sh"
conversion_status=$?
set -e
[[ "${conversion_status}" -eq 23 ]]
grep -q 'NOTIFICATION_FAILED: notifier exited 124' "$(latest_log)"
grep -q 'notification failed with exit 124' "${syslog_capture}"

reset_fixture
env "${common_env[@]}" \
  "${script_dir}/notify_conversion_failure.sh" \
    --exit-code 1 \
    --log "${fixture}/missing.log" \
    --started-at '2026-07-23T03:00:00+02:00' \
    --ended-at '2026-07-23T03:00:01+02:00' \
    --duration-seconds 1
grep -q '\[log unavailable:' "${aws_message}"

reset_fixture
empty_log="${fixture}/empty.log"
: > "${empty_log}"
env "${common_env[@]}" \
  "${script_dir}/notify_conversion_failure.sh" \
    --exit-code 1 \
    --log "${empty_log}" \
    --started-at '2026-07-23T03:00:00+02:00' \
    --ended-at '2026-07-23T03:00:01+02:00' \
    --duration-seconds 1
grep -q "Log: ${empty_log}" "${aws_message}"
grep -q 'Final 40 log lines' "${aws_message}"

reset_fixture
env "${common_env[@]}" \
  "${script_dir}/run_scheduled_conversion.sh" --test-notification
[[ "$(<"${aws_count}")" -eq 1 ]]
grep -q 'Synthetic notification test' "${aws_message}"
grep -q '\[mtk2garmin\] test notification on test-host' "${aws_subject}"

reset_fixture
set +e
env "${common_env[@]}" \
  MTK2GARMIN_SNAPSHOT_COMMAND="${fake_converter}" \
  FAKE_CONVERSION_EXIT=19 \
  "${script_dir}/run_scheduled_snapshot.sh"
snapshot_status=$?
set -e
[[ "${snapshot_status}" -eq 19 ]]
[[ "$(<"${aws_count}")" -eq 1 ]]
grep -q 'Scheduled input snapshot refresh failed' "${aws_message}"
grep -q 'Failure type: snapshot' "${aws_message}"
grep -q 'Scheduled input snapshot failed' "$(latest_snapshot_log)"

reset_fixture
executable_capture="${fixture}/snapshot-executable"
env "${common_env[@]}" \
  MTK2GARMIN_SNAPSHOT_COMMAND="${fake_converter}" \
  FAKE_CONVERSION_EXIT=0 \
  FAKE_EXECUTABLE_CAPTURE="${executable_capture}" \
  "${script_dir}/run_scheduled_snapshot.sh"
snapshot_executable="$(<"${executable_capture}")"
[[ "${snapshot_executable}" != "${fake_converter}" ]]
[[ "${snapshot_executable}" == */tmp.*/* || "${snapshot_executable}" == /tmp/* ]]
[[ ! -e "${snapshot_executable}" ]]
grep -q 'Scheduled input snapshot succeeded' "$(latest_snapshot_log)"

credentials_file="${fixture}/aws-access.env"
printf '%s\n%s' \
  'AWS_ACCESS_KEY_ID=fixture-access-key' \
  'AWS_SECRET_ACCESS_KEY=fixture-secret-key' \
  > "${credentials_file}"
reset_fixture
env -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY \
  MTK2GARMIN_AWS_CLI="${fake_aws}" \
  MTK2GARMIN_AWS_ENV_FILE="${credentials_file}" \
  MTK2GARMIN_FAILURE_TOPIC_ARN=arn:aws:sns:eu-west-1:123456789012:test \
  MTK2GARMIN_LOG_DIR="${logs_dir}" \
  MTK2GARMIN_LOGGER="${fake_logger}" \
  MTK2GARMIN_NOTIFY_HOST=test-host \
  FAKE_AWS_COUNT="${aws_count}" \
  FAKE_AWS_MESSAGE="${aws_message}" \
  FAKE_AWS_SUBJECT="${aws_subject}" \
  FAKE_SYSLOG_CAPTURE="${syslog_capture}" \
  "${script_dir}/run_scheduled_conversion.sh" --test-notification
[[ "$(<"${aws_count}")" -eq 1 ]]
grep -q 'Synthetic notification test' "${aws_message}"

echo "failure notification tests passed"
