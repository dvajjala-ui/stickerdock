from datetime import date
import unittest

import storage_usage as usage
from supabase_factory import PRIVATE_BUCKET, PUBLIC_BUCKET


class FakeClient:
    def __init__(self, pages):
        self.pages = pages
        self.calls = []

    def list_objects(self, bucket, prefix, limit=100, offset=0):
        self.calls.append((bucket, prefix, limit, offset))
        if offset:
            return []
        return self.pages.get((bucket, prefix), [])


class StorageUsageTest(unittest.TestCase):
    def test_recursive_scan_counts_files_and_skips_folders(self):
        client = FakeClient({
            (PRIVATE_BUCKET, ""): [
                {"name": "inbox", "metadata": None},
                {"name": "generated", "metadata": None},
            ],
            (PRIVATE_BUCKET, "inbox"): [
                {"name": "a.png", "metadata": {"size": 100}},
            ],
            (PRIVATE_BUCKET, "generated"): [
                {"name": "2026-08-01", "metadata": None},
            ],
            (PRIVATE_BUCKET, "generated/2026-08-01"): [
                {"name": "pack.zip", "metadata": {"size": 200}},
            ],
        })
        result = usage.bucket_usage(client, PRIVATE_BUCKET)
        self.assertEqual(result["objects"], 2)
        self.assertEqual(result["bytes"], 300)
        self.assertEqual({item["path"] for item in result["items"]}, {
            "inbox/a.png", "generated/2026-08-01/pack.zip"
        })

    def test_report_warns_and_lists_only_old_generated_days(self):
        client = FakeClient({
            (PRIVATE_BUCKET, ""): [
                {"name": "generated", "metadata": None},
            ],
            (PRIVATE_BUCKET, "generated"): [
                {"name": "2026-08-01", "metadata": None},
                {"name": "2026-10-01", "metadata": None},
            ],
            (PRIVATE_BUCKET, "generated/2026-08-01"): [
                {"name": "old.zip", "metadata": {"size": 700}},
            ],
            (PRIVATE_BUCKET, "generated/2026-10-01"): [
                {"name": "new.zip", "metadata": {"size": 100}},
            ],
            (PUBLIC_BUCKET, ""): [
                {"name": "catalog", "metadata": None},
            ],
            (PUBLIC_BUCKET, "catalog"): [
                {"name": "pack.zip", "metadata": {"size": 100}},
            ],
        })
        quota_mb = 1000 / 1024 / 1024
        report = usage.usage_report(
            client, quota_mb=quota_mb, warn_percent=80, fail_percent=95,
            retention_days=30, today=date(2026, 10, 13)
        )
        self.assertEqual(report["total_bytes"], 900)
        self.assertEqual(report["usage_percent"], 90.0)
        self.assertEqual(report["level"], "warn")
        self.assertEqual(report["private_generated_candidates"], [
            {"day": "2026-08-01", "objects": 1, "bytes": 700}
        ])

    def test_fail_threshold_and_report_only_without_quota(self):
        pages = {
            (PRIVATE_BUCKET, ""): [{"name": "a.webp", "metadata": {"size": 95}}],
            (PUBLIC_BUCKET, ""): [{"name": "b.zip", "metadata": {"size": 5}}],
        }
        quota_mb = 100 / 1024 / 1024
        failed = usage.usage_report(FakeClient(pages), quota_mb, 70, 90, 30)
        self.assertEqual(failed["level"], "fail")
        report_only = usage.usage_report(FakeClient(pages), None, 70, 90, 30)
        self.assertEqual(report_only["level"], "ok")
        self.assertIsNone(report_only["usage_percent"])

    def test_invalid_thresholds_are_rejected(self):
        client = FakeClient({(PRIVATE_BUCKET, ""): [], (PUBLIC_BUCKET, ""): []})
        with self.assertRaises(ValueError):
            usage.usage_report(client, 100, warn_percent=90, fail_percent=80)
        with self.assertRaises(ValueError):
            usage.retention_candidates([], days=0)


if __name__ == "__main__":
    unittest.main()
