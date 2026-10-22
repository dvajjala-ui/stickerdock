import json
import unittest
from datetime import datetime, timezone
from unittest.mock import patch
import production_readiness as doctor
from supabase_factory import SupabaseError


class ReadinessTest(unittest.TestCase):
    def test_delivery_stages_do_not_inherit_schema_health(self):
        now = datetime(2026, 10, 22, 12, tzinfo=timezone.utc)
        class Client:
            def _request(self, method, path):
                if "/factory_runs?select=day,status,generated,target,updated_at" in path:
                    return json.dumps([{"day": "2026-10-21", "status": "running", "generated": 20,
                                        "target": 40, "updated_at": "2026-10-21T08:00:00Z"}]).encode()
                if "status=eq.draft" in path:
                    return json.dumps([{"id": "draft", "created_at": "2026-10-22T11:00:00Z"}]).encode()
                if "status=eq.published" in path:
                    return json.dumps([{"id": "published", "updated_at": "2026-10-01T11:00:00Z"}]).encode()
                return b"[]"
        checks, stages = doctor.delivery_checks(Client(), 1, now)
        self.assertEqual(stages, {"generation": False, "review_draft": True, "catalog": False})
        self.assertEqual([item["status"] for item in checks], ["pending", "ready", "pending"])
        self.assertIn("2026-10-21", checks[0]["message"])

    def test_missing_tables_are_explained_without_mutating_backend(self):
        class Client:
            url = "https://example.supabase.co"
            def _request(self, method, path):
                if method != "GET":
                    raise AssertionError("Readiness must not mutate production")
                if "/admin_packs?" in path:
                    raise SupabaseError("Supabase HTTP 404")
                return b"[]"
            def download(self, *args, **kwargs): return None
        result = doctor.readiness(Client())
        self.assertFalse(result["admin_ready"])
        self.assertTrue(result["worker_ready"])
        self.assertEqual(result["published_packs"], 0)
        failed = next(c for c in result["checks"] if c["name"] == "admin_packs")
        self.assertIn("migration", failed["message"])

    def test_missing_server_settings_are_not_reported_as_ready(self):
        with patch.dict("os.environ", {}, clear=True):
            result = doctor.readiness()
        self.assertFalse(result["admin_ready"])
        self.assertEqual(result["checks"][0]["name"], "Server configuration")

    def test_database_availability_does_not_claim_phone_acceptance(self):
        class Client:
            url = "https://example.supabase.co"
            def _request(self, method, path): return b"[]"
            def download(self, *args, **kwargs):
                return json.dumps({"version": 1, "packs": [{"id": "example"}]}).encode()
        result = doctor.readiness(Client())
        self.assertTrue(result["admin_ready"])
        self.assertEqual(result["published_packs"], 1)
        self.assertNotIn("device_ready", result)
