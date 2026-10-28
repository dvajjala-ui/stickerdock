import io
import json
import os
from pathlib import Path
import tempfile
import unittest
import urllib.error
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
        row = self.rows.get((table, str(value)))
        if row is not None:
            row.update(values)

    def list_rows(self, table, order="updated_at.desc", limit=100):
        rows = [row for (stored_table, _), row in self.rows.items() if stored_table == table]
        return sorted(rows, key=lambda row: row.get("day", ""), reverse=True)[:limit]

    def find(self, table, column, value):
        return self.rows.get((table, str(value)))

    def rpc(self, function, payload):
        return [{"acquired": True}] if function == "acquire_catalog_lease" else [{"released": True}]


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
    def saved_batch(self, runtime, day=DAY):
        directory = runtime / "generated-stickers" / day
        directory.mkdir(parents=True, exist_ok=True)
        records = []
        for index in range(5):
            path = directory / f"{index}.webp"
            Image.new("RGB", (512, 512), "red").save(path, "WEBP")
            records.append({"id": str(worker.uuid.uuid5(worker.uuid.NAMESPACE_URL, f"saved:{day}:{index}")),
                "file": str(path), "caption": f"Reaction {index}", "mood": "happy",
                "kind": "STATIC", "rating": "GENERAL", "category": "GENERAL",
                "media_credit": {"license": "CC0", "credit": "Fixture"}})
        worker.atomic_json(directory / "state.json", {"records": records,
            "batches": [{"start": 0, "end": 5}], "requests": 1})
        return directory

    def recovery_client(self):
        client = FakeSupabase()
        client.rows[("factory_settings", "default")] = {"enabled": True, "daily_target": 40,
            "batch_size": 5, "rating": "GENERAL", "auto_migrate_completed": True}
        client.rows[("factory_runs", DAY)] = {"day": DAY, "status": "running", "generated": 5,
            "target": 40, "groq_metrics": {"postprocess_complete": False}}
        return client

    def test_interrupted_batch_recovers_on_fresh_runner_after_midnight_without_ai(self):
        client = self.recovery_client()
        original_upsert = client.upsert
        failed = []
        def fail_draft_once(table, rows, conflict):
            if table == "admin_packs" and not failed:
                failed.append(True)
                raise OSError("interrupted draft write")
            return original_upsert(table, rows, conflict)
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            root = Path(first)
            self.saved_batch(root)
            client.upload(PRIVATE_BUCKET, worker.STATE_OBJECT, worker.state_archive(root))
            with patch.object(client, "upsert", side_effect=fail_draft_once), \
                 patch.object(worker, "sync_inbox", return_value=0), \
                 patch.object(worker, "configure", return_value={"daily_target": 40, "batch_size": 5, "rating": "GENERAL", "modes": ["illustration"]}), \
                 patch.object(worker, "ensure_content_brief", side_effect=lambda c, conf, controls, now: (conf, {"source_mode": "fixture"})), \
                 patch.object(worker, "mirror_licensed_inputs"):
                with self.assertRaises(OSError):
                    worker.run_production({"daily_target": 40, "batch_size": 5}, root, client,
                        NOW + 86390, generator=lambda *_: "Saved output")
            self.assertFalse(client.rows[("factory_runs", DAY)]["groq_metrics"]["postprocess_complete"])
            fresh = Path(second)
            generator = unittest.mock.Mock(side_effect=AssertionError("Recovery must not generate"))
            result = worker.run_production({"daily_target": 40, "batch_size": 5}, fresh, client,
                                           NOW + 86401, generator=generator)
            generator.assert_not_called()
            self.assertEqual(result["historical_delivery"]["days"], [DAY])
            draft_id = result["historical_delivery"]["draft_ids"][0]
            draft = client.rows[("admin_packs", draft_id)]
            raw = client.download(PRIVATE_BUCKET, draft["private_path"])
            self.assertEqual(__import__("hashlib").sha256(raw).hexdigest(), draft["sha256"])
            self.assertTrue(client.rows[("factory_runs", DAY)]["groq_metrics"]["draft_delivery_complete"])
            state = json.loads((fresh / "generated-stickers" / DAY / "state.json").read_text())
            self.assertEqual(state["delivered_batches"], [draft_id])
            draft.update(name="Reviewed name", status="reviewed")
            replay = worker.recover_previous_batches(client, fresh / "generated-stickers", "2026-10-13", 5)
            self.assertEqual(replay["days"], [])
            self.assertEqual(draft["name"], "Reviewed name")

    def test_retention_keeps_unacknowledged_and_unreadable_days(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = self.saved_batch(root)
            unknown = directory.parent / "2026-10-10"
            unknown.mkdir()
            (unknown / "state.json").write_text("broken json")
            (unknown / "keep.webp").write_bytes(b"keep")
            worker.prune_old_days(directory.parent, NOW + 20 * 86400)
            self.assertTrue((directory / "0.webp").exists())
            self.assertTrue((unknown / "keep.webp").exists())

    def test_missing_historical_media_blocks_recovery_without_fabricating_a_draft(self):
        client = self.recovery_client()
        with tempfile.TemporaryDirectory() as temporary:
            runtime = Path(temporary)
            directory = self.saved_batch(runtime)
            (directory / "0.webp").unlink()
            generator = unittest.mock.Mock(side_effect=AssertionError("No replacement AI calls"))
            result = worker.run_production({"batch_size": 5}, runtime, client, NOW + 86401, generator)
            self.assertEqual(result["status"], "attention_required")
            self.assertEqual(result["historical_delivery"]["blocked_days"], [DAY])
            self.assertEqual(len([key for key in client.rows if key[0] == "admin_packs"]), 0)
            worker.prune_old_days(directory.parent, NOW + 20 * 86400)
            self.assertTrue((directory / "1.webp").exists())
            generator.assert_not_called()

    def test_failed_historical_recovery_preserves_today_counters_and_saved_media(self):
        client = self.recovery_client()
        today = "2026-10-13"
        client.rows[("factory_runs", today)] = {"day": today, "generated": 17, "attempts": 4}
        with tempfile.TemporaryDirectory() as temporary:
            runtime = Path(temporary)
            directory = self.saved_batch(runtime)
            with patch.object(client, "upload", side_effect=[OSError("storage unavailable"), None]):
                with self.assertRaises(OSError):
                    worker.run_production({"batch_size": 5}, runtime, client, NOW + 86401)
            self.assertEqual(client.rows[("factory_runs", today)]["generated"], 17)
            self.assertEqual(client.rows[("factory_runs", today)]["attempts"], 4)
            self.assertTrue((directory / "0.webp").exists())

    def test_acknowledgement_repairs_metrics_after_interrupted_status_write(self):
        client = self.recovery_client()
        with tempfile.TemporaryDirectory() as temporary:
            runtime = Path(temporary)
            directory = self.saved_batch(runtime)
            ids = worker.migrate_ready_batches(client, directory.parent, DAY, 5)
            # Equivalent to dying after durable ack but before factory_runs update.
            outcome = worker.recover_previous_batches(client, directory.parent, "2026-10-13", 5)
            self.assertEqual(outcome["days"], [DAY])
            self.assertTrue(client.rows[("factory_runs", DAY)]["groq_metrics"]["postprocess_complete"])
            self.assertEqual(len(ids), 1)

    def test_historical_replay_is_bounded_and_keeps_unprocessed_artwork(self):
        client = self.recovery_client()
        with tempfile.TemporaryDirectory() as temporary:
            runtime = Path(temporary)
            for day in ("2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11"):
                self.saved_batch(runtime, day)
            generated = runtime / "generated-stickers"
            first = worker.recover_previous_batches(client, generated, DAY, 5)
            self.assertEqual(len(first["days"]), 3)
            worker.prune_old_days(generated, NOW)
            self.assertTrue((generated / "2026-10-11" / "0.webp").exists())
            self.assertEqual(worker.recover_previous_batches(client, generated, DAY, 5)["days"], ["2026-10-11"])

    def test_blocked_marker_repairs_interrupted_remote_status_without_generating(self):
        client = self.recovery_client()
        update = client.update
        failures = []
        def interrupt_blocked_status(table, values, column, value):
            if values.get("groq_metrics", {}).get("delivery_recovery", {}).get("status") == "blocked" and not failures:
                failures.append(True)
                raise OSError("interrupted status write")
            return update(table, values, column, value)
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            directory = self.saved_batch(Path(first))
            (directory / "0.webp").unlink()
            with patch.object(client, "update", side_effect=interrupt_blocked_status):
                with self.assertRaises(OSError):
                    worker.run_production({"batch_size": 5}, Path(first), client, NOW + 86401)
            self.assertNotIn("delivery_recovery", client.rows[("factory_runs", DAY)]["groq_metrics"])
            generator = unittest.mock.Mock(side_effect=AssertionError("Status repair must not generate"))
            result = worker.run_production({"batch_size": 5}, Path(second), client, NOW + 86401, generator)
            self.assertEqual(result["status"], "attention_required")
            metrics = client.rows[("factory_runs", DAY)]["groq_metrics"]
            self.assertEqual(metrics["delivery_recovery"]["status"], "blocked")
            self.assertFalse(metrics["draft_delivery_complete"])
            self.assertEqual(client.rows[("factory_runs", DAY)]["generated"], 5)
            generator.assert_not_called()

    def test_missing_or_older_runtime_snapshot_is_blocked_without_claiming_delivery(self):
        for missing_directory in (True, False):
            with self.subTest(missing_directory=missing_directory), tempfile.TemporaryDirectory() as temporary:
                client = self.recovery_client()
                runtime = Path(temporary)
                generated = runtime / "generated-stickers"
                generated.mkdir()
                if not missing_directory:
                    directory = generated / DAY
                    directory.mkdir()
                    worker.atomic_json(directory / "state.json", {"records": []})
                result = worker.recover_previous_batches(client, generated, "2026-10-13", 5)
                self.assertEqual(result["blocked_days"], [DAY])
                self.assertEqual(client.rows[("factory_runs", DAY)]["generated"], 5)
                self.assertFalse(client.rows[("factory_runs", DAY)]["groq_metrics"]["draft_delivery_complete"])
                self.assertEqual(worker.recover_previous_batches(client, generated, "2026-10-13", 5)["blocked_days"], [])

    def test_acknowledged_snapshot_subset_is_not_pruned_when_database_records_more(self):
        client = self.recovery_client()
        with tempfile.TemporaryDirectory() as temporary:
            runtime = Path(temporary)
            directory = self.saved_batch(runtime)
            worker.migrate_ready_batches(client, directory.parent, DAY, 5)
            client.rows[("factory_runs", DAY)]["generated"] = 10
            result = worker.run_production({"batch_size": 5}, runtime, client, NOW + 86401)
            self.assertEqual(result["status"], "attention_required")
            self.assertEqual(client.rows[("factory_runs", DAY)]["generated"], 10)
            worker.prune_old_days(directory.parent, NOW + 20 * 86400)
            self.assertTrue((directory / "0.webp").exists())

    def test_unreadable_history_is_quarantined_on_fresh_runner_without_resetting_today(self):
        from test_daily_stickers import ideas
        client = self.recovery_client()
        raw = b'{"records": broken json'
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            directory = self.saved_batch(Path(first))
            (directory / "state.json").write_bytes(raw)
            client.upload(PRIVATE_BUCKET, worker.STATE_OBJECT, worker.state_archive(Path(first)))
            runtime = Path(second)
            generator = unittest.mock.Mock(side_effect=AssertionError("Quarantine does not generate"))
            result = worker.run_production({"batch_size": 5}, runtime, client, NOW + 86401, generator)
            self.assertEqual(result["status"], "attention_required")
            generator.assert_not_called()
            old = runtime / "generated-stickers" / DAY
            quarantined = next(old.glob("state.unreadable-*.json"))
            self.assertEqual(quarantined.read_bytes(), raw)
            self.assertTrue((old / "0.webp").exists())
            self.assertEqual(client.rows[("factory_runs", DAY)]["groq_metrics"]["delivery_recovery"]["reason"], "unreadable_runtime_state")
            with patch.dict(os.environ, {"GROQ_API_KEY": "test-only"}), \
                    patch.object(worker, "ensure_content_brief", side_effect=lambda c, conf, controls, now: (conf, {"source_mode": "fixture"})), \
                    patch("daily_stickers.groq_ideas", return_value=ideas(5)) as captions:
                result = worker.run_production({"modes": ["illustration"]}, runtime, client, NOW + 86401)
            self.assertEqual(result["generated"], 5)
            captions.assert_called_once()
            self.assertEqual(quarantined.read_bytes(), raw)
            # Current-day corruption must fail, preserving counters and bytes.
            current = runtime / "generated-stickers" / "2026-10-13" / "state.json"
            current.write_bytes(raw)
            with self.assertRaises(ValueError):
                worker.relocate_state(runtime, "2026-10-13")
            self.assertEqual(current.read_bytes(), raw)

    def test_orphan_quarantine_has_durable_block_marker_and_does_not_starve_today(self):
        client = self.recovery_client()
        del client.rows[("factory_runs", DAY)]
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second, \
                tempfile.TemporaryDirectory() as third:
            directory = self.saved_batch(Path(first))
            (directory / "state.json").write_bytes(b"corrupt orphan state")
            client.upload(PRIVATE_BUCKET, worker.STATE_OBJECT, worker.state_archive(Path(first)))
            unused = unittest.mock.Mock(side_effect=AssertionError("First pass only quarantines"))
            result = worker.run_production({"modes": ["illustration"]}, Path(second), client, NOW + 86401, unused)
            self.assertEqual(result["status"], "attention_required")
            self.assertTrue((Path(second) / "generated-stickers" / DAY / "delivery-blocked.json").exists())
            unused.assert_not_called()
            today = unittest.mock.Mock(return_value="Waiting for normal batch")
            with patch.object(worker, "ensure_content_brief", side_effect=lambda c, conf, controls, now: (conf, {"source_mode": "fixture"})):
                result = worker.run_production({"modes": ["illustration"]}, Path(third), client, NOW + 86401, today)
            today.assert_called_once()
            self.assertEqual(result["historical_delivery"]["blocked_days"], [])
            self.assertNotIn(("factory_runs", DAY), client.rows)

    def test_previous_utc_runs_close_without_losing_saved_counts(self):
        client = FakeSupabase()
        client.rows[("factory_runs", "2026-10-10")] = {
            "day": "2026-10-10", "status": "running", "generated": 20,
            "target": 40, "groq_metrics": {"postprocess_complete": True}}
        client.rows[("factory_runs", "2026-10-11")] = {
            "day": "2026-10-11", "status": "running", "generated": 0,
            "target": 40, "groq_metrics": {}}
        client.rows[("factory_runs", DAY)] = {
            "day": DAY, "status": "running", "generated": 5,
            "target": 40, "groq_metrics": {}}
        client.rows[("factory_runs", "2026-10-09")] = {
            "day": "2026-10-09", "status": "complete", "generated": 40,
            "target": 40, "groq_metrics": {}}
        closed = worker.close_previous_runs(client, DAY, NOW)
        self.assertEqual(closed, ["2026-10-11", "2026-10-10"])
        partial = client.rows[("factory_runs", "2026-10-10")]
        self.assertEqual((partial["status"], partial["generated"]), ("failed", 20))
        self.assertEqual(partial["groq_metrics"]["terminal_outcome"], "partial")
        self.assertTrue(partial["groq_metrics"]["postprocess_complete"])
        self.assertEqual(client.rows[("factory_runs", "2026-10-11")]["groq_metrics"]["terminal_outcome"], "failed")
        self.assertEqual(client.rows[("factory_runs", DAY)]["status"], "running")
        self.assertEqual(client.rows[("factory_runs", "2026-10-09")]["status"], "complete")
        self.assertEqual(worker.close_previous_runs(client, DAY, NOW), [])

    def test_previous_utc_runs_close_even_when_today_waits_for_provider(self):
        client = FakeSupabase()
        client.rows[("factory_runs", "2026-10-11")] = {
            "day": "2026-10-11", "status": "running", "generated": 5,
            "target": 40, "groq_metrics": {}}
        config = {"daily_target": 3, "batch_size": 3, "modes": ["illustration"]}
        with tempfile.TemporaryDirectory() as temporary:
            with patch.object(worker, "sync_inbox", return_value=0), \
                 patch.object(worker, "configure", return_value=config), \
                 patch.object(worker, "mirror_licensed_inputs"):
                def unavailable(*_):
                    raise worker.ProviderResponseError("invalid_response", NOW + 900)
                result = worker.run_production(config, Path(temporary), client, NOW,
                                               generator=unavailable)
        self.assertEqual(result["status"], "waiting")
        previous = client.rows[("factory_runs", "2026-10-11")]
        self.assertEqual(previous["status"], "failed")
        self.assertEqual(previous["generated"], 5)
        self.assertEqual(previous["groq_metrics"]["terminal_outcome"], "partial")

    def test_provider_auth_failure_stays_failed_during_later_cooldown(self):
        client = FakeSupabase()
        config = {"daily_target": 3, "batch_size": 3, "interval_seconds": 60, "modes": ["illustration"]}
        error = urllib.error.HTTPError("https://provider.invalid", 401, "do not expose details", {}, None)
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second, \
                patch.dict(os.environ, {"GROQ_API_KEY": "fixture-key"}), \
                patch("daily_stickers.groq_ideas", side_effect=error) as request:
            with self.assertRaises(worker.ProviderError):
                worker.run_production(config, Path(first), client, NOW)
            self.assertEqual(client.rows[("factory_runs", DAY)]["status"], "failed")
            summary = worker.run_production(config, Path(second), client, NOW + 60)
            self.assertEqual(summary["status"], "failed")
            self.assertIn("credentials", summary["result"])
            self.assertEqual(request.call_count, 1)

    def test_http_400_keeps_only_safe_provider_code_and_actionable_status(self):
        client = FakeSupabase()
        with tempfile.TemporaryDirectory() as temporary:
            day = Path(temporary) / DAY
            day.mkdir()
            (day / "state.json").write_text(json.dumps({
                "records": [], "requests": 2, "next_request_at": NOW + 900,
                "provider_error": {"http_status": 400, "retry_at": NOW + 900,
                                   "code": "json_validate_failed"}}))
            summary = worker.persist_failure(client, {"daily_target": 40}, NOW,
                                             worker.ProviderError(400, NOW + 900),
                                             Path(temporary))
        row = client.rows[("factory_runs", DAY)]
        self.assertEqual(row["groq_metrics"]["recovery"]["code"], "json_validate_failed")
        self.assertIn("HTTP 400", summary["result"])

    def test_rate_limit_survives_new_runner_then_completes_after_cooldown(self):
        config = {"daily_target": 3, "batch_size": 3, "interval_seconds": 60,
                  "modes": ["illustration"]}
        client = FakeSupabase()
        error = urllib.error.HTTPError("https://provider.invalid", 429, "private response omitted",
                                       {"Retry-After": "20000"}, None)
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second, \
                tempfile.TemporaryDirectory() as third, patch.dict(os.environ, {"GROQ_API_KEY": "fixture-key"}):
            with patch("daily_stickers.groq_ideas", side_effect=error) as request:
                waiting = worker.run_production(config, Path(first), client, NOW)
                self.assertEqual(waiting["status"], "waiting")
                self.assertEqual(waiting["retry_at"], NOW + 20000)
                worker.run_production(config, Path(second), client, NOW + 61)
                self.assertEqual(request.call_count, 1)
            row = client.rows[("factory_runs", DAY)]
            self.assertEqual(row["status"], "running")
            self.assertEqual(row["groq_metrics"]["recovery"], {"http_status": 429, "retry_at": NOW + 20000})
            self.assertNotIn("private response", json.dumps(row))
            ideas = [{"caption": f"Recovered reaction {i}", "mood": "happy", "color": "#FFD166", "source": None}
                     for i in range(3)]
            with patch("daily_stickers.groq_ideas", return_value=ideas) as request:
                complete = worker.run_production(config, Path(third), client, NOW + 20001)
                self.assertEqual(request.call_count, 1)
            self.assertEqual((complete["status"], complete["generated"]), ("complete", 3))
            self.assertNotIn("recovery", client.rows[("factory_runs", DAY)]["groq_metrics"])

    def test_real_media_mode_does_not_fall_back_to_template_faces(self):
        with tempfile.TemporaryDirectory() as temporary:
            config={"modes":["photo","clip"],"require_real_media":True,"vision_model":"test-vision"}
            with self.assertRaisesRegex(ValueError,"template faces were not substituted"):
                worker.configure(config,Path(temporary),NOW)
            refs=Path(temporary)/"reference-images"
            refs.mkdir(exist_ok=True)
            (refs/"owned.png").write_bytes(png_bytes())
            (refs/"library.json").write_text(json.dumps({"assets":[{"file":"owned.png","license":"CC0","source_url":"https://example.org/owned"}]}))
            result=worker.configure(config,Path(temporary),NOW)
            self.assertEqual(result["modes"],["photo"])
            self.assertEqual(result["allowed_media_files"],["owned.png"])

    def test_reaction_mode_excludes_scenery_and_generated_results(self):
        with tempfile.TemporaryDirectory() as temporary:
            refs = Path(temporary) / "reference-images"
            refs.mkdir()
            assets = [
                {"file": "cat.png", "license": "CC0", "title": "Yawning cat"},
                {"file": "space.png", "license": "PUBLIC_DOMAIN", "title": "Hubble ultra deep field"},
                {"file": "uploaded.png", "license": "USER_OWNED", "source": "creator-studio",
                 "purpose": "SOURCE", "title": "My original photo"},
                {"file": "result.png", "license": "USER_OWNED", "source": "creator-studio",
                 "purpose": "RESULT", "title": "Funny cat AI result"},
                {"file": "reference.png", "license": "CC0", "purpose": "REFERENCE",
                 "title": "Happy dog"},
            ]
            for asset in assets:
                (refs / asset["file"]).write_bytes(png_bytes())
            (refs / "library.json").write_text(json.dumps({"assets": assets}))
            result = worker.configure({"modes": ["photo"], "require_real_media": True,
                                       "require_reaction_media": True,
                                       "vision_model": "test-vision"}, Path(temporary), NOW)
            self.assertEqual(result["allowed_media_files"], ["cat.png", "uploaded.png"])

    def test_daily_brief_drives_categories_and_complete_pack_auto_migrates(self):
        client = FakeSupabase()
        client.rows[("factory_settings", "default")] = {
            "id": "default", "enabled": True, "daily_target": 3, "batch_size": 3,
            "topics": ["INDIA | Cricket reactions"], "rating": "GENERAL",
            "content_categories": ["INDIA"], "discovery_enabled": True,
            "auto_migrate_completed": True}
        brief = {"day": DAY, "status": "ready", "source_mode": "groq_browser_search",
            "model": "test", "rating": "GENERAL", "categories": ["INDIA"],
            "custom_topics": ["INDIA | Cricket reactions"], "items": [],
            "topic_plans": [{"category": "INDIA", "pack_category": "HINGLISH",
                             "topic": "Cricket reactions", "source": "owner"}],
            "references": [], "warnings": []}
        def generator(config, generated, font, now):
            self.assertEqual(config["topic_plans"][0]["pack_category"], "HINGLISH")
            day = generated / DAY
            day.mkdir(parents=True)
            (day / "daily-complete.zip").write_bytes(pack_bytes())
            (day / "references.json").write_text(json.dumps([{"caption": str(i)} for i in range(3)]))
            (day / "state.json").write_text(json.dumps({"records": [{"caption": str(i)} for i in range(3)]}))
            return "complete"
        with tempfile.TemporaryDirectory() as temporary, patch.object(worker, "build_brief", return_value=brief):
            summary = worker.run_production({"daily_target": 3, "batch_size": 3,
                "modes": ["illustration"]}, Path(temporary), client, NOW, generator)
        self.assertEqual(summary["content_source"], "groq_browser_search")
        self.assertIsNotNone(summary["admin_draft_id"])
        draft = client.find("admin_packs", "id", summary["admin_draft_id"])
        self.assertEqual(draft["status"], "draft")
        self.assertEqual(draft["origin_id"], str(worker.uuid.uuid5(
            worker.uuid.NAMESPACE_URL, "stickerdock-production:" + DAY)))
        self.assertIn((PRIVATE_BUCKET, "content-briefs/" + DAY + ".json"), client.objects)

    def test_partial_day_batch_becomes_review_draft_and_replay_preserves_edits(self):
        client = FakeSupabase()
        client.rows[("factory_settings", "default")] = {
            "enabled": True, "daily_target": 40, "batch_size": 5,
            "rating": "GENERAL", "auto_migrate_completed": True,
            "content_categories": ["EVERYDAY"]}
        def generator(config, generated, font, now):
            day = generated / DAY
            day.mkdir(parents=True)
            records = []
            for index in range(5):
                path = day / f"{index}.webp"
                Image.new("RGB", (512, 512), "red").save(path, "WEBP")
                records.append({"id": str(worker.uuid.uuid5(worker.uuid.NAMESPACE_URL, f"fixture:{index}")),
                    "file": str(path), "caption": f"Reaction {index}", "mood": "happy",
                    "kind": "STATIC", "rating": "GENERAL", "category": "GENERAL",
                    "media_credit": {"license": "CC0", "credit": "Fixture"}})
            (day / "state.json").write_text(json.dumps({"records": records,
                "batches": [{"start": 0, "end": 5}], "requests": 1}))
            return "Created five stickers"
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second, \
                patch.object(worker, "ensure_content_brief", side_effect=lambda client, config, controls, now:
                    (config, {"source_mode": "fixture"})):
            summary = worker.run_production({"daily_target": 40, "batch_size": 5,
                "modes": ["illustration"]}, Path(first), client, NOW, generator)
            self.assertEqual((summary["status"], summary["generated"]), ("running", 5))
            self.assertEqual(len(summary["admin_draft_ids"]), 1)
            draft_id = summary["admin_draft_ids"][0]
            draft = client.find("admin_packs", "id", draft_id)
            self.assertEqual((draft["status"], draft["sticker_count"]), ("draft", 5))
            self.assertEqual(len(draft["references"]), 5)
            with zipfile.ZipFile(io.BytesIO(client.objects[(PRIVATE_BUCKET,
                    draft["private_path"])])) as archive:
                self.assertEqual(len(json.loads(archive.read("manifest.json"))["packs"][0]["stickers"]), 5)
            draft["name"] = "Reviewed by owner"
            draft["status"] = "reviewed"
            def waiting(config, generated, font, now):
                return "Waiting for next batch"
            replay = worker.run_production({"daily_target": 40, "batch_size": 5,
                "modes": ["illustration"]}, Path(second), client, NOW + 60, waiting)
            self.assertEqual(replay["admin_draft_ids"], [draft_id])
            self.assertEqual(client.find("admin_packs", "id", draft_id)["name"], "Reviewed by owner")
            self.assertEqual(client.find("admin_packs", "id", draft_id)["status"], "reviewed")
            self.assertEqual(len([key for key in client.objects if key[0] == PRIVATE_BUCKET
                and key[1].startswith("admin/packs/")]), 1)

    def test_public_archive_must_fit_android_download_bound(self):
        from publish_factory_pack import MAX_PUBLIC_PACK_BYTES, validate_pack
        with self.assertRaises(ValueError):
            validate_pack(b"x" * (MAX_PUBLIC_PACK_BYTES + 1))

    def test_midday_audience_change_waits_for_next_utc_day(self):
        client = FakeSupabase()
        client.rows[("factory_settings", "default")] = {
            "enabled": True, "daily_target": 3, "batch_size": 3,
            "rating": "MATURE", "topics": ["ADULT_HUMOR | Private adult jokes"],
            "content_categories": ["ADULT_HUMOR"], "auto_migrate_completed": False}
        def generator(config, generated, font, now):
            self.assertEqual(config["rating"], "GENERAL")
            self.assertTrue(all(plan["category"] != "ADULT_HUMOR" for plan in config["topic_plans"]))
            return "waiting"
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"GROQ_TRENDS_API_KEY": ""}):
            runtime = Path(temporary)
            directory = runtime / "generated-stickers" / DAY
            directory.mkdir(parents=True)
            (directory / "state.json").write_text(json.dumps({"records": [
                {"rating": "GENERAL", "file": "old.webp", "caption": "Previous"}]}))
            summary = worker.run_production({"modes": ["illustration"]}, runtime, client, NOW, generator)
        self.assertTrue(summary["warnings"])
        self.assertEqual(client.rows[("factory_settings", "default")]["rating"], "MATURE")

    def test_archive_rating_is_derived_from_its_content(self):
        client = FakeSupabase()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / DAY
            directory.mkdir()
            (directory / "daily-complete.zip").write_bytes(pack_bytes("MATURE"))
            (directory / "state.json").write_text(json.dumps({"records": [{}] * 3}))
            worker.persist_run(client, root, {"rating": "GENERAL", "daily_target": 3, "modes": ["illustration"]}, NOW, "complete")
        source = next(row for (table, _), row in client.rows.items() if table == "sticker_packs")
        self.assertEqual(source["rating"], "MATURE")

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
            (refs / "second.png").write_bytes(b"abcd")
            (refs / "old.png").write_bytes(b"5678")
            day = root / "generated-stickers" / DAY
            day.mkdir(parents=True)
            (day / "state.json").write_text(json.dumps({"pending": {
                "image_path": "/old-run/reference-images/pending.png",
                "image_paths": ["/old-run/reference-images/pending.png",
                                "/old-run/reference-images/second.png"]}}))
            worker.relocate_state(root)
            pending = json.loads((day / "state.json").read_text())["pending"]
            self.assertEqual(pending["image_paths"], [str(refs / "pending.png"), str(refs / "second.png")])
            worker.trim_reference_cache(root, 8)
            self.assertTrue((refs / "pending.png").exists())
            self.assertTrue((refs / "second.png").exists())
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

    def test_inbox_excludes_legacy_pinterest_content_without_deleting_remote_originals(self):
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
            self.assertEqual(worker.sync_inbox(client, root), 0)
            self.assertEqual(json.loads((root / "reference-images/library.json").read_text())["assets"], [])
        self.assertEqual(client.objects[(PRIVATE_BUCKET, "inbox/" + digest + ".png")], data)

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
        self.assertEqual(catalog["packs"][0]["categories"], ["GENERAL"])
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
