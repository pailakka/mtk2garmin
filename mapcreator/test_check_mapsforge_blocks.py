#!/usr/bin/env python3

import contextlib
import io
import struct
import tempfile
import unittest
from pathlib import Path

import check_mapsforge_blocks


def write_fixture(path: Path, block_sizes: list[int]) -> None:
    index_size = len(block_sizes) * 5
    offsets = []
    offset = index_size
    for block_size in block_sizes:
        offsets.append(offset)
        offset += block_size

    subfile = b"".join(value.to_bytes(5, "big") for value in offsets)
    subfile += b"".join(b"x" * size for size in block_sizes)

    prefix_size = 4 + 8 + 8
    table_size = 1 + check_mapsforge_blocks.SUBFILE_METADATA_SIZE
    header_size = prefix_size + table_size
    header_end = len(check_mapsforge_blocks.MAGIC) + 4 + header_size
    file_size = header_end + len(subfile)

    body = struct.pack(">IQ", 5, file_size)
    body += b"\0" * 8
    body += b"\1"
    body += bytes((7, 0, 7))
    body += struct.pack(">QQ", header_end, len(subfile))

    path.write_bytes(
        check_mapsforge_blocks.MAGIC
        + struct.pack(">i", len(body))
        + body
        + subfile
    )


class CheckMapsforgeBlocksTest(unittest.TestCase):
    def test_accepts_reader_safe_blocks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "safe.map"
            write_fixture(path, [4, 10_000_000])

            with contextlib.redirect_stdout(io.StringIO()):
                self.assertTrue(
                    check_mapsforge_blocks.inspect(path, 10_000_000)
                )

    def test_rejects_block_larger_than_reader_buffer(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "oversized.map"
            write_fixture(path, [10_000_001])

            with contextlib.redirect_stdout(io.StringIO()):
                with contextlib.redirect_stderr(io.StringIO()):
                    self.assertFalse(
                        check_mapsforge_blocks.inspect(path, 10_000_000)
                    )

    def test_rejects_truncated_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "truncated.map"
            path.write_bytes(check_mapsforge_blocks.MAGIC)

            with self.assertRaises(check_mapsforge_blocks.MapsforgeFormatError):
                check_mapsforge_blocks.inspect(path, 10_000_000)


if __name__ == "__main__":
    unittest.main()
