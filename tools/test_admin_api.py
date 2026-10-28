import base64
import hashlib
import copy
from datetime import datetime, timezone
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
import urllib.error
from unittest.mock import patch
import uuid
import zipfile

from PIL import Image

from api import _admin as admin
from api.admin import handler
from api import _shared
from test_production_worker import FakeSupabase, pack_bytes, png_bytes, DAY, NOW
from supabase_factory import PRIVATE_BUCKET, PUBLIC_BUCKET
import production_worker


class AdminStore(FakeSupabase):
    def __init__(self):
        super().__init__()
        self.busy = False
        self.events = []

    def list_rows(self, table, *args):
        return [copy.deepcopy(row) for (name, _), row in self.rows.items() if name == table]

    def _request(self, method, path, data=None, headers=None):
        if path == "/rest/v1/admin_events" and method == "POST":
            self.events.extend(json.loads(data))
            return b""
        raise AssertionError("Unexpected request " + path)

    def update_versioned(self, table, values, row_id, version):
        row = self.rows[(table, row_id)]
        if row["version"] != version:
            raise ValueError("Pack changed")
        row.update(values)
        return copy.deepcopy(row)

    def upsert(self, table, rows, conflict):
        for row in rows:
            if table == "admin_packs":
                row = {"version": 1, **row}
            self.rows[(table, str(row[conflict]))] = copy.deepcopy(row)

    def find(self, table, column, value):
        row = super().find(table, column, value)
        return copy.deepcopy(row) if row else None

    def rpc(self, function, payload):
        return [{"acquired": not self.busy}] if function == "acquire_catalog_lease" else [{"released": True}]


class AdminApiTest(unittest.TestCase):
    def test_retry_uses_same_atomic_admission_as_new_jobs(self):
        request_id = str(uuid.uuid4())
        client = AdminStore()
        with patch.object(client, "rpc", return_value=[{
                "accepted": True, "reason": "retried", "job_status": "queued"}]) as rpc:
            result = admin.queue_action(client, {"id": request_id, "operation": "retry"})
        self.assertEqual(result, {"status": "queued"})
        rpc.assert_called_once_with("admit_creator_request", {"p_id": request_id, "p_retry": True})

    def test_retry_rejects_full_queue_or_changed_worker_claim(self):
        for reason, message in (("full", "queue is busy"), ("changed", "Request changed")):
            with self.subTest(reason=reason), patch.object(self.client, "rpc", return_value=[{
                    "accepted": False, "reason": reason, "job_status": None}]):
                with self.assertRaisesRegex(ValueError, message):
                    admin.queue_action(self.client, {"id": str(uuid.uuid4()), "operation": "retry"})

    def setUp(self):
        self.client = AdminStore()
        self.origin = str(uuid.uuid4())
        path = "generated/" + DAY + "/daily-complete.zip"
        raw = pack_bytes()
        self.client.objects[(PRIVATE_BUCKET, path)] = raw
        self.client.objects[(PRIVATE_BUCKET, "generated/" + DAY + "/references.json")] = json.dumps([{"caption": "Original"}] * 3).encode()
        self.client.rows[("sticker_packs", self.origin)] = {"id": self.origin, "day": DAY,
            "name": "Daily " + DAY, "creator": "Factory", "rating": "GENERAL", "status": "pending",
            "private_path": path, "sha256": __import__("hashlib").sha256(raw).hexdigest(), "sticker_count": 3}

    def migrate(self):
        result = admin.dispatch({"action": "migrate_factory", "id": self.origin}, self.client)
        return self.client.find("admin_packs", "id", result["id"])

    def test_overview_returns_live_data_and_empty_catalog_without_mutation(self):
        before = copy.deepcopy(self.client.objects)
        result = admin.overview(self.client)
        self.assertEqual(result["factory_packs"][0]["id"], self.origin)
        self.assertEqual(result["catalog"], {"version": 1, "packs": []})
        self.assertEqual(result["packs"], [])
        self.assertEqual(result["access_mode"], "open-workspace")
        self.assertEqual(self.client.objects, before)

    def test_overview_exposes_cooldown_then_retry_due_without_changing_database_status(self):
        today = datetime.now(timezone.utc).date().isoformat()
        row = {"day": today, "status": "running", "groq_metrics": {
            "recovery": {"http_status": 429, "retry_at": NOW + 100}}}
        self.client.rows[("factory_runs", today)] = row
        with patch.object(admin.time, "time", return_value=NOW):
            run = admin.overview(self.client)["runs"][0]
            self.assertEqual((run["display_status"], run["retry_at"]), ("waiting", NOW + 100))
        with patch.object(admin.time, "time", return_value=NOW + 101):
            self.assertEqual(admin.overview(self.client)["runs"][0]["display_status"], "retry_due")
        self.assertNotIn("display_status", row)

    def test_overview_labels_old_partial_day_without_mutating_row(self):
        row = {"day": "2026-10-11", "status": "running", "generated": 5,
               "target": 40, "groq_metrics": {}}
        self.client.rows[("factory_runs", row["day"])] = row
        run = admin.overview(self.client)["runs"][0]
        self.assertEqual(run["display_status"], "partial")
        self.assertIn("5/40", run["last_result"])
        self.assertNotIn("display_status", row)

    def test_migration_is_idempotent_and_keeps_original_archive(self):
        before = dict(self.client.objects)
        row = self.migrate()
        repeated = admin.dispatch({"action": "migrate_factory", "id": self.origin}, self.client)
        self.assertFalse(repeated["migrated"])
        self.assertEqual(repeated["id"], row["id"])
        self.assertEqual(len(self.client.list_rows("admin_packs")), 1)
        for key, value in before.items():
            self.assertEqual(self.client.objects[key], value)
        self.assertEqual(self.client.objects[(PRIVATE_BUCKET, row["private_path"])], before[(PRIVATE_BUCKET, "generated/" + DAY + "/daily-complete.zip")])

    def test_portable_animated_zip_import_is_private_idempotent_and_reviewable(self):
        frames = [Image.new("RGBA", (512, 512), color) for color in ("red", "blue", "green")]
        media = io.BytesIO()
        frames[0].save(media, "WEBP", save_all=True, append_images=frames[1:], duration=125, loop=0)
        asset = media.getvalue()
        pack_id = str(uuid.uuid4())
        stickers = [{"name": str(uuid.uuid4()) + ".webp", "sha256": hashlib.sha256(asset).hexdigest(),
                     "label": label, "tags": "animal"} for label in ("First", "Second", "Third")]
        manifest = {"schema": 5, "packs": [{"id": pack_id, "name": "Moving animals", "kind": "ANIMATED",
                    "rating": "GENERAL", "category": "REACTIONS", "stickers": stickers}]}
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as archive:
            archive.writestr("manifest.json", json.dumps(manifest))
            for sticker in stickers:
                archive.writestr("packs/" + pack_id + "/" + sticker["name"], asset)
        rights = [{"media_credit": {"license": "CC0", "source_url": "https://commons.wikimedia.org/wiki/File:Example.webm"}}
                  for _ in stickers]
        payload = {"action": "pack_import", "archive": base64.b64encode(output.getvalue()).decode(),
                   "references": rights, "creator": "StickerDock"}
        first = admin.dispatch(payload, self.client)
        self.assertTrue(first["imported"])
        self.assertEqual((first["kind"], first["sticker_count"]), ("ANIMATED", 3))
        row = self.client.find("admin_packs", "id", first["id"])
        self.assertEqual(row["status"], "draft")
        self.assertFalse(any(bucket == PUBLIC_BUCKET for bucket, _ in self.client.objects))
        self.assertEqual(admin.dispatch(payload, self.client), {"id": first["id"], "imported": False})
        self.assertEqual(len(self.client.list_rows("admin_packs")), 1)
        self.assertEqual(admin.dispatch({"action": "pack_status", "id": first["id"],
                                         "version": row["version"], "status": "reviewed"}, self.client), {"status": "reviewed"})

    def test_portable_zip_rejects_bad_encoding_and_missing_rights_without_storage(self):
        encoded = base64.b64encode(pack_bytes()).decode()
        for archive, references in (("!" * 16, []), (encoded, []),
                                    (encoded, [{"media_credit": {"license": "REFERENCE_ONLY"}}] * 3),
                                    (encoded, [{"media_credit": {"license": "CC0", "source_url": "https://pinterest.com/pin/1"}}] * 3)):
            with self.subTest(archive=archive[:12], references=references):
                with self.assertRaises(ValueError):
                    admin.dispatch({"action": "pack_import", "archive": archive,
                                    "references": references}, self.client)
        self.assertEqual(self.client.list_rows("admin_packs"), [])

    def test_review_publish_catalog_and_unpublish_flow(self):
        row = self.migrate()
        admin.dispatch({"action": "pack_status", "id": row["id"], "status": "reviewed"}, self.client)
        result = admin.dispatch({"action": "publish", "id": row["id"]}, self.client)
        self.assertEqual(result["status"], "published")
        catalog = json.loads(self.client.objects[(PUBLIC_BUCKET, "catalog/packs.json")])
        entry = catalog["packs"][0]
        self.assertEqual(entry["rating"], "GENERAL")
        self.assertEqual(entry["categories"], ["GENERAL"])
        self.assertEqual(entry["sticker_count"], 3)
        self.assertEqual(entry["kinds"], ["STATIC"])
        thumbnail = self.client.objects[(PUBLIC_BUCKET, "catalog/previews/" + entry["preview"]["file"])]
        self.assertEqual(__import__("hashlib").sha256(thumbnail).hexdigest(), entry["preview"]["sha256"])
        with Image.open(io.BytesIO(thumbnail)) as image:
            self.assertEqual(image.size, (576, 384))
            self.assertFalse(getattr(image, "is_animated", False))
        self.assertEqual(__import__("hashlib").sha256(self.client.objects[(PUBLIC_BUCKET, "catalog/packs/" + entry["file"])]).hexdigest(), entry["sha256"])
        admin.dispatch({"action": "unpublish", "id": row["id"]}, self.client)
        self.assertEqual(json.loads(self.client.objects[(PUBLIC_BUCKET, "catalog/packs.json")])["packs"], [])
        self.assertTrue(self.client.objects[(PRIVATE_BUCKET, row["private_path"])])
        self.assertTrue(any(e["action"] == "catalog.published" for e in self.client.events))

    def test_publication_reports_safe_stage_and_keeps_reviewed_draft_on_storage_failure(self):
        row = self.migrate()
        admin.dispatch({"action": "pack_status", "id": row["id"], "status": "reviewed"}, self.client)
        with patch.object(self.client, "upload", side_effect=admin.SupabaseError("Supabase HTTP 503; response body omitted")):
            with self.assertRaisesRegex(ValueError, "uploading the pack archive"):
                admin.dispatch({"action": "publish", "id": row["id"]}, self.client)
        self.assertEqual(self.client.find("admin_packs", "id", row["id"])["status"], "reviewed")
        self.assertFalse(any(bucket == PUBLIC_BUCKET for bucket, _ in self.client.objects))

    def test_preview_storage_failure_keeps_pack_downloadable_without_thumbnail(self):
        row = self.migrate()
        admin.dispatch({"action": "pack_status", "id": row["id"], "status": "reviewed"}, self.client)
        original_upload = self.client.upload

        def upload(bucket, path, data, content_type="application/octet-stream"):
            if path.startswith("catalog/previews/"):
                raise admin.SupabaseError("Supabase HTTP 400; response body omitted")
            return original_upload(bucket, path, data, content_type)

        with patch.object(self.client, "upload", side_effect=upload):
            result = admin.dispatch({"action": "publish", "id": row["id"]}, self.client)
        self.assertEqual(result["status"], "published")
        entry = json.loads(self.client.objects[(PUBLIC_BUCKET, "catalog/packs.json")])["packs"][0]
        self.assertNotIn("preview", entry)
        archive = self.client.objects[(PUBLIC_BUCKET, "catalog/packs/" + entry["file"])]
        self.assertEqual(__import__("hashlib").sha256(archive).hexdigest(), entry["sha256"])

        # After the bucket allows WebP, republishing the same reviewed archive
        # backfills the cover without duplicating a catalog row or changing ZIPs.
        version = self.client.find("admin_packs", "id", row["id"])["version"]
        repaired = admin.dispatch({"action": "publish", "id": row["id"], "version": version}, self.client)
        self.assertEqual(repaired["status"], "published")
        entries = json.loads(self.client.objects[(PUBLIC_BUCKET, "catalog/packs.json")])["packs"]
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["sha256"], entry["sha256"])
        self.assertIn("preview", entries[0])
        self.assertEqual(self.client.objects[(PUBLIC_BUCKET, "catalog/packs/" + entry["file"])], archive)

    def test_unreviewed_pack_cannot_publish(self):
        row = self.migrate()
        with self.assertRaisesRegex(ValueError, "Review"):
            admin.dispatch({"action": "publish", "id": row["id"]}, self.client)
        self.assertFalse(any(bucket == PUBLIC_BUCKET for bucket, _ in self.client.objects))

    def test_missing_or_reference_only_provenance_cannot_be_reviewed(self):
        del self.client.objects[(PRIVATE_BUCKET, "generated/" + DAY + "/references.json")]
        row = self.migrate()
        with self.assertRaisesRegex(ValueError, "private-reference-only"):
            admin.dispatch({"action": "pack_status", "id": row["id"], "status": "reviewed"}, self.client)

    def test_busy_catalog_cannot_migrate_or_publish(self):
        self.client.busy = True
        with self.assertRaisesRegex(ValueError, "publication is active"):
            admin.dispatch({"action": "migrate_factory", "id": self.origin}, self.client)
        self.assertFalse(self.client.list_rows("admin_packs"))

    def test_preview_pagination_bounds_response_and_returns_exact_bytes(self):
        row = self.migrate()
        detail = admin.dispatch({"action": "pack_detail", "id": row["id"]}, self.client)
        self.assertEqual(len(detail["stickers"]), 3)
        self.assertTrue(detail["stickers"][0]["preview"].startswith("data:image/webp;base64,"))
        self.assertLess(len(json.dumps(detail)), 4_000_000)

    def test_cover_uses_real_pack_art_without_changing_private_draft(self):
        row = self.migrate()
        before = copy.deepcopy(self.client.objects)
        result = admin.dispatch({"action": "pack_cover", "id": row["id"]}, self.client)
        self.assertEqual((result["id"], result["sha256"]), (row["id"], row["sha256"]))
        data = base64.b64decode(result["preview"].split(",", 1)[1])
        self.assertLessEqual(len(data), 100_000)
        with Image.open(io.BytesIO(data)) as cover:
            self.assertEqual(cover.size, (240, 240))
            self.assertNotEqual(cover.convert("RGB").getpixel((60, 60)), (0, 0, 0))
        for key, value in before.items():
            self.assertEqual(self.client.objects[key], value)
        self.assertEqual(set(self.client.objects) - set(before), {
            (PRIVATE_BUCKET, "admin/covers/v1/" + row["sha256"] + ".webp")})

    def test_mature_pack_cover_is_not_available_to_shared_dashboard(self):
        row = self.migrate()
        self.client.rows[("admin_packs", row["id"])]["rating"] = "MATURE"
        with self.assertRaisesRegex(ValueError, "Mature pack covers"):
            admin.pack_cover(self.client, {"id": row["id"]})

    def test_cover_cache_avoids_archive_download_and_checks_rating_before_cache(self):
        row = self.migrate()
        first = admin.pack_cover(self.client, {"id": row["id"]})
        with patch.object(admin, "pack_archive", side_effect=AssertionError("Do not fetch the archive again")):
            self.assertEqual(admin.pack_cover(self.client, {"id": row["id"]}), first)
            self.client.rows[("admin_packs", row["id"])]["rating"] = "MATURE"
            with self.assertRaisesRegex(ValueError, "Mature pack covers"):
                admin.pack_cover(self.client, {"id": row["id"]})

    def test_cover_checksum_change_invalidates_cache_and_bad_cache_is_rebuilt(self):
        row = self.migrate()
        key = (PRIVATE_BUCKET, "admin/covers/v1/" + row["sha256"] + ".webp")
        self.client.objects[key] = b"invalid cache"
        first = admin.pack_cover(self.client, {"id": row["id"]})
        self.assertNotEqual(self.client.objects[key], b"invalid cache")
        raw = self.client.objects[(PRIVATE_BUCKET, row["private_path"])]
        # A byte-different but valid immutable ZIP produces another digest/key.
        raw += b"\x00"
        new_hash = hashlib.sha256(raw).hexdigest()
        self.client.rows[("admin_packs", row["id"])]["sha256"] = new_hash
        self.client.objects[(PRIVATE_BUCKET, row["private_path"])] = raw
        with patch.object(admin, "pack_archive", wraps=admin.pack_archive) as archive:
            second = admin.pack_cover(self.client, {"id": row["id"]})
        archive.assert_called_once()
        self.assertNotEqual(first["sha256"], second["sha256"])
        self.assertIn((PRIVATE_BUCKET, "admin/covers/v1/" + new_hash + ".webp"), self.client.objects)

    def test_optional_cache_transport_failures_do_not_hide_valid_artwork(self):
        row = self.migrate()
        original_download = self.client.download
        def unavailable_cache(bucket, path, missing_ok=False):
            if path.startswith("admin/covers/"):
                raise urllib.error.URLError("cache network unavailable")
            return original_download(bucket, path, missing_ok)
        for error in (admin.SupabaseError("HTTP 503"), urllib.error.URLError("network"), TimeoutError()):
            with self.subTest(error=type(error).__name__), \
                    patch.object(self.client, "download", side_effect=unavailable_cache), \
                    patch.object(self.client, "upload", side_effect=error):
                result = admin.pack_cover(self.client, {"id": row["id"]})
                with Image.open(io.BytesIO(base64.b64decode(result["preview"].split(",", 1)[1]))) as cover:
                    self.assertEqual(cover.size, (240, 240))

    def test_pack_edits_preserve_bytes_and_reset_review(self):
        row = self.migrate()
        details = admin.pack_detail(self.client, {"id": row["id"]})
        original = self.client.objects[(PRIVATE_BUCKET, row["private_path"])]
        result = admin.dispatch({"action": "pack_save", "id": row["id"], "version": 1,
            "name": "Edited reactions", "creator": "Owner",
            "stickers": [{"name": details["stickers"][0]["name"], "label": "Chai first", "tags": "chai, work"}]}, self.client)
        self.assertTrue(result["saved"])
        updated = self.client.find("admin_packs", "id", row["id"])
        self.assertEqual(updated["status"], "draft")
        self.assertEqual(updated["version"], 2)
        after = admin.pack_detail(self.client, {"id": row["id"]})
        self.assertEqual(after["stickers"][0]["label"], "Chai first")
        self.assertEqual(after["stickers"][0]["sha256"], details["stickers"][0]["sha256"])
        self.assertEqual(self.client.objects[(PRIVATE_BUCKET, row["private_path"])], original)

    def test_visible_caption_redraws_reusable_static_photo_and_preserves_factory_archive(self):
        row = self.migrate()
        original_archive = self.client.objects[(PRIVATE_BUCKET, row["private_path"])]
        source = "inbox/" + "b" * 64 + ".png"
        self.client.rows[("admin_packs", row["id"])]["references"][0] = {
            "media_credit": {"license": "CC0", "source": "wikimedia-commons",
                             "source_url": "https://commons.wikimedia.org/wiki/File:Reaction",
                             "inbox_path": source}}
        detail = admin.pack_detail(self.client, {"id": row["id"]})
        sticker = detail["stickers"][0]
        self.assertTrue(sticker["caption_editable"])
        image = "data:image/png;base64," + base64.b64encode(png_bytes("blue")).decode()
        with patch.object(_shared, "source_metadata", return_value={"license": "CC0", "source": "wikimedia-commons"}), \
             patch.object(_shared, "fetch_inbox", return_value=image) as fetch:
            admin.save_pack(self.client, {"id": row["id"], "version": 1,
                "name": row["name"], "creator": row["creator"],
                "stickers": [{"name": sticker["name"], "caption": "Wait, what?"}]})
        fetch.assert_called_once_with(source)
        revised = admin.pack_detail(self.client, {"id": row["id"]})["stickers"][0]
        self.assertEqual(revised["label"], "Wait, what?")
        self.assertNotEqual(revised["sha256"], sticker["sha256"])
        self.assertEqual(self.client.objects[(PRIVATE_BUCKET, row["private_path"])], original_archive)
        self.assertEqual(self.client.find("admin_packs", "id", row["id"])["status"], "draft")

    def test_visible_caption_rejects_unlicensed_or_missing_original(self):
        row = self.migrate()
        sticker = admin.pack_detail(self.client, {"id": row["id"]})["stickers"][0]
        payload = {"id": row["id"], "version": 1, "name": row["name"], "creator": row["creator"],
                   "stickers": [{"name": sticker["name"], "caption": "Wait, what?"}]}
        with self.assertRaisesRegex(ValueError, "reusable original"):
            admin.save_pack(self.client, payload)
        self.client.rows[("admin_packs", row["id"])]["references"][0] = {
            "media_credit": {"license": "CC0", "source": "pinterest",
                             "inbox_path": "inbox/" + "b" * 64 + ".png"}}
        with self.assertRaisesRegex(ValueError, "reusable original"):
            admin.save_pack(self.client, payload)
        self.assertEqual(self.client.find("admin_packs", "id", row["id"])["version"], 1)

    def test_visible_caption_fails_closed_when_live_source_rights_change(self):
        row = self.migrate()
        source = "inbox/" + "b" * 64 + ".png"
        self.client.rows[("admin_packs", row["id"])]["references"][0] = {
            "media_credit": {"license": "CC0", "inbox_path": source}}
        sticker = admin.pack_detail(self.client, {"id": row["id"]})["stickers"][0]
        with patch.object(_shared, "source_metadata", return_value={"license": "REFERENCE_ONLY"}), \
             patch.object(_shared, "fetch_inbox") as fetch:
            with self.assertRaisesRegex(ValueError, "no longer has reusable rights"):
                admin.save_pack(self.client, {"id": row["id"], "version": 1,
                    "name": row["name"], "creator": row["creator"],
                    "stickers": [{"name": sticker["name"], "caption": "Wait, what?"}]})
            fetch.assert_not_called()

    def test_published_pack_cannot_be_edited_or_archived(self):
        row = self.migrate()
        self.client.rows[("admin_packs", row["id"])]["status"] = "published"
        with self.assertRaisesRegex(ValueError, "Unpublish"):
            admin.save_pack(self.client, {"id": row["id"]})
        with self.assertRaisesRegex(ValueError, "Unpublish"):
            admin.pack_status(self.client, {"id": row["id"], "status": "archived"})

    def test_version_mismatch_prevents_stale_review(self):
        row = self.migrate()
        with self.assertRaisesRegex(ValueError, "refresh"):
            admin.pack_status(self.client, {"id": row["id"], "version": 0, "status": "reviewed"})

    def test_adding_sources_preserves_originals_and_requires_fresh_review(self):
        row = self.migrate()
        old_archive = self.client.objects[(PRIVATE_BUCKET, row["private_path"])]
        target = admin.pack_detail(self.client, {"id": row["id"]})["target_packs"][0]["id"]
        source = "inbox/" + "b" * 64 + ".png"
        image = "data:image/png;base64," + base64.b64encode(png_bytes("blue")).decode()
        payload = {"id": row["id"], "version": 1, "target_pack_id": target, "sources": [source, source]}
        with patch.object(_shared, "fetch_inbox", return_value=image), patch.object(_shared, "source_metadata", return_value={"license": "USER_OWNED"}):
            self.assertEqual(admin.dispatch({"action": "pack_add_sources", **payload}, self.client), {"added": 1})
        updated = self.client.find("admin_packs", "id", row["id"])
        self.assertEqual((updated["status"], updated["version"], updated["sticker_count"]), ("draft", 2, 4))
        self.assertEqual(updated["references"][-1]["media_credit"]["license"], "USER_OWNED")
        self.assertEqual(old_archive, self.client.objects[(PRIVATE_BUCKET, row["private_path"])])
        self.assertEqual(len(admin.pack_detail(self.client, {"id": row["id"]})["stickers"]), 4)
        with self.assertRaisesRegex(ValueError, "refresh"):
            admin.add_pack_sources(self.client, payload)

    def test_category_change_is_in_the_exported_archive(self):
        row = self.migrate()
        admin.save_pack(self.client, {"id": row["id"], "version": 1, "name": row["name"],
            "creator": row["creator"], "category": "WORK", "stickers": []})
        self.assertEqual(admin.pack_detail(self.client, {"id": row["id"]})["categories"], ["WORK"])

    def test_settings_reject_batches_the_generator_cannot_render(self):
        for batch in (1, 2):
            with self.assertRaisesRegex(ValueError, "3–10"):
                admin.save_settings(self.client, {"enabled": True, "daily_target": 3, "batch_size": batch})

    def test_checksum_failure_prevents_migration(self):
        self.client.rows[("sticker_packs", self.origin)]["sha256"] = "a" * 64
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.migrate()
        self.assertFalse(self.client.list_rows("admin_packs"))

    def test_settings_are_bounded_and_pause_stops_worker_before_io(self):
        for target in (0, 101, True):
            with self.assertRaises(ValueError):
                admin.save_settings(self.client, {"enabled": True, "daily_target": target, "batch_size": 5})
        admin.save_settings(self.client, {"enabled": False, "daily_target": 40, "batch_size": 5, "topics": []})
        with tempfile.TemporaryDirectory() as tmp:
            result = production_worker.run_production({}, Path(tmp), self.client, NOW, generator=lambda *_: self.fail("Paused worker rendered"))
            self.assertEqual(result["status"], "paused")
            self.assertEqual(list(Path(tmp).iterdir()), [])

    def test_adult_discovery_requires_private_mature_setting(self):
        payload = {"enabled": True, "daily_target": 40, "batch_size": 5,
                   "topics": [], "content_categories": ["ADULT_HUMOR"],
                   "discovery_enabled": True, "auto_migrate_completed": True}
        with self.assertRaisesRegex(ValueError, "MATURE"):
            admin.save_settings(self.client, {**payload, "rating": "GENERAL"})
        admin.save_settings(self.client, {**payload, "rating": "MATURE"})
        saved = self.client.find("factory_settings", "id", "default")
        self.assertEqual(saved["rating"], "MATURE")

    def test_foreign_browser_origin_is_rejected_before_dispatch(self):
        request = object.__new__(handler)
        request.headers = {"Host": "stickerdock.vercel.app", "Origin": "https://other.example"}
        request.rfile = io.BytesIO(b'{"action":"overview"}')
        with patch.dict(os.environ, {"STUDIO_ACCESS_TOKEN": "x" * 32}), patch("api.admin.send_json") as send, patch("api.admin.dispatch") as call:
            request.do_POST()
        self.assertEqual(send.call_args.args[1], 403)
        call.assert_not_called()

    def test_create_pack_keeps_source_rights_and_normalizes_unique_stickers(self):
        paths = ["inbox/" + str(i) * 64 + ".png" for i in (1,2,3)]
        colors = dict(zip(paths, ("red", "green", "blue")))
        def image(path):
            return "data:image/png;base64," + base64.b64encode(png_bytes(colors[path])).decode()
        with patch.object(_shared, "fetch_inbox", side_effect=image), patch.object(_shared, "source_metadata", return_value={"license": "USER_OWNED"}):
            result = admin.create_pack(self.client, {"name": "Originals", "creator": "Owner", "sources": paths})
        row = self.client.find("admin_packs", "id", result["id"])
        self.assertEqual(row["sticker_count"], 3)
        self.assertTrue(all(ref["media_credit"]["license"] == "USER_OWNED" for ref in row["references"]))
        for item in admin.pack_detail(self.client, {"id": row["id"]})["stickers"]:
            raw = base64.b64decode(item["preview"].split(",",1)[1])
            with Image.open(io.BytesIO(raw)) as image:
                self.assertEqual(image.size, (512,512))
                self.assertLessEqual(len(raw), 100_000)


if __name__ == "__main__":
    unittest.main()
