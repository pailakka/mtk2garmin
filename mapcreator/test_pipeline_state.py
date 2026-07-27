#!/usr/bin/env python3

import hashlib
import json
import subprocess
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path


SCRIPT = Path(__file__).with_name("pipeline_state.py")


class PipelineStateTest(unittest.TestCase):
    def run_script(self, *args: str, expected: int = 0) -> subprocess.CompletedProcess:
        result = subprocess.run(
            ["python3", str(SCRIPT), *args],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(expected, result.returncode, result.stderr)
        return result

    def test_stage_is_reusable_only_with_matching_fingerprint_and_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source"
            output = root / "output"
            manifest = root / "stage.json"
            source.write_text("input", encoding="utf-8")
            output.write_text("result", encoding="utf-8")

            fingerprint = self.run_script(
                "fingerprint",
                "--stage",
                "example",
                "--version",
                "1",
                "--file",
                f"source={source}",
                "--value",
                "mode=production",
            ).stdout.strip()
            self.run_script(
                "record",
                "--manifest",
                str(manifest),
                "--stage",
                "example",
                "--fingerprint",
                fingerprint,
                "--status",
                "success",
                "--started-at",
                "2026-07-27T01:00:00+00:00",
                "--ended-at",
                "2026-07-27T01:00:01+00:00",
                "--duration-seconds",
                "1",
                "--exit-code",
                "0",
                "--output",
                str(output),
            )
            self.run_script(
                "check",
                "--manifest",
                str(manifest),
                "--fingerprint",
                fingerprint,
            )
            self.run_script(
                "check",
                "--manifest",
                str(manifest),
                "--fingerprint",
                "0" * 64,
                expected=1,
            )

            output.write_text("changed", encoding="utf-8")
            self.run_script(
                "check",
                "--manifest",
                str(manifest),
                "--fingerprint",
                fingerprint,
                expected=1,
            )

    def test_snapshot_records_hashes_and_rejects_stale_data(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            required = root / "mtkdata" / "mktmaasto.zip"
            required.parent.mkdir()
            required.write_bytes(b"snapshot-data")
            manifest = root / "manifest.json"
            recent = datetime.now(timezone.utc) - timedelta(minutes=5)

            self.run_script(
                "snapshot-create",
                "--root",
                str(root),
                "--manifest",
                str(manifest),
                "--snapshot-id",
                "test-snapshot",
                "--created-at",
                recent.isoformat(),
                "--required",
                "mtkdata/mktmaasto.zip",
            )
            payload = json.loads(manifest.read_text(encoding="utf-8"))
            self.assertEqual(
                hashlib.sha256(b"snapshot-data").hexdigest(),
                payload["files"][0]["sha256"],
            )
            self.run_script(
                "snapshot-check",
                "--root",
                str(root),
                "--manifest",
                str(manifest),
                "--max-age-hours",
                "1",
                "--verify-hashes",
            )
            self.run_script(
                "snapshot-check",
                "--root",
                str(root),
                "--manifest",
                str(manifest),
                "--max-age-hours",
                "0.01",
                expected=1,
            )

    def test_atomic_record_leaves_no_temporary_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "output"
            manifest = root / "stage.json"
            output.write_bytes(b"ok")
            self.run_script(
                "record",
                "--manifest",
                str(manifest),
                "--stage",
                "example",
                "--fingerprint",
                "1" * 64,
                "--status",
                "success",
                "--started-at",
                "2026-07-27T01:00:00+00:00",
                "--ended-at",
                "2026-07-27T01:00:01+00:00",
                "--duration-seconds",
                "1",
                "--exit-code",
                "0",
                "--output",
                str(output),
            )
            self.assertTrue(manifest.is_file())
            self.assertEqual([], list(root.glob(".stage.json.*.tmp")))

    def test_release_and_housekeeping_status_are_independent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            status_file = Path(temporary) / "status.json"
            self.run_script(
                "status-write",
                "--status-file",
                str(status_file),
                "--release",
                "verified",
                "--housekeeping",
                "failed",
                "--housekeeping-exit-code",
                "13",
            )
            release = self.run_script(
                "status-get",
                "--status-file",
                str(status_file),
                "--field",
                "release",
            ).stdout.strip()
            housekeeping = self.run_script(
                "status-get",
                "--status-file",
                str(status_file),
                "--field",
                "housekeeping",
            ).stdout.strip()
            self.assertEqual("verified", release)
            self.assertEqual("failed", housekeeping)

    def test_run_summary_aggregates_stage_durations(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stage_root = root / "stages"
            stage_root.mkdir()
            status_file = root / "status.json"
            summary_file = root / "summary.json"
            output = root / "output"
            output.write_bytes(b"ok")
            self.run_script(
                "record",
                "--manifest",
                str(stage_root / "conversion.json"),
                "--stage",
                "conversion",
                "--fingerprint",
                "2" * 64,
                "--status",
                "success",
                "--started-at",
                "2026-07-27T01:00:00+00:00",
                "--ended-at",
                "2026-07-27T01:02:00+00:00",
                "--duration-seconds",
                "120",
                "--exit-code",
                "0",
                "--output",
                str(output),
            )
            self.run_script(
                "status-write",
                "--status-file",
                str(status_file),
                "--release",
                "verified",
                "--housekeeping",
                "succeeded",
            )
            self.run_script(
                "run-summary",
                "--stage-root",
                str(stage_root),
                "--status-file",
                str(status_file),
                "--release-date",
                "2026-07-27",
                "--output",
                str(summary_file),
            )
            summary = json.loads(summary_file.read_text(encoding="utf-8"))
            self.assertEqual(120, summary["successful_stage_seconds"])
            self.assertEqual("conversion", summary["stages"][0]["stage"])


if __name__ == "__main__":
    unittest.main()
