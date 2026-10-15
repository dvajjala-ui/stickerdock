"""Account-isolated native cloud library. All media stays in private storage."""
from __future__ import annotations

import base64
import hashlib
import io
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from PIL import Image
from api._shared import StudioError, safe_text
from tools.supabase_factory import PRIVATE_BUCKET, SupabaseFactory

CATEGORIES = {"ALL", "GENERAL", "REACTIONS", "HINGLISH", "MOVIES", "NEWS", "ANIMALS", "LOVE", "WORK", "FESTIVALS", "OTHER"}
HASH = re.compile(r"^[0-9a-f]{64}$")


def identity(value):
    try:
        return str(uuid.UUID(str(value)))
    except (ValueError, TypeError, AttributeError):
        raise StudioError("Invalid pack identity") from None


def now():
    return datetime.now(timezone.utc).isoformat()


def auth_call(path, payload=None, token=None, method=None):
    key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()
    url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    if not key or not url.startswith("https://"):
        raise StudioError("Cloud sign-in is unavailable")
    headers = {"apikey": key, "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url + "/auth/v1/" + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers=headers, method=method or ("POST" if payload is not None else "GET"))
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read(100_000))
    except urllib.error.HTTPError as error:
        # Never return provider details containing identifiers or tokens.
        if error.code == 429:
            raise StudioError("Too many attempts. Please try again later.") from None
        raise StudioError("Sign-in failed. Check your credentials and confirm your email.") from None


def authenticate(headers):
    bearer = str(headers.get("Authorization", ""))
    if not bearer.startswith("Bearer ") or len(bearer) > 8192:
        raise PermissionError("Sign in to your StickerDock account")
    try:
        user = auth_call("user", token=bearer[7:])
        owner = identity(user.get("id"))
    except StudioError:
        raise PermissionError("Session expired. Sign in again.") from None
    if not user.get("email_confirmed_at"):
        raise PermissionError("Confirm your email before syncing")
    return owner


def auth_action(payload):
    action = payload.get("action")
    if action == "refresh":
        refresh = payload.get("refresh_token", "")
        if not isinstance(refresh, str) or not 10 <= len(refresh) <= 4096:
            raise StudioError("Sign in again")
        return auth_call("token?grant_type=refresh_token", {"refresh_token": refresh})
    email = safe_text(payload.get("email"), "email", 254, required=True)
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        raise StudioError("Enter a valid email address")
    if action in {"signup", "login"}:
        password = payload.get("password", "")
        if not isinstance(password, str) or not 10 <= len(password) <= 128:
            raise StudioError("Use a password of 10–128 characters")
        path = "signup" if action == "signup" else "token?grant_type=password"
        result = auth_call(path, {"email": email, "password": password})
        if not result.get("access_token"):
            return {"message": "Check your email to confirm your account, then sign in."}
        return result
    if action == "resend":
        auth_call("resend", {"type": "signup", "email": email})
        return {"message": "If eligible, a confirmation email has been sent."}
    raise StudioError("Unknown sign-in action")


def rows(client, table, query):
    return json.loads(client._request("GET", "/rest/v1/" + table + "?" + query) or b"[]")


def owned(client, owner, pack_id):
    found = rows(client, "cloud_packs", f"owner_id=eq.{owner}&id=eq.{identity(pack_id)}&limit=1")
    if not found:
        raise StudioError("Cloud pack not found")
    return found[0]


def validate_manifest(value):
    if not isinstance(value, dict):
        raise StudioError("Invalid pack metadata")
    kind = value.get("kind")
    if kind not in {"STATIC", "ANIMATED"} or value.get("rating") != "GENERAL":
        raise StudioError("Mature packs stay offline. Choose a general-audience pack.")
    items = value.get("stickers")
    if not isinstance(items, list) or len(items) > 30:
        raise StudioError("A pack holds up to 30 stickers")
    category = str(value.get("category", "ALL")).upper()
    if category not in CATEGORIES or category in {"GENERAL", "OTHER"}:
        category = "ALL"
    result = {"id": identity(value.get("id")),
              "name": safe_text(value.get("name"), "pack name", 60, required=True),
              "kind": kind, "rating": "GENERAL", "category": category, "stickers": []}
    seen = set()
    for item in items:
        if not isinstance(item, dict) or not HASH.fullmatch(str(item.get("sha256", ""))):
            raise StudioError("Invalid sticker checksum")
        digest = item["sha256"]
        if digest in seen:
            raise StudioError("Duplicate sticker checksum")
        seen.add(digest)
        result["stickers"].append({"sha256": digest,
            "label": safe_text(item.get("label"), "label", 80),
            "tags": safe_text(item.get("tags"), "tags", 400)})
    return result


def check_artwork(data, kind):
    if not 12 <= len(data) <= (500_000 if kind == "ANIMATED" else 100_000):
        raise StudioError("Sticker exceeds WhatsApp image limits")
    try:
        with Image.open(io.BytesIO(data)) as image:
            if image.format != "WEBP" or image.size != (512, 512):
                raise StudioError("Use a 512 × 512 WebP sticker")
            animated = bool(getattr(image, "is_animated", False))
            if animated != (kind == "ANIMATED"):
                raise StudioError("Static and animated stickers need separate packs")
            if getattr(image, "n_frames", 1) > 300:
                raise StudioError("Animation has too many frames")
            for frame in range(getattr(image, "n_frames", 1)):
                image.seek(frame)
                image.load()
    except (OSError, ValueError, Image.DecompressionBombError):
        raise StudioError("Invalid sticker image") from None


def asset_path(owner, digest):
    if not HASH.fullmatch(str(digest)):
        raise StudioError("Invalid sticker checksum")
    return f"users/{owner}/{digest}.webp"


def dispatch(payload, owner, client=None):
    client = client or SupabaseFactory()
    budget = client.rpc("cloud_budget", {"p_owner": owner})
    if not budget or not budget[0].get("allowed"):
        raise StudioError("Cloud hourly limit reached. Local stickers are unaffected; retry later.")
    action = payload.get("action")
    if action == "list":
        offset = max(0, min(1000, int(payload.get("offset", 0))))
        packs = rows(client, "cloud_packs", f"owner_id=eq.{owner}&order=updated_at.desc,id.asc&limit=50&offset={offset}")
        submissions = rows(client, "community_submissions", f"owner_id=eq.{owner}&select=pack_id,status,admin_pack_id&limit=1000")
        return {"packs": packs, "submissions": submissions, "next_offset": offset + 50 if len(packs) == 50 else None}
    if action == "upload":
        kind = payload.get("kind")
        if kind not in {"STATIC", "ANIMATED"}:
            raise StudioError("Invalid sticker kind")
        encoded = payload.get("data", "")
        if not isinstance(encoded, str) or len(encoded) > 670_000:
            raise StudioError("Sticker is too large")
        try:
            data = base64.b64decode(encoded, validate=True)
        except ValueError:
            raise StudioError("Invalid image encoding") from None
        digest = hashlib.sha256(data).hexdigest()
        if digest != payload.get("sha256"):
            raise StudioError("Sticker checksum mismatch")
        check_artwork(data, kind)
        reserved = client.rpc("cloud_reserve_asset", {"p_owner": owner, "p_sha": digest, "p_bytes": len(data)})
        if not reserved or not reserved[0].get("reserved"):
            raise StudioError("Private library limit reached (512 MB / 20,000 stickers)")
        client.upload(PRIVATE_BUCKET, asset_path(owner, digest), data, "image/webp")
        client._request("PATCH", f"/rest/v1/cloud_assets?owner_id=eq.{owner}&sha256=eq.{digest}",
                        data=b'{"ready":true}', headers={"Content-Type": "application/json"})
        return {"sha256": digest}
    if action == "download":
        pack = owned(client, owner, payload.get("id"))
        digest = payload.get("sha256")
        if digest not in {s["sha256"] for s in pack["manifest"]["stickers"]}:
            raise StudioError("Sticker is not in your pack")
        data = client.download(PRIVATE_BUCKET, asset_path(owner, digest))
        return {"data": base64.b64encode(data).decode(), "sha256": digest}
    if action == "commit":
        manifest = validate_manifest(payload.get("manifest"))
        fingerprint = hashlib.sha256(json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        query = f"owner_id=eq.{owner}&id=eq.{manifest['id']}&limit=1"
        existing = rows(client, "cloud_packs", query)
        if existing and existing[0]["fingerprint"] == fingerprint:
            return {"pack": existing[0]}
        if existing and int(payload.get("revision", 0)) != existing[0]["revision"]:
            raise StudioError("This pack changed on another device. Restore it before syncing your edits.")
        if not existing and len(rows(client, "cloud_packs", f"owner_id=eq.{owner}&select=id&limit=1000")) >= 1000:
            raise StudioError("Cloud library holds up to 1,000 packs")
        hashes = ",".join(s["sha256"] for s in manifest["stickers"])
        assets = rows(client, "cloud_assets", f"owner_id=eq.{owner}&ready=eq.true&select=sha256&sha256=in.({hashes})&limit=30") if hashes else []
        available = {a["sha256"] for a in assets}
        if any(s["sha256"] not in available for s in manifest["stickers"]):
            raise StudioError("Upload the pack's images before saving metadata")
        revision = existing[0]["revision"] + 1 if existing else 1
        row = {"owner_id": owner, "id": manifest["id"], "name": manifest["name"],
            "category": manifest["category"], "kind": manifest["kind"], "manifest": manifest,
            "fingerprint": fingerprint, "revision": revision, "updated_at": now()}
        if existing:
            changed = json.loads(client._request("PATCH", "/rest/v1/cloud_packs?" + query.replace("&limit=1", "") +
                "&revision=eq." + str(existing[0]["revision"]), data=json.dumps(row).encode(),
                headers={"Content-Type": "application/json", "Prefer": "return=representation"}) or b"[]")
            if not changed:
                raise StudioError("Pack changed during sync. Retry after restoring.")
        else:
            client._request("POST", "/rest/v1/cloud_packs", data=json.dumps(row).encode(),
                            headers={"Content-Type": "application/json"})
        # A shared version is an immutable review snapshot. Edits require resubmission.
        return {"pack": row}
    if action in {"share", "withdraw", "delete"}:
        if action == "delete" and not rows(client, "cloud_packs", f"owner_id=eq.{owner}&id=eq.{identity(payload.get('id'))}&limit=1"):
            return {"status": "deleted"}  # Retry after a lost response is safe.
        pack = owned(client, owner, payload.get("id"))
        submissions = rows(client, "community_submissions", f"owner_id=eq.{owner}&pack_id=eq.{pack['id']}&limit=1")
        previous = submissions[0] if submissions else None
        if action == "share":
            if payload.get("rights_confirmed") is not True or payload.get("adult_confirmed") is not True:
                raise StudioError("Confirm you are 18+ and own redistribution rights for every image")
            if not 3 <= len(pack["manifest"]["stickers"]) <= 30:
                raise StudioError("Public packs need 3–30 stickers")
            if previous and previous["status"] not in {"withdrawn", "rejected"}:
                return {"status": previous["status"]}
            row = {"owner_id": owner, "pack_id": pack["id"], "name": pack["name"],
                "manifest": pack["manifest"], "fingerprint": pack["fingerprint"], "status": "pending",
                "admin_pack_id": None, "rights_statement": "18+; I own or hold redistribution rights for every image; no private personal data; general audience.",
                "updated_at": now()}
            client.upsert("community_submissions", [row], "owner_id,pack_id")
            return {"status": "pending"}
        if previous:
            # Remove from the feed before acknowledging withdrawal, even if a
            # review draft exists. Previously downloaded copies cannot be recalled.
            from api._admin import publish_pack
            from tools.supabase_factory import catalog_lease
            with catalog_lease(client):
                current = client.find("community_submissions", "id", previous["id"])
                if current and current.get("admin_pack_id"):
                    admin = client.find("admin_packs", "id", current["admin_pack_id"])
                    if admin and admin["status"] == "published":
                        publish_pack(client, {"id": admin["id"], "version": admin["version"]}, unpublish=True)
                client.update("community_submissions", {"status": "withdrawn", "updated_at": now()}, "id", previous["id"])
        if action == "delete":
            client._request("DELETE", "/rest/v1/cloud_packs?" + f"owner_id=eq.{owner}&id=eq.{pack['id']}")
        return {"status": "withdrawn" if action == "withdraw" else "deleted"}
    if action == "report":
        catalog_id = safe_text(payload.get("catalog_id"), "catalog pack", 100, required=True)
        reason = safe_text(payload.get("reason"), "report reason", 1000, required=True)
        client.upsert("community_reports", [{"owner_id": owner, "catalog_id": catalog_id, "reason": reason, "status": "open"}], "owner_id,catalog_id")
        return {"reported": True}
    raise StudioError("Unknown cloud action")
