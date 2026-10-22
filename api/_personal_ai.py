"""Account-owned, opt-in image-to-caption jobs for the native creators."""
from __future__ import annotations

import hashlib
import json
import os
import uuid
from datetime import datetime, timezone

from api._shared import StudioError, decode_image, normalize_upload_image, safe_text
from tools.supabase_factory import PRIVATE_BUCKET, SupabaseError, SupabaseFactory

PUBLIC_COLUMNS = "id,status,caption,error_code,created_at,updated_at"
DEFAULT_PROMPT = "Suggest one short, funny reaction caption for this image"


class PersonalAiLimit(StudioError):
    pass


class PersonalAiConflict(StudioError):
    pass


def job_id(value):
    try:
        return str(uuid.UUID(str(value)))
    except (TypeError, ValueError, AttributeError):
        raise StudioError("Invalid AI request identity") from None


def source_path(owner, request_id, digest):
    return f"users/{owner}/ai/sources/{request_id}-{digest}.webp"


def read_rows(client, owner, query):
    raw = client._request("GET", "/rest/v1/personal_ai_jobs?owner_id=eq." + owner + "&" + query)
    result = json.loads(raw or b"[]")
    if not isinstance(result, list) or any(not isinstance(row, dict) for row in result):
        raise SupabaseError("Invalid private AI response")
    return result


def dispatch(payload, owner, client=None):
    if not isinstance(payload, dict):
        raise StudioError("Invalid AI request")
    action = payload.get("action")
    if action == "availability":
        if os.environ.get("PERSONAL_AI_ENABLED", "").lower() != "true":
            return {"available": False}
        try:
            (client or SupabaseFactory())._request("GET", "/rest/v1/personal_ai_jobs?select=id&limit=0")
            return {"available": True}
        except (SupabaseError, ValueError):
            return {"available": False}
    if action == "submit" and os.environ.get("PERSONAL_AI_ENABLED", "").lower() != "true":
        raise StudioError("AI captions are not available yet. Keep creating on your phone.")
    client = client or SupabaseFactory()
    if action == "submit":
        if (payload.get("consent") is not True or payload.get("audience") != "GENERAL" or
                payload.get("rights") != "OWNED_OR_LICENSED"):
            raise StudioError("Choose a general-audience image you may use and confirm online AI processing")
        request_id = job_id(payload.get("request_id"))
        prompt = safe_text(payload.get("prompt") or DEFAULT_PROMPT, "AI prompt", 200, required=True)
        original, _, _ = decode_image(payload.get("image"), max_bytes=2_800_000)
        image = normalize_upload_image(original)
        digest = hashlib.sha256(image).hexdigest()
        rows = client.rpc("queue_personal_ai_caption", {
            "p_owner": owner, "p_id": request_id, "p_sha": digest, "p_prompt": prompt,
        })
        if len(rows) != 1 or not isinstance(rows[0], dict):
            raise SupabaseError("Invalid private AI admission response")
        result = rows[0]
        if result.get("accepted") is not True:
            reason = result.get("reason")
            if reason == "identity_conflict":
                raise PersonalAiConflict("This request identity belongs to a different image or prompt")
            if reason in {"daily_limit", "pending_limit"}:
                raise PersonalAiLimit("AI limit reached. Keep creating stickers on your phone and retry later.")
            raise SupabaseError("Unknown private AI admission response")
        status = result["job_status"]
        if status == "uploading":
            path = source_path(owner, request_id, digest)
            client.upload(PRIVATE_BUCKET, path, image, "image/webp")
            activated = client.rpc("activate_personal_ai_caption", {"p_owner": owner, "p_id": request_id})
            if len(activated) != 1 or activated[0].get("job_status") not in {"queued", "cancelled"}:
                raise SupabaseError("Private AI activation failed")
            status = activated[0]["job_status"]
            if status == "cancelled":
                # Cancellation may win while the bounded upload is in flight.
                # A failed removal is retried by the terminal-job sweep.
                try:
                    client.remove(PRIVATE_BUCKET, [path])
                    client.update("personal_ai_jobs", {"source_purged_at": datetime.now(timezone.utc).isoformat()},
                                  "id", request_id)
                except SupabaseError:
                    pass
        return {"id": request_id, "status": status, "replayed": result.get("reason") == "replay"}
    if action == "status":
        request_id = job_id(payload.get("id"))
        rows = read_rows(client, owner, f"id=eq.{request_id}&select={PUBLIC_COLUMNS}&limit=1")
        if not rows:
            raise StudioError("AI request not found")
        return {"job": rows[0]}
    if action == "list":
        rows = read_rows(client, owner, f"select={PUBLIC_COLUMNS}&order=created_at.desc&limit=20")
        return {"jobs": rows}
    if action == "cancel":
        request_id = job_id(payload.get("id"))
        rows = client.rpc("cancel_personal_ai_caption", {"p_owner": owner, "p_id": request_id})
        if not rows:
            raise StudioError("AI request not found")
        if len(rows) != 1 or not isinstance(rows[0], dict):
            raise SupabaseError("Invalid private AI cancellation response")
        return {"id": request_id, "status": rows[0]["job_status"]}
    raise StudioError("Unsupported AI request action")
