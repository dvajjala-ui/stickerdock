"""Audit and remove unreferenced legacy Factory inbox images, retaining user originals."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
import re

from tools.reset_source_candidates import connect
from tools.supabase_factory import PRIVATE_BUCKET, SupabaseFactory

IMAGE = re.compile(r"^inbox/([0-9a-f]{64})\.(png|jpg|jpeg|webp)$")


def inventory(conn, client):
    objects = []
    for offset in range(0, 10000, 100):
        page = client.list_objects(PRIVATE_BUCKET, "inbox", 100, offset=offset)
        for entry in page:
            name = str(entry.get("name", ""))
            path = name if name.startswith("inbox/") else "inbox/" + name
            match = IMAGE.fullmatch(path)
            if not match:
                continue
            raw = client.download(PRIVATE_BUCKET, "source-metadata/" + match[1] + ".json", missing_ok=True)
            try:
                metadata = json.loads(raw) if raw else {}
                if not isinstance(metadata, dict):
                    metadata = {"invalid": True}
            except (ValueError, UnicodeError):
                metadata = {"invalid": True}
            objects.append({"path": path, "size": int((entry.get("metadata") or {}).get("size") or 0),
                            "metadata_path": "source-metadata/" + match[1] + ".json" if raw else None,
                            "metadata_sha256": hashlib.sha256(raw or b"").hexdigest(),
                            "source": str(metadata.get("source") or "unknown"),
                            "license": str(metadata.get("license") or "unknown"),
                            "derived_from": str(metadata.get("derived_from") or ""),
                            "invalid_metadata": bool(metadata.get("invalid"))})
        if len(page) < 100:
            break
    with conn.cursor() as cur:
        references = []
        for table in ("admin_packs", "creator_requests", "sticker_packs"):
            cur.execute(f"select row_to_json(t)::text from public.{table} t")
            references.extend(row[0] for row in cur.fetchall())
    text = "\n".join(references)
    protected = {entry["path"] for entry in objects if entry["path"] in text or
                 entry["license"] == "USER_OWNED" or entry["invalid_metadata"]}
    # Preserve originals used by an existing protected result, including drafts.
    changed = True
    while changed:
        before = len(protected)
        protected.update(entry["derived_from"] for entry in objects if entry["path"] in protected and entry["derived_from"])
        changed = len(protected) != before
    for entry in objects:
        entry["protected"] = entry["path"] in protected
    return sorted(objects, key=lambda entry: entry["path"])


def fingerprint(items):
    stable = [(item["path"], item["size"], item["metadata_sha256"], item["protected"])
              for item in items]
    return hashlib.sha256(json.dumps(stable, separators=(",", ":")).encode()).hexdigest()


def run(apply=False, expected_count=None, expected_sha=None):
    client = SupabaseFactory()
    with connect() as conn:
        if apply:
            with conn.cursor() as cur:
                cur.execute("lock table public.admin_packs, public.creator_requests, public.sticker_packs in share row exclusive mode")
        items = inventory(conn, client)
        if len(items) != len({item["path"] for item in items}):
            raise ValueError("Duplicate inbox storage path")
        removable = [item for item in items if not item["protected"]]
        sha = fingerprint(items)
        report = {"inbox_count": len(items), "removable_count": len(removable),
                  "protected_count": len(items) - len(removable),
                  "removable_bytes": sum(item["size"] for item in removable),
                  "sources": dict(Counter(item["source"] for item in removable)),
                  "licenses": dict(Counter(item["license"] for item in removable)),
                  "sha256": sha}
        print(json.dumps(report, sort_keys=True), flush=True)
        if not apply:
            return report
        if expected_count != len(removable) or expected_sha != sha:
            raise ValueError("Legacy inbox count or fingerprint changed; rerun dry audit")
        run_id = os.environ.get("GITHUB_RUN_ID", "")
        if not run_id.isdecimal():
            raise ValueError("Apply must run in GitHub Actions")
        manifest_path = "maintenance/legacy-inbox-reset-" + run_id + ".json"
        manifest = json.dumps({"run_id": run_id, "items": items}, sort_keys=True).encode()
        if client.download(PRIVATE_BUCKET, manifest_path, missing_ok=True) is not None:
            raise ValueError("This reset run has already written a manifest")
        client.upload(PRIVATE_BUCKET, manifest_path, manifest, "application/json")
        if client.download(PRIVATE_BUCKET, manifest_path) != manifest:
            raise RuntimeError("Legacy reset manifest did not verify")
        paths = [item["path"] for item in removable]
        for start in range(0, len(paths), 100):
            client.remove(PRIVATE_BUCKET, paths[start:start + 100])
        metadata_to_keep = {item["metadata_path"] for item in items if item["protected"]}
        metadata_to_remove = sorted({item["metadata_path"] for item in removable
                                     if item["metadata_path"] and item["metadata_path"] not in metadata_to_keep})
        for start in range(0, len(metadata_to_remove), 100):
            client.remove(PRIVATE_BUCKET, metadata_to_remove[start:start + 100])
        remaining = set()
        for offset in range(0, 10000, 100):
            page = client.list_objects(PRIVATE_BUCKET, "inbox", 100, offset=offset)
            remaining.update(name if name.startswith("inbox/") else "inbox/" + name
                             for name in (str(entry.get("name", "")) for entry in page))
            if len(page) < 100:
                break
        if remaining.intersection(paths):
            raise RuntimeError("Some removable inbox images remain; inspect the private manifest")
        if not {item["path"] for item in items if item["protected"]} <= remaining:
            raise RuntimeError("A protected inbox image is missing after reset")
        metadata_remaining = set()
        for offset in range(0, 10000, 100):
            page = client.list_objects(PRIVATE_BUCKET, "source-metadata", 100, offset=offset)
            metadata_remaining.update(name if name.startswith("source-metadata/") else "source-metadata/" + name
                                      for name in (str(entry.get("name", "")) for entry in page))
            if len(page) < 100:
                break
        if metadata_remaining.intersection(metadata_to_remove):
            raise RuntimeError("Some removable source metadata remains")
        print(json.dumps({"removed_images": len(paths), "removed_metadata": len(metadata_to_remove),
                          "freed_image_bytes": report["removable_bytes"], "manifest": manifest_path}), flush=True)
        return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--expected-count", type=int)
    parser.add_argument("--expected-sha")
    args = parser.parse_args()
    run(args.apply, args.expected_count, args.expected_sha)
