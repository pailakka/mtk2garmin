#!/usr/bin/env python3
import argparse
import struct
import sys
from pathlib import Path


SUBFILE_SUFFIXES = ("RGN", "TRE", "LBL", "NET", "NOD", "DEM", "MDR", "SRT", "MPS", "TYP", "GMP")


def img_entries(path):
    with path.open("rb") as img:
        img.seek(0x400)

        for _ in range(50000):
            record = img.read(512)
            if len(record) < 512:
                break

            name_bytes = record[1:12]
            if record[0] != 1 or not all(32 <= byte < 127 for byte in name_bytes):
                continue

            name = name_bytes.decode("ascii").rstrip()
            if not name:
                continue

            if not name.startswith("MAKEGMAP") and not name.endswith(SUBFILE_SUFFIXES):
                continue

            size = struct.unpack_from("<I", record, 12)[0]
            yield name, size


def dos_name(directory_name):
    stem = directory_name[:8].rstrip()
    suffix = directory_name[8:].rstrip()
    return f"{stem}.{suffix}" if suffix else stem


def check_img(path, max_subfile_bytes, max_img_bytes, max_tiles, required_typ_name):
    entries = list(img_entries(path))
    if not entries:
        print(f"{path}: no Garmin IMG directory entries found", file=sys.stderr)
        return False

    largest_name, largest_size = max(entries, key=lambda entry: entry[1])
    large_entries = [(name, size) for name, size in entries if size >= max_subfile_bytes]
    tile_count = len({name[:8] for name, _ in entries if name[:8].isdigit() and name.endswith("TRE")})
    img_size = path.stat().st_size

    print(
        f"{path}: size={img_size / 1048576:.1f}MiB entries={len(entries)} "
        f"tiles={tile_count} max={largest_name} {largest_size / 1048576:.3f}MiB"
    )

    ok = True
    if required_typ_name:
        typ_names = {
            dos_name(name).lower()
            for name, _ in entries
            if name.endswith("TYP")
        }
        required_typ_name = required_typ_name.lower()
        accepted_typ_names = {
            required_typ_name,
            f"000{required_typ_name}",
        }
        if typ_names.isdisjoint(accepted_typ_names):
            print(
                f"{path}: required TYP {required_typ_name} not found; "
                f"found {', '.join(sorted(typ_names)) or 'none'}",
                file=sys.stderr,
            )
            ok = False

    if img_size >= max_img_bytes:
        print(f"{path}: IMG file is {img_size / 1048576:.1f}MiB", file=sys.stderr)
        ok = False

    if tile_count > max_tiles:
        print(f"{path}: has {tile_count} tiles", file=sys.stderr)
        ok = False

    if large_entries:
        large_entries.sort(key=lambda entry: entry[1], reverse=True)
        print(f"{path}: {len(large_entries)} subfiles exceed the limit", file=sys.stderr)

    for name, size in large_entries[:10]:
        print(f"{path}: {name} is {size / 1048576:.3f}MiB", file=sys.stderr)
        ok = False

    if len(large_entries) > 10:
        print(f"{path}: {len(large_entries) - 10} more oversized subfiles omitted", file=sys.stderr)

    return ok


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--max-subfile-mib", type=float, default=4.0)
    parser.add_argument("--max-img-mib", type=float, default=1900.0)
    parser.add_argument("--max-tiles", type=int, default=1000)
    parser.add_argument("--required-typ-name", default="perus.typ")
    parser.add_argument("img", nargs="+", type=Path)
    args = parser.parse_args()

    max_subfile_bytes = int(args.max_subfile_mib * 1048576)
    max_img_bytes = int(args.max_img_mib * 1048576)
    ok = True

    for path in args.img:
        if not path.exists():
            print(f"{path}: missing", file=sys.stderr)
            ok = False
            continue

        ok = check_img(
            path,
            max_subfile_bytes,
            max_img_bytes,
            args.max_tiles,
            args.required_typ_name,
        ) and ok

    if not ok:
        print(
            "Garmin IMG compatibility check failed: "
            f"subfile limit {args.max_subfile_mib:g}MiB, "
            f"IMG limit {args.max_img_mib:g}MiB, "
            f"tile limit {args.max_tiles}",
            file=sys.stderr,
        )
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
