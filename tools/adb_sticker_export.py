#!/usr/bin/env python3
"""Copy your own locally saved WhatsApp stickers with ADB, then create
bounded ZIP batches that StickerDock's ZIP importer accepts.

No root, modified APK, secret credentials, remote sticker scraping, or network.
Only source images already present in Android/media are read.
"""
from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

REMOTE_SOURCES = {
    "personal": [
        "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
    ],
    "business": [
        "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers",
        "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Stickers",
    ],
}


def adb(base: list[str], *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run([*base, *args], text=True, capture_output=True, check=False)


def pull_stickers(destination: Path, account: str, serial: str | None) -> None:
    if not shutil.which("adb"):
        raise RuntimeError("Install Android SDK platform-tools (adb) and connect an authorized phone")
    base = ["adb"] + (["-s", serial] if serial else [])
    check = adb(base, "devices")
    if check.returncode:
        raise RuntimeError("Could not list ADB devices: " + check.stderr.strip())
    print("ADB devices:\n" + check.stdout.strip())
    sources = ["personal", "business"] if account == "both" else [account]
    for source in sources:
        received = False
        for remote in REMOTE_SOURCES[source]:
            target = destination / "raw" / source
            target.mkdir(parents=True, exist_ok=True)
            print(f"Checking {source}: {remote}")
            run = adb(base, "pull", remote, str(target))
            if run.returncode == 0:
                print(run.stdout.strip() or f"Copied {source} sticker files")
                received = True
                break
            print("Not accessible at this path; trying next location")
        if not received:
            print(f"WARNING: No accessible {source} folder. Check phone storage, ADB permission and WhatsApp version.")


def create_archives(destination: Path, max_files: int, max_megabytes: int) -> int:
    raw = destination / "raw"
    files: list[tuple[str, Path]] = []
    for source in ("personal", "business"):
        folder = raw / source
        if not folder.is_dir():
            continue
        for path in folder.rglob("*"):
            if path.is_file() and path.suffix.lower() in (".webp", ".png", ".jpg", ".jpeg"):
                files.append((source, path))
    files.sort(key=lambda e: (e[0], str(e[1])))
    if not files:
        print("No image files found; nothing to package.")
        return 0
    archives_dir = destination / "sticker_zips"
    archives_dir.mkdir(parents=True, exist_ok=True)
    created = 0
    current: ZipFile | None = None
    batch_files = 0
    batch_bytes = 0
    accepted = 0
    seen_digests = set()
    max_bytes = max_megabytes * 1_000_000
    try:
        for source, path in files:
            if not 0 < path.stat().st_size <= 1_000_000:
                print(f"SKIP (too large/empty): {path}")
                continue
            data = path.read_bytes()
            digest = hashlib.sha256(data).hexdigest()
            if digest in seen_digests:
                continue
            seen_digests.add(digest)
            if current is None or batch_files >= max_files or batch_bytes + len(data) > max_bytes:
                if current is not None:
                    current.close()
                created += 1
                current = ZipFile(archives_dir / f"stickerdock-import-{created:04d}.zip", "w", ZIP_DEFLATED)
                batch_files = 0
                batch_bytes = 0
            # Do not use filenames as paths: preserve extension but prevent
            # accidental paths/traversal or clobbering duplicate filenames.
            current.writestr(f"{source}/{digest[:32]}{path.suffix.lower()}", data)
            batch_files += 1
            batch_bytes += len(data)
            accepted += 1
    finally:
        if current is not None:
            current.close()
    print(f"Created {created} ZIP batch(es) for {accepted} unique stickers in {archives_dir}.")
    print("Transfer these ZIP files to the phone and open StickerDock > Import sticker ZIP.")
    print("Raw copies remain on your computer. None were uploaded to a server.")
    return created


def main() -> int:
    parser = argparse.ArgumentParser(description="Export existing on-device WhatsApp stickers for StickerDock")
    parser.add_argument("--output", type=Path, default=Path("StickerDock-Phone-Export"))
    parser.add_argument("--account", choices=("personal", "business", "both"), default="both")
    parser.add_argument("--serial", help="ADB device serial; optional for single connected device")
    parser.add_argument("--existing-files", action="store_true", help="Skip ADB and package files already under output/raw/")
    parser.add_argument("--max-files", type=int, default=250, help="Stickers per ZIP, up to 1000")
    parser.add_argument("--max-mb", type=int, default=45, help="Uncompressed image MB per ZIP, up to 65")
    args = parser.parse_args()
    if args.serial and not re.fullmatch(r"[A-Za-z0-9:._-]+", args.serial):
        parser.error("Invalid ADB device serial")
    if not 1 <= args.max_files <= 1000 or not 1 <= args.max_mb <= 65:
        parser.error("max-files must be 1–1000 and max-mb 1–65")
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        if not args.existing_files:
            pull_stickers(args.output, args.account, args.serial)
        return 0 if create_archives(args.output, args.max_files, args.max_mb) > 0 else 1
    except RuntimeError as exc:
        print("ERROR:", exc, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
