#!/usr/bin/env python3
"""Process one account-owned visual caption job without publishing any media."""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import re
import tempfile
import urllib.parse
import uuid
from datetime import datetime, timedelta, timezone

from PIL import Image

from daily_stickers import groq_ideas
from supabase_factory import PRIVATE_BUCKET, SupabaseFactory, require_production_environment

SOURCE = re.compile(r"users/([0-9a-f-]{36})/ai/sources/([0-9a-f-]{36})-([0-9a-f]{64})\.webp")


def checked_job(row):
    if not isinstance(row, dict):
        raise ValueError("Invalid personal AI job")
    try:
        owner = str(uuid.UUID(str(row.get("owner_id"))))
        request_id = str(uuid.UUID(str(row.get("id"))))
    except (ValueError, TypeError, AttributeError):
        raise ValueError("Invalid personal AI identity") from None
    path = str(row.get("source_path", ""))
    match = SOURCE.fullmatch(path)
    if (match is None or match.group(1) != owner or match.group(2) != request_id or
            match.group(3) != row.get("source_sha") or
            not 1 <= len(str(row.get("prompt", ""))) <= 200):
        raise ValueError("Invalid personal AI source")
    return request_id, owner, path, match.group(3), row["prompt"]


def finish(client, request_id, worker_id, caption=None, error_code=None):
    result = client.rpc("finish_personal_ai_caption", {
        "p_id": request_id, "p_worker_id": worker_id,
        "p_caption": caption, "p_error_code": error_code,
    })
    if len(result) != 1 or result[0].get("updated") not in {True, False}:
        raise ValueError("Invalid personal AI completion response")
    return result[0]["updated"]


def purge_source(client, request_id, path):
    client.remove(PRIVATE_BUCKET, [path])
    client.update("personal_ai_jobs", {"source_purged_at": datetime.now(timezone.utc).isoformat()},
                  "id", request_id)


def cleanup_terminal(client, limit=5):
    """Retry deletion after crashes/cancellation without touching phone media."""
    cutoff = (datetime.now(timezone.utc) - timedelta(minutes=20)).isoformat()
    raw = client._request("GET", "/rest/v1/personal_ai_jobs?source_purged_at=is.null"
                          "&status=in.(complete,failed,cancelled)&updated_at=lt." +
                          urllib.parse.quote(cutoff, safe="") +
                          "&select=id,owner_id,source_sha,source_path,prompt&order=updated_at.asc&limit=" + str(min(limit, 20)))
    rows = json.loads(raw or b"[]")
    if not isinstance(rows, list):
        raise ValueError("Invalid AI cleanup list")
    purged = 0
    for row in rows:
        request_id, _, path, _, _ = checked_job(row)
        purge_source(client, request_id, path)
        purged += 1
    return purged


def process_one(client, worker_id="github-actions", key=None, model=None,
                idea_generator=groq_ideas):
    rows = client.rpc("claim_personal_ai_caption", {"p_worker_id": worker_id})
    if not rows:
        return {"status": "idle"}
    if len(rows) != 1:
        raise ValueError("Invalid personal AI claim response")
    request_id = None
    source_path = None
    provider_started = False
    budget_exhausted = False
    try:
        request_id, _, path, digest, prompt = checked_job(rows[0])
        source_path = path
        data = client.download(PRIVATE_BUCKET, path)
        if not data or len(data) > 3_000_000 or hashlib.sha256(data).hexdigest() != digest:
            raise ValueError("Missing or altered personal AI source")
        with Image.open(io.BytesIO(data)) as image:
            if image.format != "WEBP" or getattr(image, "is_animated", False) or image.width * image.height > 20_000_000:
                raise ValueError("Invalid personal AI image")
            image.verify()
        groq_key = (key or os.environ.get("GROQ_API_KEY", "")).strip()
        vision_model = (model or os.environ.get("GROQ_VISION_MODEL", "")).strip()
        if not groq_key or not vision_model:
            raise ValueError("Personal AI provider is not configured")
        budget = client.rpc("reserve_personal_ai_provider_call", {
            "p_id": request_id, "p_worker_id": worker_id,
        })
        if len(budget) != 1 or budget[0].get("allowed") not in {True, False}:
            raise ValueError("Invalid personal AI provider budget response")
        if not budget[0]["allowed"]:
            budget_exhausted = True
            raise ValueError("Personal AI provider limit reached")
        with tempfile.TemporaryDirectory(prefix="stickerdock-personal-ai-") as directory:
            source = Path(directory) / "chosen.webp"
            source.write_bytes(data)
            provider_started = True
            ideas = idea_generator(groq_key, vision_model, 1, prompt, [], [], image_path=source)
        caption = ideas[0]["caption"]
        if not isinstance(caption, str) or not 1 <= len(caption) <= 64 or not re.fullmatch(r"[ -~]+", caption):
            raise ValueError("Invalid personal AI caption")
        saved = finish(client, request_id, worker_id, caption=caption)
        return {"status": "complete" if saved else "cancelled", "id": request_id}
    except Exception:
        if request_id is not None:
            finish(client, request_id, worker_id,
                   error_code="limit" if budget_exhausted else
                   "provider" if provider_started else "invalid_source")
        raise
    finally:
        if source_path is not None:
            try:
                purge_source(client, request_id, source_path)
            except Exception:
                # Terminal jobs are retained for audit; a separate retention
                # sweep can retry removal without changing the job result.
                pass


def main():
    require_production_environment()
    client = SupabaseFactory()
    worker = "personal-" + os.environ.get("GITHUB_RUN_ID", "manual")[:100]
    old_sources_purged = cleanup_terminal(client)
    result = process_one(client, worker)
    result["old_sources_purged"] = old_sources_purged
    print(json.dumps(result, separators=(",", ":")))


if __name__ == "__main__":
    main()
