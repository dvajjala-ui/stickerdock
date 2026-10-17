"""Opt-in private original-art pack worker. No paid requests by default.

One invocation submits OR polls one image. A durable reservation precedes each
POST: an interrupted/ambiguous submission is never automatically charged again.
No scheduler, Android API credential, publication, or production migration.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid

from PIL import Image, ImageOps
from daily_stickers import atomic_json, export_zip, locked
from reaction_media import encode_static

MODEL = "flux-2-klein-4b"
ENDPOINT = "https://api.bfl.ai/v1/" + MODEL
MAX_IMAGE_BYTES = 12_000_000


def validate_brief(value):
    if not isinstance(value, dict) or value.get("schema") != 1:
        raise ValueError("Original pack brief must use schema 1")
    for key, limit in (("title", 60), ("character", 600), ("style", 600)):
        text = value.get(key)
        if not isinstance(text, str) or not text.strip() or len(text) > limit or any(ord(c) < 32 for c in text):
            raise ValueError("Invalid " + key)
    stickers = value.get("stickers")
    if not isinstance(stickers, list) or not 3 <= len(stickers) <= 30:
        raise ValueError("Plan 3–30 reactions for one static pack")
    ids, captions = set(), set()
    for item in stickers:
        if not isinstance(item, dict) or not isinstance(item.get("id"), str) or not re.fullmatch(r"[a-z0-9-]{1,40}", item["id"]):
            raise ValueError("Use a unique simple reaction id")
        for key, limit in (("caption", 48), ("pose", 600), ("emotion", 40)):
            if not isinstance(item.get(key), str) or not item[key].strip() or len(item[key]) > limit or any(ord(c) < 32 for c in item[key]):
                raise ValueError("Invalid reaction " + key)
        if item["id"] in ids or item["caption"].strip().casefold() in captions:
            raise ValueError("Each reaction needs a different id and caption")
        ids.add(item["id"]); captions.add(item["caption"].strip().casefold())
    # A canonical contract discards irrelevant provider/key/path fields.
    return {"schema": 1, **{key: value[key].strip() for key in ("title", "character", "style")},
            "stickers": [{key: item[key].strip() for key in ("id", "caption", "pose", "emotion")} for item in stickers]}


def prompt_for(brief, item):
    return (f"One original chat sticker, part of the '{brief['title']}' collection. "
            f"Consistent character: {brief['character']}. Visual style: {brief['style']}. "
            f"Emotion: {item['emotion']}. Pose: {item['pose']}. "
            "One isolated large subject, clear silhouette, generous margins, simple plain background. "
            "No grid, no collage, no logos, no watermark, no lettering. Readable at chat size.")


def validate_url(url, delivery=False):
    parsed = urllib.parse.urlsplit(url)
    host = parsed.hostname or ""
    allowed = (host.startswith("delivery.") and host.endswith(".bfl.ai")) if delivery else (host == "api.bfl.ai" or host.endswith(".bfl.ai"))
    if parsed.scheme != "https" or not allowed or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError("Unexpected image provider host")
    if not delivery and parsed.path != "/v1/get_result":
        raise ValueError("Unexpected image provider polling path")
    return url


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None


class BflProvider:
    def __init__(self, key):
        if not key:
            raise ValueError("BFL_API_KEY is not configured. No request was sent.")
        self.key = key

    def request(self, url, payload=None, authenticated=True, limit=1_000_000):
        headers = {"Accept": "application/json", "Content-Type": "application/json"}
        if authenticated:
            headers["x-key"] = self.key
        request = urllib.request.Request(url, data=json.dumps(payload).encode() if payload is not None else None, headers=headers)
        try:
            with urllib.request.build_opener(NoRedirect()).open(request, timeout=45) as response:
                data = response.read(limit + 1)
        except urllib.error.HTTPError as error:
            raise ValueError(f"Image provider HTTP {error.code}; response and credentials omitted") from None
        except OSError:
            raise ValueError("Image provider connection failed; submission outcome may be unknown") from None
        if len(data) > limit:
            raise ValueError("Image provider response exceeds the limit")
        return data

    def submit(self, prompt):
        result = json.loads(self.request(ENDPOINT, {"prompt": prompt, "width": 1024, "height": 1024, "output_format": "png"}))
        request_id = result.get("id")
        if not isinstance(request_id, str) or not request_id or len(request_id) > 200:
            raise ValueError("Image provider returned no valid job id")
        return {"request_id": request_id, "polling_url": validate_url(result["polling_url"])}

    def poll(self, job):
        return json.loads(self.request(validate_url(job["polling_url"])))

    def download(self, url):
        # Signed delivery URLs receive no API credential and are never printed.
        return self.request(validate_url(url, delivery=True), authenticated=False, limit=MAX_IMAGE_BYTES)


def normalize_draft(data):
    if not 12 <= len(data) <= MAX_IMAGE_BYTES:
        raise ValueError("Invalid generated image size")
    with Image.open(io.BytesIO(data)) as source:
        if source.width * source.height > 4_194_304 or min(source.size) < 256 or getattr(source, "is_animated", False):
            raise ValueError("Generated draft must be a bounded static image")
        source.load()
        fitted = ImageOps.contain(source.convert("RGBA"), (448, 448), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (512, 512))
        canvas.alpha_composite(fitted, ((512 - fitted.width) // 2, (512 - fitted.height) // 2))
        return encode_static(canvas)


def advance(brief, directory, provider=None, allow_paid=False, max_submissions=0):
    brief = validate_brief(brief)
    if type(max_submissions) is not int or not 0 <= max_submissions <= 30:
        raise ValueError("max_submissions must be 0–30")
    fingerprint = hashlib.sha256(json.dumps(brief, sort_keys=True).encode()).hexdigest()
    directory = Path(directory)
    with locked(directory):
        state_path = directory / "original-jobs.json"
        state = json.loads(state_path.read_text(encoding="utf-8")) if state_path.exists() else {
            "schema": 1, "brief_sha256": fingerprint, "model": MODEL, "submissions": 0, "jobs": {}}
        if state.get("brief_sha256") != fingerprint or state.get("model") != MODEL:
            raise ValueError("This folder belongs to another brief/model. Use a new output folder.")
        atomic_json(directory / "brief.json", brief)
        for item in brief["stickers"]:
            job = state["jobs"].get(item["id"])
            if job and job["status"] == "ready":
                artifact = directory / (item["id"] + ".webp")
                if not artifact.is_file() or hashlib.sha256(artifact.read_bytes()).hexdigest() != job["sha256"]:
                    raise ValueError("A saved artwork is missing or changed; do not regenerate automatically")
                continue
            if job and job["status"] in ("submitting", "needs_attention", "rejected"):
                return {"status": job["status"], "reaction": item["id"], "submissions": state["submissions"]}
            if job is None:
                if not allow_paid or state["submissions"] >= max_submissions:
                    atomic_json(state_path, state)
                    return {"status": "spending_disabled" if not allow_paid else "submission_limit", "submissions": state["submissions"]}
                if provider is None:
                    raise ValueError("Configure an image provider before enabling paid generation")
                state["submissions"] += 1
                job = state["jobs"][item["id"]] = {"status": "submitting"}
                atomic_json(state_path, state)
                # No automatic POST retry, even on timeout: the service may have charged.
                try:
                    job.update(provider.submit(prompt_for(brief, item)))
                    job["status"] = "waiting"
                except Exception:
                    job["status"] = "needs_attention"
                    atomic_json(state_path, state)
                    raise
                atomic_json(state_path, state)
                return {"status": "waiting", "reaction": item["id"], "submissions": state["submissions"]}
            if provider is None:
                raise ValueError("Configure the same provider to resume the saved job")
            result = provider.poll(job)
            status = result.get("status")
            if status in ("Error", "Failed", "Request Moderated", "Content Moderated", "Task not found"):
                job["status"] = "rejected"
            elif status == "Ready":
                raw = provider.download(result["result"]["sample"])
                webp = normalize_draft(raw)
                digest = hashlib.sha256(webp).hexdigest()
                if any(other.get("sha256") == digest for other in state["jobs"].values()):
                    job["status"] = "needs_attention"
                    atomic_json(state_path, state)
                    return {"status": "duplicate_artwork", "reaction": item["id"], "submissions": state["submissions"]}
                source = directory / (item["id"] + ".source.png")
                source_temporary = source.with_suffix(".tmp")
                source_temporary.write_bytes(raw); source_temporary.replace(source)
                output = directory / (item["id"] + ".webp")
                temporary = output.with_suffix(".tmp")
                temporary.write_bytes(webp); temporary.replace(output)
                job.update(status="ready", sha256=digest, source_sha256=hashlib.sha256(raw).hexdigest())
            elif status not in ("Pending", "Processing"):
                raise ValueError("Unknown image-provider state; no new request sent")
            atomic_json(state_path, state)
            return {"status": job["status"], "reaction": item["id"], "submissions": state["submissions"]}
        records = [{"id": str(uuid.uuid5(uuid.NAMESPACE_URL, fingerprint + item["id"])),
                    "file": str(directory / (item["id"] + ".webp")), "caption": item["caption"],
                    "mood": item["emotion"], "pack_title": brief["title"], "category": "REACTIONS"}
                   for item in brief["stickers"]]
        export_zip(directory / "review-pack.zip", records, fingerprint[:12])
        return {"status": "needs_review", "stickers": len(records), "submissions": state["submissions"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--brief", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--allow-paid", action="store_true")
    parser.add_argument("--max-submissions", type=int, default=0)
    args = parser.parse_args()
    if args.brief.stat().st_size > 100_000:
        raise SystemExit("Brief is too large")
    provider = BflProvider(os.environ.get("BFL_API_KEY")) if args.allow_paid else None
    try:
        result = advance(json.loads(args.brief.read_text(encoding="utf-8")), args.output,
                         provider, args.allow_paid, args.max_submissions)
        print(json.dumps(result))
    except (ValueError, OSError, KeyError):
        # Do not print SDK exceptions, signed delivery URLs or prompt contents.
        raise SystemExit("Original artwork job needs attention. Inspect private local state; no automatic submission retry.") from None


if __name__ == "__main__":
    main()
