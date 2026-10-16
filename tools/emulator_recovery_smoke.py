"""Exercise Discover/preview/save/restart on an empty, rooted Android test emulator.

Requires the signed candidate already installed and a one-pack reviewed test
catalog (the current pilot catalog). Never clears data, installs an
APK, signs in, publishes content, or operates a physical phone. The selected
reviewed public catalog pack is saved locally only after the explicit Save tap.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET


class RecoverySmoke:
    def __init__(self, adb, serial, output):
        self.adb_path, self.serial, self.output = adb, serial, output
        if not serial.startswith("emulator-"):
            raise ValueError("Use an isolated Android test emulator, never a personal phone")
        if self.adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
            raise ValueError("The selected device is not an Android emulator")
        if self.adb("shell", "id", "-u").strip() != "0":
            raise ValueError("This test needs adb root on an AOSP test emulator for read-only SQLite assertions")
        self.output.mkdir(parents=True, exist_ok=True)

    def adb(self, *args):
        return subprocess.run([self.adb_path, "-s", self.serial, *args],
                              check=True, capture_output=True, timeout=40).stdout.decode("utf-8", errors="replace")

    def tree(self):
        self.adb("shell", "uiautomator", "dump", "/sdcard/stickerdock-smoke.xml")
        return ET.fromstring(self.adb("shell", "cat", "/sdcard/stickerdock-smoke.xml"))

    def nodes(self, text):
        return [node for node in self.tree().iter("node")
                if (node.get("text") == text or node.get("content-desc") == text) and self.visible(node)]

    @staticmethod
    def visible(node):
        bounds = tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))
        return len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]

    def wait_text(self, text):
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            if self.nodes(text):
                return
            time.sleep(0.4)
        self.screenshot("failure")
        raise AssertionError("Expected visible control: " + text)

    def scroll_to_text(self, text, forward=True, attempts=8):
        for _ in range(attempts):
            tree = self.tree()
            if any(text in (n.get("text"), n.get("content-desc")) and self.visible(n) for n in tree.iter("node")):
                return
            regions = [tuple(map(int, re.findall(r"\d+", n.get("bounds", ""))))
                       for n in tree.iter("node") if n.get("scrollable") == "true"]
            regions = [r for r in regions if len(r) == 4 and r[3] - r[1] > 150]
            if not regions:
                break
            left, top, right, bottom = max(regions, key=lambda r: (r[2] - r[0]) * (r[3] - r[1]))
            x = (left + right) // 2
            low, high = round(top + (bottom - top) * .8), round(top + (bottom - top) * .2)
            start, end = (low, high) if forward else (high, low)
            self.adb("shell", "input", "swipe", str(x), str(start), str(x), str(end), "350")
        self.wait_text(text)

    def wait_preview(self):
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            if any(n.get("content-desc", "").startswith("Sticker previews from ") for n in self.tree().iter("node")):
                return
            time.sleep(0.4)
        raise AssertionError("The actual pack artwork did not load")

    def action_node(self, text):
        tree = self.tree()
        parents = {child: parent for parent in tree.iter() for child in parent}
        controls = set()
        visible = [node for node in tree.iter("node") if self.visible(node)]
        # A wide two-pane layout shows cover thumbnails alongside the sticker
        # grid. Prefer a visible text label over an identical cover description.
        matches = [node for node in visible if node.get("text") == text]
        if not matches:
            matches = [node for node in visible if node.get("content-desc") == text]
        for node in matches:
            while node.get("clickable") != "true" and node in parents:
                node = parents[node]
            if node.get("clickable") == "true" and self.visible(node):
                controls.add(node)
        if len(controls) != 1:
            raise AssertionError(f"Expected one actionable {text!r} control; found {len(controls)}")
        return controls.pop()

    def tap(self, text):
        node = self.action_node(text)
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        self.adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

    def counts(self):
        result = self.adb("shell", "sqlite3 /data/data/app.stickerdock.duo/databases/stickerdock.db "
                          "'SELECT (SELECT COUNT(*) FROM packs), (SELECT COUNT(*) FROM stickers);'")
        return tuple(map(int, result.strip().split("|")))

    def launch(self, restart=False):
        if restart:
            self.adb("shell", "am", "force-stop", "app.stickerdock.duo")
        self.adb("shell", "am", "start", "-W", "-n", "app.stickerdock.duo/.StickerDockPolishedActivity")

    def screenshot(self, name):
        remote = "/sdcard/stickerdock-smoke.png"
        self.adb("shell", "screencap", "-p", remote)
        self.adb("pull", remote, str(self.output / (name + ".png")))

    def run(self):
        self.launch(restart=True)
        if self.counts() != (0, 0):
            raise AssertionError("Start with an empty isolated emulator library; this test never deletes existing data")
        self.wait_text("Your library starts here")
        self.screenshot("01-empty-library")
        self.tap("Discover")
        self.wait_text("Preview pack")
        self.tap("Refresh")
        self.wait_text("Preview pack")
        assert self.counts() == (0, 0), "Refresh imported a pack"
        self.screenshot("02-discover")
        self.tap("Preview pack")
        self.wait_text("Save to library")
        self.wait_preview()
        self.screenshot("03-preview-before-save")
        assert self.counts() == (0, 0), "Preview imported a pack"
        self.tap("Keep browsing")
        assert self.counts() == (0, 0)
        self.launch(restart=True)
        self.wait_text("Your library starts here")
        assert self.counts() == (0, 0), "Restart imported a pack"
        self.tap("Discover")
        self.wait_text("Preview pack")
        self.tap("Preview pack")
        self.wait_text("Save to library")
        self.tap("Save to library")
        self.wait_text("Pack options")
        saved = self.counts()
        assert saved[0] >= 1 and saved[1] >= 3, "Explicit save did not persist stickers"
        self.screenshot("04-saved-pack")
        name = self.adb("shell", "sqlite3 /data/data/app.stickerdock.duo/databases/stickerdock.db 'SELECT name FROM packs LIMIT 1;'").strip()
        self.launch(restart=True)
        self.wait_text(name)
        assert self.counts() == saved, "Saved pack did not survive process restart"
        self.screenshot("05-library-after-restart")
        self.tap("Discover")
        self.wait_text("Open in library")
        self.tap("Open in library")
        self.wait_text("Pack options")
        assert self.counts() == saved, "Opening a saved pack duplicated it"
        report = {"passed": True, "serial": self.serial, "packs": saved[0], "stickers": saved[1],
                  "checks": ["empty library", "refresh does not import", "preview does not import",
                             "dismiss preview does not import", "restart does not import",
                             "explicit save persists", "saved pack survives restart", "open saved pack does not duplicate"],
                  "limitations": ["Android emulator only", "no WhatsApp or Business acceptance", "no physical Samsung SAF test"]}
        (self.output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, default=Path("work/recovery-emulator"))
    args = parser.parse_args()
    if not args.adb:
        parser.error("Provide --adb or add Android platform-tools to PATH")
    RecoverySmoke(args.adb, args.serial, args.output).run()
