"""Verify durable local editing projects on an isolated rooted QA emulator.

Requires My stickers / qa-original-blue.png from the synthetic photo-import test.
Run `draft`, cold boot without clearing app data, then `verify-cold-start`.
Never uses a phone, user sign-in, publication, collection reset or remote API.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil

from emulator_editor_smoke import EditorSmoke


class ProjectsSmoke(EditorSmoke):
    LABEL = "qa-original-blue.png"

    def draft(self):
        self.launch(restart=True)
        before = self.row()
        counts = self.counts()
        self.verify_bytes(before, "starting.webp")
        self.open_editor()
        assert not self.enabled("Save")
        assert self.control("Mirror").get("checked") == "false", "Use the untouched synthetic blue fixture"
        self.tap("Rotate")
        self.tap("Mirror")
        self.wait_text("Draft saved on this device")
        assert self.row() == before, "Draft changed the saved export"
        self.screenshot("01-draft-before-force-stop")
        self.launch(restart=True)
        self.wait_text("Unfinished edits")
        self.screenshot("02-library-after-force-stop")
        self.tap("Resume edit")
        self.wait_text("Sticker preview")
        assert self.control("Mirror").get("checked") == "true"
        assert self.enabled("Undo")
        self.wait_text("Draft saved on this device")
        assert self.row() == before and self.counts() == counts
        report = {"before": before, "counts": counts, "draft_stage_passed": True, "cold_start_passed": False,
                  "checks": ["draft autosave", "draft leaves exported artwork unchanged", "force-stop recovery",
                             "library Resume edit", "mirror and undo restored"],
                  "limitations": ["synthetic normalized starting sticker, not full-resolution photo", "emulator only"]}
        self.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report))

    def verify_cold_start(self):
        report = json.loads(self.report.read_text(encoding="utf-8"))
        before = report["before"]
        self.launch(restart=True)
        self.wait_text("Unfinished edits")
        assert self.row() == before and list(self.counts()) == report["counts"]
        self.tap("Resume edit")
        self.wait_text("Sticker preview")
        assert self.control("Mirror").get("checked") == "true"
        assert self.enabled("Undo")
        self.wait_enabled("Save")
        self.screenshot("03-draft-after-cold-boot")
        self.tap("Save")
        self.scroll_to_text("Pack options", forward=False)
        self.wait_text("Pack options")
        edited = self.row()
        assert edited["id"] == before["id"] and edited["sha256"] != before["sha256"]
        self.verify_bytes(edited, "edited.webp")
        self.launch(restart=True)
        self.open_editor()
        self.wait_text("Saved version")
        assert self.control("Mirror").get("checked") == "true", "Saved recipe was flattened or lost"
        assert not self.enabled("Save")
        self.screenshot("04-reopened-saved-recipe")
        self.tap("Reset")
        self.wait_enabled("Save")
        self.tap("Save")
        self.scroll_to_text("Pack options", forward=False)
        self.wait_text("Pack options")
        reset = self.row()
        assert reset["id"] == before["id"] and reset["sha256"] == before["sha256"]
        self.verify_bytes(reset, "reset.webp")
        assert (self.output / "reset.webp").read_bytes() == (self.output / "starting.webp").read_bytes()
        assert list(self.counts()) == report["counts"]
        self.launch(restart=True)
        self.wait_text(self.PACK)
        assert not self.nodes("Unfinished edits"), "Save left a stale draft"
        report["cold_start_passed"] = True
        report["checks"] += ["cold boot draft recovery", "saved recipe reopens editable", "reset restores exact starting bytes",
                             "save clears draft", "sticker identity and counts retained"]
        report["reset"] = reset
        self.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=("draft", "verify-cold-start"))
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, default=Path("work/projects-emulator"))
    args = parser.parse_args()
    if not args.adb:
        parser.error("Provide --adb or add platform-tools to PATH")
    smoke = ProjectsSmoke(args.adb, args.serial, args.output)
    if args.stage == "draft":
        smoke.draft()
    else:
        smoke.verify_cold_start()
