import json
import unittest
from unittest.mock import patch
import production_readiness as doctor
from supabase_factory import SupabaseError


class ReadinessTest(unittest.TestCase):
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
