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

    def list_objects(self, bucket, prefix, limit=100, offset=0):
        return [{"name": path, "metadata": {"size": len(data)}}
                for (stored_bucket, path), data in self.objects.items()
                if stored_bucket == bucket and path.startswith(prefix)][offset:offset + limit]

    def upsert(self, table, rows, conflict):
        for row in rows:
            self.rows[(table, str(row[conflict]))] = row

    def update(self, table, values, column, value):
        self.updates.append((table, values, column, value))

    def find(self, table, column, value):
        return self.rows.get((table, str(value)))


def png_bytes(color="red"):
    output = io.BytesIO()
    Image.new("RGB", (64, 64), color).save(output, "PNG")
    return output.getvalue()


def pack_bytes(rating="GENERAL"):
    output = io.BytesIO()
    asset = io.BytesIO()
    Image.new("RGB", (512, 512), "red").save(asset, "WEBP")
    raw = asset.getvalue()
    stickers = [{"name": f"00000000-0000-0000-0000-00000000000{i}.webp",
                 "sha256": __import__("hashlib").sha256(raw).hexdigest()} for i in range(3)]
    manifest = {"schema": 3, "packs": [{"id": "00000000-0000-0000-0000-000000000000",
        "name": "Daily", "kind": "STATIC", "rating": rating, "stickers": stickers}]}
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("manifest.json", json.dumps(manifest))
        for item in stickers:
            archive.writestr("packs/00000000-0000-0000-0000-000000000000/" + item["name"], raw)
    return output.getvalue()


class ProductionWorkerTest(unittest.TestCase):
    def test_public_archive_must_fit_android_download_bound(self):
        from publish_factory_pack import MAX_PUBLIC_PACK_BYTES, validate_pack
        with self.assertRaises(ValueError):
            validate_pack(b"x" * (MAX_PUBLIC_PACK_BYTES + 1))

    def test_animation_duration_matches_phone_import_rules(self):
        from publish_factory_pack import validate_animation
        frames = [Image.new("RGB", (512, 512), color) for color in ("red", "blue")]
        for duration, valid in ((125, True), (5, False), (6000, False)):
            output = io.BytesIO()
            frames[0].save(output, "WEBP", save_all=True, append_images=frames[1:],
                           duration=duration, loop=0)
            if valid:
                validate_animation(output.getvalue())
            else:
                with self.assertRaises(ValueError):
                    validate_animation(output.getvalue())

    def test_bad_inbox_file_does_not_block_valid_inputs(self):
        client = FakeSupabase()
        client.objects[(PRIVATE_BUCKET, "inbox/broken.png")] = b"not an image"
        client.objects[(PRIVATE_BUCKET, "inbox/good.png")] = png_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertEqual(worker.sync_inbox(client, root), 1)
            self.assertEqual(worker.sync_inbox(client, root), 0)
            self.assertEqual(len(json.loads((root / "inbox-state.json").read_text())["processed"]), 2)

    def test_failure_preserves_successful_progress_and_usage(self):
        client = FakeSupabase()
        def fail(config, generated, font, now):
            day = generated / DAY
            day.mkdir(parents=True)
            (day / "state.json").write_text(json.dumps({"records": [{"caption": "Saved"}],
                "requests": 2, "last_groq_metrics": {"usage": {"total_tokens": 100}}}))
            raise ValueError("Provider unavailable")
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(ValueError):
                worker.run_production({"daily_target": 40}, Path(temporary), client, NOW, fail)
        row = client.rows[("factory_runs", DAY)]
        self.assertEqual((row["generated"], row["attempts"]), (1, 2))
        self.assertEqual(row["groq_metrics"]["usage"]["total_tokens"], 100)

    def test_licensed_library_is_mirrored_once_with_reuse_metadata(self):
        client = FakeSupabase()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            references = root / "reference-images"
            references.mkdir()
            (references / "example.png").write_bytes(png_bytes())
            (references / "library.json").write_text(json.dumps({"assets": [{
                "file": "example.png", "sha256": "source-hash", "license": "CC0",
                "source_url": "https://commons.wikimedia.org/example", "download_url": "https://upload.wikimedia.org/example.png"}]}))
            worker.mirror_licensed_inputs(client, root)
            objects = dict(client.objects)
            worker.mirror_licensed_inputs(client, root)
            self.assertEqual(client.objects, objects)
            self.assertEqual(worker.sync_inbox(client, root), 0)
            metadata = next(json.loads(data) for (_, path), data in objects.items()
                            if path.startswith("source-metadata/"))
            self.assertEqual(metadata["license"], "CC0")

    def test_real_generator_resumes_on_different_runner_directory(self):
        config = {"daily_target": 6, "batch_size": 3, "interval_seconds": 60,
                  "modes": ["illustration"]}
        client = FakeSupabase()
        ideas = [{"caption": f"Test caption {i}", "mood": "happy", "color": "#FFD166",
                  "source": None} for i in range(3)]
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second, \
                patch.dict(os.environ, {"GROQ_API_KEY": "test"}), \
                patch("daily_stickers.groq_ideas", side_effect=[ideas,
                    [{**idea, "caption": idea["caption"] + " again"} for idea in ideas]]):
            worker.run_production(config, Path(first), client, NOW)
            result = worker.run_production(config, Path(second), client, NOW + 61)
        self.assertEqual(result["generated"], 6)
        self.assertEqual(result["status"], "complete")
        with zipfile.ZipFile(io.BytesIO(client.objects[(PRIVATE_BUCKET,
                "generated/" + DAY + "/daily-complete.zip")])) as archive:
            self.assertEqual(len(json.loads(archive.read("manifest.json"))["packs"][0]["stickers"]), 6)

    def test_inbox_pagination_moves_past_processed_first_page(self):
        client = FakeSupabase()
        for index in range(101):
            client.objects[(PRIVATE_BUCKET, f"inbox/{index:03d}.png")] = png_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for _ in range(4):
                worker.sync_inbox(client, root)
            self.assertEqual(len(json.loads((root / "inbox-state.json").read_text())["processed"]), 101)

    def test_reference_cache_removes_old_files_but_preserves_pending_input(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            refs = root / "reference-images"
            refs.mkdir()
            (refs / "pending.png").write_bytes(b"1234")
            (refs / "old.png").write_bytes(b"5678")
            day = root / "generated-stickers" / DAY
            day.mkdir(parents=True)
            (day / "state.json").write_text(json.dumps({"pending": {"image_path": str(refs / "pending.png")}}))
            worker.trim_reference_cache(root, 4)
            self.assertTrue((refs / "pending.png").exists())
            self.assertFalse((refs / "old.png").exists())

    def test_repeated_complete_day_preserves_approval(self):
        client = FakeSupabase()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            day = root / DAY
            day.mkdir()
            (day / "daily-complete.zip").write_bytes(pack_bytes())
            (day / "state.json").write_text(json.dumps({"records": [{"caption": str(i)} for i in range(3)]}))
            config = {"daily_target": 3, "rating": "GENERAL", "modes": ["illustration"]}
            worker.persist_run(client, root, config, NOW, "complete")
            row = next(value for (table, _), value in client.rows.items() if table == "sticker_packs")
            row["status"] = "approved"
            worker.persist_run(client, root, config, NOW, "complete")
            self.assertEqual(row["status"], "approved")
            self.assertEqual(client.find("sticker_packs", "id", row["id"])["status"], "approved")

    def test_publisher_rejects_unlisted_and_corrupt_archive_entries(self):
        from publish_factory_pack import validate_pack
        data = pack_bytes()
        with io.BytesIO(data) as output:
            with zipfile.ZipFile(output, "a") as archive:
                archive.writestr("../unexpected", b"bad")
            with self.assertRaises(ValueError):
                validate_pack(output.getvalue())

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
        client.objects[references] = json.dumps([{"caption": str(i)} for i in range(3)]).encode()
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
                "license": "REFERENCE_ONLY", "source": "pinterest-official"}}] * 3).encode()
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
