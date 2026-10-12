import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

from PIL import Image
import media_library as library

NOW = 1791763200


def photo_bytes():
    output = io.BytesIO()
    Image.new("RGB", (1200, 800), "red").save(output, format="JPEG")
    return output.getvalue()


def asset(url="https://example.org/photo.jpg", license_id="CC0"):
    return {"download_url": url, "source_url": "https://example.org/source",
            "license": license_id, "title": "Example reference"}


class MediaLibraryTest(unittest.TestCase):
    def test_commons_filters_license_metadata(self):
        def page(name):
            return {"title": "File:Cat.jpg", "imageinfo": [{"thumburl": "https://upload.wikimedia.org/cat.jpg",
                "descriptionurl": "https://commons.wikimedia.org/wiki/File:Cat.jpg",
                "extmetadata": {"LicenseShortName": {"value": name}, "Artist": {"value": "<b>Artist</b>"}}}]}
        response = {"query": {"pages": {str(i): page(name) for i, name in enumerate(
            ["CC0", "Public domain", "CC BY-SA 4.0", "All rights reserved", ""])}}}
        with patch.object(library, "fetch", return_value=json.dumps(response).encode()):
            candidates = library.commons_candidates("cats")
        self.assertEqual([item["license"] for item in candidates], ["CC0", "PUBLIC_DOMAIN"])
        self.assertTrue(all(item["credit"] == "Artist" for item in candidates))

    def test_save_sanitizes_and_keeps_provenance(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(library, "fetch", return_value=photo_bytes()):
            root = Path(temporary)
            saved = library.save_asset(asset(), root)
            with Image.open(root / saved["file"]) as photo:
                self.assertEqual(photo.format, "PNG")
                self.assertLessEqual(max(photo.size), 1024)
            self.assertEqual(saved["source_url"], "https://example.org/source")
            self.assertEqual(saved["license"], "CC0")

    def test_unknown_license_never_downloads(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(library, "fetch") as fetch:
            with self.assertRaises(ValueError):
                library.save_asset(asset(license_id="Pinterest visible"), Path(temporary))
            fetch.assert_not_called()

    def test_html_disguised_as_jpeg_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(library, "fetch", return_value=b"<html>not a photo</html>"):
            with self.assertRaises(OSError):
                library.save_asset(asset(), Path(temporary))
            self.assertEqual(list(Path(temporary).iterdir()), [])

    def test_sync_caps_downloads_and_skips_known_urls(self):
        config = {"daily_download_limit": 6, "max_downloads_per_run": 2,
                  "queue": [asset(f"https://example.org/{i}.jpg") for i in range(8)]}
        with tempfile.TemporaryDirectory() as temporary, patch.object(library, "fetch", return_value=photo_bytes()) as fetch:
            root = Path(temporary)
            first = library.sync(config, root, NOW)
            self.assertEqual(first["added"], 1)  # same content hashes deduplicate
            library.sync(config, root, NOW + 1)
            self.assertEqual(fetch.call_count, 2)
            library.sync(config, root, NOW + 3600)
            library.sync(config, root, NOW + 7200)
            self.assertLessEqual(fetch.call_count, 4)

    def test_429_cooldown_crosses_midnight(self):
        error = urllib.error.HTTPError("https://example.org/photo.jpg", 429, "limited", {"Retry-After": "20000"}, None)
        with tempfile.TemporaryDirectory() as temporary, patch.object(library, "fetch", side_effect=error) as fetch:
            root = Path(temporary)
            library.sync({"queue": [asset()]}, root, NOW + 86300)
            library.sync({"queue": [asset()]}, root, NOW + 86400)
            self.assertEqual(fetch.call_count, 1)

    def test_http_and_embedded_credentials_are_rejected(self):
        for url in ["http://example.org/a.jpg", "https://user:password@example.org/a.jpg", "file:///tmp/a"]:
            with self.assertRaises(ValueError):
                library.https_url(url)

    def test_library_provenance_survives_import_into_generator(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            saved = {**asset(), "file": "test.png", "credit": "Author"}
            library.atomic_json(root / "library.json", {"assets": [saved]})
            self.assertEqual(library.provenance(root / "test.png")["credit"], "Author")

    def test_pinterest_downloads_require_per_pin_rights(self):
        pins = {"items": [{"id": str(i), "title": "Reference", "media": {"images": {
            "small": {"url": f"https://i.pinimg.com/{i}.jpg", "width": 400}}}} for i in (1, 2)]}
        with patch.dict("os.environ", {"PINTEREST_ACCESS_TOKEN": "test-only"}), patch.object(
                library, "fetch", return_value=json.dumps(pins).encode()) as fetch:
            candidates, bookmarks = library.pinterest_candidates({
                "pinterest_board_id": "123", "pinterest_pin_licenses": {"1": "USER_OWNED"}})
        self.assertEqual(len(bookmarks), 2)
        self.assertEqual(len(candidates), 1)
        self.assertEqual(candidates[0]["download_url"], "https://i.pinimg.com/1.jpg")
        self.assertIn("api.pinterest.com/v5/boards/123/pins", fetch.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
