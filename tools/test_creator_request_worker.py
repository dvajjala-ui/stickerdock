import hashlib
from pathlib import Path
import tempfile
import unittest
import uuid
from unittest.mock import patch

import creator_request_worker as creator


class FakeClient:
    def __init__(self, request=None, source=b"source-bytes"):
        self.request = request
        self.source = source
        self.uploads = []
        self.updates = []

    def claim_creator_request(self, worker_id):
        self.worker_id = worker_id
        return self.request

    def download(self, bucket, path):
        self.downloaded = (bucket, path)
        return self.source

    def upload(self, bucket, path, data, content_type="application/octet-stream"):
        self.uploads.append((bucket, path, data, content_type))

    def update_creator_request(self, request_id, values):
        self.updates.append((request_id, values))


class CreatorRequestWorkerTest(unittest.TestCase):
    def request_for(self, source, prompt="make it funny"):
        digest = hashlib.sha256(source).hexdigest()
        return {
            "id": str(uuid.uuid4()),
            "source_path": f"inbox/{digest}.png",
            "prompt": prompt,
        }

    def test_idle_when_nothing_is_queued(self):
        client = FakeClient()
        self.assertEqual(creator.process_one(client), {"status": "idle"})
        self.assertEqual(client.worker_id, "github-actions")

    def test_success_preserves_private_provenance_and_completes_request(self):
        source = b"owned-source-image"
        request = self.request_for(source, "  funny   caption  ")
        client = FakeClient(request, source)
        sticker = b"RIFF\x0c\x00\x00\x00WEBPVP8 test"
        with tempfile.TemporaryDirectory() as directory:
            font = Path(directory) / "font.ttf"
            font.write_bytes(b"font")
            with patch.object(creator, "inbox_metadata", return_value={
                "license": "USER_OWNED",
                "source_url": "creator-studio://manual",
                "download_url": "creator-studio://manual",
            }):
                result = creator.process_one(
                    client,
                    worker_id="run-123",
                    key="groq-key",
                    model="vision-model",
                    font_path=font,
                    idea_generator=lambda *args, **kwargs: [{
                        "caption": "OK", "mood": "happy", "color": "#FFD166", "source": None
                    }],
                    renderer=lambda *args, **kwargs: sticker,
                )
        self.assertEqual(result["status"], "complete")
        self.assertEqual(client.worker_id, "run-123")
        self.assertEqual(len(client.uploads), 2)
        self.assertTrue(client.uploads[0][1].startswith("inbox/"))
        self.assertTrue(client.uploads[1][1].startswith("source-metadata/"))
        self.assertEqual(client.updates[-1][1]["status"], "complete")
        self.assertEqual(client.updates[-1][1]["result_path"], result["result_path"])

    def test_hash_mismatch_fails_closed_and_marks_request_failed(self):
        request = self.request_for(b"expected")
        client = FakeClient(request, b"different")
        with self.assertRaises(ValueError):
            creator.process_one(client, key="groq-key", model="vision-model")
        self.assertEqual(client.uploads, [])
        self.assertEqual(client.updates[-1][1]["status"], "failed")
        self.assertNotIn("different", client.updates[-1][1]["error_message"])

    def test_invalid_request_payload_is_not_processed(self):
        request = {"id": str(uuid.uuid4()), "source_path": "../../secret.png", "prompt": "hello"}
        client = FakeClient(request)
        with self.assertRaises(ValueError):
            creator.process_one(client, key="groq-key", model="vision-model")
        self.assertEqual(client.uploads, [])
        self.assertEqual(client.updates[-1][1]["status"], "failed")


if __name__ == "__main__":
    unittest.main()
