#!/usr/bin/env python3
"""Evaluate production Mapsforge candidates with runtime and parity gates."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


STATUS = re.compile(r"^status=success\s+elapsed_millis=(\d+)\b", re.MULTILINE)
INTEGER_METRIC = re.compile(r"^([a-z][a-z0-9_]*)=(\d+)$", re.MULTILINE)
GLOBAL_LOAD = re.compile(r"\bglobal_store_load_millis=(\d+)\b")
CORRECTNESS_KEYS = (
    "nodes",
    "ways",
    "relations",
    "way_refs",
    "relation_members",
    "tagged_elements",
    "poi_nodes",
    "poi_tile_assignments",
    "optimized_poi_tags",
    "optimized_way_tags",
    "multipolygon_relations",
    "render_relevant_multipolygon_relations",
    "relation_way_members",
    "multipolygon_member_refs",
    "simple_multipolygon_relations_with_inner_rings",
    "multipolygon_inner_rings_attached",
    "inner_ways_without_additional_tags",
    "partial_multipolygon_relations",
    "unsupported_multipolygon_relations",
    "ways_needing_handling",
    "ways_with_renderable_tags",
    "ways_overlapping_bbox",
    "way_tile_candidates",
    "missing_way_nodes",
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(8 * 1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def last_integer_metrics(text: str) -> dict[str, int]:
    metrics: dict[str, int] = {}
    for name, value in INTEGER_METRIC.findall(text):
        metrics[name] = int(value)
    return metrics


def load_result(root: Path, name: str) -> dict[str, object]:
    log_path = root / f"{name}.log"
    map_path = root / f"{name}.map"
    text = log_path.read_text(encoding="utf-8", errors="replace") if log_path.is_file() else ""
    metrics = last_integer_metrics(text)
    status = STATUS.findall(text)
    global_load = GLOBAL_LOAD.findall(text)
    successful = bool(status) and map_path.is_file() and map_path.stat().st_size > 0
    return {
        "candidate": name,
        "successful": successful,
        "elapsed_millis": int(status[-1]) if status else metrics.get("elapsed_millis"),
        "pass4_millis": metrics.get("pass4_filter_ways_tile_candidates_millis"),
        "write_map_file_millis": metrics.get("write_map_file_millis"),
        "peak_rss_bytes": metrics.get("peak_rss_bytes"),
        "global_store_load_millis": int(global_load[-1]) if global_load else None,
        "map_sha256": sha256_file(map_path) if successful else None,
        "metrics": {key: metrics.get(key) for key in CORRECTNESS_KEYS},
        "reader_parity": (root / f"{name}.reader.ok").is_file(),
        "render_parity": (root / f"{name}.render.ok").is_file(),
        "block_limit": (root / f"{name}.blocks.ok").is_file(),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--baseline", default="baseline")
    parser.add_argument("--max-rss-gb", type=float, default=28)
    parser.add_argument("--max-total-minutes", type=float, default=60)
    parser.add_argument("--max-pass4-minutes", type=float, default=40)
    parser.add_argument("--max-global-load-seconds", type=float, default=90)
    args = parser.parse_args()

    root = Path(args.root)
    names = sorted(path.stem for path in root.glob("*.map"))
    results = [load_result(root, name) for name in names]
    by_name = {result["candidate"]: result for result in results}
    baseline = by_name.get(args.baseline)
    if baseline is None or not baseline["successful"]:
        print("Mapsforge benchmark baseline is missing or unsuccessful.", file=sys.stderr)
        return 1

    rss_limit = int(args.max_rss_gb * 1024**3)
    total_limit = int(args.max_total_minutes * 60_000)
    pass4_limit = int(args.max_pass4_minutes * 60_000)
    load_limit = int(args.max_global_load_seconds * 1_000)
    baseline_metrics = baseline["metrics"]
    eligible = []

    for result in results:
        if result["candidate"] == args.baseline:
            continue
        invariant_deltas = {
            key: result["metrics"].get(key)
            for key in CORRECTNESS_KEYS
            if result["metrics"].get(key) != baseline_metrics.get(key)
        }
        result["invariant_deltas"] = invariant_deltas
        result["count_parity"] = not invariant_deltas
        elapsed = result["elapsed_millis"]
        baseline_elapsed = baseline["elapsed_millis"]
        result["improvement_percent"] = (
            round(100 * (baseline_elapsed - elapsed) / baseline_elapsed, 2)
            if elapsed is not None and baseline_elapsed
            else None
        )
        result["gates"] = {
            "successful": bool(result["successful"]),
            "total_runtime": elapsed is not None and elapsed < total_limit,
            "pass4_runtime": result["pass4_millis"] is not None
            and result["pass4_millis"] < pass4_limit,
            "peak_rss": result["peak_rss_bytes"] is not None
            and result["peak_rss_bytes"] < rss_limit,
            "global_store_load": result["global_store_load_millis"] is not None
            and result["global_store_load_millis"] < load_limit,
            "missing_way_nodes": result["metrics"].get("missing_way_nodes") == 0,
            "count_parity": result["count_parity"],
            "reader_parity": result["reader_parity"],
            "render_parity": result["render_parity"],
            "block_limit": result["block_limit"],
        }
        result["eligible"] = all(result["gates"].values())
        if result["eligible"]:
            eligible.append(result)

    selected = None
    if eligible:
        by_runtime = sorted(eligible, key=lambda item: item["elapsed_millis"])
        selected = by_runtime[0]
        if len(by_runtime) > 1:
            fastest, second = by_runtime[:2]
            runtime_difference = second["elapsed_millis"] - fastest["elapsed_millis"]
            difference_percent = 100 * runtime_difference / second["elapsed_millis"]
            if difference_percent < 3:
                selected = min(
                    eligible,
                    key=lambda item: (item["peak_rss_bytes"], item["elapsed_millis"]),
                )

    summary = {
        "schema": 2,
        "baseline": baseline,
        "results": results,
        "selected": selected,
        "criteria": {
            "max_rss_gb": args.max_rss_gb,
            "max_total_minutes": args.max_total_minutes,
            "max_pass4_minutes": args.max_pass4_minutes,
            "max_global_load_seconds": args.max_global_load_seconds,
            "runtime_selection_threshold_percent": 3,
            "byte_identity_required": False,
            "reader_render_parity_required": True,
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
        f"{selected['elapsed_millis'] / 60_000:.2f} minutes, "
        f"{selected['peak_rss_bytes'] / 1024**3:.2f} GiB peak RSS"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
