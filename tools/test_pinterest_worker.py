"""Keep the legacy Pinterest command fail-closed."""
import unittest

import pinterest_worker


class PinterestWorkerRetirementTest(unittest.TestCase):
    def test_archiver_entry_point_is_retired(self):
        with self.assertRaisesRegex(SystemExit, "Pin archiving is retired"):
            pinterest_worker.main()

    def test_no_callable_download_or_upload_path_remains(self):
        self.assertFalse(hasattr(pinterest_worker, "run_hosted"))
        self.assertFalse(hasattr(pinterest_worker, "upload_downloads"))


if __name__ == "__main__":
    unittest.main()
