import base64
import io
import json
import os
from unittest.mock import patch
import unittest
import uuid

from PIL import Image, PngImagePlugin
from api import _shared as studio


def image_data_url():
    output = io.BytesIO()
    Image.new("RGB", (32, 32), "red").save(output, "PNG")
    return "data:image/png;base64," + base64.b64encode(output.getvalue()).decode()


class StudioApiTest(unittest.TestCase):
    def test_legacy_pinterest_source_cannot_enter_sticker_conversion(self):
        with patch.object(studio,"source_metadata",return_value={"source":"pinterest-official"}), patch.object(studio,"supabase_request") as storage:
            with self.assertRaisesRegex(studio.StudioError,"Pinterest references cannot"):
                studio.fetch_inbox("inbox/"+"a"*64+".png")
        storage.assert_not_called()

    def _supabase_headers_for(self, key):
        captured = {}

        def fake_request(url, method="GET", data=None, headers=None, limit=None, missing_ok=False):
            captured.update(headers or {})
            return b"[]"

        with patch.dict(os.environ, {
            "SUPABASE_URL": "https://example.supabase.co",
            "SUPABASE_SERVICE_ROLE_KEY": key,
        }, clear=False), patch.object(studio, "request", side_effect=fake_request):
            studio.supabase_request("/rest/v1/creator_requests")
        return captured

    def test_new_secret_key_is_apikey_only(self):
        headers = self._supabase_headers_for("sb_secret_test-only")
        self.assertEqual(headers["apikey"], "sb_secret_test-only")
        self.assertNotIn("Authorization", headers)

    def test_legacy_service_role_keeps_bearer(self):
        headers = self._supabase_headers_for("legacy-service-role")
        self.assertEqual(headers["apikey"], "legacy-service-role")
        self.assertEqual(headers["Authorization"], "Bearer legacy-service-role")

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
            result = studio.upload_user_image(image_data_url(), "My original", rights="USER_OWNED")
        self.assertEqual(len(calls), 2)
        self.assertIn("/inbox/", calls[1][0])
        metadata = json.loads(calls[0][1]["data"])
        self.assertEqual(metadata["license"], "USER_OWNED")
        self.assertEqual(metadata["purpose"], "SOURCE")
        self.assertEqual(len(result["sha256"]), 64)

    def test_unspecified_rights_stay_reference_only(self):
        calls = []
        with patch.object(studio, "supabase_request", side_effect=lambda path, **kwargs:
                calls.append((path, kwargs)) or b""):
            studio.upload_user_image(image_data_url(), "Inspiration")
        metadata = json.loads(calls[0][1]["data"])
        self.assertEqual((metadata["license"], metadata["purpose"]),
                         ("REFERENCE_ONLY", "REFERENCE"))
        with patch.object(studio, "supabase_request") as storage:
            with self.assertRaises(studio.StudioError):
                studio.upload_user_image(image_data_url(), rights="CC0")
            storage.assert_not_called()

    def test_upload_applies_orientation_and_removes_embedded_metadata(self):
        original = io.BytesIO()
        exif = Image.Exif()
        exif[274] = 6
        exif[270] = "SENSITIVE_MARKER"
        Image.new("RGB", (40, 20), "red").save(original, "JPEG", exif=exif)
        data_url = "data:image/jpeg;base64," + base64.b64encode(original.getvalue()).decode()
        calls = []
        with patch.object(studio, "supabase_request", side_effect=lambda path, **kwargs:
                calls.append((path, kwargs)) or b""):
            result = studio.upload_user_image(data_url, "Oriented")
        stored = calls[-1][1]["data"]
        self.assertEqual(calls[-1][1]["content_type"], "image/webp")
        self.assertEqual(result["sha256"], __import__("hashlib").sha256(stored).hexdigest())
        self.assertNotIn(b"SENSITIVE_MARKER", stored)
        with Image.open(io.BytesIO(stored)) as image:
            self.assertEqual(image.size, (20, 40))
            self.assertFalse(image.getexif())

    def test_upload_keeps_alpha_and_drops_png_text(self):
        original = io.BytesIO()
        metadata = PngImagePlugin.PngInfo()
        metadata.add_text("Comment", "SENSITIVE_MARKER")
        Image.new("RGBA", (16, 16), (255, 0, 0, 0)).save(original, "PNG", pnginfo=metadata)
        data_url = "data:image/png;base64," + base64.b64encode(original.getvalue()).decode()
        calls = []
        with patch.object(studio, "supabase_request", side_effect=lambda path, **kwargs:
                calls.append((path, kwargs)) or b""):
            studio.upload_user_image(data_url, "Transparent")
        stored = calls[-1][1]["data"]
        self.assertNotIn(b"SENSITIVE_MARKER", stored)
        with Image.open(io.BytesIO(stored)) as image:
            self.assertEqual(image.convert("RGBA").getpixel((0, 0))[3], 0)

    def test_media_page_reaches_items_after_first_hundred(self):
        requests = []
        def listing(path, **kwargs):
            payload = json.loads(kwargs["data"])
            requests.append(payload)
            names = [f"{payload['offset'] + index:064x}.png" for index in range(100)]
            return json.dumps([{"name": name, "metadata": {"size": 128}} for name in names]).encode()
        with patch.object(studio, "supabase_request", side_effect=listing):
            first = studio.list_inbox(0, 100)
            second = studio.list_inbox(first["next_offset"], 100)
        self.assertEqual((len(first["items"]), second["next_offset"]), (100, 200))
        self.assertNotEqual(first["items"][-1]["path"], second["items"][0]["path"])
        self.assertEqual([call["offset"] for call in requests], [0, 100])
        with self.assertRaises(studio.StudioError):
            studio.list_inbox(-1)

    def test_media_preview_is_small_and_uses_human_title(self):
        source = "inbox/" + "a" * 64 + ".png"
        picture = base64.b64decode(image_data_url().split(",", 1)[1])
        def storage(path, **kwargs):
            if "source-metadata" in path:
                return json.dumps({"source": "creator-studio", "license": "USER_OWNED",
                    "title": "My cat reaction"}).encode()
            return picture
        with patch.object(studio, "supabase_request", side_effect=storage):
            result = studio.preview_inbox(source)
        self.assertEqual((result["name"], result["license"]), ("My cat reaction", "USER_OWNED"))
        self.assertEqual(result["purpose"], "SOURCE")
        self.assertTrue(result["preview"].startswith("data:image/webp;base64,"))
        self.assertLess(len(result["preview"]), 100_000)

    def test_storage_paths_reject_traversal(self):
        with self.assertRaises(studio.StudioError):
            studio.storage_path("../secret")

    def test_creator_request_queue_is_private_and_bounded(self):
        source = "inbox/" + "b" * 64 + ".webp"
        calls = []

        def capture(path, **kwargs):
            calls.append((path, kwargs))
            if kwargs.get("method", "GET") == "GET":
                return b"[]"
            return b""

        with patch.object(studio, "supabase_request", side_effect=capture):
            result = studio.queue_creator_request("  make   it funny  ", source)
        self.assertEqual(result["status"], "queued")
        uuid.UUID(result["id"])
        self.assertEqual(len(calls), 2)
        self.assertIn("status=in.%28queued%2Crunning%29", calls[0][0])
        self.assertEqual(calls[1][0], "/rest/v1/creator_requests")
        payload = json.loads(calls[1][1]["data"])
        self.assertEqual(payload[0]["source_path"], source)
        self.assertEqual(payload[0]["prompt"], "make it funny")
        self.assertEqual(payload[0]["status"], "queued")

    def test_creator_request_queue_rejects_when_backlog_is_full(self):
        source = "inbox/" + "b" * 64 + ".webp"
        backlog = [{"id": str(uuid.uuid4())} for _ in range(studio.CREATOR_MAX_OUTSTANDING)]
        with patch.object(studio, "supabase_request", return_value=json.dumps(backlog).encode()) as request:
            with self.assertRaisesRegex(studio.StudioError, "queue is busy"):
                studio.queue_creator_request("make it funny", source)
        request.assert_called_once()
        self.assertIn("status=in.%28queued%2Crunning%29", request.call_args.args[0])

    def test_creator_request_queue_rejects_invalid_backlog_response(self):
        source = "inbox/" + "b" * 64 + ".webp"
        with patch.object(studio, "supabase_request", return_value=b"{}"):
            with self.assertRaisesRegex(studio.StudioError, "invalid Creator queue"):
                studio.queue_creator_request("make it funny", source)

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
