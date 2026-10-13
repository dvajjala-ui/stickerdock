import base64
import io
import json
import os
from unittest.mock import patch
import unittest
import uuid

from PIL import Image
from api import _shared as studio


def image_data_url():
    output = io.BytesIO()
    Image.new("RGB", (32, 32), "red").save(output, "PNG")
    return "data:image/png;base64," + base64.b64encode(output.getvalue()).decode()


class StudioApiTest(unittest.TestCase):
    def test_truncated_jpeg_is_rejected(self):
        image = io.BytesIO()
        Image.new("RGB", (64, 64), "red").save(image, "JPEG")
        data = "data:image/jpeg;base64," + base64.b64encode(image.getvalue()[:-20]).decode()
        with self.assertRaises(studio.StudioError):
            studio.decode_image(data)

    def test_header_only_image_is_rejected_before_upload(self):
        fake = "data:image/png;base64," + base64.b64encode(b"\x89PNG\r\n\x1a\ninvalid data").decode()
        with patch.object(studio, "supabase_request") as request:
            with self.assertRaises(studio.StudioError):
                studio.upload_user_image(fake)
            request.assert_not_called()

    def test_edited_reference_retains_source_rights(self):
        source = "inbox/" + "a" * 64 + ".png"
        calls = []
        def provider(path, **kwargs):
            calls.append((path, kwargs))
            if kwargs.get("method", "GET") == "GET":
                return json.dumps({"license": "REFERENCE_ONLY", "source_url": "https://www.pinterest.com/pin/12345/"}).encode()
            return b""
        with patch.object(studio, "supabase_request", side_effect=provider):
            studio.upload_user_image(image_data_url(), "Edited caption", source)
        metadata = json.loads(calls[1][1]["data"])
        self.assertEqual(metadata["license"], "REFERENCE_ONLY")
        self.assertEqual(metadata["derived_from"], source)

    def test_missing_source_metadata_stays_reference_only(self):
        source = "inbox/" + "a" * 64 + ".png"
        with patch.object(studio, "supabase_request", return_value=None):
            self.assertEqual(studio.source_metadata(source)["license"], "REFERENCE_ONLY")

    def test_invalid_source_cannot_write_anything(self):
        with patch.object(studio, "supabase_request") as request:
            with self.assertRaises(studio.StudioError):
                studio.upload_user_image(image_data_url(), source_path="../../state/runtime.zip")
            request.assert_not_called()

    def test_image_decoder_checks_declared_type_and_size(self):
        data, mime, extension = studio.decode_image(image_data_url())
        self.assertTrue(data.startswith(b"\x89PNG"))
        self.assertEqual((mime, extension), ("image/png", ".png"))
        with self.assertRaises(studio.StudioError):
            studio.decode_image("data:image/jpeg;base64," + base64.b64encode(data).decode())

    def test_upload_writes_media_and_owner_metadata(self):
        calls = []
        def capture(path, **kwargs):
            calls.append((path, kwargs))
            return b""
        with patch.object(studio, "supabase_request", side_effect=capture):
            result = studio.upload_user_image(image_data_url(), "My original")
        self.assertEqual(len(calls), 2)
        self.assertIn("/inbox/", calls[1][0])
        metadata = json.loads(calls[0][1]["data"])
        self.assertEqual(metadata["license"], "USER_OWNED")
        self.assertEqual(len(result["sha256"]), 64)

    def test_storage_paths_reject_traversal(self):
        with self.assertRaises(studio.StudioError):
            studio.storage_path("../secret")

    def test_creator_request_queue_is_private_and_bounded(self):
        source = "inbox/" + "b" * 64 + ".webp"
        calls = []
        def capture(path, **kwargs):
            calls.append((path, kwargs))
            return b""
        with patch.object(studio, "supabase_request", side_effect=capture):
            result = studio.queue_creator_request("  make   it funny  ", source)
        self.assertEqual(result["status"], "queued")
        uuid.UUID(result["id"])
        self.assertEqual(calls[0][0], "/rest/v1/creator_requests")
        payload = json.loads(calls[0][1]["data"])
        self.assertEqual(payload[0]["source_path"], source)
        self.assertEqual(payload[0]["prompt"], "make it funny")
        self.assertEqual(payload[0]["status"], "queued")

    def test_creator_request_rejects_bad_input_before_supabase(self):
        with patch.object(studio, "supabase_request") as request:
            with self.assertRaises(studio.StudioError):
                studio.queue_creator_request("", "inbox/" + "a" * 64 + ".png")
            with self.assertRaises(studio.StudioError):
                studio.queue_creator_request("hello", "../../private.png")
            with self.assertRaises(studio.StudioError):
                studio.queue_creator_request("x" * 501, "inbox/" + "a" * 64 + ".png")
            request.assert_not_called()

    def test_creator_request_status_is_validated(self):
        request_id = str(uuid.uuid4())
        row = [{
            "id": request_id,
            "status": "complete",
            "source_path": "inbox/" + "c" * 64 + ".png",
            "prompt": "caption this",
            "result_path": "inbox/" + "d" * 64 + ".png",
            "error_message": None,
            "attempts": 1,
            "created_at": "2026-10-13T10:00:00Z",
            "updated_at": "2026-10-13T10:01:00Z",
        }]
        with patch.object(studio, "supabase_request", return_value=json.dumps(row).encode()):
            result = studio.creator_request_status(request_id)
        self.assertEqual(result["status"], "complete")
        self.assertEqual(result["result_path"], row[0]["result_path"])
        with self.assertRaises(studio.StudioError):
            studio.creator_request_status("not-a-uuid")


if __name__ == "__main__":
    unittest.main()
