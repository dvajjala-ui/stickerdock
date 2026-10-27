"""Authenticated HandNavigate candidates; isolated from the generation inbox."""
from __future__ import annotations
import base64
import hashlib
import hmac
import json
import os
import re
import uuid
from urllib.parse import urlsplit
from api._shared import StudioError, decode_image, normalize_upload_image, safe_text
from tools.supabase_factory import PRIVATE_BUCKET, SupabaseFactory


def authorized(headers):
    token = os.environ.get("HANDNAVIGATE_INGEST_TOKEN", "")
    value = headers.get("Authorization", "")
    return len(token) >= 32 and not headers.get("Origin") and hmac.compare_digest(value, "Bearer " + token)


def public_url(value):
    if not isinstance(value, str) or len(value) > 2000:
        raise StudioError("Invalid source URL")
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise StudioError("Source provenance needs an HTTPS URL")
    return value


def dispatch(payload, client=None):
    client = client or SupabaseFactory()
    action = payload.get("action")
    if action == "status":
        client.find("media_intake_items", "id", str(uuid.UUID(int=0)))
        return {"configured": True, "table": "media_intake_items", "generation_eligible": False}
    if action == "get":
        item_id = str(uuid.UUID(str(payload.get("id", ""))))
        row = client.find("media_intake_items", "id", item_id)
        if not row:
            raise StudioError("Intake item not found")
        raw = client.download(PRIVATE_BUCKET, row["storage_path"])
        if hashlib.sha256(raw).hexdigest() != row["stored_sha256"]:
            raise StudioError("Stored candidate checksum mismatch")
        return {"item": row, "image": "data:image/webp;base64," + base64.b64encode(raw).decode()}
    if action != "store":
        raise StudioError("Unknown media intake action")
    collection = str(uuid.UUID(str(payload.get("collection_id", ""))))
    source = safe_text(payload.get("platform"), "platform", 40, required=True)
    if not re.fullmatch(r"[a-z][a-z0-9_-]{1,39}", source):
        raise StudioError("Invalid platform")
    source_url, download_url = public_url(payload.get("source_url")), public_url(payload.get("download_url"))
    original_sha = payload.get("original_sha256", "")
    if not isinstance(original_sha, str) or not re.fullmatch(r"[0-9a-f]{64}", original_sha):
        raise StudioError("Invalid original-image checksum")
    topic = safe_text(payload.get("topic"), "topic", 240, required=True)
    raw, _, _ = decode_image(payload.get("image"))
    normalized = normalize_upload_image(raw)
    digest = hashlib.sha256(normalized).hexdigest()
    # Identity is independent of collection retries. Different claims cannot overwrite provenance.
    item_id = str(uuid.uuid5(uuid.NAMESPACE_URL, "handnavigate:" + original_sha))
    existing = client.find("media_intake_items", "id", item_id)
    if existing:
        if any(existing.get(k) != v for k, v in {"platform": source, "source_url": source_url,
                                               "original_sha256": original_sha, "stored_sha256": digest}.items()):
            raise StudioError("Candidate identity conflicts with existing provenance")
        return {"item": existing, "reused": True}
    path = "handnavigate/candidates/" + digest + ".webp"
    # This path is intentionally outside inbox/: current Factory must not consume unknown-rights media.
    row = {"id": item_id, "collection_id": collection, "platform": source, "topic": topic,
           "source_url": source_url, "download_url": download_url,
           "original_sha256": original_sha, "stored_sha256": digest, "storage_path": path,
           "rights_status": "UNREVIEWED", "generation_eligible": False}
    client.upload(PRIVATE_BUCKET, path, normalized, "image/webp")
    stored = client.download(PRIVATE_BUCKET, path)
    if hashlib.sha256(stored).hexdigest() != digest:
        raise StudioError("Candidate storage read-back failed")
    # Ignore duplicate PK races, then compare the winning row on read-back.
    saved = client._request("POST", "/rest/v1/media_intake_items?on_conflict=id",
        data=json.dumps([row]).encode(), headers={"Content-Type": "application/json",
        "Prefer": "resolution=ignore-duplicates,return=representation"})
    actual = json.loads(saved or b"[]")
    actual = actual[0] if actual else client.find("media_intake_items", "id", item_id)
    if not actual or any(actual.get(k) != row[k] for k in ("platform", "source_url", "original_sha256", "stored_sha256")):
        raise StudioError("Candidate database read-back mismatch")
    return {"item": actual, "reused": False}
