# Phone, Sticker.ly, and WhatsApp sticker migration

## What is supported

October 21: signed Android 15 emulator acceptance now verifies normal SAF
selection, pause/resume, force-stop recovery, EXIF grouping, static/animated
separation, duplicates, invalid files and preservation of a 10,003-sticker
baseline. See [the measured results and limits](MIGRATION_ACCEPTANCE_2026-10-21.md).
Samsung/physical WhatsApp roots remain unverified.

StickerDock can import local images you have access to, user-selected folders, ZIP archives, and its own exported pack archives. It separately stores public Sticker.ly *links* as bookmarks. It cannot retrieve the complete catalog hosted on Sticker.ly and does not claim the right to redistribute it.

### Method A — Samsung / Android folder import (recommended)

1. Open StickerDock → **Create** → **Import and backups** → **Import phone sticker folder**.
2. In Android's system folder picker, browse to the stickers saved by **your** WhatsApp:
   - WhatsApp personal: `Internal storage/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers`
   - WhatsApp Business: often under `Internal storage/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers`. Exact folder labels vary.
3. Select **Use this folder** and accept the Android permission prompt.
4. StickerDock reads WebP/PNG/JPEG from this selected directory and its subfolders, uses EXIF pack names if present, separates animated from static, and deduplicates content.
5. Do the same for the other WhatsApp account if it is a separate installation. Two accounts inside one WhatsApp app can share an on-device media folder, so files cannot reliably be assigned to the originating account.

**Limitations:** This folder normally contains stickers *sent or received in chats*, not necessarily every sticker from an installed or browsed Sticker.ly pack. Some Android versions' picker restricts access; do not try to bypass app sandbox restrictions. The MVP imports up to 20,000 recognized image files per selection, with a 1.5GB cumulative limit and 1MB per-image read limit; larger data should use smaller batches. Imports run on a worker thread, journal each processed URI/outcome in SQLite, and expose **Pause**, **Resume** and **Discard**. When the document provider honors persisted read access, reopening StickerDock after a process stop resumes by re-scanning the selected tree and skipping journaled source URIs. Folders deeper than the traversal bound are reported and should be imported separately. This recovery path still needs real-device kill/restart QA.

### Method B — ADB bulk export on a Windows/Mac/Linux computer

Prerequisites: install the official Android SDK platform-tools (ADB) and Python 3.10+. Connect **your own** Android phone, enable Developer options/USB debugging and approve the computer's authorization.

From the repo root:

```powershell
python tools/adb_sticker_export.py --account both --output StickerDock-Phone-Export
```

The script tries the standard personal and Business Android/media folders. It copies image files to `StickerDock-Phone-Export/raw/`, hashes and deduplicates them, then writes 250-image or up to 45MB ZIP batches under `StickerDock-Phone-Export/sticker_zips/`. It does not read `/data/data`, bypass protections, require root, modify WhatsApp or access the internet. If ADB cannot see a folder, the output explains the limitation.

Transfer ZIP batches to your phone and use **Import sticker ZIP**; you can select up to 100 ZIP files in one picker operation. A StickerDock export ZIP preserves original user pack names, content labels, and static/animated flag. Generic ZIP batches recover pack names from WebP metadata where available, otherwise use folder names.

Alternatively, to ZIP your own previously exported local images without ADB:

```powershell
python tools/adb_sticker_export.py --existing-files --output StickerDock-Phone-Export
```

Put images under `StickerDock-Phone-Export/raw/personal/` or `raw/business/` first.

### Method C — Share or bookmark public Sticker.ly packs

From Sticker.ly, share a public pack link or code to StickerDock, or paste a list in **Saved Sticker.ly pack links**. StickerDock stores the links locally; tap a saved entry to open the official public pack page. This deliberately does not scrape/rehost a creator's image files.

If an authorized export supplies **actual** image files, import them with the system picker or as a ZIP. If Sticker.ly exports stickers to WhatsApp and those stickers then appear in the WhatsApp media sticker folder, Method A may recover them. Not every exported pack is guaranteed to appear in the readable folder.

### Method D — move your StickerDock packs between Android and iPhone

The iPhone client is a tested-source candidate; a signed iPhone installation is
still required. It cannot scan Android folders or another iPhone app's sandbox.
The October 21 Apple native test's actual exported MATURE/static pack was
explicitly imported on Android, preserving exact media and all pre-existing
library rows through restart. See [the source-bound evidence and limits](CREATOR_ACCEPTANCE_2026-10-21.md).
This is a simulator/emulator round trip, not physical-phone acceptance.

1. On Android, open a pack → **Share pack**, or choose **Sticker backup** in the
   import/backup tools. Transfer the ZIP through a sharing service you choose.
   **Editable backup** is an Android project archive and is not directly readable
   by the iPhone client; use the portable Sticker backup for this transfer.
2. On iPhone, save that ZIP in Files. Open StickerDock → **Library** → settings
   → **Import sticker ZIP**; the empty library also has an import action.
3. Check the imported names, static/animated types and private ratings. Saved
   media stays in StickerDock's private local library. Adding to WhatsApp is a
   separate explicit action and requires 3–30 stickers in each pack.
4. To return a pack to Android, use the iPhone pack's **Pack options** → **Share
   ZIP**, then Android **Create** → **Import and backups** → **Sticker ZIPs**.

StickerDock schema-5 ZIPs preserve media bytes and metadata; supported older
schemas 1–4 remain readable. iPhone 0.10.2 generic ZIP import supports PNG/JPEG
with bounded native conversion and WebP byte retention, but .wastickers metadata
and EXIF pack-name grouping remain separate work. See [photo ZIP acceptance](IPHONE_PHOTO_ARCHIVES.md). Use a StickerDock
export for that migration. Editing recipes/full-resolution originals are not
included in portable backups. Unsigned IPA/CI results do not prove a physical
iPhone or WhatsApp installation.

### Can the modified Sticker.ly APK supply everything?

An APK primarily contains executable code and bundled assets. A modified APK is **not** a complete online sticker catalog, and other app-private caches/databases are generally inaccessible without app cooperation or privileged device access. We have not inspected your modified APK because it has not been supplied. Treat untrusted modded APKs as a credential/phone security risk; use authorized data exports instead.

### Public repo comparisons

- [StickerVault](https://github.com/mintnchocochip/stickervault) implements non-root SAF folder scans, portable backups, and metadata-based pack recovery. GPL-3.0 — we used the technical idea as a reference; its source is **not copied** into this MIT-licensed project.
- [WhatsApp official stickers](https://github.com/WhatsApp/stickers) documents provider/metadata and static-vs-animated constraints; BSD license.
- [theabbie/sticker.ly](https://github.com/theabbie/sticker.ly) is an unofficial historical API wrapper; present-day reliability, service compatibility, and permission for mass collection are unverified.
- [ciwga/Stickerly](https://github.com/ciwga/Stickerly) is an archived downloader, not a maintained official account export.

Sticker.ly publishes stickers under a limited personal-use license; their being publicly visible does **not** grant a right to clone and republish its entire catalog. Read https://sticker.ly/terms-of-use before redistribution.

## Still to do

Validate on-device Android 14/15+ SAF folder selection, very-large libraries, metadata grouping, animated preview, export into each WhatsApp installation, actual process-kill/restart resume behavior, and legal user-owned Sticker.ly exports. The build pipeline checks compilation, not these runtime claims.
