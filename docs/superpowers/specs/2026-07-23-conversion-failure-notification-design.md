# Conversion failure notification design

## Context

The twice-weekly mtk2garmin cron job writes conversion output to timestamped
files under `/home/teemu`, but its nonzero exit status does not currently reach
the operator. Recent image-build, mkgmap, and late publication failures were
therefore visible only after manually inspecting those logs.

The host has no local mail transport. It has a working host-native AWS CLI and
an existing AWS credential used for map publication, so a dedicated Amazon SNS
email topic is the smallest independent delivery path.

## Approved behavior

- Notify once whenever a scheduled conversion exits nonzero.
- Do not send success or recovery notifications.
- Do not add timeout, hang, or missed-schedule detection in this version.
- Preserve the conversion's exact exit status whether notification succeeds or
  fails.
- Include the host, pipeline, exit status, start and end timestamps, duration,
  absolute log path, and a redacted final log excerpt.
- Keep the complete local log unchanged.
- Use a dedicated SNS topic and direct email subscription rather than the
  existing user-facing Hylly event topic.

## Execution boundary

Cron will invoke `run_scheduled_conversion.sh`. The wrapper owns timestamped log
creation and runs the existing `convert_docker.sh` without changing its pipeline
semantics. An exit trap captures normal command failures and signal-derived
nonzero exits, then invokes a separate notifier exactly once.

The wrapper exits with the conversion status after notification handling.
Failure to create the log before conversion starts is reported to syslog because
there is no usable conversion log or reliable email context yet.

## SNS notifier

`notify_conversion_failure.sh` accepts named metadata arguments and publishes a
plain-text message. It reads AWS credentials without evaluating the credentials
file as shell code and never enables command tracing.

The message contains at most 40 trailing log lines, with each line capped at
1,000 characters. Before publication, it redacts:

- AWS access keys, secret keys, and session tokens;
- authorization headers;
- signed-request credential, signature, and security-token parameters;
- password, secret, and token assignments.

The notifier uses the host-native AWS CLI with standard retry mode, at most
three attempts, and a 30-second outer timeout. Notification failure is appended
to the conversion log and written to syslog, but cannot replace the original
conversion exit status.

The wrapper supports `--test-notification`, which sends a clearly labeled
synthetic message without starting conversion.

## AWS resources

The dedicated topic is
`arn:aws:sns:eu-west-1:210444919710:mtk2garmin-conversion-failures`. An AWS
administrator will:

1. create the topic;
2. subscribe the operator email address and complete email confirmation;
3. grant IAM user `ratakivi` only `sns:Publish` on that topic.

The existing ignored `mapcreator/aws-access.env` remains the runtime credential
source and will be restricted to mode `0600`. No credential or email address is
added to Git.

## Cron and logs

The cron schedule remains `0 3 * * 2,6`. The entry changes to:

```text
0 3 * * 2,6 cd /home/teemu/mtk2garmin/mapcreator && ./run_scheduled_conversion.sh
```

The wrapper preserves the existing
`/home/teemu/mtk2garmin_YYYYMMDDHHMMSS-cron.log` naming convention. The stale
`normenv` export and cron-level redirection are removed because the wrapper uses
controlled defaults and owns the log.

## Validation

- Shell syntax and ShellCheck pass for all added scripts.
- A successful fake converter sends no message and returns zero.
- A failed fake converter sends exactly once and preserves its nonzero status.
- Notification rejection and timeout do not mask the conversion status.
- Metadata and bounded log tail appear in the captured SNS message.
- Secret fixtures are absent from the captured SNS message.
- Missing or empty logs still produce a useful bounded message.
- The confirmed SNS subscription receives `--test-notification`.
- The installed crontab contains the wrapper entry and no direct conversion
  invocation.

## Success criteria

Every nonzero scheduled conversion produces one actionable email when SNS is
available. Conversion status and full local diagnostics remain authoritative,
and notification infrastructure cannot make a failed conversion appear
successful.
