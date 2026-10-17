"""Check the cutout footer at 360dp width / 130% text on an isolated QA emulator.

Restores emulator density/font settings afterward and never saves an artwork edit.
Uses only the synthetic photo fixture required by emulator_projects_smoke.py.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil

from emulator_projects_smoke import ProjectsSmoke


class LayoutSmoke(ProjectsSmoke):
    def run(self):
        density = self.adb("shell", "wm", "density")
        match = re.search(r"Override density:\s*(\d+)", density)
        restore_density = match.group(1) if match else "reset"
        font = self.adb("shell", "settings", "get", "system", "font_scale").strip()
        before, counts = self.row(), self.counts()
        try:
            self.adb("shell", "wm", "density", "480")
            self.adb("shell", "settings", "put", "system", "font_scale", "1.3")
            self.launch(restart=True)
            self.open_editor()
            self.scroll_to_text("Cutout")
            self.tap("Cutout")
            self.wait_text("Trace cutout on starting image")
            node = self.nodes("Trace cutout on starting image")[0]
            left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
            for x, y in ((.2, .2), (.8, .2), (.8, .8), (.2, .8)):
                self.adb("shell", "input", "tap", str(round(left + (right - left) * x)), str(round(top + (bottom - top) * y)))
            self.wait_enabled("Apply cutout")
            self.tap("Start over")
            self.wait_text("Trace cutout on starting image")
            assert not self.enabled("Apply cutout"), "Start over missed its button or did not clear the outline"
            self.screenshot("01-cutout-360dp-font130")
            self.tap("Cancel cutout")
            self.wait_text("Edit sticker")
            self.tap("Cancel")
            self.scroll_to_text("Pack options", forward=False)
            assert self.row() == before and self.counts() == counts
        finally:
            self.adb("shell", "wm", "density", restore_density)
            if font == "null":
                self.adb("shell", "settings", "delete", "system", "font_scale")
            else:
                self.adb("shell", "settings", "put", "system", "font_scale", font)
            self.launch(restart=True)
        report = {"passed": True, "profile": "360dp width / 130% text / three-button navigation",
                  "checks": ["outline can be drawn", "Apply enabled", "Start over reaches app button and clears outline",
                             "Cancel reaches app button", "saved artwork unchanged", "density/font settings restored"],
                  "limitations": ["Android 15 AOSP emulator only", "no physical Samsung or iPhone acceptance"]}
        self.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, default=Path("work/editor-layout-emulator"))
    args = parser.parse_args()
    if not args.adb:
        parser.error("Provide --adb or add platform-tools to PATH")
    LayoutSmoke(args.adb, args.serial, args.output).run()
