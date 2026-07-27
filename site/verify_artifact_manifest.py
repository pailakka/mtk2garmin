#!/usr/bin/env python3
"""Verify release files against an artifact manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(8 * 1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root")
    parser.add_argument("manifest")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    errors = []
    names = set()
    for artifact in manifest.get("artifacts", []):
        name = artifact.get("name", "")
        relative = Path(name)
        if (
            not name
            or relative.is_absolute()
            or ".." in relative.parts
            or name in names
        ):
            errors.append(f"invalid artifact name: {name}")
            continue
        names.add(name)
        path = root / relative
        if not path.is_file():
            errors.append(f"missing artifact: {name}")
            continue
        if path.stat().st_size != artifact.get("size"):
            errors.append(f"size mismatch: {name}")
            continue
        if sha256_file(path) != artifact.get("sha256"):
            errors.append(f"sha256 mismatch: {name}")
    if not names:
        errors.append("artifact manifest is empty")
    if errors:
        print("; ".join(errors), file=sys.stderr)
        return 1
    print(f"Verified {len(names)} artifacts for release {manifest.get('release')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
