import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from pinterest_autosync.database import Database
from pinterest_autosync.models import Candidate
import pinterest_worker
from supabase_factory import PRIVATE_BUCKET


class FakeSupabase:
    def __init__(self):
        self.objects = {}

    def download(self, bucket, path, missing_ok=False):
        value = self.objects.get((bucket, path))
        if value is None and not missing_ok:
            raise RuntimeError("missing")
        return value

    def upload(self, bucket, path, data, content_type="application/octet-stream"):
        self.objects[(bucket, path)] = data


class PinterestWorkerTest(unittest.TestCase):
    def test_environment_builds_authorized_boards_and_explicit_topics(self):
        config = json.loads((Path(__file__).parent / "pinterest-production.json").read_text())
        with patch.dict(os.environ, {
                "PINTEREST_ACCESS_TOKEN": "test", "PINTEREST_BOARD_IDS": "12345,67890",
                "PINTEREST_UNOFFICIAL_ENABLED": "true", "PINTEREST_TOPICS": "cats,office memes",
                "PINTEREST_DAILY_TARGET": "100", "PINTEREST_PER_RUN": "25"}, clear=True):
            result, rights = pinterest_worker.configure(config)
        self.assertTrue(result["official"]["enabled"])
        self.assertEqual([item["id"] for item in result["official"]["boards"]],
                         ["12345", "67890"])
        self.assertTrue(result["unofficial"]["enabled"])
        self.assertEqual(result["limits"]["daily_downloads"], 100)
        self.assertLessEqual(result["limits"]["max_total_storage_gb"] * 1024 ** 3, 54_000_000)
        self.assertLessEqual(result["limits"]["max_file_mb"] * 1024 ** 2, 20_000_000)
        self.assertEqual(rights, {})

    def test_downloads_upload_with_reference_only_default_and_offload(self):
        client = FakeSupabase()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "downloads/images"
            output.mkdir(parents=True)
            data = b"Pinterest image bytes"
            digest = hashlib.sha256(data).hexdigest()
            local = output / (digest[:24] + ".jpg")
            local.write_bytes(data)
            candidate = Candidate("pin:12345", "official", "board:99999", "12345",
                                  "https://www.pinterest.com/pin/12345/", "image",
                                  "https://i.pinimg.com/test.jpg", {"title": "Example"})
            with Database(root / "pinterest.sqlite3") as database:
                database.upsert_candidate(candidate)
                database.register_hash(digest, "images/" + local.name, len(data))
                database.mark_done(candidate.key, "images/" + local.name, digest, len(data))
            count = pinterest_worker.upload_downloads(client, root, {})
            metadata = json.loads(client.objects[
                (PRIVATE_BUCKET, "source-metadata/" + digest + ".json")])
            self.assertEqual(count, 1)
            self.assertEqual(metadata["license"], "REFERENCE_ONLY")
            self.assertEqual(local.read_bytes(), b"")
            with Database(root / "pinterest.sqlite3") as database:
                self.assertEqual(database.recorded_storage_bytes(), 0)

    def test_access_token_can_discover_boards_without_ids(self):
        config = json.loads((Path(__file__).parent / "pinterest-production.json").read_text())
        with patch.dict(os.environ, {"PINTEREST_ACCESS_TOKEN": "test"}, clear=True):
            result, _ = pinterest_worker.configure(config)
        self.assertTrue(result["official"]["enabled"])
        self.assertEqual(result["official"]["boards"], [])

    def test_per_pin_rights_are_validated(self):
        self.assertEqual(pinterest_worker.rights_map('{"12345":"USER_OWNED"}'),
                         {"12345": "USER_OWNED"})
        for invalid in ('[]', '{"abc":"USER_OWNED"}', '{"12345":"COPYRIGHTED"}'):
            with self.assertRaises(ValueError):
                pinterest_worker.rights_map(invalid)


if __name__ == "__main__":
    unittest.main()
