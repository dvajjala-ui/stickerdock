#!/usr/bin/env python3
"""Process one private Creator Studio AI request.

This worker is intentionally manual-only until Supabase/Groq live smoke testing is
complete. It never exposes Groq credentials to the browser and writes generated
results only to the private factory inbox.
"""
from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import uuid

from daily_stickers import groq_ideas, render
from production_worker import inbox_metadata
from supabase_factory import PRIVATE_BUCKET, SupabaseFactory, require_production_environment

SOURCE_RE = re.compile(r"inbox/([0-9a-f]{64})\.(png|jpg|jpeg|webp)")
FONT_PATH = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def safe_request(row):
    if not isinstance(row, dict):
        raise ValueError("Invalid creator request")
    try:
        request_id = str(uuid.UUID(str(row.get("id", ""))))
    except (ValueError, TypeError, AttributeError) as error:
        raise ValueError("Invalid creator request id") from error
    source_path = str(row.get("source_path", ""))
    match = SOURCE_RE.fullmatch(source_path)
    prompt = " ".join(str(row.get("prompt", "")).split())
    if match is None or not 1 <= len(prompt) <= 500:
        raise ValueError("Invalid creator request payload")
    return request_id, source_path, match.group(1), "." + match.group(2), prompt


def process_one(client, worker_id="github-actions", key=None, model=None,
                font_path=FONT_PATH, idea_generator=groq_ideas, renderer=render):
    request = client.claim_creator_request(worker_id)
    if request is None:
        return {"status": "idle"}

    request_id = None
    try:
        request_id, source_path, expected_digest, suffix, prompt = safe_request(request)
        data = client.download(PRIVATE_BUCKET, source_path)
        if not data or len(data) > 20_000_000:
            raise ValueError("Creator source image is missing or too large")
        actual_digest = hashlib.sha256(data).hexdigest()
        if actual_digest != expected_digest:
            raise ValueError("Creator source image hash does not match its path")

        groq_key = (key or os.environ.get("GROQ_API_KEY", "")).strip()
        vision_model = (model or os.environ.get("GROQ_VISION_MODEL", "")).strip()
        if not groq_key or not vision_model:
            raise ValueError("GROQ_API_KEY and GROQ_VISION_MODEL are required")
        if not Path(font_path).is_file():
            raise ValueError("Sticker font is unavailable")

        with tempfile.TemporaryDirectory(prefix="stickerdock-creator-") as directory:
            source = Path(directory) / (actual_digest + suffix)
            source.write_bytes(data)
            idea = idea_generator(groq_key, vision_model, 1, prompt, [], [], image_path=source)[0]
            sticker = renderer(idea, font_path, image_path=source, animated=False)

        if not (12 <= len(sticker) <= 100_000 and sticker.startswith(b"RIFF") and sticker[8:12] == b"WEBP"):
            raise ValueError("Creator result is not a valid static WebP sticker")

        result_digest = hashlib.sha256(sticker).hexdigest()
        result_path = "inbox/" + result_digest + ".webp"
        source_rights = inbox_metadata(client, actual_digest, source_path)
        metadata = {
            **source_rights,
            "source": "creator-request",
            "derived_from": source_path,
            "creator_request_id": request_id,
            "title": prompt[:240],
        }
        client.upload(PRIVATE_BUCKET, result_path, sticker, "image/webp")
        client.upload(
            PRIVATE_BUCKET,
            "source-metadata/" + result_digest + ".json",
            json.dumps(metadata, ensure_ascii=False, separators=(",", ":")).encode(),
            "application/json",
        )
        client.update_creator_request(request_id, {
            "status": "complete",
            "result_path": result_path,
            "error_message": None,
            "completed_at": utc_now(),
        })
        return {"status": "complete", "id": request_id, "result_path": result_path}
    except Exception as error:
        if request_id is None:
            try:
                request_id = str(uuid.UUID(str((request or {}).get("id", ""))))
            except (ValueError, TypeError, AttributeError):
                request_id = None
        if request_id is not None:
            client.update_creator_request(request_id, {
                "status": "failed",
                "error_message": type(error).__name__ + ": request failed; inspect Actions logs",
                "completed_at": utc_now(),
            })
        raise


def main():
    require_production_environment()
    client = SupabaseFactory()
    worker = os.environ.get("GITHUB_RUN_ID", "manual")
    print(json.dumps(process_one(client, "creator-" + worker), separators=(",", ":")))


if __name__ == "__main__":
    main()
