#!/usr/bin/env python3
"""Reject Mapsforge files containing blocks the official reader will skip."""

from __future__ import annotations

import argparse
import struct
import sys
from dataclasses import dataclass
from pathlib import Path

MAGIC = b"mapsforge binary OSM"
DEBUG_INDEX_SIGNATURE = b"+++IndexStart+++"
OFFSET_MASK = (1 << 39) - 1
READER_MAXIMUM_BUFFER_SIZE = 10_000_000
SUBFILE_METADATA_SIZE = 19


class MapsforgeFormatError(ValueError):
    """The file does not contain structurally valid Mapsforge metadata."""


@dataclass(frozen=True)
class Subfile:
    base_zoom: int
    min_zoom: int
    max_zoom: int
    start: int
    size: int


def read_exact(source, size: int) -> bytes:
    data = source.read(size)
    if len(data) != size:
        raise MapsforgeFormatError("unexpected end of file")
    return data


def read_u64(data: bytes) -> int:
    return struct.unpack(">Q", data)[0]


def find_subfiles(source, file_size: int, header_end: int) -> list[Subfile]:
    for count in range(1, 256):
        table_start = header_end - 1 - count * SUBFILE_METADATA_SIZE
        if table_start < len(MAGIC) + 4:
            break

        source.seek(table_start)
        if read_exact(source, 1)[0] != count:
            continue

        subfiles = []
        for _ in range(count):
            base_zoom, min_zoom, max_zoom = read_exact(source, 3)
            start = read_u64(read_exact(source, 8))
            size = read_u64(read_exact(source, 8))
            subfiles.append(
                Subfile(base_zoom, min_zoom, max_zoom, start, size)
            )

        if any(
            subfile.min_zoom > subfile.base_zoom
            or subfile.base_zoom > subfile.max_zoom
            or subfile.max_zoom > 24
            or subfile.size < 5
            for subfile in subfiles
        ):
            continue
        if subfiles[0].start != header_end:
            continue
        if any(
            current.start + current.size != following.start
            for current, following in zip(subfiles, subfiles[1:])
        ):
            continue
        if subfiles[-1].start + subfiles[-1].size != file_size:
            continue
        return subfiles

    raise MapsforgeFormatError("could not locate a valid zoom-interval table")


def block_sizes(source, subfile: Subfile) -> list[int]:
    source.seek(subfile.start)
    debug_size = 0
    if read_exact(source, len(DEBUG_INDEX_SIGNATURE)) == DEBUG_INDEX_SIGNATURE:
        debug_size = len(DEBUG_INDEX_SIGNATURE)

    source.seek(subfile.start + debug_size)
    first_entry = int.from_bytes(read_exact(source, 5), "big") & OFFSET_MASK
    if first_entry < debug_size + 5 or (first_entry - debug_size) % 5 != 0:
        raise MapsforgeFormatError(
            f"invalid tile index size in base-zoom {subfile.base_zoom} subfile"
        )

    tile_count = (first_entry - debug_size) // 5
    source.seek(subfile.start + debug_size)
    offsets = [
        int.from_bytes(read_exact(source, 5), "big") & OFFSET_MASK
        for _ in range(tile_count)
    ]

    if offsets[0] != first_entry:
        raise MapsforgeFormatError(
            f"invalid first block pointer in base-zoom {subfile.base_zoom} subfile"
        )
    if any(
        current > following
        for current, following in zip(offsets, offsets[1:])
    ):
        raise MapsforgeFormatError(
            f"non-monotonic block pointers in base-zoom {subfile.base_zoom} subfile"
        )
    if offsets[-1] > subfile.size:
        raise MapsforgeFormatError(
            f"block pointer exceeds base-zoom {subfile.base_zoom} subfile"
        )

    return [
        following - current
        for current, following in zip(offsets, offsets[1:] + [subfile.size])
    ]


def inspect(path: Path, maximum_block_size: int) -> bool:
    file_size = path.stat().st_size
    with path.open("rb") as source:
        if read_exact(source, len(MAGIC)) != MAGIC:
            raise MapsforgeFormatError("invalid Mapsforge magic bytes")

        header_size = struct.unpack(">i", read_exact(source, 4))[0]
        if header_size <= 0:
            raise MapsforgeFormatError(f"invalid header size: {header_size}")
        header_end = len(MAGIC) + 4 + header_size
        if header_end >= file_size:
            raise MapsforgeFormatError("header extends beyond the file")

        source.seek(len(MAGIC) + 4 + 4)
        declared_file_size = read_u64(read_exact(source, 8))
        if declared_file_size != file_size:
            raise MapsforgeFormatError(
                f"declared file size {declared_file_size} != actual {file_size}"
            )

        subfiles = find_subfiles(source, file_size, header_end)
        oversized = []
        total_blocks = 0
        largest_size = 0
        largest_zoom = 0
        largest_index = 0

        for subfile in subfiles:
            sizes = block_sizes(source, subfile)
            total_blocks += len(sizes)
            for block_index, size in enumerate(sizes):
                if size > largest_size:
                    largest_size = size
                    largest_zoom = subfile.base_zoom
                    largest_index = block_index
                if size > maximum_block_size:
                    oversized.append((subfile.base_zoom, block_index, size))

    print(
        f"{path}: subfiles={len(subfiles)} blocks={total_blocks} "
        f"max={largest_size} base_zoom={largest_zoom} block={largest_index} "
        f"limit={maximum_block_size}"
    )
    for base_zoom, block_index, size in oversized[:20]:
        print(
            f"oversized Mapsforge block: base_zoom={base_zoom} "
            f"block={block_index} size={size} limit={maximum_block_size}",
            file=sys.stderr,
        )
    if len(oversized) > 20:
        print(
            f"... and {len(oversized) - 20} more oversized blocks",
            file=sys.stderr,
        )
    return not oversized


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Check that Mapsforge tile blocks fit the official reader buffer."
        )
    )
    parser.add_argument(
        "--max-block-bytes",
        type=int,
        default=READER_MAXIMUM_BUFFER_SIZE,
    )
    parser.add_argument("maps", nargs="+", type=Path)
    args = parser.parse_args()

    if args.max_block_bytes < 1:
        parser.error("--max-block-bytes must be positive")

    passed = True
    for path in args.maps:
        try:
            passed = inspect(path, args.max_block_bytes) and passed
        except (OSError, MapsforgeFormatError) as error:
            print(f"{path}: {error}", file=sys.stderr)
            passed = False
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
