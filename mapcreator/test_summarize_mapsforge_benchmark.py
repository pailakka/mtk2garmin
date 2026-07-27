#!/usr/bin/env python3

import json
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("summarize_mapsforge_benchmark.py")


class MapsforgeBenchmarkSummaryTest(unittest.TestCase):
    def write_candidate(
        self, root: Path, name: str, pass4: int, peak: int, payload: bytes
    ) -> None:
        (root / f"{name}.log").write_text(
            f"pass4_filter_ways_tile_candidates_millis={pass4}\n"
            f"peak_rss_bytes={peak}\n"
            "status=success elapsed_millis=123\n",
            encoding="utf-8",
        )
        (root / f"{name}.map").write_bytes(payload)

    def test_selects_fastest_candidate_that_passes_all_gates(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            payload = b"identical-map"
            self.write_candidate(root, "disk-4096", 1000, 10_000, payload)
            self.write_candidate(root, "disk-16384", 900, 10_000, payload)
            self.write_candidate(root, "disk-65536", 700, 20_000, payload)
            self.write_candidate(root, "memory", 600, 20_000, b"different")
            subprocess.run(
                ["python3", str(SCRIPT), "--root", str(root)],
                check=True,
            )
            summary = json.loads((root / "summary.json").read_text(encoding="utf-8"))
            self.assertEqual("disk-65536", summary["selected"]["candidate"])


if __name__ == "__main__":
    unittest.main()
