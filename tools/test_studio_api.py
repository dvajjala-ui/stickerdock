import json
import os
import unittest
from unittest.mock import patch

from api import _shared as studio


class StudioSupabaseAuthTest(unittest.TestCase):
    def _headers_for(self, key):
        captured = {}
        def fake_request(url, method="GET", data=None, headers=None, limit=None, missing_ok=False):
            captured.update(headers or {})
            return b"[]"
        with patch.dict(os.environ, {"SUPABASE_URL": "https://example.supabase.co", "SUPABASE_SERVICE_ROLE_KEY": key}, clear=False), patch.object(studio, "request", side_effect=fake_request):
            studio.supabase_request("/rest/v1/creator_requests")
        return captured

    def test_new_secret_key_is_apikey_only(self):
        headers = self._headers_for("sb_secret_test-only")
        self.assertEqual(headers["apikey"], "sb_secret_test-only")
        self.assertNotIn("Authorization", headers)

    def test_legacy_service_role_keeps_bearer(self):
        headers = self._headers_for("legacy-service-role")
        self.assertEqual(headers["apikey"], "legacy-service-role")
        self.assertEqual(headers["Authorization"], "Bearer legacy-service-role")

    def test_queue_request_validates_and_posts(self):
        calls = []
        with patch.object(studio, "supabase_request", side_effect=lambda *a, **kw: calls.append((a, kw)) or b""):
            result = studio.queue_creator_request("  friendly   reaction ", "inbox/" + "a" * 64 + ".png")
        self.assertEqual(result["status"], "queued")
        args, kwargs = calls[0]
        self.assertEqual(args[0], "/rest/v1/creator_requests")
        self.assertEqual(kwargs["method"], "POST")
        payload = json.loads(kwargs["data"])
        self.assertEqual(payload[0]["prompt"], "friendly reaction")
        self.assertEqual(payload[0]["status"], "queued")


if __name__ == "__main__":
    unittest.main()
