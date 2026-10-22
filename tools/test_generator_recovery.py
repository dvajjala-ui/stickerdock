import json
import io
import os
from pathlib import Path
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

import daily_stickers as generator
import factory_preflight
import production_worker as worker
from test_daily_stickers import ideas, FONT, NOW
from test_production_worker import FakeSupabase, DAY


class GeneratorRecoveryTest(unittest.TestCase):
    def response(self, value, finish="stop"):
        return json.dumps({"choices": [{"finish_reason": finish, "message": {
            "content": json.dumps(value)}}], "usage": {"total_tokens": 10}}).encode()

    def test_configured_models_request_a_strict_top_level_batch(self):
        for model in ("qwen/qwen3.8-27b", "openai/gpt-oss-20b", "openai/gpt-oss-120b"):
            with self.subTest(model=model), patch.object(generator, "fetch_bytes", return_value=self.response({"stickers": ideas(5)})) as fetch:
                self.assertEqual(generator.groq_ideas("test", model, 5, "everyday", [], []), ideas(5))
                sent = json.loads(fetch.call_args.args[1])
                schema = sent["response_format"]["json_schema"]
                self.assertTrue(schema["strict"])
                self.assertEqual(schema["schema"]["required"], ["stickers"])
                self.assertEqual(sent["reasoning_effort"], "none" if model.startswith("qwen/") else "low")

    def test_unknown_models_keep_json_mode_and_truncation_is_never_saved(self):
        with patch.object(generator, "fetch_bytes", return_value=self.response({"stickers": ideas(3)})) as fetch:
            generator.groq_ideas("test", "custom-vision", 3, "reactions", [], [])
            self.assertEqual(json.loads(fetch.call_args.args[1])["response_format"], {"type": "json_object"})
        for value, finish in (({"stickers": ideas(3)}, "length"), ({"caption": "wrong shape"}, "stop"), ([], "stop")):
            with self.subTest(finish=finish, value_type=type(value)), patch.object(generator, "fetch_bytes", return_value=self.response(value, finish)):
                with self.assertRaises(ValueError):
                    generator.groq_ideas("test", "custom", 3, "reactions", [], [])

    def test_validation_failure_records_only_a_bounded_reason_code(self):
        bad = {"stickers": [{**idea, "caption": "same"} for idea in ideas(3)]}
        with patch.object(generator, "fetch_bytes", return_value=self.response(bad)):
            with self.assertRaises(ValueError):
                generator.groq_ideas("test", "custom", 3, "reactions", [], [])
        self.assertEqual(generator.LAST_GROQ_METRICS["validation_error"], "duplicate_caption")

    def test_provider_http_code_never_exposes_free_text(self):
        body = json.dumps({"error": {"code": "json_validate_failed",
                 "message": "private prompt and provider details"}}).encode()
        error = urllib.error.HTTPError(generator.API_URL, 400, "bad", {}, io.BytesIO(body))
        self.assertEqual(generator.safe_provider_code(error), "json_validate_failed")
        error.close()
        invalid = urllib.error.HTTPError(
            generator.API_URL, 400, "bad", {}, io.BytesIO(
                b'{"error":{"code":"private prompt token"}}'))
        self.assertIsNone(generator.safe_provider_code(invalid))
        invalid.close()

    def test_http_error_persists_safe_request_shape_for_diagnosis(self):
        body = json.dumps({"error": {"code": "invalid_request_error",
                 "message": "private provider response"}}).encode()
        error = urllib.error.HTTPError(generator.API_URL, 400, "bad", {}, io.BytesIO(body))
        def reject(*_):
            generator.LAST_GROQ_METRICS["request_shape"] = {
                "format": "json_object", "source_images": 5,
                "encoded_image_bytes": 45000}
            raise error
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"GROQ_API_KEY": "test"}), \
             patch.object(generator, "groq_ideas", side_effect=reject):
            with self.assertRaises(generator.ProviderError):
                generator.run({}, Path(temporary), FONT, NOW)
            state = json.loads((Path(temporary) / DAY / "state.json").read_text())
        self.assertEqual(state["provider_error"]["code"], "invalid_request_error")
        self.assertEqual(state["last_groq_metrics"]["request_shape"]["source_images"], 5)
        self.assertNotIn("private provider response", json.dumps(state))

    def test_invalid_output_retries_after_fifteen_minutes_without_spending_during_wait(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"GROQ_API_KEY": "test"}):
            root = Path(temporary)
            with patch.object(generator, "groq_ideas", side_effect=ValueError("private provider text")) as call:
                with self.assertRaises(generator.ProviderResponseError):
                    generator.run({}, root, FONT, NOW)
                generator.run({}, root, FONT, NOW + 899)
                self.assertEqual(call.call_count, 1)
            state = json.loads((root / DAY / "state.json").read_text())
            self.assertEqual(state["requests"], 1)
            self.assertEqual(state["provider_error"], {"kind": "invalid_response", "retry_at": NOW + 900})
            self.assertNotIn("private provider text", json.dumps(state))
            with patch.object(generator, "groq_ideas", return_value=ideas(5)):
                generator.run({}, root, FONT, NOW + 900)
            state = json.loads((root / DAY / "state.json").read_text())
            self.assertEqual(len(state["records"]), 5)
            self.assertNotIn("provider_error", state)
            self.assertEqual(state["next_request_at"], NOW + 900 + 10800)

    def test_invalid_response_is_exposed_as_a_safe_worker_outcome(self):
        client = FakeSupabase()
        summary = worker.persist_failure(client, {"daily_target": 3}, NOW,
                                         generator.ProviderResponseError("invalid_response", NOW + 900))
        self.assertEqual(summary["recovery_kind"], "invalid_response")
        self.assertNotIn("private provider", json.dumps(summary))

    def test_preflight_obeys_pause_daily_budget_and_saved_render(self):
        client = FakeSupabase()
        self.assertTrue(factory_preflight.due(client, now=NOW)[0])
        run = {"generated": 5, "attempts": 1, "groq_metrics": {"next_request_at": NOW + 100}}
        client.rows[("factory_runs", DAY)] = run
        self.assertFalse(factory_preflight.due(client, now=NOW)[0])
        self.assertTrue(factory_preflight.due(client, now=NOW + 101)[0])
        run["attempts"] = 16
        self.assertFalse(factory_preflight.due(client, now=NOW + 101)[0])
        run["groq_metrics"]["postprocess_complete"] = False
        self.assertEqual(factory_preflight.due(client, now=NOW),
                         (True, "Resume saved batch delivery without a new AI request"))
        run["groq_metrics"].pop("postprocess_complete")
        run["groq_metrics"]["pending_render"] = True
        self.assertTrue(factory_preflight.due(client, now=NOW)[0])
        run.update(generated=40, status="failed")
        run["groq_metrics"]["pending_render"] = False
        self.assertTrue(factory_preflight.due(client, now=NOW)[0])
        run["status"] = "complete"
        run["groq_metrics"]["postprocess_complete"] = False
        self.assertTrue(factory_preflight.due(client, now=NOW)[0])
        run["groq_metrics"]["postprocess_complete"] = True
        self.assertFalse(factory_preflight.due(client, now=NOW)[0])
        client.rows[("factory_settings", "default")] = {"enabled": False}
        self.assertFalse(factory_preflight.due(client, now=NOW)[0])

    def test_fresh_runners_recover_then_finish_forty_with_normal_intervals(self):
        client = FakeSupabase()
        config = {"daily_target": 40, "batch_size": 5, "modes": ["illustration"], "interval_seconds": 10800}
        calls = []

        def captions(*args):
            calls.append(1)
            if len(calls) == 1:
                raise ValueError("bad shape")
            return ideas(5, (len(calls) - 2) * 5)

        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"GROQ_API_KEY": "test"}), patch.object(generator, "groq_ideas", side_effect=captions):
            root = Path(temporary)
            for tick in range(96):
                now = NOW + tick * 900
                if factory_preflight.due(client, now=now)[0]:
                    result = worker.run_production(config, root / str(tick), client, now)
            self.assertEqual((result["status"], result["generated"]), ("complete", 40))
            self.assertEqual(len(calls), 9)
            self.assertEqual(client.rows[("factory_runs", DAY)]["attempts"], 9)

    def test_repeated_invalid_output_exhausts_budget_without_claiming_completion(self):
        client = FakeSupabase()
        config = {"daily_target": 3, "batch_size": 3, "modes": ["illustration"], "max_requests_per_day": 1}
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"GROQ_API_KEY": "test"}), patch.object(generator, "groq_ideas", side_effect=ValueError("private text")):
            with self.assertRaises(generator.ProviderResponseError):
                worker.run_production(config, Path(temporary), client, NOW)
        row = client.rows[("factory_runs", DAY)]
        self.assertEqual((row["status"], row["generated"]), ("failed", 0))
        self.assertIn("budget exhausted", row["last_result"])
        self.assertNotIn("private text", json.dumps(row))

    def test_completed_artwork_storage_failure_resumes_without_another_ai_call(self):
        client = FakeSupabase()
        upload = client.upload
        failed = []

        def fail_complete_once(bucket, path, data, content_type="application/octet-stream"):
            if path.endswith("daily-complete.zip") and not failed:
                failed.append(True)
                raise OSError("simulated interrupted upload")
            return upload(bucket, path, data, content_type)

        config = {"daily_target": 3, "batch_size": 3, "modes": ["illustration"], "max_requests_per_day": 1}
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"GROQ_API_KEY": "test"}), patch.object(client, "upload", side_effect=fail_complete_once):
            with patch.object(generator, "groq_ideas", return_value=ideas(3)) as request:
                with self.assertRaises(OSError):
                    worker.run_production(config, Path(temporary) / "first", client, NOW)
                self.assertEqual(request.call_count, 1)
            self.assertTrue(factory_preflight.due(client, target=3, budget=1, now=NOW + 1)[0])
            self.assertNotIn("budget exhausted", client.rows[("factory_runs", DAY)]["last_result"])
            with patch.object(generator, "groq_ideas", side_effect=AssertionError("Must reuse saved output")):
                result = worker.run_production(config, Path(temporary) / "fresh-runner", client, NOW + 1)
            self.assertEqual((result["status"], result["generated"]), ("complete", 3))
            self.assertTrue(client.rows[("factory_runs", DAY)]["groq_metrics"]["postprocess_complete"])
            self.assertFalse(factory_preflight.due(client, target=3, budget=1, now=NOW + 1)[0])
