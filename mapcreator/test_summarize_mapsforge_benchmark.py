#!/usr/bin/env python3

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import summarize_mapsforge_benchmark as benchmark


SCRIPT = Path(__file__).with_name("summarize_mapsforge_benchmark.py")


class MapsforgeBenchmarkSummaryTest(unittest.TestCase):
    def write_candidate(
        self,
        root: Path,
        name: str,
        *,
        elapsed: int,
        pass4: int,
        peak: int,
        poi_nodes: int = 10,
        parity: bool = True,
    ) -> None:
        metrics = {key: 1 for key in benchmark.CORRECTNESS_KEYS}
        metrics["poi_nodes"] = poi_nodes
        metrics["missing_way_nodes"] = 0
        lines = [
            *(f"{key}={value}" for key, value in metrics.items()),
            f"pass4_filter_ways_tile_candidates_millis={pass4}",
            "write_map_file_millis=1000",
            f"peak_rss_bytes={peak}",
            "progress phase=write_map_file global_store_load_millis=1000",
            f"status=success elapsed_millis={elapsed} peak_rss_bytes={peak}",
        ]
        (root / f"{name}.log").write_text("\n".join(lines) + "\n", encoding="utf-8")
        (root / f"{name}.map").write_bytes(f"map-{name}".encode())
        if name != "baseline" and parity:
            for gate in ("reader", "render", "blocks"):
                (root / f"{name}.{gate}.ok").touch()

    def summarize(self, root: Path, *, check: bool = True) -> dict:
        subprocess.run(
            ["python3", str(SCRIPT), "--root", str(root)],
            check=check,
        )
        return json.loads((root / "summary.json").read_text(encoding="utf-8"))

    def test_uses_lower_rss_when_runtimes_differ_by_less_than_three_percent(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_candidate(
                root, "baseline", elapsed=6_100_000, pass4=4_900_000, peak=10_000
            )
            self.write_candidate(
                root, "disk-65536", elapsed=3_000_000, pass4=2_000_000, peak=20_000
            )
            self.write_candidate(
                root, "memory", elapsed=2_940_000, pass4=1_900_000, peak=30_000
            )

            summary = self.summarize(root)
            self.assertEqual("disk-65536", summary["selected"]["candidate"])
            self.assertFalse(summary["criteria"]["byte_identity_required"])

    def test_rejects_candidate_with_count_or_reader_parity_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_candidate(
                root, "baseline", elapsed=6_100_000, pass4=4_900_000, peak=10_000
            )
            self.write_candidate(
                root,
                "disk-65536",
                elapsed=3_000_000,
                pass4=2_000_000,
                peak=20_000,
                poi_nodes=11,
            )
            self.write_candidate(
                root,
                "memory",
                elapsed=2_500_000,
                pass4=1_800_000,
                peak=30_000,
                parity=False,
            )

            completed = subprocess.run(
                ["python3", str(SCRIPT), "--root", str(root)],
                check=False,
            )
            self.assertEqual(1, completed.returncode)
            summary = json.loads((root / "summary.json").read_text(encoding="utf-8"))
            self.assertIsNone(summary["selected"])
            by_name = {item["candidate"]: item for item in summary["results"]}
            self.assertFalse(by_name["disk-65536"]["gates"]["count_parity"])
            self.assertFalse(by_name["memory"]["gates"]["reader_parity"])


if __name__ == "__main__":
    unittest.main()
