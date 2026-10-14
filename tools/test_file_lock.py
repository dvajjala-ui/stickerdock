import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from file_lock import acquire


class FileLockTest(unittest.TestCase):
    def test_competing_process_is_blocked_and_close_releases_lock(self):
        script = """
import sys
from file_lock import acquire
with open(sys.argv[1], 'a+b') as handle:
    try:
        acquire(handle)
    except BlockingIOError:
        sys.exit(7)
"""
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "worker.lock"
            env = {**os.environ, "PYTHONPATH": str(Path(__file__).parent.resolve())}

            def contender():
                return subprocess.run([sys.executable, "-c", script, str(path)],
                                      env=env, capture_output=True, text=True, timeout=15)

            with path.open("a+b") as handle:
                acquire(handle)
                result = contender()
                self.assertEqual(result.returncode, 7, result.stderr)
            result = contender()
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(path.stat().st_size, 0)

    def test_exception_releases_generator_lock(self):
        from daily_stickers import locked
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(RuntimeError, "interrupted"):
                with locked(root):
                    raise RuntimeError("interrupted")
            with locked(root):
                self.assertTrue((root / "generator.lock").exists())
