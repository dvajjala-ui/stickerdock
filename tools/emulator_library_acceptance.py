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
import zipfile
import xml.etree.ElementTree as ET

from PIL import Image
from emulator_clip_creator_smoke import ClipCreatorSmoke

PACKAGE = "app.stickerdock.duo"
ROOT = "/data/user/0/" + PACKAGE


class LibraryAcceptance(ClipCreatorSmoke):
    def launch(self, restart=False):
        if restart:
            self.adb("shell", "am", "force-stop", PACKAGE)
        # Reset the task/navigation only; every saved pack and media file stays.
        self.adb("shell", "am", "start", "-W", "-f", "0x10008000", "-n", PACKAGE + "/.StickerDockPolishedActivity")

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

    def wait_hidden(self, text):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if not self.nodes(text):
                return
            time.sleep(0.4)
        raise AssertionError("Expected dismissed sheet: " + text)

    def memory(self):
        raw = self.adb("shell", "dumpsys", "meminfo", PACKAGE)
        (self.output / "meminfo.txt").write_text(raw)
        match = re.search(r"TOTAL PSS:\s*(\d+)", raw) or re.search(r"^\s*TOTAL\s+(\d+)", raw, re.M)
        assert match, "No application memory report"
        return int(match.group(1))

    def photo_destination(self):
        # Repo-owned icon is a deterministic non-flat image, not a personal photo
        # or claimed catalog-quality artwork. Exercise the real DocumentsUI picker.
        fixture = Path(__file__).resolve().parents[1] / "ios/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
        self.adb("push", str(fixture), "/sdcard/Download/qa-photo.png")
        before = self.counts()
        for save in (False, True):
            self.launch(restart=True)
            self.wait_text("Search library"); self.tap("Create")
            self.wait_text("Make stickers from photos")
            self.screenshot("15-create-screen")
            self.wait_text("Make stickers from photos"); self.tap("Make stickers from photos")
            self.wait_text("Show roots")
            if not self.nodes("Images in Downloads"):
                self.tap("Show roots"); self.wait_text("Downloads")
                root = self.nodes("Downloads")[-1]
                left, top, right, bottom = map(int, re.findall(r"\d+", root.get("bounds")))
                self.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                self.wait_text("Images in Downloads")
            if self.nodes("List view"):
                self.tap("List view")
            self.wait_text("qa-photo.png"); self.tap("qa-photo.png")
            self.wait_text("Save to a pack")
            assert self.counts() == before, "Photo selection mutated the library before Save"
            self.fill("Pack name", "My photo reactions")
            self.screenshot("09-photo-destination")
            if not save:
                self.tap("Cancel")
                self.wait_hidden("Save to a pack")
                assert self.counts() == before, "Cancelling photos created a pack"
            else:
                self.tap("Save to pack"); self.wait_text("Pack options")
                assert self.counts() == (before[0] + 1, before[1] + 1), "Explicit photo save did not create exactly one pack/sticker"
                self.screenshot("10-saved-photo-pack")
        self.launch(restart=True); self.wait_text("My photo reactions")
        assert self.counts() == (before[0] + 1, before[1] + 1), "Photo pack did not survive restart"
        self.screenshot("11-photo-pack-library")

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

    def favorites(self):
        """Exercise shortcuts on the newly created QA photo; retain the 10k seed."""
        def database_rows(query, args=()):
            target = self.output / "favorites.db"
            self.adb("pull", ROOT + "/databases/stickerdock.db", str(target))
            with sqlite3.connect(target) as db:
                return db.execute(query, args).fetchall()

        def favorite_ids():
            raw = self.adb("shell", "cat", ROOT + "/shared_prefs/sticker_favorites.xml")
            return {node.text for node in ET.fromstring(raw).findall("./set[@name='sticker_ids']/string")}

        def open_favorites():
            self.launch(restart=True); self.wait_text("Search library")
            self.tap("Favorites (1)")
            self.wait_text("1 favorite stickers")

        old_rows = set(database_rows("SELECT id,pack_id,file_name,sha256 FROM stickers"))
        row = database_rows("SELECT s.id,s.label,s.sha256,p.version FROM stickers s JOIN packs p ON p.id=s.pack_id WHERE p.name=?", ("My photo reactions",))[0]
        original_id, label, digest, version = row
        before = self.counts()
        self.launch(restart=True); self.wait_text("Search library"); self.fill("Search library", "My photo")
        self.wait_text("My photo reactions"); self.tap("My photo reactions")
        self.wait_text(label); self.tap(label)
        self.scroll_to_text("Add to favorites"); self.tap("Add to favorites")
        self.wait_text("Remove from favorites")
        assert favorite_ids() == {original_id}
        assert database_rows("SELECT s.sha256,p.version FROM stickers s JOIN packs p ON p.id=s.pack_id WHERE s.id=?", (original_id,))[0] == (digest, version), "Favoriting changed exported data/version"
        assert self.counts() == before
        open_favorites(); self.fill("Search library", "My photo reactions")
        self.wait_text(label); self.screenshot("22-favorites-search")
        self.adb("shell", "settings", "put", "system", "font_scale", "2.0")
        open_favorites(); self.screenshot("23-favorites-large-text")
        self.tap(label)
        self.scroll_to_text("Copy or move to another pack"); self.tap("Copy or move to another pack")
        self.wait_text("New pack name")
        self.wait_text("Copy"); self.wait_text("Move")
        self.screenshot("25-transfer-large-text")
        self.adb("shell", "settings", "put", "system", "font_scale", "1.0")
        open_favorites(); self.tap(label)
        self.scroll_to_text("Copy or move to another pack"); self.tap("Copy or move to another pack")
        self.fill("New pack name", "Favorite destination QA")
        self.tap("Create new pack"); self.wait_enabled("Move"); self.tap("Move")
        self.wait_hidden("Copy or move sticker")
        deadline = time.monotonic() + 35
        while True:
            moved = database_rows("SELECT s.id FROM stickers s JOIN packs p ON p.id=s.pack_id WHERE p.name=? AND s.sha256=?", ("Favorite destination QA", digest))
            if moved and favorite_ids() == {moved[0][0]}:
                break
            assert time.monotonic() < deadline, "Move did not persist its sticker and favorite shortcut"
            time.sleep(0.3)
        moved_id = moved[0][0]
        assert moved_id != original_id and favorite_ids() == {moved_id}
        assert self.counts() == (before[0] + 1, before[1])
        open_favorites(); self.wait_text("Favorite destination QA"); self.tap(label)
        self.scroll_to_text("Remove sticker"); self.tap("Remove sticker")
        self.tap("Undo"); self.wait_text("Sticker restored")
        restored_id = database_rows("SELECT s.id FROM stickers s JOIN packs p ON p.id=s.pack_id WHERE p.name=? AND s.sha256=?", ("Favorite destination QA", digest))[0][0]
        assert restored_id != moved_id and favorite_ids() == {restored_id}, "Undo did not restore the shortcut to the replacement row"
        open_favorites(); self.wait_text("Favorite destination QA"); self.screenshot("24-favorites-after-undo")
        self.tap("Remove " + label + " from favorites"); self.wait_text("Favorites (0)")
        assert not favorite_ids() and self.counts() == (before[0] + 1, before[1])
        assert old_rows - {entry for entry in old_rows if entry[0] == original_id} <= set(database_rows("SELECT id,pack_id,file_name,sha256 FROM stickers")), "Favorites changed unrelated saved rows"
        self.launch(restart=True); self.wait_text("Favorites (0)")
        report = {"passed": True, "before": before, "after": self.counts(), "sticker_sha256": digest,
                  "checks": ["explicit favorite does not change media, pack version or saved count", "favorites search and cold restart", "200% text including reachable transfer actions", "favorite follows move to new pack", "delete and Undo restores favorite", "unfavorite preserves media and all unrelated rows"]}
        (self.output / "favorites.json").write_text(json.dumps(report, indent=2))

    def shared_motion(self):
        """Cold/warm local video/GIF shares enter creation without an implicit save."""
        before = self.counts()
        gif = self.output / "qa-shared.gif"
        frames = [Image.new("RGB", (160, 120), color) for color in ("red", "blue", "green", "yellow")]
        frames[0].save(gif, save_all=True, append_images=frames[1:], duration=200, loop=0)
        directory = ROOT + "/cache/shared"
        self.adb("shell", "mkdir", "-p", directory)
        for index, (fixture, mime) in enumerate(((self.output / "qa-motion.mp4", "video/mp4"), (gif, "image/gif"))):
            self.adb("push", str(fixture), directory + "/" + fixture.name)
            uid = self.adb("shell", "stat", "-c", "%u", ROOT).strip()
            assert uid.isdigit()
            self.adb("shell", "chown", "-R", f"{uid}:{uid}", directory)
            self.adb("shell", "restorecon", "-RF", directory)
            if index == 0:
                self.adb("shell", "am", "force-stop", PACKAGE)
            uri = "content://" + PACKAGE + ".exports/shared/" + fixture.name
            self.adb("shell", "am", "start", "-W", "-a", "android.intent.action.SEND", "-t", mime,
                     "--eu", "android.intent.extra.STREAM", uri, "--grant-read-uri-permission", "-n", PACKAGE + "/.StickerDockPolishedActivity")
            self.wait_text("Make an animated reaction")
            self.scroll_to_text("Preview animation")
            self.wait_enabled("Preview animation")
            assert not self.enabled("Save sticker") and self.counts() == before
            self.preview("Shared reaction QA", f"26-shared-motion-{index}")
            self.tap("Cancel"); self.wait_text("Close creation"); self.tap("Close creation")
            self.wait_hidden("Make an animated reaction")
            assert self.counts() == before, "Shared media preview/cancellation changed the library"
        self.launch(restart=True); self.wait_text("Search library")
        assert self.counts() == before
        (self.output / "shared-motion.json").write_text(json.dumps({"passed": True, "before": before, "after": self.counts(),
            "checks": ["cold video SEND and warm GIF SEND", "local granted FileProvider media", "moving preview with caption", "Save disabled until preview", "cancel and restart retain the library"]}, indent=2))

    def brush_refinement(self):
        sticker_id = str(uuid.uuid5(uuid.NAMESPACE_URL, "stickerdock-qa-sticker:1:0"))

        def saved_state():
            database = self.output / "brush.db"
            self.adb("pull", ROOT + "/databases/stickerdock.db", str(database))
            with sqlite3.connect(database) as connection:
                return connection.execute("SELECT s.sha256,a.draft_history FROM stickers s LEFT JOIN artwork_projects a ON a.sticker_id=s.id WHERE s.id=?", (sticker_id,)).fetchone()

        def open_editor():
            self.launch(restart=True)
            self.wait_text("Search library"); self.fill("Search library", "001")
            self.wait_text("QA pack 001"); self.tap("QA pack 001")
            self.wait_text("QA sticker 00"); self.tap("QA sticker 00")
            self.wait_text("Edit artwork and captions"); self.tap("Edit artwork and captions")
            self.wait_text("Edit sticker"); self.scroll_to_text("Refine edges"); self.wait_enabled("Refine edges")

        def brush():
            self.tap("Refine edges"); self.wait_text("Brush on sticker edges")
            # Loading the retained image is asynchronous; Undo becomes available
            # only after a real stroke. Retry an ignored loading-time gesture.
            for _ in range(5):
                node = self.nodes("Brush on sticker edges")[0]
                left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
                y = (top + bottom) // 2
                self.adb("shell", "input", "swipe", str(left + (right - left) // 3), str(y), str(left + (right - left) * 2 // 3), str(y), "400")
                if self.enabled("Undo"):
                    break
            self.wait_enabled("Apply")

        original, _ = saved_state()
        open_editor(); brush()
        self.screenshot("12-brush-refinement")
        self.tap("Undo"); assert not self.enabled("Apply"), "Undo retained a brush edit"
        self.tap("Redo"); self.wait_enabled("Apply")
        self.tap("Cancel refinement"); self.wait_hidden("Cancel refinement")
        assert saved_state() == (original, None), "Cancelled refinement mutated the sticker or draft"
        brush(); self.tap("Apply"); self.wait_hidden("Cancel refinement")
        saved, draft = saved_state()
        assert saved == original and draft, "Apply must retain a draft without overwriting the saved sticker"
        self.screenshot("13-refined-draft")
        # A force-stop exercises the persisted mask and draft, not Activity state.
        open_editor(); self.wait_enabled("Save"); self.tap("Save")
        self.wait_text("Pack options")
        changed, draft = saved_state()
        assert changed != original and draft is None, "Saved brush artwork was not committed"
        open_editor(); self.scroll_to_text("Reset", forward=False); self.tap("Reset"); self.wait_enabled("Save"); self.tap("Save")
        self.wait_text("Pack options")
        assert saved_state() == (original, None), "Reset did not restore the exact original bytes"
        self.screenshot("14-restored-original")

    def public_pack_links(self):
        before = self.counts()
        self.adb("shell", "am", "force-stop", PACKAGE)
        # A valid-shaped but deliberately unpublished ID exercises cold and
        # singleTop delivery without relying on a mutable production catalog.
        for slug in ("qa-unpublished-pack", "qa-another-unpublished-pack"):
            self.adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", "stickerdock://pack/" + slug)
            self.wait_text("Open pack code")
            self.wait_text("SD:" + slug)
            assert self.counts() == before, "Opening a public code silently imported content"
            self.tap("Cancel"); self.wait_hidden("Open pack code")
        self.adb("shell", "am", "start", "-W", "-a", "android.intent.action.SEND", "-t", "text/plain",
                 "--es", "android.intent.extra.TEXT", "SD:qa-shared-unpublished-pack", "-n", PACKAGE + "/.StickerDockPolishedActivity")
        self.wait_text("Open pack code"); self.wait_text("SD:qa-shared-unpublished-pack")
        self.screenshot("16-pack-code-preview")
        self.tap("Cancel"); self.wait_hidden("Open pack code")
        assert self.counts() == before, "Shared code or cancellation mutated the library"

    def editable_backups(self):
        before = self.counts()

        def open_options():
            self.launch(restart=True); self.wait_text("Search library"); self.tap("Create")
            self.scroll_to_text("Import and backups"); self.tap("Import and backups")
            self.scroll_to_text("Back up library"); self.tap("Back up library")
            self.wait_text("Back up your library")

        def downloads():
            self.wait_text("Show roots"); self.tap("Show roots"); self.wait_text("Downloads")
            root = self.nodes("Downloads")[-1]
            left, top, right, bottom = map(int, re.findall(r"\d+", root.get("bounds")))
            self.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

        open_options(); self.screenshot("18-backup-options")
        self.adb("shell", "settings", "put", "system", "font_scale", "2.0")
        self.wait_text("Back up your library"); self.scroll_to_text("Sticker backup")
        self.screenshot("19-backup-large-text"); self.tap("Cancel")
        self.scroll_to_text("Back up library"); self.screenshot("21-import-large-text")
        self.adb("shell", "settings", "put", "system", "font_scale", "1.0")
        # Both picker cancellation and the format choice leave the collection intact.
        open_options(); self.tap("Sticker backup"); self.wait_text("Show roots")
        self.adb("shell", "input", "keyevent", "4")
        self.wait_text("Back up library"); assert self.counts() == before
        open_options(); self.tap("Editable backup"); downloads(); self.wait_text("SAVE"); self.tap("SAVE")
        self.wait_text("Editable backup saved")
        archive = self.output / "editable-export.zip"
        self.adb("pull", "/sdcard/Download/stickerdock-editable.zip", str(archive))
        with zipfile.ZipFile(archive) as outer:
            manifest = json.loads(outer.read("editable-backup.json"))
            library = outer.read("library.zip")
            assert hashlib.sha256(library).hexdigest() == manifest["library_sha256"]
            project = manifest["projects"][0]
            assert project["masks"], "Export discarded the retained brush history"
            for digest in project["masks"]:
                assert hashlib.sha256(outer.read(f"blobs/{digest}.png")).hexdigest() == digest
            assert hashlib.sha256(outer.read(f"blobs/{project['source_sha256']}.webp")).hexdigest() == project["source_sha256"]
            # Restore one project from this real export, keeping the 10k acceptance
            # library intact without doubling every synthetic pack/sticker.
            _, pack_id, filename = project["sticker"].split("/")
            with zipfile.ZipFile(io.BytesIO(library)) as saved:
                document = json.loads(saved.read("manifest.json"))
                pack = next(p for p in document["packs"] if p["id"] == pack_id)
                pack["name"] = "Editable backup restored"
                pack["stickers"] = [s for s in pack["stickers"] if s["name"] == filename]
                document["packs"] = [pack]
                stream = io.BytesIO()
                with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as compact:
                    compact.writestr("manifest.json", json.dumps(document))
                    compact.writestr(project["sticker"], saved.read(project["sticker"]))
            library = stream.getvalue()
            manifest["library_sha256"] = hashlib.sha256(library).hexdigest()
            manifest["projects"] = [project]
            restore = self.output / "qa-editable-restore.zip"
            with zipfile.ZipFile(restore, "w", zipfile.ZIP_DEFLATED) as compact:
                compact.writestr("editable-backup.json", json.dumps(manifest))
                compact.writestr("library.zip", library)
                paths = [f"blobs/{project['source_sha256']}.webp"] + [f"blobs/{mask}.png" for mask in project["masks"]]
                for path in paths:
                    compact.writestr(path, outer.read(path))
        assert self.counts() == before, "Backup export changed the library"
        self.adb("push", str(restore), "/sdcard/Download/qa-editable-restore.zip")
        self.scroll_to_text("Restore backup"); self.tap("Restore backup"); downloads()
        if self.nodes("List view"):
            self.tap("List view")
        self.scroll_to_text("qa-editable-restore.zip"); self.tap("qa-editable-restore.zip")
        self.wait_text("Restored 1 packs")
        assert self.counts() == (before[0] + 1, before[1] + 1)
        self.launch(restart=True); self.wait_text("Editable backup restored")
        self.screenshot("20-restored-editable-pack")
        database = self.output / "editable-restored.db"
        self.adb("pull", ROOT + "/databases/stickerdock.db", str(database))
        with sqlite3.connect(database) as connection:
            record = connection.execute("SELECT a.source_sha256,a.saved_history,a.draft_history FROM artwork_projects a JOIN stickers s ON a.sticker_id=s.id JOIN packs p ON s.pack_id=p.id WHERE p.name=?", ("Editable backup restored",)).fetchone()
        assert record == (project["source_sha256"], project["saved_history"], project["draft_history"]), "Editable restore lost project history"

    def received_pack(self):
        before = self.counts()
        pack_id, sticker_id = str(uuid.uuid4()), str(uuid.uuid4())
        image = io.BytesIO()
        Image.new("RGBA", (512, 512), (240, 190, 55, 255)).save(image, format="WEBP", lossless=True)
        content = image.getvalue()
        archive = self.output / "Weekend reactions.zip"
        manifest = {"schema": 5, "packs": [{"id": pack_id, "name": "Weekend reactions", "kind": "STATIC", "rating": "GENERAL", "category": "REACTIONS",
                    "stickers": [{"name": sticker_id + ".webp", "sha256": hashlib.sha256(content).hexdigest(), "label": "Weekend", "tags": "qa"}]}]}
        with zipfile.ZipFile(archive, "w") as zipped:
            zipped.writestr("manifest.json", json.dumps(manifest))
            zipped.writestr(f"packs/{pack_id}/{sticker_id}.webp", content)
        # Use a real scoped FileProvider URI, as the sender's share sheet does.
        # A shell-created DocumentsProvider URI does not prove a user-selected
        # document grant and can fail before the app reads the archive.
        directory = ROOT + "/cache/shared"
        self.adb("shell", "mkdir", "-p", directory)
        self.adb("push", str(archive), directory + "/qa-received.zip")
        uid = self.adb("shell", "stat", "-c", "%u", ROOT).strip()
        assert uid.isdigit()
        self.adb("shell", "chown", "-R", f"{uid}:{uid}", directory)
        self.adb("shell", "restorecon", "-RF", directory)
        uri = "content://" + PACKAGE + ".exports/shared/qa-received.zip"
        for save in (False, True):
            self.adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-t", "application/zip", "-d", uri,
                     "--grant-read-uri-permission", "-n", PACKAGE + "/.StickerDockPolishedActivity")
            self.wait_text("Import shared packs?")
            assert self.counts() == before, "Opening a received archive imported before confirmation"
            self.screenshot("17-received-pack")
            self.tap("Import packs" if save else "Cancel")
            if save:
                deadline = time.monotonic() + 30
                while time.monotonic() < deadline and self.nodes("Import shared packs?"):
                    visible = [n.get("text", "") for n in self.tree().iter("node") if self.visible(n)]
                    errors = [value for value in visible if "Shared import:" in value]
                    assert not errors, "Received archive failed: " + " ".join(errors)
                    time.sleep(.4)
            self.wait_hidden("Import shared packs?")
            assert self.counts() == ((before[0] + 1, before[1] + 1) if save else before)
        self.launch(restart=True); self.wait_text("Weekend reactions")

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
        self.wait_hidden("Rename pack")
        self.tap("Use in WhatsApp")
        self.wait_text("WhatsApp not installed"); self.wait_text("Business not installed")
        self.screenshot("06-targets-font200")
        self.adb("shell", "input", "keyevent", "4")
        self.wait_hidden("WhatsApp not installed")
        self.tap("Back")
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
        self.photo_destination()
        self.favorites()
        self.shared_motion()
        self.brush_refinement()
        self.public_pack_links()
        self.received_pack()
        self.editable_backups()
        crashes = self.adb("logcat", "-b", "crash", "-d")
        assert "FATAL EXCEPTION" not in crashes, "App crashed during acceptance"
        report = {"passed": True, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "packs": 334, "stickers": 10000,
                  "initial_pss_kb": initial_memory, "scrolled_pss_kb": scrolled_memory,
                  "preview_interaction_seconds": round(preview_seconds, 2),
                  "favorites": json.loads((self.output / "favorites.json").read_text()),
                  "shared_motion": json.loads((self.output / "shared-motion.json").read_text()),
                  "checks": ["large library and scrolling", "animated grid posters and detail playback", "360dp at 200% text", "Android tablet two-pane layout", "scrollable filters and WhatsApp targets", "memory trim and restart preservation", "real MP4 streaming preview without saving", "photo picker, named destination, cancel without mutation, explicit save and restart", "brush undo/redo/cancel, durable mask draft, process restart, save and exact-original reset", "public pack custom-link cold start, warm intent, shared code and cancellation without import", "received pack file explicit import, cancel without mutation and restart preservation", "editable backup format choice/200% text, picker cancellation, real DocumentsUI export, source/mask integrity, one-project DocumentsUI restore and persisted history"],
                  "limitations": ["AOSP emulator; physical WhatsApp/Business acceptance remains separate", "low-storage rollback covered by native unit tests", "Google Play subject-model inference and cutout quality require separate device acceptance", "Pack-code fixture is deliberately unpublished; live published-pack and HTTPS sharing acceptance remains separate"]}
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
        # A disconnected emulator must not replace the original failure with a
        # second screenshot/transport exception. Preserve useful crash evidence.
        for name, command in (("crash", ("logcat", "-b", "crash", "-d")),
                              ("system", ("logcat", "-d", "-t", "500"))):
            try:
                (args.output / (name + "-diagnostic.log")).write_text(test.adb(*command), encoding="utf-8")
            except Exception as diagnostic_error:
                print("Could not collect", name, "diagnostics:", str(diagnostic_error), flush=True)
        try:
            test.screenshot("failure")
            print("Visible controls at failure:", json.dumps([
                node.get("text") or node.get("content-desc") for node in test.tree().iter("node")
                if test.visible(node) and (node.get("text") or node.get("content-desc"))]), flush=True)
        except Exception as diagnostic_error:
            print("Could not collect failure screen:", str(diagnostic_error), flush=True)
        raise
