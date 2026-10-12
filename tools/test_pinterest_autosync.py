import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from pinterest_autosync.config import load_config
from pinterest_autosync.connectors import APIBudgetExceeded, OfficialBoards, PublicTopics
from pinterest_autosync.database import Database
from pinterest_autosync.downloader import StorageLimitReached, UnsupportedMedia, official_download, validate_cdn_url
from pinterest_autosync.runner import AlreadyRunning, ProcessLock, run_scan

ROOT = Path(__file__).resolve().parent


class DummyNative:
    def __init__(self, pin_id):
        self.pin_id = pin_id

    def to_dict(self):
        return {"id": self.pin_id, "url": f"https://www.pinterest.com/pin/{self.pin_id}/",
                "media_type": "image"}


class FakeHTTPResponse:
    def __init__(self, status=200, payload=None, headers=None, data=b"abcdef"):
        self.status_code = status
        self._payload = payload or {}
        self.headers = headers or {"Content-Type": "image/jpeg",
                                   "Content-Length": str(len(data))}
        self._data = data

    def json(self):
        return self._payload

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(self.status_code)

    def iter_content(self, chunk_size):
        yield self._data

    def __enter__(self):
        return self

    def __exit__(self, *_):
        pass


class FakeHTTP:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def get(self, url, **kwargs):
        self.calls.append((url, kwargs))
        return self.responses.pop(0)


class FakePublic:
    def __init__(self, count):
        self.count = count

    def discover(self):
        return [PublicTopics.from_native(DummyNative(str(1000000 + index)), "test")
                for index in range(self.count)]

    def get_single(self, _):
        return None


class FakeOfficial:
    def discover(self, _):
        return []


class PinterestAutoSyncTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.config = load_config(ROOT / "pinterest-production.json")
        self.config["limits"]["delay_between_downloads_seconds"] = 0
        self.config["limits"]["daily_downloads"] = 4
        self.config["limits"]["max_downloads_per_scan"] = 3
        self.config["unofficial"]["enabled"] = True

    def test_production_defaults_match_bounded_schedule(self):
        actual = load_config(ROOT / "pinterest-production.json")
        self.assertEqual(actual["limits"]["daily_downloads"], 90)
        self.assertEqual(actual["limits"]["max_downloads_per_scan"], 30)
        self.assertEqual(actual["schedule"]["interval_hours"], 6)
        self.assertFalse(actual["official"]["enabled"])
        self.assertFalse(actual["unofficial"]["enabled"])

    def test_official_image_selection_and_restricted_video(self):
        image = {"id": "123456789", "media": {"media_type": "image", "images": {
            "small": {"url": "https://i.pinimg.com/small.jpg", "width": 100, "height": 100},
            "large": {"url": "https://i.pinimg.com/large.jpg", "width": 1200, "height": 800}}}}
        self.assertEqual(OfficialBoards._candidate(image, "99999").media_url,
                         "https://i.pinimg.com/large.jpg")
        video = {"id": "123456789", "media": {"media_type": "video", "images": {
            "preview": {"url": "https://i.pinimg.com/preview.jpg"}}}}
        self.assertIsNone(OfficialBoards._candidate(video, "99999").media_url)

    def test_official_api_budget_reserves_before_request(self):
        with Database(self.root / "test.sqlite3") as database, patch.dict(
                "os.environ", {"PINTEREST_ACCESS_TOKEN": "test-only"}):
            config = self.config["official"]
            config["_api_cap"] = 1
            http = FakeHTTP([FakeHTTPResponse(payload={"items": [], "bookmark": None})])
            client = OfficialBoards(config, database, http)
            self.assertEqual(client.fetch_page("12345", None)["items"], [])
            with self.assertRaises(APIBudgetExceeded):
                client.fetch_page("12345", None)
            self.assertEqual(database.api_used_24h(), 1)
            self.assertNotIn("test-only", http.calls[0][0])

    def test_cdn_redirect_and_size_controls(self):
        validate_cdn_url("https://i.pinimg.com/image.jpg")
        for invalid in ("http://i.pinimg.com/a.jpg", "https://pinimg.com.evil.org/a.jpg",
                        "file:///etc/passwd"):
            with self.assertRaises(ValueError):
                validate_cdn_url(invalid)
        stage = self.root / "stage"
        stage.mkdir()
        too_large = FakeHTTPResponse(data=b"123456", headers={
            "Content-Length": "6", "Content-Type": "image/jpeg"})
        with self.assertRaises(StorageLimitReached):
            official_download("https://i.pinimg.com/a.jpg", stage, "image", 5,
                              FakeHTTP([too_large]))
        redirect = FakeHTTPResponse(status=302, headers={"Location": "https://evil.example/a"})
        with self.assertRaises(ValueError):
            official_download("https://i.pinimg.com/a.jpg", stage, "image", 100,
                              FakeHTTP([redirect]))

    def test_daily_and_per_scan_caps_survive_repeated_runs(self):
        def fake_save(item, database, base, _limits):
            folder = base / "images"
            folder.mkdir(exist_ok=True)
            path = folder / (item.pin_id + ".jpg")
            path.write_bytes(b"test" + item.pin_id.encode())
            database.mark_done(item.key, str(path.relative_to(base)), item.pin_id,
                               path.stat().st_size)
            return "saved", str(path.relative_to(base))

        with patch("pinterest_autosync.runner.save_candidate", side_effect=fake_save):
            first = run_scan(self.config, self.root, official=FakeOfficial(), public=FakePublic(10))
            second = run_scan(self.config, self.root, official=FakeOfficial(), public=FakePublic(10))
            third = run_scan(self.config, self.root, official=FakeOfficial(), public=FakePublic(10))
        self.assertEqual(first["new_downloads"], 3)
        self.assertEqual(second["new_downloads"], 1)
        self.assertEqual(third["new_downloads"], 0)

    def test_nonoverlap_lock(self):
        with ProcessLock(self.root / "run.lock"):
            with self.assertRaises(AlreadyRunning):
                with ProcessLock(self.root / "run.lock"):
                    pass


if __name__ == "__main__":
    unittest.main()
