"""Edit a synthetic QA sticker on an isolated rooted emulator; verify after a cold boot.

Requires the known QA photo-import fixture (My stickers / qa-original-green.png).
Never clears collections or operates a phone. Run `edit`, cold-boot the emulator,
then run `verify-cold-start`. Both stages record actual bytes, not only SQL rows.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import time

from emulator_recovery_smoke import RecoverySmoke


class EditorSmoke(RecoverySmoke):
    LABEL = "qa-original-green.png"
    PACK = "My stickers"

    def row(self):
        raw = self.adb("shell", "sqlite3 /data/data/app.stickerdock.duo/databases/stickerdock.db "
                       f'"SELECT id,pack_id,file_name,sha256 FROM stickers WHERE label=\'{self.LABEL}\';"').strip()
        parts = raw.split("|")
        if len(parts) != 4:
            raise AssertionError("Import the synthetic QA photos before running this test")
        return dict(zip(("id", "pack_id", "file_name", "sha256"), parts))

    def verify_bytes(self, row, name):
        path = f'/data/data/app.stickerdock.duo/files/stickers/{row["pack_id"]}/{row["file_name"]}'
        target = self.output / name
        self.adb("pull", path, str(target))
        raw = target.read_bytes()
        assert len(raw) > 30 and raw[:4] == b"RIFF" and raw[8:12] == b"WEBP", "Saved artwork is empty or invalid"
        assert hashlib.sha256(raw).hexdigest() == row["sha256"], "Saved bytes do not match the database checksum"

    def open_editor(self):
        self.wait_text(self.PACK)
        self.tap(self.PACK)
        # Library cover thumbnails use the same sticker descriptions. Wait for
        # pack navigation before resolving a sticker, especially after a font
        # configuration change, or the test can tap the old cover a second time.
        self.wait_text("Pack options")
        self.scroll_to_text(self.LABEL)
        self.wait_text(self.LABEL)
        self.tap(self.LABEL)
        self.wait_text("Edit artwork and captions")
        self.tap("Edit artwork and captions")
        self.wait_text("Sticker preview")

    def control(self, label):
        return self.action_node(label)

    def enabled(self, label):
        return self.control(label).get("enabled") == "true"

    def wait_enabled(self, label):
        deadline = time.monotonic() + 25
        while time.monotonic() < deadline:
            if self.enabled(label):
                return
            time.sleep(0.3)
        raise AssertionError(label + " did not become enabled")

    def edit(self):
        self.launch(restart=True)
        before = self.row()
        counts = self.counts()
        self.verify_bytes(before, "before.webp")
        self.open_editor()
        assert not self.enabled("Undo") and not self.enabled("Save")
        tree = self.tree()
        def bounds(label):
            node = next(n for n in tree.iter("node") if label in (n.get("text"), n.get("content-desc")))
            return tuple(map(int, re.findall(r"\d+", node.get("bounds"))))
        heading, preview, rotate = bounds("Edit sticker"), bounds("Sticker preview"), bounds("Rotate")
        assert heading[3] <= preview[1] and preview[3] <= rotate[1], "Editor preview overlaps its controls"
        self.tap("Rotate")
        self.tap("Mirror")
        assert self.control("Mirror").get("checked") == "true"
        self.tap("Undo")
        assert self.control("Mirror").get("checked") == "false"
        self.tap("Redo")
        assert self.control("Mirror").get("checked") == "true"
        self.tap("Reset")
        assert not self.enabled("Save")
        self.tap("Undo")
        self.tap("Cancel")
        self.wait_text("Discard your edits?")
        self.tap("Keep editing")
        self.wait_text("Edit sticker")
        self.screenshot("01-editor-layout-and-undo")
        # Home allows Android to save instance state; am kill simulates background
        # process reclamation, unlike force-stop which intentionally discards it.
        pid = self.adb("shell", "pidof", "app.stickerdock.duo").strip()
        self.adb("shell", "input", "keyevent", "3")
        time.sleep(1)
        self.adb("shell", "am", "kill", "app.stickerdock.duo")
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            running = self.adb("shell", "pidof app.stickerdock.duo || true").strip()
            if not running:
                break
            time.sleep(0.3)
        else:
            raise AssertionError("Android did not reclaim the background process")
        self.launch()
        self.wait_text("Edit sticker")
        assert self.adb("shell", "pidof", "app.stickerdock.duo").strip() != pid
        assert self.control("Mirror").get("checked") == "true"
        assert self.enabled("Undo") and self.enabled("Redo")
        self.wait_enabled("Save")
        self.screenshot("02-draft-after-process-recreation")
        self.tap("Save")
        self.scroll_to_text("Pack options", forward=False)
        self.wait_text("Pack options")
        after = self.row()
        assert after["id"] == before["id"] and after["pack_id"] == before["pack_id"]
        assert after["sha256"] != before["sha256"] and after["file_name"] != before["file_name"]
        assert self.counts() == counts
        self.verify_bytes(after, "after.webp")
        report = {"saved": after, "counts": counts, "edit_stage_passed": True,
                  "checks": ["no preview overlap", "undo/redo", "reset is undoable", "discard protection",
                             "background process recreation retains draft and history", "save preserves identity",
                             "saved bytes match checksum"], "cold_start_passed": False}
        self.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report))

    @property
    def report(self):
        return self.output / "report.json"

    def verify_cold_start(self):
        report = json.loads(self.report.read_text(encoding="utf-8"))
        self.launch(restart=True)
        after = self.row()
        assert after == report["saved"] and list(self.counts()) == report["counts"]
        self.verify_bytes(after, "after-cold-start.webp")
        self.open_editor()
        self.screenshot("03-artwork-after-cold-start")
        report["cold_start_passed"] = True
        report["checks"].append("cold boot retains matching nonempty artwork and native preview")
        self.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=("edit", "verify-cold-start"))
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, default=Path("work/editor-emulator"))
    args = parser.parse_args()
    if not args.adb:
        parser.error("Provide --adb or add platform-tools to PATH")
    smoke = EditorSmoke(args.adb, args.serial, args.output)
    if args.stage == "edit":
        smoke.edit()
    else:
        smoke.verify_cold_start()
