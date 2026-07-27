#!/usr/bin/env python3

import hashlib
import json
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("generate_artifact_manifest.py")
VERIFY_SCRIPT = Path(__file__).with_name("verify_artifact_manifest.py")


class ArtifactManifestTest(unittest.TestCase):
    def test_manifest_records_each_immutable_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "map.img").write_bytes(b"garmin")
            (root / "map.map").write_bytes(b"mapsforge")
            subprocess.run(
                [
                    "python3",
                    str(SCRIPT),
                    "--root",
                    str(root),
                    "--release",
                    "2026-07-27",
                ],
                check=True,
            )

            manifest = json.loads(
                (root / "artifact-manifest.json").read_text(encoding="utf-8")
            )
            self.assertEqual(
                0o644,
                stat.S_IMODE((root / "artifact-manifest.json").stat().st_mode),
            )
            self.assertEqual("2026-07-27", manifest["release"])
            by_name = {item["name"]: item for item in manifest["artifacts"]}
            self.assertEqual(
                hashlib.sha256(b"garmin").hexdigest(),
                by_name["map.img"]["sha256"],
            )
            self.assertNotIn("artifact-manifest.json", by_name)
            subprocess.run(
                [
                    "python3",
                    str(VERIFY_SCRIPT),
                    str(root),
                    str(root / "artifact-manifest.json"),
                ],
                check=True,
            )

            (root / "map.img").write_bytes(b"changed")
            result = subprocess.run(
                [
                    "python3",
                    str(VERIFY_SCRIPT),
                    str(root),
                    str(root / "artifact-manifest.json"),
                ],
                check=False,
            )
            self.assertEqual(1, result.returncode)


if __name__ == "__main__":
    unittest.main()
