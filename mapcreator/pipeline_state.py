#!/usr/bin/env python3
"""Atomic manifests for mtk2garmin input snapshots and resumable stages."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

SCHEMA_VERSION = 1
HASH_CHUNK_SIZE = 8 * 1024 * 1024


def parse_timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError(f"timestamp has no timezone: {value}")
    return parsed.astimezone(timezone.utc)


def now_utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(HASH_CHUNK_SIZE):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_digest(value: Any) -> str:
    payload = json.dumps(
        value,
        ensure_ascii=True,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def atomic_write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=path.parent,
        prefix=f".{path.name}.",
        suffix=".tmp",
    )
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as target:
            json.dump(value, target, ensure_ascii=True, indent=2, sort_keys=True)
            target.write("\n")
            target.flush()
            os.fsync(target.fileno())
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def key_values(values: Iterable[str]) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for item in values:
        key, separator, value = item.partition("=")
        if not separator or not key:
            raise ValueError(f"expected KEY=VALUE, got: {item}")
        parsed[key] = value
    return parsed


def named_paths(values: Iterable[str]) -> dict[str, Path]:
    parsed: dict[str, Path] = {}
    for item in values:
        key, separator, value = item.partition("=")
        if not separator or not key or not value:
            raise ValueError(f"expected KEY=PATH, got: {item}")
        parsed[key] = Path(value)
    return parsed


def file_record(path: Path, *, relative_to: Path | None = None) -> dict[str, Any]:
    if not path.is_file():
        raise FileNotFoundError(f"required file is missing: {path}")
    stat = path.stat()
    record_path = path
    if relative_to is not None:
        record_path = path.relative_to(relative_to)
    return {
        "path": str(record_path),
        "size": stat.st_size,
        "sha256": sha256_file(path),
    }


def expand_outputs(files: Iterable[str], trees: Iterable[str]) -> list[Path]:
    outputs = {Path(path).resolve() for path in files}
    for tree_value in trees:
        tree = Path(tree_value).resolve()
        if not tree.is_dir():
            raise FileNotFoundError(f"output tree is missing: {tree}")
        outputs.update(path.resolve() for path in tree.rglob("*") if path.is_file())
    return sorted(outputs)


def command_fingerprint(args: argparse.Namespace) -> int:
    files = named_paths(args.file)
    file_inputs = {}
    for key, path in sorted(files.items()):
        if not path.is_file():
            raise FileNotFoundError(f"fingerprint input is missing: {path}")
        file_inputs[key] = {
            "path": str(path.resolve()),
            "size": path.stat().st_size,
            "sha256": sha256_file(path),
        }
    payload = {
        "schema": SCHEMA_VERSION,
        "stage": args.stage,
        "version": args.version,
        "values": key_values(args.value),
        "files": file_inputs,
    }
    print(canonical_digest(payload))
    return 0


def command_record(args: argparse.Namespace) -> int:
    outputs = []
    if args.status == "success":
        for path in expand_outputs(args.output, args.output_tree):
            outputs.append(file_record(path))
        if not outputs:
            raise ValueError("a successful stage must record at least one output")

    manifest = {
        "schema": SCHEMA_VERSION,
        "kind": "stage",
        "stage": args.stage,
        "fingerprint": args.fingerprint,
        "status": args.status,
        "started_at": args.started_at,
        "ended_at": args.ended_at,
        "duration_seconds": args.duration_seconds,
        "exit_code": args.exit_code,
        "metadata": key_values(args.metadata),
        "outputs": outputs,
    }
    atomic_write_json(Path(args.manifest), manifest)
    return 0


def validate_recorded_files(
    records: Iterable[dict[str, Any]], *, verify_hashes: bool
) -> list[str]:
    errors = []
    for record in records:
        path = Path(record["path"])
        if not path.is_file():
            errors.append(f"missing output: {path}")
            continue
        if path.stat().st_size != record["size"]:
            errors.append(f"size changed: {path}")
            continue
        if verify_hashes and sha256_file(path) != record["sha256"]:
            errors.append(f"sha256 changed: {path}")
    return errors


def command_check(args: argparse.Namespace) -> int:
    manifest_path = Path(args.manifest)
    if not manifest_path.is_file():
        print(f"stage manifest is missing: {manifest_path}", file=sys.stderr)
        return 1
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"stage manifest is unreadable: {error}", file=sys.stderr)
        return 1

    errors = []
    if manifest.get("schema") != SCHEMA_VERSION:
        errors.append("schema mismatch")
    if manifest.get("kind") != "stage":
        errors.append("not a stage manifest")
    if manifest.get("status") != "success":
        errors.append("stage was not successful")
    if manifest.get("fingerprint") != args.fingerprint:
        errors.append("fingerprint mismatch")
    errors.extend(
        validate_recorded_files(
            manifest.get("outputs", []),
            verify_hashes=not args.skip_hashes,
        )
    )
    if errors:
        print("; ".join(errors), file=sys.stderr)
        return 1
    print(f"Reusable stage: {manifest.get('stage')}")
    return 0


def command_snapshot_create(args: argparse.Namespace) -> int:
    root = Path(args.root).resolve()
    records = []
    for relative_value in sorted(set(args.required)):
        relative_path = Path(relative_value)
        if relative_path.is_absolute() or ".." in relative_path.parts:
            raise ValueError(f"snapshot path must be relative: {relative_path}")
        records.append(file_record(root / relative_path, relative_to=root))

    created_at = args.created_at or now_utc()
    parse_timestamp(created_at)
    snapshot = {
        "schema": SCHEMA_VERSION,
        "kind": "input-snapshot",
        "snapshot_id": args.snapshot_id,
        "created_at": created_at,
        "files": records,
    }
    snapshot["fingerprint"] = canonical_digest(
        {
            "schema": snapshot["schema"],
            "kind": snapshot["kind"],
            "snapshot_id": snapshot["snapshot_id"],
            "created_at": snapshot["created_at"],
            "files": snapshot["files"],
        }
    )
    atomic_write_json(Path(args.manifest), snapshot)
    print(snapshot["fingerprint"])
    return 0


def command_snapshot_check(args: argparse.Namespace) -> int:
    root = Path(args.root).resolve()
    manifest_path = Path(args.manifest)
    if not manifest_path.is_file():
        print(f"snapshot manifest is missing: {manifest_path}", file=sys.stderr)
        return 1
    try:
        snapshot = json.loads(manifest_path.read_text(encoding="utf-8"))
        created_at = parse_timestamp(snapshot["created_at"])
    except (OSError, KeyError, ValueError, json.JSONDecodeError) as error:
        print(f"snapshot manifest is invalid: {error}", file=sys.stderr)
        return 1

    errors = []
    if snapshot.get("schema") != SCHEMA_VERSION:
        errors.append("schema mismatch")
    if snapshot.get("kind") != "input-snapshot":
        errors.append("not an input snapshot")
    age_seconds = (datetime.now(timezone.utc) - created_at).total_seconds()
    if age_seconds < -300:
        errors.append("snapshot timestamp is in the future")
    if age_seconds > args.max_age_hours * 3600:
        errors.append(
            f"snapshot is {age_seconds / 3600:.2f} hours old; "
            f"limit is {args.max_age_hours}"
        )

    records = []
    for record in snapshot.get("files", []):
        updated = dict(record)
        relative_path = Path(updated.get("path", ""))
        if relative_path.is_absolute() or ".." in relative_path.parts:
            errors.append(f"unsafe snapshot path: {relative_path}")
            continue
        updated["path"] = str(root / relative_path)
        records.append(updated)
    errors.extend(
        validate_recorded_files(records, verify_hashes=args.verify_hashes)
    )
    if not records:
        errors.append("snapshot contains no files")

    if errors:
        print("; ".join(errors), file=sys.stderr)
        return 1
    print(
        f"Valid input snapshot {snapshot.get('snapshot_id')} "
        f"({age_seconds / 3600:.2f} hours old)"
    )
    return 0


def command_status_write(args: argparse.Namespace) -> int:
    status = {
        "schema": SCHEMA_VERSION,
        "kind": "run-status",
        "updated_at": now_utc(),
        "release": args.release,
        "housekeeping": args.housekeeping,
        "housekeeping_exit_code": args.housekeeping_exit_code,
    }
    atomic_write_json(Path(args.status_file), status)
    return 0


def command_status_get(args: argparse.Namespace) -> int:
    try:
        status = json.loads(Path(args.status_file).read_text(encoding="utf-8"))
        value = status[args.field]
    except (OSError, KeyError, json.JSONDecodeError) as error:
        print(f"run status is unavailable: {error}", file=sys.stderr)
        return 1
    print(value)
    return 0


def command_run_summary(args: argparse.Namespace) -> int:
    stages = []
    stage_root = Path(args.stage_root)
    if stage_root.is_dir():
        for manifest_path in sorted(stage_root.glob("*.json")):
            try:
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
            if manifest.get("kind") == "stage":
                stages.append(manifest)

    run_status = None
    status_path = Path(args.status_file)
    if status_path.is_file():
        try:
            run_status = json.loads(status_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            run_status = None

    summary = {
        "schema": SCHEMA_VERSION,
        "kind": "run-summary",
        "generated_at": now_utc(),
        "release_date": args.release_date,
        "run_status": run_status,
        "stages": stages,
        "successful_stage_seconds": sum(
            stage.get("duration_seconds", 0)
            for stage in stages
            if stage.get("status") == "success"
        ),
    }
    atomic_write_json(Path(args.output), summary)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)

    fingerprint = commands.add_parser("fingerprint")
    fingerprint.add_argument("--stage", required=True)
    fingerprint.add_argument("--version", required=True)
    fingerprint.add_argument("--file", action="append", default=[])
    fingerprint.add_argument("--value", action="append", default=[])
    fingerprint.set_defaults(handler=command_fingerprint)

    record = commands.add_parser("record")
    record.add_argument("--manifest", required=True)
    record.add_argument("--stage", required=True)
    record.add_argument("--fingerprint", required=True)
    record.add_argument("--status", choices=("success", "failed"), required=True)
    record.add_argument("--started-at", required=True)
    record.add_argument("--ended-at", required=True)
    record.add_argument("--duration-seconds", type=int, required=True)
    record.add_argument("--exit-code", type=int, required=True)
    record.add_argument("--metadata", action="append", default=[])
    record.add_argument("--output", action="append", default=[])
    record.add_argument("--output-tree", action="append", default=[])
    record.set_defaults(handler=command_record)

    check = commands.add_parser("check")
    check.add_argument("--manifest", required=True)
    check.add_argument("--fingerprint", required=True)
    check.add_argument("--skip-hashes", action="store_true")
    check.set_defaults(handler=command_check)

    snapshot_create = commands.add_parser("snapshot-create")
    snapshot_create.add_argument("--root", required=True)
    snapshot_create.add_argument("--manifest", required=True)
    snapshot_create.add_argument("--snapshot-id", required=True)
    snapshot_create.add_argument("--created-at")
    snapshot_create.add_argument("--required", action="append", default=[])
    snapshot_create.set_defaults(handler=command_snapshot_create)

    snapshot_check = commands.add_parser("snapshot-check")
    snapshot_check.add_argument("--root", required=True)
    snapshot_check.add_argument("--manifest", required=True)
    snapshot_check.add_argument("--max-age-hours", type=float, required=True)
    snapshot_check.add_argument("--verify-hashes", action="store_true")
    snapshot_check.set_defaults(handler=command_snapshot_check)

    status_write = commands.add_parser("status-write")
    status_write.add_argument("--status-file", required=True)
    status_write.add_argument(
        "--release",
        choices=("not-requested", "pending", "verified", "failed"),
        required=True,
    )
    status_write.add_argument(
        "--housekeeping",
        choices=("not-requested", "pending", "succeeded", "failed"),
        required=True,
    )
    status_write.add_argument("--housekeeping-exit-code", type=int)
    status_write.set_defaults(handler=command_status_write)

    status_get = commands.add_parser("status-get")
    status_get.add_argument("--status-file", required=True)
    status_get.add_argument(
        "--field",
        choices=("release", "housekeeping", "housekeeping_exit_code"),
        required=True,
    )
    status_get.set_defaults(handler=command_status_get)

    run_summary = commands.add_parser("run-summary")
    run_summary.add_argument("--stage-root", required=True)
    run_summary.add_argument("--status-file", required=True)
    run_summary.add_argument("--release-date", required=True)
    run_summary.add_argument("--output", required=True)
    run_summary.set_defaults(handler=command_run_summary)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        return args.handler(args)
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
