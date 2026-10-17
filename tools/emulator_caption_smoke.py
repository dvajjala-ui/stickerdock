"""Verify keyboard layout and editable captions on the synthetic QA emulator.

Requires the reset blue fixture from emulator_projects_smoke.py. Never operates
a phone, clears data, signs in or publishes; restores the starting saved image.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil

from emulator_projects_smoke import ProjectsSmoke


class CaptionSmoke(ProjectsSmoke):
    def bounds(self, label):
        return tuple(map(int, re.findall(r"\d+", self.action_node(label).get("bounds"))))

    def focus(self, label):
        self.scroll_to_text(label)
        node = self.nodes(label)[0]
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
        self.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def save_caption(self):
        self.adb("shell", "input", "keyevent", "4")
        self.scroll_to_text("Edit sticker", forward=False)
        self.wait_enabled("Save")
        self.tap("Save")
        self.scroll_to_text("Pack options", forward=False)

    def run(self):
        before, counts = self.row(), self.counts()
        self.verify_bytes(before, "starting.webp")
        self.launch(restart=True)
        self.open_editor()
        header_before = self.bounds("Cancel")
        self.focus("Top caption")
        self.adb("shell", "input", "text", "First%scaption")
        self.wait_text("First caption")
        header_typing = self.bounds("Cancel")
        self.screenshot("01-caption-with-keyboard")
        assert header_typing[1] >= header_before[1] - 2, "Keyboard panned the editor header under the status bar"
        self.save_caption()
        self.launch(restart=True)
        self.open_editor()
        self.scroll_to_text("Top caption")
        assert self.nodes("First caption"), "Saved caption did not reopen as editable text"
        self.screenshot("02-reopened-first-caption")
        self.focus("Top caption")
        self.adb("shell", "input", "keycombination", "113", "29")
        self.adb("shell", "input", "text", "Second%scaption")
        self.wait_text("Second caption")
        self.save_caption()
        self.launch(restart=True)
        self.open_editor()
        self.scroll_to_text("Top caption")
        assert self.nodes("Second caption") and not self.nodes("First caption")
        self.scroll_to_text("Edit sticker", forward=False)
        self.screenshot("03-reopened-second-caption")
        self.tap("Reset")
        self.wait_enabled("Save")
        self.tap("Save")
        self.scroll_to_text("Pack options", forward=False)
        after = self.row()
        assert after["sha256"] == before["sha256"] and after["id"] == before["id"]
        self.verify_bytes(after, "reset.webp")
        assert (self.output / "starting.webp").read_bytes() == (self.output / "reset.webp").read_bytes()
        assert self.counts() == counts
        report = {"passed": True, "checks": ["caption input with system keyboard", "header stays below status bar while typing",
                  "save/reopen editable caption", "replace caption and reopen", "reset exact starting bytes"],
                  "header_before": header_before, "header_typing": header_typing, "counts": counts,
                  "limitations": ["Android 15 AOSP emulator with synthetic media only"]}
        self.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, default=Path("work/caption-emulator"))
    args = parser.parse_args()
    if not args.adb:
        parser.error("Provide --adb or add platform-tools to PATH")
    CaptionSmoke(args.adb, args.serial, args.output).run()
