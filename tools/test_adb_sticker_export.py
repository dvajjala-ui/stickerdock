#!/usr/bin/env python3
"""Local smoke tests for the non-root ADB ZIP batch packager."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

SCRIPT = Path(__file__).resolve().parent / "adb_sticker_export.py"


class ExportScriptTests(unittest.TestCase):
    def test_existing_files_dedupe_and_batch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "raw" / "personal").mkdir(parents=True)
            (root / "raw" / "business").mkdir(parents=True)
            (root / "raw" / "personal" / "one.webp").write_bytes(b"sample1")
            (root / "raw" / "personal" / "duplicate.webp").write_bytes(b"sample1")
            (root / "raw" / "business" / "another.webp").write_bytes(b"sample2")
            command = [sys.executable, str(SCRIPT), "--existing-files", "--output", str(root), "--max-files", "1"]
            result = subprocess.run(command, capture_output=True, text=True, check=False)
            self.assertEqual(0, result.returncode, result.stderr)
            batches = sorted((root / "sticker_zips").glob("*.zip"))
            self.assertEqual(2, len(batches))
            combined = []
            for zip_path in batches:
                with ZipFile(zip_path) as archive:
                    for item in archive.namelist():
                        self.assertNotIn("..", item)
                        combined.append(archive.read(item))
            self.assertCountEqual([b"sample1", b"sample2"], combined)


if __name__ == "__main__":
    unittest.main()
