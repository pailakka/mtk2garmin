#!/usr/bin/env python3
"""Generate the immutable artifact contract for one published release."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(8 * 1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--release", required=True)
    parser.add_argument("--output", default="artifact-manifest.json")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    output = root / args.output
    artifacts = []
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        if path == output:
            continue
        artifacts.append(
            {
                "name": str(path.relative_to(root)),
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
            }
        )

    manifest = {
        "schema": 1,
        "release": args.release,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "artifacts": artifacts,
    }
    descriptor, temporary_name = tempfile.mkstemp(
        dir=root,
        prefix=f".{output.name}.",
        suffix=".tmp",
    )
    temporary = Path(temporary_name)
    try:
        os.fchmod(descriptor, 0o644)
        with os.fdopen(descriptor, "w", encoding="utf-8") as target:
            json.dump(manifest, target, ensure_ascii=True, indent=2, sort_keys=True)
            target.write("\n")
            target.flush()
            os.fsync(target.fileno())
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
