import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import zipfile

from PIL import Image
import daily_stickers as generator

FONT = generator.default_font()
NOW = 1791763200  # 2026-10-12 00:00 UTC


def ideas(count, offset=0):
    return [{"caption": f"Reaction number {i}", "mood": generator.MOODS[i % 6],
             "color": generator.COLORS[i % 6], "source": None}
            for i in range(offset, offset + count)]


@unittest.skipUnless(FONT.exists(), "Install DejaVu Sans font")
class DailyStickersTest(unittest.TestCase):
    def test_rendered_assets_and_native_archive(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            records = []
            for i, idea in enumerate(ideas(40)):
                data = generator.render(idea, FONT)
                image = Image.open(io.BytesIO(data))
                self.assertEqual(image.size, (512, 512))
                self.assertEqual(image.getpixel((0, 0))[3], 0)
                self.assertLessEqual(len(data), 100_000)
                self.assertFalse(getattr(image, "is_animated", False))
                path = root / f"{i}.webp"
                path.write_bytes(data)
                records.append({"file": str(path), "id": str(generator.uuid.uuid4())})
            archive_path = root / "complete.zip"
            generator.export_zip(archive_path, records, "2026-10-12")
            with zipfile.ZipFile(archive_path) as archive:
                manifest = json.loads(archive.read("manifest.json"))
                self.assertEqual(manifest["schema"], 4)
                self.assertEqual([len(p["stickers"]) for p in manifest["packs"]], [20, 20])
                self.assertEqual(len(archive.namelist()), 41)
                for pack in manifest["packs"]:
                    self.assertEqual(pack["kind"], "STATIC")
                    self.assertEqual(pack["category"], "GENERAL")
                    for sticker in pack["stickers"]:
                        path = f"packs/{pack['id']}/{sticker['name']}"
                        data = archive.read(path)
                        self.assertEqual(hashlib.sha256(data).hexdigest(), sticker["sha256"])

    def test_eight_spaced_batches_stop_at_forty(self):
        config = {"daily_target": 40, "batch_size": 5}
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            root = Path(temporary)
            with patch.object(generator, "groq_ideas") as call:
                call.side_effect = [ideas(5, i * 5) for i in range(8)]
                for i in range(8):
                    generator.run(config, root, FONT, NOW + i * 10800)
                    generator.run(config, root, FONT, NOW + i * 10800 + 1)
                self.assertEqual(call.call_count, 8)
            state = json.loads((root / "2026-10-12/state.json").read_text())
            self.assertEqual(len(state["records"]), 40)
            self.assertEqual(state["requests"], 8)
            self.assertTrue((root / "2026-10-12/daily-complete.zip").exists())

    def test_rate_limit_saves_retry_after_and_counts_attempt(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            root = Path(temporary)
            error = urllib.error.HTTPError(generator.API_URL, 429, "rate limited", {"Retry-After": "20000"}, None)
            with patch.object(generator, "groq_ideas", side_effect=error) as call:
                with self.assertRaises(ValueError):
                    generator.run({}, root, FONT, NOW)
                generator.run({}, root, FONT, NOW + 10800)
                self.assertEqual(call.call_count, 1)
            state = json.loads((root / "2026-10-12/state.json").read_text())
            self.assertEqual(state["next_request_at"], NOW + 20000)
            self.assertEqual(state["requests"], 1)

    def test_resume_saved_ideas_without_api_call(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            root = Path(temporary)
            with patch.object(generator, "groq_ideas", return_value=ideas(5)), patch.object(generator, "render", side_effect=OSError("interrupted")):
                with self.assertRaises(OSError):
                    generator.run({}, root, FONT, NOW)
            with patch.object(generator, "groq_ideas") as call:
                generator.run({}, root, FONT, NOW + 1)
                call.assert_not_called()
            state = json.loads((root / "2026-10-12/state.json").read_text())
            self.assertEqual(len(state["records"]), 5)
            self.assertIsNone(state["pending"])

    def test_provider_cooldown_survives_midnight(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            root = Path(temporary)
            error = urllib.error.HTTPError(generator.API_URL, 429, "limited", {"Retry-After": "20000"}, None)
            with patch.object(generator, "groq_ideas", side_effect=error) as call:
                with self.assertRaises(ValueError):
                    generator.run({}, root, FONT, NOW + 86300)
                generator.run({}, root, FONT, NOW + 86400)
                self.assertEqual(call.call_count, 1)

    def test_request_cap_and_daily_reset(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            root = Path(temporary)
            with patch.object(generator, "groq_ideas", side_effect=ValueError("bad output")) as call:
                with self.assertRaises(ValueError):
                    generator.run({"max_requests_per_day": 1}, root, FONT, NOW)
                generator.run({"max_requests_per_day": 1}, root, FONT, NOW + 10800)
                self.assertEqual(call.call_count, 1)
            with patch.object(generator, "groq_ideas", return_value=ideas(5)) as call:
                generator.run({}, root, FONT, NOW + 86400)
                call.assert_called_once()

    def test_caption_validation_rejects_duplicates_and_injection(self):
        cases = [ideas(2), [{**ideas(1)[0], "caption": "<script>alert(1)</script>"}],
                 [{**ideas(1)[0], "source": 99}], [{**ideas(1)[0], "mood": "execute"}]]
        # Printable markup is drawn literally, never interpreted or executed.
        self.assertEqual(generator.validate_ideas({"stickers": cases[1]}, 1, [], 0)[0]["caption"], cases[1][0]["caption"])
        with self.assertRaises(ValueError):
            generator.validate_ideas({"stickers": ideas(1)}, 1, ["Reaction number 0"], 0)
        for raw in (cases[0], cases[2], cases[3]):
            with self.assertRaises(ValueError):
                generator.validate_ideas({"stickers": raw}, 1, [], 0)

    def test_feed_failure_is_explicit_fallback(self):
        with patch.object(generator, "fetch_bytes", side_effect=OSError()):
            sources, warnings = generator.references(["https://example.org/feed"], [], NOW)
        self.assertEqual(sources, [])
        self.assertTrue(warnings)

    def test_old_feed_titles_are_not_presented_as_news(self):
        xml = b'<rss><channel><item><title>Old</title><link>https://example.org/old</link><pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate></item><item><title>New</title><link>https://example.org/new</link><pubDate>Mon, 12 Oct 2026 00:00:00 GMT</pubDate></item></channel></rss>'
        with patch.object(generator, "fetch_bytes", return_value=xml):
            sources, _ = generator.references(["https://example.org/feed"], [], NOW)
        self.assertEqual([s["title"] for s in sources], ["New"])

    def test_animation_is_bounded_and_has_real_frames(self):
        data = generator.render(ideas(1)[0], FONT, animated=True)
        self.assertLessEqual(len(data), 500_000)
        with Image.open(io.BytesIO(data)) as image:
            self.assertTrue(image.is_animated)
            self.assertEqual(image.size, (512, 512))
            self.assertGreaterEqual(image.n_frames, 2)
        # Independently inspect the WhatsApp ANMF duration fields.
        offset, durations = 12, []
        while offset + 8 <= len(data):
            length = int.from_bytes(data[offset + 4:offset + 8], "little")
            if data[offset:offset + 4] == b"ANMF":
                durations.append(int.from_bytes(data[offset + 20:offset + 23], "little"))
            offset += 8 + length + (length % 2)
        self.assertTrue(durations)
        self.assertGreaterEqual(min(durations), 8)
        self.assertLessEqual(sum(durations), 10000)

    def test_photo_mode_passes_image_to_vision_and_renders(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            root = Path(temporary)
            inputs = root / "inputs"
            inputs.mkdir()
            photo = inputs / "reference.png"
            Image.new("RGB", (800, 600), "red").save(photo)
            config = {"modes": ["photo"], "image_directory": str(inputs), "vision_model": "test-vision"}
            with patch.object(generator, "groq_ideas", return_value=ideas(5)) as call:
                generator.run(config, root / "outputs", FONT, NOW)
                self.assertEqual(call.call_args.args[1], "test-vision")
            self.assertEqual(Path(call.call_args.args[-1]).resolve(), photo.resolve())
            records = json.loads((root / "outputs/2026-10-12/state.json").read_text())["records"]
            with Image.open(records[0]["file"]) as sticker:
                r, g, b, _ = sticker.convert("RGBA").getpixel((256, 150))
                self.assertGreater(r, 200)
                self.assertLess(g, 30)
                self.assertLess(b, 30)

    def test_portrait_photo_fills_reaction_area_without_changing_the_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            source=Path(temporary)/"portrait.png"
            Image.new("RGB",(120,800),"red").save(source)
            original=source.read_bytes()
            rendered=generator.render(ideas(1)[0],FONT,source)
            with Image.open(io.BytesIO(rendered)) as image:
                self.assertGreater(image.convert("RGBA").getpixel((35,150))[0],200)
                self.assertEqual(image.convert("RGBA").getpixel((0,0))[3],0)
            self.assertEqual(source.read_bytes(),original)

    def test_static_and_animated_packs_stay_separate(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            root = Path(temporary)
            config = {"daily_target": 10, "modes": ["illustration", "animated"], "rating": "MATURE"}
            with patch.object(generator, "groq_ideas", side_effect=[ideas(5), ideas(5, 5)]):
                generator.run(config, root, FONT, NOW)
                generator.run(config, root, FONT, NOW + 10800)
            with zipfile.ZipFile(root / "2026-10-12/daily-complete.zip") as archive:
                packs = json.loads(archive.read("manifest.json"))["packs"]
            self.assertEqual([p["kind"] for p in packs], ["STATIC", "ANIMATED"])
            self.assertEqual([len(p["stickers"]) for p in packs], [5, 5])
            self.assertTrue(all(p["rating"] == "MATURE" for p in packs))

    def test_content_plan_exports_category_separated_packs(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            root = Path(temporary)
            config = {"daily_target": 6, "batch_size": 3, "interval_seconds": 60,
                      "modes": ["illustration"], "topic_plans": [
                          {"topic": "A current event reaction", "pack_category": "NEWS"},
                          {"topic": "An original series reaction", "pack_category": "MOVIES"}]}
            with patch.object(generator, "groq_ideas", side_effect=[ideas(3), ideas(3, 3)]):
                generator.run(config, root, FONT, NOW)
                generator.run(config, root, FONT, NOW + 61)
            with zipfile.ZipFile(root / "2026-10-12/daily-complete.zip") as archive:
                packs = json.loads(archive.read("manifest.json"))["packs"]
            self.assertEqual([p["category"] for p in packs], ["NEWS", "MOVIES"])

    def test_categorized_batch_receives_only_its_bound_source(self):
        source = "https://example.org/current"
        config = {"daily_target": 3, "batch_size": 3, "topic_plans": [{
            "topic": "A current reaction", "pack_category": "NEWS", "source": source}],
            "references": [{"title": "Current", "url": source},
                           {"title": "Unrelated", "url": "https://example.org/other"}]}
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}), \
                patch.object(generator, "groq_ideas", return_value=ideas(3)) as call:
            generator.run(config, Path(temporary), FONT, NOW)
        self.assertEqual(call.call_args.args[4], [{"title": "Current", "url": source}])

    def test_remaining_stickers_do_not_make_ineligible_tiny_batch(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            with patch.object(generator, "groq_ideas", return_value=ideas(6)) as call:
                generator.run({"daily_target": 6}, Path(temporary), FONT, NOW)
                self.assertEqual(call.call_args.args[2], 6)


if __name__ == "__main__":
    unittest.main()
