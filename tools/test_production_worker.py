import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from PIL import Image
import production_worker as worker
from publish_factory_pack import publish_day
from supabase_factory import PRIVATE_BUCKET, PUBLIC_BUCKET, SupabaseFactory

NOW = 1791763200
DAY = "2026-10-12"


class FakeSupabase:
    def __init__(self):
        self.url = "https://example.supabase.co"
        self.objects = {}
        self.rows = {}
        self.updates = []

    def download(self, bucket, path, missing_ok=False):
        value = self.objects.get((bucket, path))
        if value is None and not missing_ok:
            raise RuntimeError("missing object")
        return value

    def download_public(self, path, missing_ok=False):
        return self.download(PUBLIC_BUCKET, path, missing_ok)

    def upload(self, bucket, path, data, content_type="application/octet-stream"):
        self.objects[(bucket, path)] = data

    def list_objects(self, bucket, prefix, limit=100):
        return [{"name": path, "metadata": {"size": len(data)}}
                for (stored_bucket, path), data in self.objects.items()
                if stored_bucket == bucket and path.startswith(prefix)][:limit]

    def upsert(self, table, rows, conflict):
        for row in rows:
            self.rows[(table, str(row[conflict]))] = row

    def update(self, table, values, column, value):
        self.updates.append((table, values, column, value))


def png_bytes(color="red"):
    output = io.BytesIO()
    Image.new("RGB", (64, 64), color).save(output, "PNG")
    return output.getvalue()


def pack_bytes(rating="GENERAL"):
    output = io.BytesIO()
    stickers = [{"name": f"00000000-0000-0000-0000-00000000000{i}.webp",
                 "sha256": "0" * 64} for i in range(3)]
    manifest = {"schema": 3, "packs": [{"id": "00000000-0000-0000-0000-000000000000",
        "name": "Daily", "kind": "STATIC", "rating": rating, "stickers": stickers}]}
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("manifest.json", json.dumps(manifest))
        for item in stickers:
            archive.writestr("packs/00000000-0000-0000-0000-000000000000/" + item["name"], b"fake")
    return output.getvalue()


class ProductionWorkerTest(unittest.TestCase):
    def test_state_archive_round_trip_and_traversal_rejection(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "nested").mkdir()
            (root / "nested/state.json").write_text('{"ok":true}')
            data = worker.state_archive(root)
            restored = root / "restored"
            restored.mkdir()
            worker.safe_restore(data, restored)
            self.assertEqual((restored / "nested/state.json").read_text(), '{"ok":true}')
            bad = io.BytesIO()
            with zipfile.ZipFile(bad, "w") as archive:
                archive.writestr("../outside", "bad")
            with self.assertRaises(ValueError):
                worker.safe_restore(bad.getvalue(), restored)

    def test_inbox_is_persistent_and_deduplicated(self):
        client = FakeSupabase()
        client.objects[(PRIVATE_BUCKET, "inbox/one.png")] = png_bytes()
        client.objects[(PRIVATE_BUCKET, "inbox/copy.png")] = png_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertEqual(worker.sync_inbox(client, root), 1)
            self.assertEqual(worker.sync_inbox(client, root), 0)
            state = json.loads((root / "inbox-state.json").read_text())
            self.assertEqual(len(state["processed"]), 2)
            library = json.loads((root / "reference-images/library.json").read_text())
            self.assertEqual(len(library["assets"]), 1)

    def test_inbox_preserves_pinterest_reference_only_metadata(self):
        client = FakeSupabase()
        data = png_bytes()
        digest = __import__("hashlib").sha256(data).hexdigest()
        client.objects[(PRIVATE_BUCKET, "inbox/" + digest + ".png")] = data
        client.objects[(PRIVATE_BUCKET, "source-metadata/" + digest + ".json")] = json.dumps({
            "source": "pinterest-official", "source_url": "https://www.pinterest.com/pin/12345/",
            "download_url": "https://i.pinimg.com/test.png", "license": "REFERENCE_ONLY",
            "title": "Reference", "credit": "Pinterest source"}).encode()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertEqual(worker.sync_inbox(client, root), 1)
            asset = json.loads((root / "reference-images/library.json").read_text())["assets"][0]
        self.assertEqual(asset["license"], "REFERENCE_ONLY")
        self.assertEqual(asset["source"], "pinterest-official")

    def test_worker_restores_generates_uploads_and_records_pending_pack(self):
        client = FakeSupabase()
        client.objects[(PRIVATE_BUCKET, "inbox/photo.png")] = png_bytes()

        def generator(config, generated, font, now):
            day = generated / DAY
            day.mkdir(parents=True)
            (day / "daily-complete.zip").write_bytes(pack_bytes())
            (day / "references.json").write_text("[]")
            (day / "state.json").write_text(json.dumps({
                "records": [{"caption": str(i)} for i in range(40)], "requests": 8,
                "last_groq_metrics": {"usage": {"total_tokens": 100}}}))
            return "Created 5 stickers (40/40 today)"

        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {
                "GROQ_API_KEY": "test-only", "GROQ_VISION_MODEL": "vision-test"}):
            summary = worker.run_production({"daily_target": 40, "modes": ["photo"]},
                                            Path(temporary), client, NOW, generator)
        self.assertEqual(summary["status"], "complete")
        self.assertEqual(summary["generated"], 40)
        self.assertIn((PRIVATE_BUCKET, "generated/2026-10-12/daily-complete.zip"), client.objects)
        self.assertIn((PRIVATE_BUCKET, worker.STATE_OBJECT), client.objects)
        pack_rows = [value for (table, _), value in client.rows.items() if table == "sticker_packs"]
        self.assertEqual(pack_rows[0]["status"], "pending")
        self.assertEqual(pack_rows[0]["rating"], "GENERAL")

    def test_publisher_updates_public_catalog_and_rejects_mature(self):
        client = FakeSupabase()
        private = (PRIVATE_BUCKET, "generated/2026-10-12/daily-complete.zip")
        client.objects[private] = pack_bytes()
        references = (PRIVATE_BUCKET, "generated/2026-10-12/references.json")
        client.objects[references] = b"[]"
        result = publish_day(client, DAY)
        self.assertEqual(result["published"], "daily-2026-10-12")
        catalog = json.loads(client.objects[(PUBLIC_BUCKET, "catalog/packs.json")])
        self.assertEqual(catalog["packs"][0]["rating"], "GENERAL")
        self.assertEqual(len(client.updates), 1)
        client.objects[private] = pack_bytes("MATURE")
        with self.assertRaises(ValueError):
            publish_day(client, DAY)

    def test_publisher_refuses_reference_only_pinterest_media(self):
        client = FakeSupabase()
        client.objects[(PRIVATE_BUCKET, "generated/2026-10-12/daily-complete.zip")] = pack_bytes()
        client.objects[(PRIVATE_BUCKET, "generated/2026-10-12/references.json")] = json.dumps([{
            "caption": "Private inspiration", "media_credit": {
                "license": "REFERENCE_ONLY", "source": "pinterest-official"}}]).encode()
        with self.assertRaises(ValueError):
            publish_day(client, DAY)

    def test_worker_failure_is_recorded_and_state_is_saved(self):
        client = FakeSupabase()
        def fail(*_):
            raise ValueError("provider detail that should not enter state")
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"GROQ_API_KEY": "test"}):
            with self.assertRaises(ValueError):
                worker.run_production({"daily_target": 40}, Path(temporary), client, NOW, fail)
        row = client.rows[("factory_runs", DAY)]
        self.assertEqual(row["status"], "failed")
        self.assertNotIn("provider detail", row["last_result"])
        self.assertIn((PRIVATE_BUCKET, worker.STATE_OBJECT), client.objects)

    def test_supabase_object_paths_reject_traversal(self):
        with self.assertRaises(ValueError):
            SupabaseFactory._object_path(PRIVATE_BUCKET, "../secret")
        self.assertEqual(SupabaseFactory._object_path(PRIVATE_BUCKET, "inbox/a.png"),
                         "sticker-factory-private/inbox/a.png")


if __name__ == "__main__":
    unittest.main()
