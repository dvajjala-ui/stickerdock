"""Exercise real cutout touches, undo, draft recovery and alpha output on a QA emulator.

Requires the synthetic blue photo fixture and no pending unrelated draft. Never
clears data or operates a phone. Only that synthetic sticker is edited/reset.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import math

from emulator_projects_smoke import ProjectsSmoke


class CutoutSmoke(ProjectsSmoke):
    def run(self):
        from PIL import Image

        self.launch(restart=True)
        before = self.row()
        counts = self.counts()
        self.verify_bytes(before, "starting.webp")
        self.open_editor()
        self.tap("Cutout")
        self.wait_text("Trace cutout on starting image")
        assert not self.enabled("Apply cutout")
        node = self.nodes("Trace cutout on starting image")[0]
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
        trace = [(round(left + (right - left) * (.5 + .3 * math.cos(i * math.tau / 24))),
                  round(top + (bottom - top) * (.5 + .3 * math.sin(i * math.tau / 24)))) for i in range(25)]
        commands = [f"input motionevent DOWN {trace[0][0]} {trace[0][1]}"]
        commands += [f"input motionevent MOVE {x} {y}" for x, y in trace[1:]]
        commands += [f"input motionevent UP {trace[-1][0]} {trace[-1][1]}"]
        try:
            self.adb("shell", "; ".join(commands))
        finally:
            self.adb("shell", "input", "motionevent", "UP", str(trace[-1][0]), str(trace[-1][1]))
        self.wait_enabled("Apply cutout")
        self.screenshot("00-drag-outline")
        self.tap("Start over")
        assert not self.enabled("Apply cutout")
        # Coordinates come from the actual native canvas bounds, not a fixed screen.
        for x, y in ((.2, .2), (.8, .2), (.8, .8), (.2, .8)):
            self.adb("shell", "input", "tap", str(round(left + (right - left) * x)), str(round(top + (bottom - top) * y)))
        self.wait_enabled("Apply cutout")
        self.screenshot("01-outline")
        self.tap("Apply cutout")
        self.wait_text("Remove cutout")
        self.tap("Undo")
        assert not self.nodes("Remove cutout")
        self.tap("Redo")
        self.wait_text("Remove cutout")
        self.wait_text("Draft saved on this device")
        self.tap("Cancel")
        self.wait_text("Keep draft")
        self.tap("Keep draft")
        self.scroll_to_text("Pack options", forward=False)
        self.wait_text("Pack options")
        assert self.row() == before
        self.launch(restart=True)
        self.wait_text("Unfinished edits")
        self.tap("Resume edit")
        self.wait_text("Remove cutout")
        self.wait_enabled("Save")
        self.screenshot("02-recovered-cutout-preview")
        self.tap("Save")
        self.scroll_to_text("Pack options", forward=False)
        self.wait_text("Pack options")
        cutout = self.row()
        self.verify_bytes(cutout, "cutout.webp")
        with Image.open(self.output / "cutout.webp") as image:
            image = image.convert("RGBA")
            assert image.size == (512, 512)
            assert image.getpixel((256, 80))[3] == 0, "Outside the outline is not transparent"
            assert image.getpixel((256, 256))[3] > 240, "The retained subject became transparent"
        assert cutout["id"] == before["id"] and cutout["sha256"] != before["sha256"]
        self.launch(restart=True)
        self.open_editor()
        self.wait_text("Remove cutout")
        assert not self.enabled("Save")
        self.tap("Remove cutout")
        self.wait_enabled("Save")
        self.tap("Save")
        self.scroll_to_text("Pack options", forward=False)
        self.wait_text("Pack options")
        reset = self.row()
        assert reset["sha256"] == before["sha256"] and self.counts() == counts
        self.verify_bytes(reset, "removed-cutout.webp")
        assert (self.output / "starting.webp").read_bytes() == (self.output / "removed-cutout.webp").read_bytes()
        report = {"passed": True, "counts": counts,
                  "checks": ["native continuous drag and tapped outline input", "invalid empty outline disabled", "cutout undo/redo",
                             "Keep draft", "force-stop cutout draft recovery", "512px transparent export",
                             "saved cutout remains editable", "remove cutout restores exact starting bytes"],
                  "limitations": ["manual outline, not automatic segmentation", "synthetic fixture", "emulator only"]}
        self.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, default=Path("work/cutout-emulator"))
    args = parser.parse_args()
    if not args.adb:
        parser.error("Provide --adb or add platform-tools to PATH")
    CutoutSmoke(args.adb, args.serial, args.output).run()
