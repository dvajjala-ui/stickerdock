import base64
import io
import json
import os
from unittest.mock import patch
import unittest

from PIL import Image
from api import _shared as studio


def image_data_url():
    output = io.BytesIO()
    Image.new("RGB", (32, 32), "red").save(output, "PNG")
    return "data:image/png;base64," + base64.b64encode(output.getvalue()).decode()


class StudioApiTest(unittest.TestCase):
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
        self.assertIn("/inbox/", calls[0][0])
        metadata = json.loads(calls[1][1]["data"])
        self.assertEqual(metadata["license"], "USER_OWNED")
        self.assertEqual(len(result["sha256"]), 64)

    def test_storage_paths_reject_traversal(self):
        with self.assertRaises(studio.StudioError):
            studio.storage_path("../secret")


if __name__ == "__main__":
    unittest.main()
