"""Signed-APK acceptance on a disposable rooted AOSP emulator with 10k fixtures.

Refuses physical devices. Only synthetic, isolated QA data is created; no account,
personal collection, public catalog publication or WhatsApp install is involved.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tarfile
import time
import uuid

from PIL import Image
from emulator_clip_creator_smoke import ClipCreatorSmoke

PACKAGE = "app.stickerdock.duo"
ROOT = "/data/user/0/" + PACKAGE


class LibraryAcceptance(ClipCreatorSmoke):
    def adb(self, *args):
        try:
            return super().adb(*args)
        except subprocess.CalledProcessError as error:
            print(error.stderr.decode("utf-8", errors="replace"), flush=True)
            raise

    def nodes(self, text):
        return [node for node in self.tree().iter("node") if self.visible(node) and
                any(text in node.get(field, "") for field in ("text", "content-desc"))]

    def action_node(self, text):
        values = [node.get(field, "") for node in self.tree().iter("node") if self.visible(node)
                  for field in ("text", "content-desc") if text in node.get(field, "")]
        return super().action_node(min(values, key=len) if values else text)

    def counts(self):
        path = self.output / "count.db"
        self.adb("pull", ROOT + "/databases/stickerdock.db", str(path))
        with sqlite3.connect(path) as connection:
            return (connection.execute("SELECT count(*) FROM packs").fetchone()[0],
                    connection.execute("SELECT count(*) FROM stickers").fetchone()[0])

    def memory(self):
        raw = self.adb("shell", "dumpsys", "meminfo", PACKAGE)
        (self.output / "meminfo.txt").write_text(raw)
        match = re.search(r"TOTAL PSS:\s*(\d+)", raw) or re.search(r"^\s*TOTAL\s+(\d+)", raw, re.M)
        assert match, "No application memory report"
        return int(match.group(1))

    def seed(self):
        self.launch(restart=True)
        self.wait_text("Your library starts here")
        self.adb("shell", "am", "force-stop", PACKAGE)
        database = self.output / "seed.db"
        self.adb("pull", ROOT + "/databases/stickerdock.db", str(database))
        connection = sqlite3.connect(database)
        assert connection.execute("PRAGMA user_version").fetchone()[0] == 8
        media = self.output / "seed.tar"
        artwork = {}
        with tarfile.open(media, "w") as archive:
            for pack_index in range(334):
                pack_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"stickerdock-qa-pack:{pack_index}"))
                animated = pack_index == 0
                name = f"QA animated {pack_index:03}" if animated else f"QA pack {pack_index:03}"
                connection.execute("INSERT INTO packs(id,name,version,created_at,kind,rating,category) VALUES(?,?,?,?,?,?,?)",
                                   (pack_id, name, 1, 10000 - pack_index, "ANIMATED" if animated else "STATIC", "GENERAL", "REACTIONS"))
                for index in range(min(30, 10000 - pack_index * 30)):
                    key = (animated, index)
                    if key not in artwork:
                        canvas = Image.new("RGBA", (512, 512), (20 + index * 7, 70, 180, 255))
                        stream = io.BytesIO()
                        options = {"format": "WEBP", "lossless": True}
                        if animated:
                            options.update(save_all=True, append_images=[Image.new("RGBA", (512, 512), (180, 40 + index * 5, 50, 255))], duration=200, loop=0)
                        canvas.save(stream, **options)
                        artwork[key] = stream.getvalue()
                    data = artwork[key]
                    sticker_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"stickerdock-qa-sticker:{pack_index}:{index}"))
                    filename = sticker_id + ".webp"
                    entry = tarfile.TarInfo(f"stickers/{pack_id}/{filename}")
                    entry.size = len(data); entry.mode = 0o600
                    archive.addfile(entry, io.BytesIO(data))
                    connection.execute("INSERT INTO stickers(id,pack_id,file_name,sha256,created_at,label,tags) VALUES(?,?,?,?,?,?,?)",
                                       (sticker_id, pack_id, filename, hashlib.sha256(data).hexdigest(), index, f"QA sticker {index:02}", "synthetic, qa"))
        connection.commit(); connection.close()
        self.adb("push", str(database), ROOT + "/databases/stickerdock.db")
        self.adb("push", str(media), "/data/local/tmp/stickerdock-qa.tar")
        self.adb("shell", "mkdir", "-p", ROOT + "/files")
        self.adb("shell", "tar", "-xf", "/data/local/tmp/stickerdock-qa.tar", "-C", ROOT + "/files")
        uid = self.adb("shell", "stat", "-c", "%u", ROOT).strip()
        assert uid.isdigit()
        self.adb("shell", "chown", "-R", f"{uid}:{uid}", ROOT + "/files/stickers", ROOT + "/databases")
        self.adb("shell", "restorecon", "-RF", ROOT + "/files", ROOT + "/databases")

    def run_acceptance(self, apk):
        assert self.adb("shell", "getprop", "ro.kernel.qemu").strip() == "1", "Only a disposable emulator is allowed"
        self.adb("install", "-r", str(apk))
        self.adb("logcat", "-c")
        self.seed()
        before = (334, 10000)
        self.launch(restart=True)
        self.wait_text("334 packs")
        self.wait_text("QA animated 000")
        self.screenshot("01-large-library")
        initial_memory = self.memory()
        for _ in range(12):
            self.adb("shell", "input", "swipe", "540", "1500", "540", "500", "180")
        self.screenshot("02-scrolled-library")
        scrolled_memory = self.memory()
        assert scrolled_memory - initial_memory < 100 * 1024, "Scrolling retained over 100 MB of additional memory"
        self.launch(restart=True)
        self.wait_text("QA animated 000"); self.tap("QA animated 000")
        self.wait_text("Tap to play")
        self.wait_text("Use in WhatsApp")
        self.screenshot("03-animated-posters")
        self.tap("QA sticker 00")
        self.wait_text("Copy or move to a new pack")
        self.screenshot("04-moving-detail")
        self.adb("shell", "input", "keyevent", "4")
        self.wait_text("Pack options")
        self.adb("shell", "wm", "size", "1080x1920")
        self.adb("shell", "wm", "density", "480")
        self.adb("shell", "settings", "put", "system", "font_scale", "2.0")
        self.wait_text("Pack options")
        self.screenshot("05-360dp-font200")
        self.tap("Pack options"); self.wait_text("Rename pack")
        self.adb("shell", "input", "keyevent", "4")
        self.tap("Use in WhatsApp")
        self.wait_text("WhatsApp not installed"); self.wait_text("Business not installed")
        self.screenshot("06-targets-font200")
        self.adb("shell", "input", "keyevent", "4")
        self.adb("shell", "input", "keyevent", "4")
        self.wait_text("Search library"); self.tap("Filters")
        self.wait_text("Filter your library")
        self.scroll_to_text("Show results"); self.tap("Show results")
        self.adb("shell", "settings", "put", "system", "font_scale", "1.0")
        self.adb("shell", "wm", "size", "1600x1200")
        self.adb("shell", "wm", "density", "160")
        self.launch(restart=True); self.wait_text("QA animated 000")
        self.tap("QA animated 000"); self.wait_text("Use in WhatsApp")
        self.screenshot("07-android-tablet")
        self.adb("shell", "wm", "size", "1080x1920")
        self.adb("shell", "wm", "density", "420")
        # RUNNING_LOW can be injected while foreground. COMPLETE requires the
        # process to reach a cached state; Home delivery alone does not prove
        # that asynchronous transition has happened on an emulator.
        self.adb("shell", "am", "send-trim-memory", PACKAGE, "RUNNING_LOW")
        self.adb("shell", "input", "keyevent", "3")
        self.launch(restart=True); self.wait_text("334 packs")
        # Exercise the new streaming conversion through Android's real picker.
        clip = self.output / "qa-motion.mp4"
        subprocess.run(["ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=10", "-t", "2.5", "-c:v", "libx264", "-pix_fmt", "yuv420p", str(clip)], check=True, capture_output=True)
        self.adb("push", str(clip), "/sdcard/Download/qa-motion.mp4")
        self.open_clip("qa-motion.mp4", from_create=True)
        preview_started = time.monotonic()
        self.preview("Streaming preview QA", "08-streamed-clip")
        preview_seconds = time.monotonic() - preview_started
        self.tap("Cancel"); self.wait_text("Close creation"); self.tap("Close creation")
        self.launch(restart=True); self.wait_text("334 packs")
        self.adb("shell", "am", "force-stop", PACKAGE)
        final_db = self.output / "after.db"
        self.adb("pull", ROOT + "/databases/stickerdock.db", str(final_db))
        with sqlite3.connect(final_db) as connection:
            after = (connection.execute("SELECT count(*) FROM packs").fetchone()[0], connection.execute("SELECT count(*) FROM stickers").fetchone()[0])
        assert before == after, "Browsing or preview changed saved data"
        crashes = self.adb("logcat", "-b", "crash", "-d")
        assert "FATAL EXCEPTION" not in crashes, "App crashed during acceptance"
        report = {"passed": True, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "packs": 334, "stickers": 10000,
                  "initial_pss_kb": initial_memory, "scrolled_pss_kb": scrolled_memory,
                  "preview_interaction_seconds": round(preview_seconds, 2),
                  "checks": ["large library and scrolling", "animated grid posters and detail playback", "360dp at 200% text", "Android tablet two-pane layout", "scrollable filters and WhatsApp targets", "memory trim and restart preservation", "real MP4 streaming preview without saving"],
                  "limitations": ["AOSP emulator; physical WhatsApp/Business acceptance remains separate", "low-storage rollback covered by native unit tests"]}
        (self.output / "acceptance.json").write_text(json.dumps(report, indent=2))
        print(json.dumps(report), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assert args.serial.startswith("emulator-"), "Only a disposable emulator is allowed"
    command = [args.adb, "-s", args.serial]
    assert subprocess.check_output(command + ["shell", "getprop", "ro.kernel.qemu"], text=True).strip() == "1"
    subprocess.run(command + ["root"], check=True)
    subprocess.run(command + ["wait-for-device"], check=True)
    test = LibraryAcceptance(args.adb, args.serial, args.output)
    try:
        test.run_acceptance(args.apk)
    except Exception:
        test.screenshot("failure")
        raise
