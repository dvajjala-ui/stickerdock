import io
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image
from daily_stickers import reference_image, run
from video_stickers import clip_frames, render_clip

FONT = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")


@unittest.skipUnless(shutil.which("ffmpeg") and FONT.exists(), "Requires FFmpeg and DejaVu Sans")
class VideoStickerTest(unittest.TestCase):
    def fixture(self, root):
        path = root / "clip.gif"
        frames = [Image.new("RGB", (320, 240), color) for color in ("red", "green", "blue", "yellow")]
        frames[0].save(path, save_all=True, append_images=frames[1:], duration=250, loop=0)
        return path

    def test_real_gif_becomes_captioned_animated_webp(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = self.fixture(Path(temporary))
            data = render_clip(path, {"caption": "Scene kya hai bhai?"}, FONT)
            self.assertLessEqual(len(data), 500000)
            with Image.open(io.BytesIO(data)) as image:
                self.assertTrue(image.is_animated)
                self.assertEqual(image.size, (512, 512))
                self.assertGreaterEqual(image.n_frames, 2)
                self.assertLessEqual(image.n_frames, 16)
                self.assertEqual(image.convert("RGBA").getpixel((0, 0))[3], 0)

    def test_real_mp4_preview_and_bounded_frames(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            gif = self.fixture(root)
            mp4 = root / "clip.mp4"
            subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
                            "-i", str(gif), "-pix_fmt", "yuv420p", str(mp4)], check=True, timeout=20)
            frames = clip_frames(mp4)
            self.assertGreaterEqual(len(frames), 2)
            self.assertLessEqual(len(frames), 16)
            preview = reference_image(mp4)
            self.assertEqual(preview.mode, "RGB")

    def test_non_media_and_corrupted_input_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "bad.gif"
            path.write_bytes(b"not an image")
            with self.assertRaises(ValueError):
                clip_frames(path)
            with self.assertRaises(ValueError):
                clip_frames(Path(temporary) / "playlist.m3u8")

    def test_factory_clip_batch_exports_animated_pack(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict("os.environ", {"GROQ_API_KEY": "test-only"}):
            root = Path(temporary)
            inputs = root / "inputs"
            inputs.mkdir()
            clip = self.fixture(inputs)
            ideas = [{"caption": f"Scene number {i}", "mood": "wow", "color": "#FFD166", "source": None} for i in range(3)]
            config = {"daily_target": 3, "batch_size": 3, "modes": ["clip"],
                      "image_directory": str(inputs), "vision_model": "test-vision"}
            with patch("daily_stickers.groq_ideas", return_value=ideas) as call, \
                    patch("video_stickers.clip_frames", wraps=clip_frames) as decode:
                run(config, root / "outputs", FONT, now=1791763200)
                self.assertEqual(call.call_args.args[-1], str(clip))
                self.assertEqual(decode.call_count, 2)  # one preview, one shared render input
            records = json.loads((root / "outputs/2026-10-12/state.json").read_text())["records"]
            self.assertTrue(all(record["kind"] == "ANIMATED" for record in records))
            self.assertTrue(all(record["media_credit"]["source"] == "user-supplied" for record in records))
            self.assertTrue((root / "outputs/2026-10-12/daily-complete.zip").exists())


if __name__ == "__main__":
    unittest.main()
