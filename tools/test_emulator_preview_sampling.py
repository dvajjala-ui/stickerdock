from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image
from emulator_clip_creator_smoke import ClipCreatorSmoke


class PreviewSamplingTest(unittest.TestCase):
    def harness(self, output, colors):
        test = object.__new__(ClipCreatorSmoke)
        test.output = Path(output)
        frames = iter(colors)

        def screenshot(name):
            # Changing system chrome must not pass a frozen artwork crop.
            index, color = next(frames)
            image = Image.new("RGB", (30, 30), (index, index, index))
            image.paste(color, (5, 5, 25, 25))
            image.save(test.output / (name + ".png"))

        test.screenshot = screenshot
        return test

    @patch("emulator_clip_creator_smoke.time.sleep")
    def test_repeated_loop_phase_then_different_frame_passes(self, sleep):
        with tempfile.TemporaryDirectory() as output:
            test = self.harness(output, enumerate(["red", "red", "red", "blue"]))
            test.assert_preview_moves("moving", (5, 5, 25, 25))
            self.assertEqual(sleep.call_count, 3)
            self.assertTrue((Path(output) / "moving-preview-d.png").exists())

    @patch("emulator_clip_creator_smoke.time.sleep")
    def test_frozen_artwork_fails_despite_changing_system_chrome(self, sleep):
        with tempfile.TemporaryDirectory() as output:
            test = self.harness(output, enumerate(["red"] * 5))
            with self.assertRaisesRegex(AssertionError, "did not move across five captures"):
                test.assert_preview_moves("frozen", (5, 5, 25, 25))
            self.assertEqual(sleep.call_count, 4)


if __name__ == "__main__":
    unittest.main()
