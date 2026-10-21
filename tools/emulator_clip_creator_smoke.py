"""Create actual reaction stickers through the native GIF/video creator.

Use only a rooted AOSP emulator with the licensed Facepalm trial clips in
Downloads. Never operates a phone, signs in, uploads or clears app data. Saves
three caption variants in a new explicit QA pack; retains all existing packs.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import time

from PIL import Image, ImageChops

from emulator_recovery_smoke import RecoverySmoke


class ClipCreatorSmoke(RecoverySmoke):
    PACK = "Native video reactions"

    def enabled(self, text):
        return self.action_node(text).get("enabled") == "true"

    def wait_enabled(self, text, timeout=120):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            if self.enabled(text):
                return
            time.sleep(.4)
        self.screenshot("failure")
        raise AssertionError(f"{text} did not become enabled")

    def fill(self, label, value):
        self.scroll_to_text(label)
        node = self.action_node(label)
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
        self.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        self.adb("shell", "input", "keycombination", "113", "29")
        self.adb("shell", "input", "text", value.replace(" ", "%s"))
        if "mInputShown=true" in self.adb("shell", "dumpsys", "input_method"):
            self.adb("shell", "input", "keyevent", "4")
        # Assert the actual editable field, not just the floating label. Labels
        # can sit on the border and a tap there does not always focus Android.
        field = self.action_node(label)
        assert field.get("text") == value, f"{label} did not accept the requested text"

    def open_clip(self, filename, from_create=False):
        print("Choosing " + filename, flush=True)
        self.adb("shell", "am", "force-stop", "app.stickerdock.duo")
        # Reset only the emulator activity task, not app data. An orphaned
        # DocumentsUI picker can otherwise remain the root after force-stop.
        self.adb("shell", "am", "start", "-W", "-f", "0x10008000", "-n",
                 "app.stickerdock.duo/.StickerDockPolishedActivity")
        if from_create:
            self.wait_text("Search library")
            self.tap("Create")
            self.scroll_to_text("Make an animated reaction")
        self.wait_text("Make an animated reaction")
        self.tap("Make an animated reaction")
        self.wait_text("Show roots")
        if not self.nodes("Files in Downloads"):
            self.tap("Show roots")
            self.wait_text("Downloads")
            # DocumentsUI exposes drawer-row labels without clickable
            # ancestors; the open drawer is the last accessibility subtree.
            root = self.nodes("Downloads")[-1]
            left, top, right, bottom = map(int, re.findall(r"\d+", root.get("bounds")))
            self.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            self.wait_text("Files in Downloads")
        if self.nodes("List view"):
            self.tap("List view")
        self.scroll_to_text(filename)
        # DocumentsUI can expose a last row underneath the system navigation
        # bar. Positive accessibility bounds alone do not make that row safe
        # to tap; its center may activate Home instead of choosing the file.
        for _ in range(4):
            tree = self.tree()
            bars = [tuple(map(int, re.findall(r"\d+", n.get("bounds", ""))))
                    for n in tree.iter("node") if n.get("resource-id") == "android:id/navigationBarBackground"]
            if not bars:
                break
            safe_bottom = min(b[1] for b in bars)
            control = self.action_node(filename)
            a, b, c, d = map(int, re.findall(r"\d+", control.get("bounds")))
            if d <= safe_bottom - 20:
                break
            regions = [tuple(map(int, re.findall(r"\d+", n.get("bounds", ""))))
                       for n in tree.iter("node") if n.get("scrollable") == "true"]
            left, top, right, bottom = max(regions, key=lambda r: (r[2] - r[0]) * (r[3] - r[1]))
            low = min(bottom - 30, safe_bottom - 40)
            self.adb("shell", "input", "swipe", str((left + right) // 2), str(low),
                     str((left + right) // 2), str(max(top + 30, low - 420)), "350")
            self.wait_text(filename)
        else:
            raise AssertionError("The selected file is hidden under system navigation")
        self.tap(filename)
        self.wait_text("Make an animated reaction")
        self.scroll_to_text("Preview animation")
        self.wait_enabled("Preview animation")
        assert not self.enabled("Save sticker"), "New creation reused a stale preview"

    def preview(self, caption, prefix):
        print("Previewing " + caption, flush=True)
        before = self.counts()
        self.fill("Reaction caption", caption)
        self.scroll_to_text("Preview animation")
        self.tap("Preview animation")
        self.wait_enabled("Save sticker")
        assert self.counts() == before, "Preview saved a sticker without acceptance"
        self.scroll_to_text("Make an animated reaction", forward=False)
        self.wait_text("Animated reaction preview")
        node = self.nodes("Animated reaction preview")[0]
        bounds = tuple(map(int, re.findall(r"\d+", node.get("bounds"))))
        self.assert_preview_moves(prefix, bounds)

    def assert_preview_moves(self, prefix, bounds):
        # A screenshot includes adb/screencap time. Two captures can land on
        # the same phase of a short looping GIF, even with a fixed sleep.
        # Keep a bounded series of unequal intervals; a frozen crop still fails.
        self.screenshot(prefix + "-preview-a")
        with Image.open(self.output / (prefix + "-preview-a.png")) as image:
            first = image.convert("RGB").crop(bounds)
        for suffix, delay in zip("bcde", (.35, .17, .29, .43)):
            time.sleep(delay)
            self.screenshot(prefix + "-preview-" + suffix)
            with Image.open(self.output / (prefix + "-preview-" + suffix + ".png")) as image:
                current = image.convert("RGB").crop(bounds)
            if ImageChops.difference(first, current).getbbox():
                return
        raise AssertionError("Native generated preview did not move across five captures")

    def save(self, first=False):
        print("Saving accepted preview", flush=True)
        self.scroll_to_text("Save to pack")
        if first:
            self.fill("Pack name", self.PACK)
        else:
            self.scroll_to_text(self.PACK)
            self.tap(self.PACK)
        self.tap("Save sticker")
        self.wait_text("Pack options")

    def saved(self, label, filename):
        query = ("sqlite3 /data/data/app.stickerdock.duo/databases/stickerdock.db "
                 f'"SELECT s.pack_id,s.file_name,s.sha256 FROM stickers s JOIN packs p ON p.id=s.pack_id '
                 f"WHERE p.name='{self.PACK}' AND s.label='{label}';\"")
        row = self.adb("shell", query).strip().split("|")
        assert len(row) == 3, "Saved reaction not found"
        target = self.output / filename
        self.adb("pull", f"/data/data/app.stickerdock.duo/files/stickers/{row[0]}/{row[1]}", str(target))
        data = target.read_bytes()
        assert hashlib.sha256(data).hexdigest() == row[2] and len(data) <= 500_000
        with Image.open(io.BytesIO(data)) as im:
            assert im.size == (512, 512) and im.n_frames >= 2
            durations, frames = [], []
            for index in range(im.n_frames):
                im.seek(index); im.load()
                durations.append(im.info["duration"])
                frames.append(im.convert("RGBA"))
            assert min(durations) >= 8 and sum(durations) <= 10_000
            assert frames[0].getpixel((0, 0))[3] == 0
            assert any(ImageChops.difference(frames[0].convert("RGB"), frame.convert("RGB")).getbbox() for frame in frames[1:])
            return {"bytes": len(data), "frames": len(frames), "duration_ms": sum(durations), "sha256": row[2]}

    def run(self):
        before = self.counts()
        # Unique known pack keeps repeated runs from accidentally modifying a
        # previously accepted QA pack; do not clear or delete it to rerun.
        exists = self.adb("shell", "sqlite3 /data/data/app.stickerdock.duo/databases/stickerdock.db "
                          f'"SELECT COUNT(*) FROM packs WHERE name=\'{self.PACK}\';"').strip()
        assert exists == "0", "This QA pack already exists; choose a new isolated fixture"
        results = []
        for index, (name, caption) in enumerate((("facepalm.webm", "Bas kar bhai"),
                                                ("facepalm-native.mp4", "Not this again"),
                                                ("facepalm-native.gif", "Group chat moment"))):
            self.open_clip(name)
            self.preview(caption, f"0{index + 1}")
            if index == 0:
                self.fill("Reaction caption", "Bas kar bhai please")
                assert not self.enabled("Save sticker"), "Caption change left stale preview saveable"
                self.preview("Bas kar bhai please", "01-updated")
                caption = "Bas kar bhai please"
            self.save(first=index == 0)
            assert self.counts() == (before[0] + 1, before[1] + index + 1)
            results.append({"source": name, **self.saved(caption, f"reaction-{index + 1}.webp")})
        self.launch(restart=True)
        self.wait_text(self.PACK)
        self.tap(self.PACK)
        self.wait_text("Pack options")
        self.screenshot("05-saved-pack-after-restart")
        report = {"passed": True, "checks": ["native WebM, MP4 and GIF selection", "local captioned animation conversion",
                  "preview leaves library unchanged", "changed caption invalidates preview", "native generated preview visibly moves",
                  "explicit new/existing animated pack save", "three stickers survive force-stop", "export bytes/checksum/alpha/frame durations"],
                  "before": before, "after": self.counts(), "results": results,
                  "limitations": ["one licensed source clip and caption variants", "Android 15 AOSP emulator only", "no physical WhatsApp/iPhone acceptance"]}
        (self.output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, default=Path("work/clip-creator-emulator"))
    args = parser.parse_args()
    if not args.adb:
        parser.error("Provide --adb or add platform-tools to PATH")
    ClipCreatorSmoke(args.adb, args.serial, args.output).run()
