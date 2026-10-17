import hashlib
import io
import json
from pathlib import Path
import shutil
import tempfile
import unittest
import zipfile

from PIL import Image

from build_reaction_pack import build
from reaction_media import caption_text, compose, decode_clip, encode_animation, encode_static, read_photo
from sticker_fonts import default_font


FONT = default_font()
CREDIT = {"title": "Test input", "author": "Test fixture", "source_url": "https://example.com/fixture",
          "license": "USER_OWNED", "license_url": "https://example.com/fixture-license"}


@unittest.skipUnless(FONT.is_file(), "Requires the configured sticker font")
class ReactionMediaTests(unittest.TestCase):
    def test_focal_crop_keeps_subjects_at_either_edge(self):
        image = Image.new("RGB", (800, 200), "red")
        image.paste("blue", (400, 0, 800, 200))
        left = compose(image, "Still here", FONT, focus=(0, .5))
        right = compose(image, "Still here", FONT, focus=(1, .5))
        self.assertEqual(left.getpixel((256, 150)), (255, 0, 0, 255))
        self.assertEqual(right.getpixel((256, 150)), (0, 0, 255, 255))
        self.assertEqual(image.getpixel((0, 0)), (255, 0, 0))

    def test_invalid_focus_is_rejected_before_creating_an_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for bad in (None, (float("nan"), .5), (-.1, .5), (True, .5), (1, 1, 1), ("0", .5)):
                with self.assertRaisesRegex(ValueError, "focus"):
                    build(root / "missing.png", ["a", "b", "c"], CREDIT, root / "result", FONT, focus=bad)
                self.assertFalse((root / "result").exists())

    def test_pack_preserves_the_chosen_framing_and_exports_the_visible_subject(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source.png"
            image = Image.new("RGB", (800, 200), "red")
            image.paste("blue", (400, 0, 800, 200)); image.save(source)
            result = build(source, ["One", "Two", "Three"], CREDIT, root / "result", FONT, focus=(1, .5))
            self.assertEqual(result["source"]["framing"], {"fit": "cover", "focus_x": 1., "focus_y": .5})
            with Image.open(root / "result/static-1.webp") as sticker:
                self.assertGreater(sticker.convert("RGB").getpixel((256, 150))[2], 240)

    def fixture(self, root):
        path = root / "motion.gif"
        frames = [Image.new("RGB", (180, 120), color) for color in ("red", "green", "blue", "yellow")]
        frames[0].save(path, save_all=True, append_images=frames[1:], duration=500, loop=0)
        return path

    def test_photo_remains_prominent_and_contain_preserves_transparency(self):
        image = Image.new("RGB", (400, 200), (25, 100, 150))
        covered = compose(image, "Bas kar bhai", FONT)
        self.assertEqual(covered.size, (512, 512))
        self.assertEqual(covered.getpixel((256, 150)), (25, 100, 150, 255))
        contained = compose(image, "Still here", FONT, "contain", "top")
        self.assertEqual(contained.getpixel((0, 0))[3], 0)
        data = encode_static(covered)
        self.assertLessEqual(len(data), 100_000)
        with Image.open(io.BytesIO(data)) as checked:
            self.assertEqual(checked.size, (512, 512))
            self.assertEqual(getattr(checked, "n_frames", 1), 1)

    def test_caption_controls_bounds_and_unicode(self):
        self.assertEqual(caption_text("  Kya   scene hai? "), "Kya scene hai?")
        self.assertEqual(caption_text("क्या बात है"), "क्या बात है")
        for bad in ("", "x" * 81, "hidden\x00text", None):
            with self.assertRaises(ValueError):
                caption_text(bad)
        with self.assertRaises(ValueError):
            build(Path("unused"), "world", CREDIT, Path("unused-result"), FONT)

    def test_no_fake_motion_for_identical_frames(self):
        frame = Image.new("RGBA", (512, 512), "red")
        with self.assertRaisesRegex(ValueError, "no visible motion"):
            encode_animation([frame, frame.copy()], 10)
        with self.assertRaises(ValueError):
            encode_animation([frame], 10)
        with self.assertRaisesRegex(ValueError, "ten seconds"):
            encode_animation([frame] * 60, 6)
        with self.assertRaises(ValueError):
            encode_static(Image.new("RGB", (32, 32)))

    def test_corrupt_or_moving_photo_and_invalid_edit_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bad = root / "bad.png"; bad.write_bytes(b"not really an image")
            with self.assertRaises(Exception):
                read_photo(bad)
            with self.assertRaisesRegex(ValueError, "animated creator"):
                read_photo(self.fixture(root))
            with self.assertRaises(ValueError):
                compose(Image.new("RGB", (32, 32)), "ok", FONT, "stretch")

    @unittest.skipUnless(shutil.which("ffmpeg"), "Requires FFmpeg")
    def test_clip_trim_and_frame_bounds(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = self.fixture(Path(temporary))
            frames = decode_clip(source, start_ms=1000, duration_ms=500, fps=10)
            self.assertGreaterEqual(len(frames), 2)
            self.assertLessEqual(len(frames), 5)
            self.assertEqual(frames[0].getpixel((50, 50))[:3], (0, 0, 255))
            for kwargs in ({"duration_ms": 10_000}, {"start_ms": -1}, {"fps": 60}):
                with self.assertRaises(ValueError):
                    decode_clip(source, **kwargs)

    @unittest.skipUnless(shutil.which("ffmpeg"), "Requires FFmpeg")
    def test_native_archives_have_distinct_kinds_checksums_metadata_and_separate_credits(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); source = self.fixture(root); target = root / "result"
            original = source.read_bytes()
            report = build(source, ["Bas kar bhai", "Again?", "Group chat moment"], CREDIT, target, FONT,
                           duration_ms=2000, fps=6, title="Group chat reactions")
            self.assertEqual(len(report["artifacts"]), 6)
            self.assertFalse(report["source"]["source_uploaded"])
            self.assertEqual(source.read_bytes(), original)
            self.assertTrue((target / "README.txt").is_file())
            for kind in ("STATIC", "ANIMATED"):
                with zipfile.ZipFile(target / f"real-reactions-{kind.lower()}.zip") as archive:
                    self.assertIsNone(archive.testzip())
                    manifest = json.loads(archive.read("manifest.json"))
                    self.assertEqual(manifest["schema"], 5)
                    pack = manifest["packs"][0]
                    self.assertEqual(pack["name"], f"Group chat reactions · {kind.title()}")
                    self.assertEqual(pack["kind"], kind)
                    self.assertEqual(len(pack["stickers"]), 3)
                    for sticker in pack["stickers"]:
                        data = archive.read(f"packs/{pack['id']}/{sticker['name']}")
                        self.assertEqual(hashlib.sha256(data).hexdigest(), sticker["sha256"])
                        self.assertTrue(sticker["label"])
                        with Image.open(io.BytesIO(data)) as image:
                            self.assertEqual(image.size, (512, 512))
                            self.assertEqual(getattr(image, "n_frames", 1) > 1, kind == "ANIMATED")
                    self.assertNotIn("credits.json", archive.namelist())
            with self.assertRaisesRegex(ValueError, "existing outputs"):
                build(source, ["a", "b", "c"], CREDIT, target, FONT)
            with self.assertRaises(ValueError):
                build(source, ["a", "b", "c"], {}, root / "invalid", FONT)
            self.assertFalse((root / "invalid").exists())


if __name__ == "__main__":
    unittest.main()
