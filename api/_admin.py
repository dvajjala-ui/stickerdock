"""Shared admin operations. No service credential is returned to a browser."""
from __future__ import annotations

import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
import os
import time
import uuid
import zipfile
from PIL import Image, ImageOps

from api import _shared
from tools.publish_factory_pack import manifest_categories, validate_pack, validate_references, publish_preview
from tools.supabase_factory import PRIVATE_BUCKET, PUBLIC_BUCKET, SupabaseFactory, catalog_lease, public_catalog_base
from tools.production_readiness import readiness
from tools import pinterest_connection

CATEGORIES = {"GENERAL", "REACTIONS", "HINGLISH", "MOVIES", "NEWS", "ANIMALS", "LOVE", "WORK", "FESTIVALS", "OTHER"}
CONTENT_CATEGORIES = {"TRENDING", "POLITICS", "CREATIVE", "MOVIES", "SERIES", "INDIA", "SPORTS", "TECHNOLOGY", "EVERYDAY", "ADULT_HUMOR"}


def zip_entry(archive, path, data):
    entry = zipfile.ZipInfo(path, date_time=(1980, 1, 1, 0, 0, 0))
    entry.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(entry, data)


def text(value, name, maximum, required=True):
    return _shared.safe_text(value, name, maximum, required)


def identity(value):
    return _shared.normalize_request_id(value)


def audit(client, action, entity=None, details=None):
    payload = [{"id": str(uuid.uuid4()), "action": action,
                "entity_id": entity, "details": details or {}}]
    client._request("POST", "/rest/v1/admin_events", data=json.dumps(payload).encode(),
                    headers={"Content-Type": "application/json", "Prefer": "return=minimal"})


def current_pack(client, payload):
    row = client.find("admin_packs", "id", identity(payload.get("id")))
    if not row:
        raise ValueError("Pack not found")
    if payload.get("version") is not None and payload["version"] != row["version"]:
        raise ValueError("Pack changed; refresh before continuing")
    return row


def pack_archive(client, row):
    path = row.get("private_path", "")
    if not path.startswith(("admin/packs/", "generated/")):
        raise ValueError("Invalid pack path")
    data = client.download(PRIVATE_BUCKET, path)
    if hashlib.sha256(data).hexdigest() != row["sha256"]:
        raise ValueError("Stored pack checksum mismatch")
    return data, validate_pack(data, public=False)


def read_catalog(client):
    data = client.download(PUBLIC_BUCKET, "catalog/packs.json", missing_ok=True)
    doc = json.loads(data) if data else {"version": 1, "packs": []}
    if doc.get("version") != 1 or not isinstance(doc.get("packs"), list):
        raise ValueError("Invalid existing catalog")
    return doc


def overview(client):
    reads = {
        "packs": lambda: client.list_rows("admin_packs"),
        "factory_packs": lambda: client.list_rows("sticker_packs"),
        "runs": lambda: client.list_rows("factory_runs", "day.desc", 30),
        "requests": lambda: client.list_rows("creator_requests", "created_at.desc", 100),
        "events": lambda: client.list_rows("admin_events", "created_at.desc", 30),
        "briefs": lambda: client.list_rows("content_briefs", "day.desc", 14),
        "settings": lambda: client.find("factory_settings", "id", "default"),
        "catalog": lambda: read_catalog(client),
    }
    with ThreadPoolExecutor(max_workers=4) as pool:
        pending = {key: pool.submit(read) for key, read in reads.items()}
        result = {key: future.result() for key, future in pending.items()}
    for run in result["runs"]:
        recovery = run.get("groq_metrics", {}).get("recovery", {})
        retryable = recovery.get("http_status") == 429 or recovery.get("http_status", 0) >= 500 or recovery.get("kind") in {"invalid_response", "network_error"}
        if retryable and run["status"] == "running":
            retry_at = recovery.get("retry_at", 0)
            run["display_status"] = "waiting" if retry_at > time.time() else "retry_due"
            run["retry_at"] = retry_at
    return {**result, "catalog_base": public_catalog_base(client.url),
            "access_mode": "open-workspace"}


def pack_detail(client, payload):
    row = current_pack(client, payload)
    data, manifest = pack_archive(client, row)
    offset = max(0, min(int(payload.get("offset", 0)), 100))
    details = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for pack in manifest["packs"]:
            for sticker in pack["stickers"]:
                details.append({**sticker, "pack_id": pack["id"], "kind": pack["kind"],
                                "pack_name": pack["name"], "category": pack.get("category", "GENERAL")})
        # Base64 responses remain under Vercel's response limit, even for animation.
        for item in details[offset:offset + 5]:
            asset = archive.read("packs/" + item["pack_id"] + "/" + item["name"])
            item["preview"] = "data:image/webp;base64," + base64.b64encode(asset).decode()
    return {"pack": row, "stickers": details, "offset": offset, "preview_count": 5,
            "categories": manifest_categories(manifest),
            "target_packs": [{"id": pack["id"], "name": pack["name"], "kind": pack["kind"],
                              "count": len(pack["stickers"])} for pack in manifest["packs"]]}


def store_draft(client, data, manifest, references, name, creator, origin_id=None, pack_id=None):
    validate_pack(data, public=False)
    digest = hashlib.sha256(data).hexdigest()
    pack_id = pack_id or str(uuid.uuid4())
    path = f"admin/packs/{pack_id}/{digest}.zip"
    client.upload(PRIVATE_BUCKET, path, data, "application/zip")
    row = {"id": pack_id, "name": name, "creator": creator,
           "rating": "MATURE" if any(p["rating"] == "MATURE" for p in manifest["packs"]) else "GENERAL", "status": "draft",
           "private_path": path, "sha256": digest,
           "sticker_count": sum(len(p["stickers"]) for p in manifest["packs"]),
           "references": references, "origin_id": origin_id}
    client.upsert("admin_packs", [row], "id")
    audit(client, "pack.created", pack_id, {"sha256": digest, "origin_id": origin_id})
    return {"id": pack_id, "migrated": True}


def migrate_factory(client, payload):
    origin_id = identity(payload.get("id"))
    source = client.find("sticker_packs", "id", origin_id)
    if not source:
        raise ValueError("Factory output not found")
    data, manifest = pack_archive(client, source)
    target_id = str(uuid.uuid5(uuid.NAMESPACE_URL, "stickerdock-admin:" + origin_id + ":" + source["sha256"]))
    if client.find("admin_packs", "id", target_id):
        return {"id": target_id, "migrated": False}
    references = client.download(PRIVATE_BUCKET, "generated/" + source["day"] + "/references.json", missing_ok=True)
    count = sum(len(p["stickers"]) for p in manifest["packs"])
    refs = json.loads(references) if references else [{"media_credit": {"license": "REFERENCE_ONLY"}} for _ in range(count)]
    if not isinstance(refs, list) or len(refs) != count:
        raise ValueError("Factory provenance count mismatch")
    return store_draft(client, data, manifest, refs, source["name"], source["creator"], origin_id, target_id)


def normalize_static(data):
    with Image.open(io.BytesIO(data)) as source:
        if getattr(source, "is_animated", False):
            raise ValueError("Use Factory output migration for animated packs")
        image = ImageOps.contain(ImageOps.exif_transpose(source).convert("RGBA"), (512, 512))
        canvas = Image.new("RGBA", (512, 512))
        canvas.alpha_composite(image, ((512 - image.width) // 2, (512 - image.height) // 2))
        for quality in range(95, 14, -10):
            output = io.BytesIO()
            canvas.save(output, "WEBP", quality=quality, method=4)
            if len(output.getvalue()) <= 100_000:
                return output.getvalue()
    raise ValueError("Image cannot fit the sticker size limit")


def create_pack(client, payload):
    name = text(payload.get("name"), "pack name", 60)
    creator = text(payload.get("creator", "StickerDock"), "creator", 60)
    category = payload.get("category", "GENERAL")
    if category not in CATEGORIES:
        raise ValueError("Invalid category")
    sources = payload.get("sources")
    if not isinstance(sources, list) or not 1 <= len(sources) <= 30:
        raise ValueError("Select 1–30 inbox images")
    pack_id, assets, refs, seen = str(uuid.uuid4()), {}, [], set()
    for path in sources:
        if not isinstance(path, str) or not _shared.CREATOR_SOURCE_RE.fullmatch(path):
            raise ValueError("Invalid source path")
        # fetch_inbox performs bounded byte, MIME and decoded-image validation.
        data, _, _ = _shared.decode_image(_shared.fetch_inbox(path), 3_000_000)
        normalized = normalize_static(data)
        digest = hashlib.sha256(normalized).hexdigest()
        if digest in seen:
            continue
        seen.add(digest)
        filename = str(uuid.uuid4()) + ".webp"
        assets[filename] = normalized
        refs.append({"media_credit": _shared.source_metadata(path), "source_path": path})
    stickers = [{"name": filename, "sha256": hashlib.sha256(asset).hexdigest(), "label": "", "tags": ""}
                for filename, asset in assets.items()]
    manifest = {"schema": 5, "packs": [{"id": pack_id, "name": name, "kind": "STATIC",
                "rating": "GENERAL", "category": category, "stickers": stickers}]}
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        zip_entry(archive, "manifest.json", json.dumps(manifest))
        for filename, asset in assets.items():
            zip_entry(archive, "packs/" + pack_id + "/" + filename, asset)
    return store_draft(client, output.getvalue(), manifest, refs, name, creator, pack_id=pack_id)


def save_pack(client, payload):
    row = current_pack(client, payload)
    if row["status"] == "published":
        raise ValueError("Unpublish the pack before editing")
    data, manifest = pack_archive(client, row)
    name = text(payload.get("name"), "pack name", 60)
    creator = text(payload.get("creator"), "creator", 60)
    category = payload.get("category")
    if category is not None and category not in CATEGORIES:
        raise ValueError("Invalid category")
    edits = payload.get("stickers")
    if not isinstance(edits, list):
        raise ValueError("Missing sticker edits")
    edit_map = {}
    for item in edits:
        if not isinstance(item, dict) or item.get("name") in edit_map:
            raise ValueError("Invalid or duplicate sticker edit")
        edit_map[item.get("name")] = item
    all_names = {item["name"] for pack in manifest["packs"] for item in pack["stickers"]}
    if not set(edit_map).issubset(all_names):
        raise ValueError("Unknown sticker edit")
    output, references, index = io.BytesIO(), [], 0
    with zipfile.ZipFile(io.BytesIO(data)) as old, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as new:
        surviving_packs = []
        for pack in manifest["packs"]:
            surviving = []
            for item in pack["stickers"]:
                edit = edit_map.get(item["name"], {})
                reference = row["references"][index] if index < len(row["references"]) else {"media_credit": {"license": "REFERENCE_ONLY"}}
                index += 1
                if edit.get("remove") is True:
                    continue
                item["label"] = text(edit.get("label", item.get("label", "")), "label", 80, False)
                item["tags"] = text(edit.get("tags", item.get("tags", "")), "tags", 400, False)
                surviving.append(item)
                references.append(reference)
                path = "packs/" + pack["id"] + "/" + item["name"]
                zip_entry(new, path, old.read(path))
            if surviving:
                pack.update(name=name, stickers=surviving, category=category or pack.get("category", "GENERAL"))
                surviving_packs.append(pack)
        if not surviving_packs:
            raise ValueError("A pack must keep at least one sticker; archive it instead")
        manifest.update(schema=5, packs=surviving_packs)
        zip_entry(new, "manifest.json", json.dumps(manifest))
    revised = output.getvalue()
    validate_pack(revised, public=False)
    digest = hashlib.sha256(revised).hexdigest()
    path = f"admin/packs/{row['id']}/{digest}.zip"
    client.upload(PRIVATE_BUCKET, path, revised, "application/zip")
    client.update_versioned("admin_packs", {"name": name, "creator": creator, "status": "draft",
        "private_path": path, "sha256": digest, "references": references,
        "sticker_count": len(references), "version": row["version"] + 1}, row["id"], row["version"])
    audit(client, "pack.edited", row["id"], {"old_sha256": row["sha256"], "sha256": digest})
    return {"saved": True}


def add_pack_sources(client, payload):
    row = current_pack(client, payload)
    if row["status"] == "published":
        raise ValueError("Unpublish the pack before editing")
    data, manifest = pack_archive(client, row)
    target = next((p for p in manifest["packs"] if p["id"] == payload.get("target_pack_id")), None)
    if target is None or target["kind"] != "STATIC":
        raise ValueError("Choose a static destination pack")
    sources = payload.get("sources")
    if not isinstance(sources, list) or not 1 <= len(sources) <= 30:
        raise ValueError("Select 1–30 inbox images")
    additions, seen = [], {item["sha256"] for item in target["stickers"]}
    for path in sources:
        if not isinstance(path, str) or not _shared.CREATOR_SOURCE_RE.fullmatch(path):
            raise ValueError("Invalid source path")
        raw, _, _ = _shared.decode_image(_shared.fetch_inbox(path), 3_000_000)
        normalized = normalize_static(raw)
        digest = hashlib.sha256(normalized).hexdigest()
        if digest in seen:
            continue
        seen.add(digest)
        additions.append(({"name": str(uuid.uuid4()) + ".webp", "sha256": digest, "label": "", "tags": ""},
                          normalized, {"media_credit": _shared.source_metadata(path), "source_path": path}))
    if not additions:
        raise ValueError("All selected images are already in this pack")
    if len(target["stickers"]) + len(additions) > 30 or row["sticker_count"] + len(additions) > 100:
        raise ValueError("Destination packs hold at most 30 stickers and an archive at most 100; choose fewer images")
    output, references, index = io.BytesIO(), [], 0
    with zipfile.ZipFile(io.BytesIO(data)) as old, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as new:
        for pack in manifest["packs"]:
            pack.setdefault("category", "GENERAL")
            for item in pack["stickers"]:
                path = "packs/" + pack["id"] + "/" + item["name"]
                zip_entry(new, path, old.read(path))
                references.append(row["references"][index] if index < len(row["references"]) else
                                  {"media_credit": {"license": "REFERENCE_ONLY"}})
                index += 1
            if pack is target:
                for item, asset, reference in additions:
                    zip_entry(new, "packs/" + pack["id"] + "/" + item["name"], asset)
                    references.append(reference)
                pack["stickers"].extend(item for item, _, _ in additions)
        manifest["schema"] = 5
        zip_entry(new, "manifest.json", json.dumps(manifest))
    revised = output.getvalue()
    validate_pack(revised, public=False)
    digest = hashlib.sha256(revised).hexdigest()
    path = f"admin/packs/{row['id']}/{digest}.zip"
    client.upload(PRIVATE_BUCKET, path, revised, "application/zip")
    client.update_versioned("admin_packs", {"private_path": path, "sha256": digest, "references": references,
        "sticker_count": len(references), "status": "draft", "version": row["version"] + 1}, row["id"], row["version"])
    audit(client, "pack.stickers_added", row["id"], {"added": len(additions), "sha256": digest})
    return {"added": len(additions)}


def pack_status(client, payload):
    row = current_pack(client, payload)
    target = payload.get("status")
    if target not in {"draft", "reviewed", "rejected", "archived"}:
        raise ValueError("Invalid status")
    if row["status"] == "published":
        raise ValueError("Unpublish before changing status")
    if target == "reviewed":
        data, _ = pack_archive(client, row)
        manifest = validate_pack(data)
        if row["rating"] != "GENERAL":
            raise ValueError("Mature packs remain private")
        validate_references(json.dumps(row["references"]).encode(), row["sticker_count"])
    client.update_versioned("admin_packs", {"status": target, "version": row["version"] + 1}, row["id"], row["version"])
    audit(client, "pack." + target, row["id"])
    return {"status": target}


def publish_pack(client, payload, unpublish=False):
    row = current_pack(client, payload)
    slug = "pack-" + row["id"]
    catalog = read_catalog(client)
    catalog["packs"] = [p for p in catalog["packs"] if p.get("id") != slug]
    if not unpublish:
        from api._cloud import rows
        community = any(r.get("source") == "user-submission" for r in row.get("references", []))
        submissions = rows(client, "community_submissions", "admin_pack_id=eq." + row["id"] + "&limit=1") if community else []
        if community and (not submissions or submissions[0]["status"] != "draft"):
            raise ValueError("The creator withdrew sharing permission")
        if row["status"] not in {"reviewed", "published"} or row["rating"] != "GENERAL":
            raise ValueError("Review a GENERAL pack before publishing")
        data, _ = pack_archive(client, row)
        manifest = validate_pack(data)
        validate_references(json.dumps(row["references"]).encode(), row["sticker_count"])
        filename = row["id"] + "-" + row["sha256"][:16] + ".zip"
        client.upload(PUBLIC_BUCKET, "catalog/packs/" + filename, data, "application/zip")
        catalog["packs"].insert(0, {"id": slug, "name": row["name"], "creator": row["creator"],
                                 "rating": "GENERAL", "file": filename, "sha256": row["sha256"],
                                 "categories": manifest_categories(manifest),
                                 **publish_preview(client, data, manifest)})
        if community:
            catalog["packs"][0]["publisher_id"] = submissions[0]["owner_id"]
        if len(catalog["packs"]) > 100:
            raise ValueError("Catalog is full; unpublish a pack first")
    audit(client, "catalog.unpublish.intent" if unpublish else "catalog.publish.intent", row["id"], {"sha256": row["sha256"]})
    client.upload(PUBLIC_BUCKET, "catalog/packs.json", json.dumps(catalog, separators=(",", ":")).encode(), "application/json")
    # Read back with the authenticated client to avoid a stale public CDN response.
    verified = json.loads(client.download(PUBLIC_BUCKET, "catalog/packs.json"))
    if verified != catalog:
        raise ValueError("Catalog read-back mismatch; refresh before retrying")
    status = "draft" if unpublish else "published"
    client.update_versioned("admin_packs", {"status": status, "version": row["version"] + 1}, row["id"], row["version"])
    audit(client, "catalog.unpublished" if unpublish else "catalog.published", row["id"])
    return {"status": status, "catalog_base": public_catalog_base(client.url)}


def save_settings(client, payload):
    if not isinstance(payload.get("enabled"), bool):
        raise ValueError("Enabled must be true or false")
    target, batch = payload.get("daily_target"), payload.get("batch_size")
    if type(target) is not int or not 3 <= target <= 100 or type(batch) is not int or not 3 <= batch <= 10:
        raise ValueError("Use a daily target of 3–100 and batch size of 3–10")
    if target > batch * 8:
        raise ValueError("The three-hour schedule runs eight batches daily; use a target no larger than eight times the batch size")
    topics = payload.get("topics", [])
    if not isinstance(topics, list) or len(topics) > 20:
        raise ValueError("Use up to 20 topics")
    if not isinstance(payload.get("discovery_enabled", True), bool) or not isinstance(payload.get("auto_migrate_completed", True), bool):
        raise ValueError("Discovery and migration settings must be true or false")
    rating = payload.get("rating", "GENERAL")
    if rating not in {"GENERAL", "MATURE"}:
        raise ValueError("Invalid Factory audience")
    categories = payload.get("content_categories", ["TRENDING", "CREATIVE", "MOVIES", "SERIES", "INDIA", "TECHNOLOGY", "EVERYDAY"])
    if (not isinstance(categories, list) or not 1 <= len(categories) <= 10 or
            len(categories) != len(set(categories)) or any(item not in CONTENT_CATEGORIES for item in categories)):
        raise ValueError("Choose 1–10 unique content categories")
    if "ADULT_HUMOR" in categories and rating != "MATURE":
        raise ValueError("Adult humor requires a private MATURE Factory run")
    values = {"id": "default", "enabled": payload["enabled"], "daily_target": target, "batch_size": batch,
              "topics": [text(topic, "topic", 240) for topic in topics],
              "discovery_enabled": payload.get("discovery_enabled", True),
              "auto_migrate_completed": payload.get("auto_migrate_completed", True),
              "rating": rating, "content_categories": categories}
    audit(client, "factory.settings", details=values)
    client.upsert("factory_settings", [values], "id")
    return {"saved": True}


def queue_action(client, payload):
    request_id = identity(payload.get("id"))
    action = payload.get("operation")
    if action not in {"cancel", "retry"}:
        raise ValueError("Invalid queue action")
    # Conditional mutation avoids overwriting a worker claim or completion.
    status_filter = "queued" if action == "cancel" else "in.(failed,cancelled)"
    values = {"status": "cancelled"} if action == "cancel" else {
        "status": "queued", "attempts": 0, "error_message": None,
        "started_at": None, "completed_at": None, "worker_id": None, "result_path": None}
    if action == "retry" and _shared.creator_outstanding_count() >= _shared.CREATOR_MAX_OUTSTANDING:
        raise ValueError("Creator queue is full")
    rows = json.loads(client._request("PATCH", f"/rest/v1/creator_requests?id=eq.{request_id}&status={status_filter}",
        data=json.dumps(values).encode(), headers={"Content-Type": "application/json", "Prefer": "return=representation"}) or b"[]")
    if not rows:
        raise ValueError("Request changed or is running; refresh before continuing")
    audit(client, "request." + action, request_id)
    return {"status": values["status"]}


def signed_download(client, payload):
    row = current_pack(client, payload)
    # Authenticate and checksum-validate before issuing access to a stored archive.
    pack_archive(client, row)
    path = client._object_path(PRIVATE_BUCKET, row["private_path"])
    raw = client._request("POST", "/storage/v1/object/sign/" + path, data=b'{"expiresIn":300}',
                          headers={"Content-Type": "application/json"})
    signed = json.loads(raw).get("signedURL", "")
    if not signed.startswith("/object/sign/"):
        raise ValueError("Invalid signed download response")
    return {"url": client.url + "/storage/v1" + signed}


def dispatch(payload, client=None):
    if payload.get("action") == "health":
        return readiness(client)
    client = client or SupabaseFactory()
    action = payload.get("action")
    if action in {"community_list", "community_detail", "community_draft", "community_reject", "report_resolve"}:
        from api._cloud_admin import dispatch as community_dispatch
        return community_dispatch(client, payload)
    if action == "pinterest_status":
        return pinterest_connection.connection_status(client, check=payload.get("check") is True)
    if action == "pinterest_browse":
        return pinterest_connection.browse(client, payload)
    if action == "pinterest_disconnect":
        with catalog_lease(client):
            client.upload(PRIVATE_BUCKET, pinterest_connection.CONNECTION_PATH, b'{"disconnected":true}', "application/json")
        audit(client, "pinterest.disconnected")
        return {"disconnected": True}
    if action == "overview":
        return overview(client)
    if action == "pack_detail":
        return pack_detail(client, payload)
    if action == "pack_download":
        return signed_download(client, payload)
    if action == "queue_action":
        return queue_action(client, payload)
    if action == "settings_save":
        return save_settings(client, payload)
    if action == "source_upload":
        result = _shared.upload_user_image(payload.get("image"), payload.get("title", "Admin upload"),
                                           rights=payload.get("rights", "REFERENCE_ONLY"))
        audit(client, "source.uploaded", result["path"])
        return result
    if action == "source_info":
        metadata = _shared.source_metadata(payload.get("source_path"))
        return {"metadata": metadata, "ai_allowed": not str(metadata.get("source", "")).startswith("pinterest")}
    if action == "queue_create":
        result = _shared.queue_creator_request(payload.get("prompt"), payload.get("source_path"),
                                                payload.get("request_id"))
        audit(client, "request.queued", result["id"])
        return result
    operations = {"migrate_factory": migrate_factory, "pack_create": create_pack,
                  "pack_save": save_pack, "pack_add_sources": add_pack_sources,
                  "pack_status": pack_status, "publish": publish_pack}
    if action == "unpublish":
        with catalog_lease(client):
            return publish_pack(client, payload, unpublish=True)
    if action in operations:
        with catalog_lease(client):
            return operations[action](client, payload)
    raise ValueError("Unknown admin action")
