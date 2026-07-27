#!/usr/bin/env python3
"""Select a Mapsforge node-index mode from production-sized benchmark logs."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

PASS4 = re.compile(r"^pass4_filter_ways_tile_candidates_millis=(\d+)$", re.MULTILINE)
PEAK_RSS = re.compile(r"^peak_rss_bytes=(\d+)$", re.MULTILINE)
STATUS = re.compile(r"^status=success\b", re.MULTILINE)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(8 * 1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--max-rss-gb", type=float, default=32)
    parser.add_argument("--minimum-improvement-percent", type=float, default=15)
    args = parser.parse_args()

    root = Path(args.root)
    results = []
    for log_path in sorted(root.glob("*.log")):
        name = log_path.stem
        map_path = root / f"{name}.map"
        text = log_path.read_text(encoding="utf-8", errors="replace")
        pass4 = PASS4.findall(text)
        peak = PEAK_RSS.findall(text)
        successful = bool(STATUS.search(text)) and map_path.is_file() and pass4 and peak
        results.append(
            {
                "candidate": name,
                "successful": bool(successful),
                "pass4_millis": int(pass4[-1]) if pass4 else None,
                "peak_rss_bytes": int(peak[-1]) if peak else None,
                "map_sha256": sha256_file(map_path) if successful else None,
            }
        )

    by_name = {result["candidate"]: result for result in results}
    baseline = by_name.get("disk-4096")
    selected = None
    if baseline and baseline["successful"]:
        candidates = []
        rss_limit = int(args.max_rss_gb * 1024**3)
        for result in results:
            if not result["successful"] or result["candidate"] == "disk-4096":
                continue
            result["improvement_percent"] = round(
                100
                * (baseline["pass4_millis"] - result["pass4_millis"])
                / baseline["pass4_millis"],
                2,
            )
            result["strict_parity"] = (
                result["map_sha256"] == baseline["map_sha256"]
            )
            if (
                result["improvement_percent"] >= args.minimum_improvement_percent
                and result["peak_rss_bytes"] < rss_limit
                and result["strict_parity"]
            ):
                candidates.append(result)
        if candidates:
            selected = min(candidates, key=lambda item: item["pass4_millis"])

    summary = {
        "schema": 1,
        "baseline": baseline,
        "results": results,
        "selected": selected,
        "criteria": {
            "max_rss_gb": args.max_rss_gb,
            "minimum_improvement_percent": args.minimum_improvement_percent,
            "strict_byte_parity": True,
        },
    }
    (root / "summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    if selected is None:
        print("No candidate passed the Mapsforge benchmark gates.", file=sys.stderr)
        return 1
    print(
        f"Selected {selected['candidate']}: "
        f"{selected['improvement_percent']}% pass4 improvement"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
