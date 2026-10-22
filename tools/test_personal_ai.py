import base64
import hashlib
import io
import json
import os
import sys
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from PIL import Image

from api import _personal_ai as api
from api._shared import StudioError

sys.path.insert(0, str(Path(__file__).resolve().parent))
import personal_ai_worker as worker


OWNER = str(uuid.uuid4())
OTHER = str(uuid.uuid4())


def data_url():
    output = io.BytesIO()
    Image.new("RGB", (96, 96), "orange").save(output, "PNG")
    return "data:image/png;base64," + base64.b64encode(output.getvalue()).decode()


class ApiClient:
    def __init__(self, admitted=True, reason="created", status="uploading"):
        self.admitted, self.reason, self.status = admitted, reason, status
        self.calls = []

    def rpc(self, name, payload):
        self.calls.append((name, payload))
        if name == "queue_personal_ai_caption":
            return [{"accepted": self.admitted, "reason": self.reason, "job_status": self.status}]
        if name == "activate_personal_ai_caption":
            return [{"job_status": "queued"}]
        if name == "cancel_personal_ai_caption":
            return [{"job_status": "cancelled"}]
        raise AssertionError(name)

    def upload(self, bucket, path, data, mime):
        self.calls.append(("upload", path, data, mime))

    def _request(self, method, path):
        self.calls.append((method, path))
        return json.dumps([{"id": "example", "status": "complete", "caption": "Seriously?"}]).encode()


class PersonalAiApiTest(unittest.TestCase):
    def setUp(self):
        self.flag = patch.dict(os.environ, {"PERSONAL_AI_ENABLED": "true"})
        self.flag.start()

    def tearDown(self):
        self.flag.stop()

    def payload(self):
        return {"action": "submit", "request_id": str(uuid.uuid4()), "image": data_url(),
                "prompt": "Make a funny reply", "consent": True, "audience": "GENERAL",
                "rights": "OWNED_OR_LICENSED"}

    def test_admission_precedes_private_upload_and_owner_is_server_chosen(self):
        client = ApiClient()
        payload = self.payload()
        payload["owner"] = OTHER
        result = api.dispatch(payload, OWNER, client)
        self.assertEqual(result["status"], "queued")
        self.assertEqual([call[0] for call in client.calls],
                         ["queue_personal_ai_caption", "upload", "activate_personal_ai_caption"])
        self.assertEqual(client.calls[0][1]["p_owner"], OWNER)
        self.assertTrue(client.calls[1][1].startswith("users/" + OWNER + "/ai/sources/"))
        self.assertNotIn(OTHER, client.calls[1][1])
        self.assertEqual(client.calls[1][3], "image/webp")
        self.assertNotIn(b"exif", client.calls[1][2].lower())

    def test_replays_do_not_upload_again(self):
        client = ApiClient(reason="replay", status="complete")
        result = api.dispatch(self.payload(), OWNER, client)
        self.assertEqual(result["status"], "complete")
        self.assertTrue(result["replayed"])
        self.assertEqual(len(client.calls), 1)

    def test_rejected_jobs_cannot_fill_storage(self):
        for reason, error in (("daily_limit", api.PersonalAiLimit),
                              ("pending_limit", api.PersonalAiLimit),
                              ("identity_conflict", api.PersonalAiConflict)):
            client = ApiClient(admitted=False, reason=reason)
            with self.assertRaises(error):
                api.dispatch(self.payload(), OWNER, client)
            self.assertEqual(len(client.calls), 1)

    def test_requires_explicit_consent_and_general_audience(self):
        for change in ({"consent": False}, {"audience": "MATURE"}, {"rights": "UNKNOWN"}):
            payload = self.payload(); payload.update(change)
            client = ApiClient()
            with self.assertRaises(StudioError):
                api.dispatch(payload, OWNER, client)
            self.assertEqual(client.calls, [])

    def test_availability_fails_closed_without_backend_configuration(self):
        with patch.dict(os.environ, {"PERSONAL_AI_ENABLED": "false"}):
            self.assertEqual(api.dispatch({"action": "availability"}, OWNER), {"available": False})
            with self.assertRaises(StudioError):
                api.dispatch(self.payload(), OWNER, ApiClient())
        self.assertEqual(api.dispatch({"action": "availability"}, OWNER, ApiClient()), {"available": True})

    def test_status_and_cancel_cannot_choose_another_owner(self):
        request_id = str(uuid.uuid4())
        client = ApiClient()
        api.dispatch({"action": "status", "id": request_id, "owner": OTHER}, OWNER, client)
        self.assertIn("owner_id=eq." + OWNER, client.calls[0][1])
        self.assertNotIn("owner_id=eq." + OTHER, client.calls[0][1])
        self.assertNotIn("source_path", client.calls[0][1])
        api.dispatch({"action": "cancel", "id": request_id, "owner": OTHER}, OWNER, client)
        self.assertEqual(client.calls[1][1]["p_owner"], OWNER)


class WorkerClient:
    def __init__(self, data, owner=OWNER):
        self.data = data
        self.owner = owner
        self.request_id = str(uuid.uuid4())
        self.job = {"id": self.request_id, "owner_id": owner,
                    "source_sha": hashlib.sha256(data).hexdigest(),
                    "source_path": api.source_path(owner, hashlib.sha256(data).hexdigest()),
                    "prompt": "A chat reply"}
        self.calls = []

    def rpc(self, name, payload):
        self.calls.append((name, payload))
        if name == "claim_personal_ai_caption": return [self.job]
        if name == "reserve_personal_ai_provider_call": return [{"allowed": True}]
        if name == "finish_personal_ai_caption": return [{"updated": True}]
        raise AssertionError(name)

    def download(self, bucket, path):
        self.calls.append(("download", path))
        return self.data


class PersonalAiWorkerTest(unittest.TestCase):
    def image(self):
        output = io.BytesIO(); Image.new("RGB", (96, 96), "orange").save(output, "WEBP")
        return output.getvalue()

    def test_verified_private_source_yields_one_caption_only(self):
        client = WorkerClient(self.image())
        seen = []
        result = worker.process_one(client, key="test", model="vision", idea_generator=lambda *a, **k:
            seen.append((a, k)) or [{"caption": "Seriously?"}])
        self.assertEqual(result["status"], "complete")
        self.assertEqual(len(seen), 1)
        self.assertEqual(client.calls[-1][1]["p_caption"], "Seriously?")
        self.assertEqual(client.calls[-1][1]["p_worker_id"], "github-actions")
        self.assertTrue(any(name == "reserve_personal_ai_provider_call" for name, _ in client.calls))

    def test_foreign_source_and_tamper_fail_before_provider(self):
        for tamper in ("foreign", "hash"):
            client = WorkerClient(self.image())
            if tamper == "foreign":
                client.job["source_path"] = api.source_path(OTHER, client.job["source_sha"])
            else:
                client.data = b"different"
            used = []
            with self.assertRaises(ValueError):
                worker.process_one(client, key="test", model="vision",
                                   idea_generator=lambda *a, **k: used.append(1))
            self.assertEqual(used, [])
            self.assertEqual(client.calls[-1][1]["p_error_code"] if tamper == "hash" else
                             client.calls[-1][0], "invalid_source" if tamper == "hash" else "claim_personal_ai_caption")

    def test_ambiguous_provider_failure_is_terminal(self):
        client = WorkerClient(self.image())
        with self.assertRaises(RuntimeError):
            worker.process_one(client, key="test", model="vision",
                               idea_generator=lambda *a, **k: (_ for _ in ()).throw(RuntimeError("provider timeout")))
        self.assertEqual(client.calls[-1][1]["p_error_code"], "provider")

    def test_global_budget_denial_skips_provider(self):
        client = WorkerClient(self.image())
        original = client.rpc
        def deny(name, payload):
            if name == "reserve_personal_ai_provider_call": return [{"allowed": False}]
            return original(name, payload)
        client.rpc = deny
        seen = []
        with self.assertRaises(ValueError):
            worker.process_one(client, key="test", model="vision",
                               idea_generator=lambda *a, **k: seen.append(1))
        self.assertEqual(seen, [])
        self.assertEqual(client.calls[-1][1]["p_error_code"], "limit")


if __name__ == "__main__":
    unittest.main()
