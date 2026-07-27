#!/usr/bin/env bash
set -uo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
aws_cli="${MTK2GARMIN_AWS_CLI:-/home/teemu/aws/dist/aws2}"
aws_env_file="${MTK2GARMIN_AWS_ENV_FILE:-${script_dir}/aws-access.env}"
aws_region="${MTK2GARMIN_AWS_REGION:-eu-west-1}"
topic_arn="${MTK2GARMIN_FAILURE_TOPIC_ARN:-arn:aws:sns:eu-west-1:210444919710:mtk2garmin-conversion-failures}"
notify_timeout_seconds="${MTK2GARMIN_NOTIFY_TIMEOUT_SECONDS:-30}"
tail_lines="${MTK2GARMIN_NOTIFY_TAIL_LINES:-40}"
line_limit="${MTK2GARMIN_NOTIFY_LINE_LIMIT:-1000}"
notify_host="${MTK2GARMIN_NOTIFY_HOST:-$(hostname -s)}"

exit_code=""
log_path=""
started_at=""
ended_at=""
duration_seconds=""
test_notification=0

usage() {
  cat >&2 <<'EOF'
Usage:
  notify_conversion_failure.sh \
    --exit-code STATUS \
    --log PATH \
    --started-at TIMESTAMP \
    --ended-at TIMESTAMP \
    --duration-seconds SECONDS \
    [--test-notification]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --exit-code | --log | --started-at | --ended-at | --duration-seconds)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for $1" >&2
        usage
        exit 2
      fi

      case "$1" in
        --exit-code)
          exit_code="$2"
          ;;
        --log)
          log_path="$2"
          ;;
        --started-at)
          started_at="$2"
          ;;
        --ended-at)
          ended_at="$2"
          ;;
        --duration-seconds)
          duration_seconds="$2"
          ;;
      esac
      shift 2
      ;;
    --test-notification)
      test_notification=1
      shift
      ;;
    --help | -h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ ! "${exit_code}" =~ ^[0-9]+$ ]]; then
  echo "Notification exit code must be a non-negative integer" >&2
  exit 2
fi

if [[ ! "${duration_seconds}" =~ ^[0-9]+$ ]]; then
  echo "Notification duration must be a non-negative integer" >&2
  exit 2
fi

if [[ -z "${log_path}" || -z "${started_at}" || -z "${ended_at}" ]]; then
  echo "Notification metadata is incomplete" >&2
  usage
  exit 2
fi

for numeric_setting in "${notify_timeout_seconds}" "${tail_lines}" "${line_limit}"; do
  if [[ ! "${numeric_setting}" =~ ^[1-9][0-9]*$ ]]; then
    echo "Notification limits must be positive integers" >&2
    exit 2
  fi
done

load_aws_credentials() {
  local key
  local value

  if [[ -n "${AWS_ACCESS_KEY_ID:-}" && -n "${AWS_SECRET_ACCESS_KEY:-}" ]]; then
    return
  fi

  if [[ ! -r "${aws_env_file}" ]]; then
    echo "AWS credential file is not readable: ${aws_env_file}" >&2
    return 1
  fi

  while IFS='=' read -r key value || [[ -n "${key:-}${value:-}" ]]; do
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    value="${value%$'\r'}"

    case "${key}" in
      AWS_ACCESS_KEY_ID | AWS_SECRET_ACCESS_KEY | AWS_SESSION_TOKEN)
        if [[ -z "${!key:-}" ]]; then
          printf -v "${key}" '%s' "${value}"
        fi
        ;;
    esac
  done < "${aws_env_file}"

  export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
  if [[ -n "${AWS_SESSION_TOKEN:-}" ]]; then
    export AWS_SESSION_TOKEN
  fi

  if [[ -z "${AWS_ACCESS_KEY_ID:-}" || -z "${AWS_SECRET_ACCESS_KEY:-}" ]]; then
    echo "AWS credentials are incomplete" >&2
    return 1
  fi
}

redact_log_tail() {
  if [[ ! -r "${log_path}" ]]; then
    printf '[log unavailable: %s]\n' "${log_path}"
    return
  fi

  tail -n "${tail_lines}" -- "${log_path}" |
    cut -c "1-${line_limit}" |
    sed -E \
      -e 's/AKIA[0-9A-Z]{16}/[REDACTED_AWS_ACCESS_KEY]/g' \
      -e 's/((AWS_)?(ACCESS_KEY_ID|SECRET_ACCESS_KEY|SESSION_TOKEN)|PASSWORD|SECRET|TOKEN)([[:space:]]*[=:][[:space:]]*)[^[:space:]&]+/\1\4[REDACTED]/Ig' \
      -e 's/(X-Amz-(Credential|Signature|Security-Token)=)[^&[:space:]]+/\1[REDACTED]/Ig' \
      -e 's/(Authorization:[[:space:]]*).*/\1[REDACTED]/Ig'
}

if [[ ! -x "${aws_cli}" ]]; then
  echo "AWS CLI is not executable: ${aws_cli}" >&2
  exit 1
fi

if ! load_aws_credentials; then
  exit 1
fi

message_file="$(mktemp)"
trap 'rm -f -- "${message_file}"' EXIT

if [[ "${test_notification}" == "1" ]]; then
  subject="[mtk2garmin] test notification on ${notify_host}"
  headline="Synthetic notification test"
else
  subject="[mtk2garmin] conversion failed on ${notify_host} (exit ${exit_code})"
  headline="Scheduled conversion failed"
fi
subject="${subject:0:100}"

{
  printf '%s\n\n' "${headline}"
  printf 'Host: %s\n' "${notify_host}"
  printf 'Exit status: %s\n' "${exit_code}"
  printf 'Started: %s\n' "${started_at}"
  printf 'Ended: %s\n' "${ended_at}"
  printf 'Duration: %s seconds\n' "${duration_seconds}"
  printf 'Log: %s\n' "${log_path}"
  printf '\nFinal %s log lines (redacted):\n\n' "${tail_lines}"
  redact_log_tail
} > "${message_file}"

AWS_RETRY_MODE=standard \
AWS_MAX_ATTEMPTS=3 \
  timeout --foreground "${notify_timeout_seconds}" \
    "${aws_cli}" sns publish \
      --region "${aws_region}" \
      --topic-arn "${topic_arn}" \
      --subject "${subject}" \
      --message "file://${message_file}" \
      >/dev/null
publish_status=$?

if [[ "${publish_status}" -ne 0 ]]; then
  echo "Conversion failure notification publish failed with exit ${publish_status}" >&2
  exit "${publish_status}"
fi

echo "Conversion failure notification published to ${topic_arn}"
