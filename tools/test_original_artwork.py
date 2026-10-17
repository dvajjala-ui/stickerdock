import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock
import zipfile

from PIL import Image
import original_artwork as original


def brief():
    return {"schema": 1, "title": "Test original pack", "character": "An original teal bird",
            "style": "Clean illustration", "stickers": [
                {"id": f"reaction-{i}", "caption": caption, "emotion": "happy", "pose": f"Pose {i}"}
                for i, caption in enumerate(("Hi", "Thanks", "Bye"))]}


def image_bytes(color="red"):
    output = io.BytesIO()
    Image.new("RGB", (512, 512), color).save(output, "PNG")
    return output.getvalue()


class OriginalArtworkTest(unittest.TestCase):
    def test_default_never_contacts_a_provider_and_rejects_mutated_brief(self):
        with tempfile.TemporaryDirectory() as folder:
            provider = Mock()
            result = original.advance(brief(), folder, provider)
            self.assertEqual(result["status"], "spending_disabled")
            provider.submit.assert_not_called()
            changed = brief(); changed["title"] = "Different"
            with self.assertRaises(ValueError):
                original.advance(changed, folder, provider)

    def test_timeout_reserves_attempt_and_cannot_double_submit_after_restart(self):
        with tempfile.TemporaryDirectory() as folder:
            provider = Mock(); provider.submit.side_effect = TimeoutError("simulated")
            with self.assertRaises(TimeoutError):
                original.advance(brief(), folder, provider, True, 3)
            self.assertEqual(original.advance(brief(), folder, provider, True, 3)["status"], "needs_attention")
            provider.submit.assert_called_once()
            state = json.loads((Path(folder) / "original-jobs.json").read_text())
            self.assertEqual(state["submissions"], 1)

    def test_resume_download_failure_does_not_submit_again(self):
        with tempfile.TemporaryDirectory() as folder:
            provider = Mock()
            provider.submit.return_value = {"request_id": "job", "polling_url": "https://api.bfl.ai/v1/get_result?id=job"}
            provider.poll.return_value = {"status": "Ready", "result": {"sample": "https://delivery.eu.bfl.ai/result"}}
            provider.download.side_effect = [OSError("lost connection"), image_bytes()]
            original.advance(brief(), folder, provider, True, 1)
            with self.assertRaises(OSError):
                original.advance(brief(), folder, provider, True, 1)
            self.assertEqual(original.advance(brief(), folder, provider, True, 1)["status"], "ready")
            self.assertEqual(original.advance(brief(), folder, provider, True, 1)["status"], "submission_limit")
            provider.submit.assert_called_once()

    def test_complete_pack_keeps_caption_metadata_and_requires_review(self):
        with tempfile.TemporaryDirectory() as folder:
            provider = Mock()
            provider.submit.return_value = {"request_id": "job", "polling_url": "https://api.bfl.ai/v1/get_result?id=job"}
            provider.poll.return_value = {"status": "Ready", "result": {"sample": "https://delivery.eu.bfl.ai/result"}}
            provider.download.side_effect = [image_bytes(color) for color in ("red", "blue", "green")]
            for _ in range(6):
                original.advance(brief(), folder, provider, True, 3)
            self.assertEqual(original.advance(brief(), folder, provider, True, 3)["status"], "needs_review")
            self.assertEqual(provider.submit.call_count, 3)
            with zipfile.ZipFile(Path(folder) / "review-pack.zip") as archive:
                manifest = json.loads(archive.read("manifest.json"))
                pack = manifest["packs"][0]
                self.assertEqual(manifest["schema"], 5)
                self.assertEqual(pack["name"], brief()["title"])
                self.assertEqual([s["label"] for s in pack["stickers"]], ["Hi", "Thanks", "Bye"])
                for sticker in pack["stickers"]:
                    data = archive.read(f"packs/{pack['id']}/{sticker['name']}")
                    self.assertEqual(hashlib.sha256(data).hexdigest(), sticker["sha256"])
                    self.assertLessEqual(len(data), 100_000)
                    with Image.open(io.BytesIO(data)) as image:
                        self.assertEqual(image.size, (512, 512))
                        self.assertEqual(image.getpixel((0, 0))[3], 0)
            (Path(folder) / "reaction-0.webp").write_bytes(b"damaged")
            with self.assertRaises(ValueError):
                original.advance(brief(), folder, provider, True, 3)

    def test_duplicate_pixels_stop_for_review_without_paying_for_replacement(self):
        with tempfile.TemporaryDirectory() as folder:
            provider = Mock()
            provider.submit.return_value = {"request_id": "job", "polling_url": "https://api.bfl.ai/v1/get_result?id=job"}
            provider.poll.return_value = {"status": "Ready", "result": {"sample": "https://delivery.eu.bfl.ai/result"}}
            provider.download.return_value = image_bytes()
            for _ in range(3):
                original.advance(brief(), folder, provider, True, 3)
            self.assertEqual(original.advance(brief(), folder, provider, True, 3)["status"], "duplicate_artwork")
            self.assertEqual(original.advance(brief(), folder, provider, True, 3)["status"], "needs_attention")
            self.assertEqual(provider.submit.call_count, 2)

    def test_brief_rejects_traversal_duplicates_and_incomplete_packs(self):
        for mutate in (lambda b: b["stickers"][0].update(id="../outside"),
                       lambda b: b["stickers"].pop(),
                       lambda b: b["stickers"][1].update(caption="Hi")):
            value = copy.deepcopy(brief()); mutate(value)
            with self.assertRaises(ValueError):
                original.validate_brief(value)

    def test_only_official_polling_and_delivery_hosts_are_allowed(self):
        for url in ("https://api.bfl.ai.evil.test/v1/get_result", "http://api.bfl.ai/v1/get_result",
                    "https://user@api.bfl.ai/v1/get_result", "https://api.bfl.ai/v1/flux-2-klein-4b"):
            with self.assertRaises(ValueError):
                original.validate_url(url)
        self.assertEqual(original.validate_url("https://api.eu.bfl.ai/v1/get_result?id=test"), "https://api.eu.bfl.ai/v1/get_result?id=test")
        with self.assertRaises(ValueError):
            original.validate_url("https://api.bfl.ai/v1/get_result", delivery=True)

    def test_moderated_results_are_never_downloaded_or_automatically_retried(self):
        with tempfile.TemporaryDirectory() as folder:
            provider = Mock()
            provider.submit.return_value = {"request_id": "job", "polling_url": "https://api.bfl.ai/v1/get_result?id=job"}
            provider.poll.return_value = {"status": "Content Moderated"}
            original.advance(brief(), folder, provider, True, 3)
            self.assertEqual(original.advance(brief(), folder, provider, True, 3)["status"], "rejected")
            self.assertEqual(original.advance(brief(), folder, provider, True, 3)["status"], "rejected")
            provider.download.assert_not_called()
            provider.submit.assert_called_once()


if __name__ == "__main__":
    unittest.main()
