import base64
import copy
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import uuid

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
        self.assertEqual(result["access_mode"], "single-owner-token")
        self.assertEqual(self.client.objects, before)

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

    def test_review_publish_catalog_and_unpublish_flow(self):
        row = self.migrate()
        admin.dispatch({"action": "pack_status", "id": row["id"], "status": "reviewed"}, self.client)
        result = admin.dispatch({"action": "publish", "id": row["id"]}, self.client)
        self.assertEqual(result["status"], "published")
        catalog = json.loads(self.client.objects[(PUBLIC_BUCKET, "catalog/packs.json")])
        entry = catalog["packs"][0]
        self.assertEqual(entry["rating"], "GENERAL")
        self.assertEqual(entry["categories"], ["GENERAL"])
        self.assertEqual(__import__("hashlib").sha256(self.client.objects[(PUBLIC_BUCKET, "catalog/packs/" + entry["file"])]).hexdigest(), entry["sha256"])
        admin.dispatch({"action": "unpublish", "id": row["id"]}, self.client)
        self.assertEqual(json.loads(self.client.objects[(PUBLIC_BUCKET, "catalog/packs.json")])["packs"], [])
        self.assertTrue(self.client.objects[(PRIVATE_BUCKET, row["private_path"])])
        self.assertTrue(any(e["action"] == "catalog.published" for e in self.client.events))

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

    def test_endpoint_fails_closed_before_dispatch(self):
        request = object.__new__(handler)
        request.headers = {"X-StickerDock-Token": "wrong"}
        request.rfile = io.BytesIO(b'{"action":"overview"}')
        with patch.dict(os.environ, {"STUDIO_ACCESS_TOKEN": "x" * 32}), patch("api.admin.send_json") as send, patch("api.admin.dispatch") as call:
            request.do_POST()
        self.assertEqual(send.call_args.args[1], 401)
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


from PIL import Image

if __name__ == "__main__":
    unittest.main()
