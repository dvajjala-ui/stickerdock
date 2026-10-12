#!/usr/bin/env python3
"""Manually publish one reviewed GENERAL factory day to the Android catalog."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import uuid
import zipfile
from PIL import Image

from supabase_factory import PRIVATE_BUCKET, PUBLIC_BUCKET, SupabaseFactory, public_catalog_base

PUBLIC_MEDIA_LICENSES = {"CC0", "PUBLIC_DOMAIN", "USER_OWNED"}


def validate_pack(data):
    if not 12 <= len(data) <= 75_000_000:
        raise ValueError("Invalid pack size")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        if len(names) > 4000 or len(names) != len(set(names)) or "manifest.json" not in names:
            raise ValueError("Invalid factory pack")
        if (archive.getinfo("manifest.json").file_size > 128_000 or
                sum(item.file_size for item in archive.infolist()) > 75_000_000):
            raise ValueError("Factory archive exceeds size limits")
        manifest = json.loads(archive.read("manifest.json"))
        if manifest.get("schema") not in (3, 4) or not isinstance(manifest.get("packs"), list) or not manifest["packs"]:
            raise ValueError("Unsupported factory pack")
        if any(pack.get("rating") != "GENERAL" for pack in manifest["packs"]):
            raise ValueError("Only reviewed GENERAL packs can be public")
        if any(not 3 <= len(pack.get("stickers", [])) <= 30 for pack in manifest["packs"]):
            raise ValueError("Public pack has an invalid sticker count")
        expected = {"manifest.json"}
        for pack in manifest["packs"]:
            pack_id = str(pack.get("id", ""))
            if not re.fullmatch(r"[0-9a-f-]{36}", pack_id) or pack.get("kind") not in ("STATIC", "ANIMATED"):
                raise ValueError("Invalid pack metadata")
            animated = pack["kind"] == "ANIMATED"
            for sticker in pack["stickers"]:
                name = str(sticker.get("name", ""))
                if not re.fullmatch(r"[0-9a-f-]{36}\.webp", name):
                    raise ValueError("Invalid sticker name")
                path = "packs/" + pack_id + "/" + name
                if path in expected or path not in names:
                    raise ValueError("Missing or duplicate sticker")
                expected.add(path)
                size = archive.getinfo(path).file_size
                if not 12 <= size <= (500_000 if animated else 100_000):
                    raise ValueError("Sticker exceeds size limits")
                asset = archive.read(path)
                if hashlib.sha256(asset).hexdigest() != sticker.get("sha256"):
                    raise ValueError("Sticker checksum mismatch")
                with Image.open(io.BytesIO(asset)) as image:
                    if (image.format != "WEBP" or image.size != (512, 512) or
                            bool(getattr(image, "is_animated", False)) != animated):
                        raise ValueError("Sticker does not meet its declared media type")
        if set(names) != expected:
            raise ValueError("Unexpected factory archive entry")
    return manifest


def validate_references(data, count=None):
    if data is None or len(data) > 1_000_000:
        raise ValueError("Reviewed reference metadata is required")
    references = json.loads(data)
    if not isinstance(references, list) or len(references) > 100 or (count is not None and len(references) != count):
        raise ValueError("Invalid reference metadata")
    for reference in references:
        if not isinstance(reference, dict):
            raise ValueError("Invalid reference metadata")
        credit = reference.get("media_credit")
        if credit is not None and (not isinstance(credit, dict) or
                                   credit.get("license") not in PUBLIC_MEDIA_LICENSES):
            raise ValueError("A source is private-reference-only and cannot be published")
    return references


def publish_day(client, day):
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", day):
        raise ValueError("Day must be YYYY-MM-DD")
    private_path = "generated/" + day + "/daily-complete.zip"
    data = client.download(PRIVATE_BUCKET, private_path)
    manifest = validate_pack(data)
    validate_references(client.download(
        PRIVATE_BUCKET, "generated/" + day + "/references.json", missing_ok=True),
        sum(len(pack["stickers"]) for pack in manifest["packs"]))
    digest = hashlib.sha256(data).hexdigest()
    slug = "daily-" + day
    filename = slug + ".zip"
    client.upload(PUBLIC_BUCKET, "catalog/packs/" + filename, data, "application/zip")
    current = client.download_public("catalog/packs.json", missing_ok=True)
    catalog = json.loads(current) if current else {"version": 1, "packs": []}
    entry = {"id": slug, "name": "Daily " + day, "creator": "StickerDock AI Factory",
             "rating": "GENERAL", "file": filename, "sha256": digest}
    catalog["packs"] = [item for item in catalog.get("packs", []) if item.get("id") != slug]
    catalog["packs"].insert(0, entry)
    catalog["packs"] = catalog["packs"][:100]
    client.upload(PUBLIC_BUCKET, "catalog/packs.json",
                  json.dumps(catalog, separators=(",", ":")).encode(), "application/json")
    pack_id = str(uuid.uuid5(uuid.NAMESPACE_URL, "stickerdock-production:" + day))
    client.update("sticker_packs", {"status": "approved",
                  "public_path": "catalog/packs/" + filename}, "id", pack_id)
    return {"published": slug, "sha256": digest,
            "catalog_base": public_catalog_base(client.url)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--day", required=True)
    args = parser.parse_args()
    print(json.dumps(publish_day(SupabaseFactory(), args.day), sort_keys=True))
