import json
import unittest
from unittest import mock
import uuid

from supabase_factory import SupabaseFactory


class FakeSupabase(SupabaseFactory):
    def __init__(self, responses=None):
        super().__init__(url="https://example.supabase.co", service_key="service-key")
        self.responses = list(responses or [])
        self.calls = []

    def _request(self, method, path, data=None, headers=None, missing_ok=False):
        self.calls.append((method, path, data, headers or {}, missing_ok))
        return self.responses.pop(0) if self.responses else b""


class _Response:
    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self, _limit):
        return b"[]"


class SupabaseFactoryCreatorQueueTest(unittest.TestCase):
    def test_authenticated_mutable_reads_use_distinct_urls_and_no_cache(self):
        client=FakeSupabase([b"old",b"new"])
        self.assertEqual(client.download("sticker-factory-public","catalog/packs.json"),b"old")
        self.assertEqual(client.download("sticker-factory-public","catalog/packs.json"),b"new")
        self.assertNotEqual(client.calls[0][1],client.calls[1][1])
        self.assertIn("/authenticated/sticker-factory-public/catalog/packs.json?cacheNonce=",client.calls[0][1])
        self.assertEqual(client.calls[0][3]["Cache-Control"],"no-cache")

    def test_new_secret_key_uses_apikey_without_bearer(self):
        captured = {}

        def fake_urlopen(request, timeout):
            captured["headers"] = {key.lower(): value for key, value in request.header_items()}
            self.assertEqual(timeout, 60)
            return _Response()

        client = SupabaseFactory(
            url="https://example.supabase.co",
            service_key="sb_secret_test-value",
        )
        with mock.patch("supabase_factory.urllib.request.urlopen", side_effect=fake_urlopen):
            client._request("GET", "/rest/v1/factory_runs?limit=1")

        self.assertEqual(captured["headers"]["apikey"], "sb_secret_test-value")
        self.assertNotIn("authorization", captured["headers"])

    def test_legacy_service_role_keeps_bearer_header(self):
        captured = {}

        def fake_urlopen(request, timeout):
            captured["headers"] = {key.lower(): value for key, value in request.header_items()}
            return _Response()

        client = SupabaseFactory(
            url="https://example.supabase.co",
            service_key="legacy-jwt-value",
        )
        with mock.patch("supabase_factory.urllib.request.urlopen", side_effect=fake_urlopen):
            client._request("GET", "/rest/v1/factory_runs?limit=1")

        self.assertEqual(captured["headers"]["apikey"], "legacy-jwt-value")
        self.assertEqual(captured["headers"]["authorization"], "Bearer legacy-jwt-value")

    def test_claim_creator_request_uses_atomic_rpc(self):
        request_id = str(uuid.uuid4())
        client = FakeSupabase([json.dumps([{"id": request_id, "status": "running"}]).encode()])
        result = client.claim_creator_request(" worker-123 ")
        self.assertEqual(result["id"], request_id)
        method, path, data, headers, _ = client.calls[0]
        self.assertEqual((method, path), ("POST", "/rest/v1/rpc/claim_creator_request"))
        self.assertEqual(json.loads(data), {"p_worker_id": "worker-123"})
        self.assertEqual(headers["Content-Type"], "application/json")

    def test_claim_creator_request_returns_none_for_empty_queue(self):
        client = FakeSupabase([b"[]"])
        self.assertIsNone(client.claim_creator_request("worker"))

    def test_invalid_worker_id_never_calls_provider(self):
        client = FakeSupabase()
        with self.assertRaises(ValueError):
            client.claim_creator_request("")
        with self.assertRaises(ValueError):
            client.claim_creator_request("x" * 121)
        self.assertEqual(client.calls, [])

    def test_creator_request_update_restricts_fields_and_validates_uuid(self):
        client = FakeSupabase()
        request_id = str(uuid.uuid4())
        client.update_creator_request(request_id, {
            "status": "complete", "result_path": "inbox/" + "a" * 64 + ".webp"
        })
        method, path, data, headers, _ = client.calls[0]
        self.assertEqual(method, "PATCH")
        self.assertIn("creator_requests", path)
        self.assertIn(request_id, path)
        self.assertEqual(json.loads(data)["status"], "complete")
        self.assertEqual(headers["Prefer"], "return=minimal")
        with self.assertRaises(ValueError):
            client.update_creator_request("not-a-uuid", {"status": "failed"})
        with self.assertRaises(ValueError):
            client.update_creator_request(request_id, {"prompt": "mutate input"})


if __name__ == "__main__":
    unittest.main()
