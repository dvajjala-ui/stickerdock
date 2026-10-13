import json
import unittest
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


class SupabaseFactoryCreatorQueueTest(unittest.TestCase):
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
