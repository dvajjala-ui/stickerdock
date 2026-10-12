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

from supabase_factory import PRIVATE_BUCKET, PUBLIC_BUCKET, SupabaseFactory, public_catalog_base


def validate_pack(data):
    if not 12 <= len(data) <= 75_000_000:
        raise ValueError("Invalid pack size")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        if len(names) > 4000 or "manifest.json" not in names:
            raise ValueError("Invalid factory pack")
        manifest = json.loads(archive.read("manifest.json"))
        if manifest.get("schema") != 3 or not manifest.get("packs"):
            raise ValueError("Unsupported factory pack")
        if any(pack.get("rating") != "GENERAL" for pack in manifest["packs"]):
            raise ValueError("Only reviewed GENERAL packs can be public")
        if any(not 3 <= len(pack.get("stickers", [])) <= 30 for pack in manifest["packs"]):
            raise ValueError("Public pack has an invalid sticker count")
    return manifest


def publish_day(client, day):
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", day):
        raise ValueError("Day must be YYYY-MM-DD")
    private_path = "generated/" + day + "/daily-complete.zip"
    data = client.download(PRIVATE_BUCKET, private_path)
    validate_pack(data)
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
