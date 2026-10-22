#!/usr/bin/env python3
"""Persistent scheduled StickerDock worker for GitHub Actions + Supabase."""
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import tempfile
import uuid
import zipfile
from PIL import Image

from daily_stickers import atomic_json, export_zip, run as run_generator, ProviderError, ProviderResponseError
from sticker_fonts import default_font
from media_library import sync as sync_media
from supabase_factory import PRIVATE_BUCKET, SupabaseFactory, SupabaseError, require_production_environment
from trend_intelligence import build_brief, DEFAULT_CATEGORIES
from video_stickers import CLIP_EXTENSIONS

STATE_OBJECT = "state/runtime.zip"
SOURCE_METADATA_PREFIX = "source-metadata/"
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"}
INBOX_EXTENSIONS = IMAGE_EXTENSIONS | CLIP_EXTENSIONS
SOURCE_LICENSES = {"CC0", "PUBLIC_DOMAIN", "USER_OWNED", "REFERENCE_ONLY"}


def content_settings(config, controls):
    return {"rating": controls.get("rating", config.get("rating", "GENERAL")),
            "content_categories": (controls.get("content_categories") or
                                   config.get("content_categories") or DEFAULT_CATEGORIES),
            "topics": controls.get("topics") or config.get("topics", []),
            "feeds": config.get("feeds", []),
            "discovery_enabled": controls.get("discovery_enabled", True)}


def ensure_content_brief(client, config, controls, now):
    """Create at most one brief per UTC day and settings revision."""
    day = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
    settings = content_settings(config, controls)
    signature = hashlib.sha256(json.dumps(settings, sort_keys=True,
        separators=(",", ":")).encode()).hexdigest()
    existing = client.find("content_briefs", "day", day)
    if existing and existing.get("settings_hash") == signature:
        brief = {key: existing.get(key) for key in (
            "day", "status", "source_mode", "model", "rating", "categories",
            "custom_topics", "items", "topic_plans", "references", "warnings")}
    else:
        brief = build_brief(settings, now)
        row = {**brief, "settings_hash": signature,
               "item_count": len(brief["items"])}
        client.upsert("content_briefs", [row], "day")
        client.upload(PRIVATE_BUCKET, "content-briefs/" + day + ".json",
                      json.dumps(brief, ensure_ascii=False, separators=(",", ":")).encode(),
                      "application/json")
    configured = dict(config)
    configured["topic_plans"] = brief["topic_plans"]
    configured["references"] = brief["references"]
    configured["rating"] = brief["rating"]
    return configured, brief


def auto_migrate_completed(client, generated, summary):
    """Copy a completed immutable Factory archive into the editable admin inbox."""
    if summary.get("status") != "complete":
        return None
    day = summary["day"]
    source_id = str(uuid.uuid5(uuid.NAMESPACE_URL, "stickerdock-production:" + day))
    source = client.find("sticker_packs", "id", source_id)
    if not source:
        raise ValueError("Completed Factory pack metadata is missing")
    target_id = str(uuid.uuid5(uuid.NAMESPACE_URL,
        "stickerdock-admin:" + source_id + ":" + source["sha256"]))
    if client.find("admin_packs", "id", target_id):
        return target_id
    archive = (generated / day / "daily-complete.zip").read_bytes()
    if hashlib.sha256(archive).hexdigest() != source["sha256"]:
        raise ValueError("Completed Factory archive checksum mismatch")
    references_path = generated / day / "references.json"
    references = json.loads(references_path.read_text()) if references_path.exists() else []
    if len(references) != source["sticker_count"]:
        raise ValueError("Completed Factory provenance count mismatch")
    private_path = f"admin/packs/{target_id}/{source['sha256']}.zip"
    client.upload(PRIVATE_BUCKET, private_path, archive, "application/zip")
    client.upsert("admin_packs", [{"id": target_id, "name": source["name"],
        "creator": source["creator"], "rating": source["rating"], "status": "draft",
        "private_path": private_path, "sha256": source["sha256"],
        "sticker_count": source["sticker_count"], "references": references,
        "origin_id": source_id}], "id")
    return target_id


def batch_references(records):
    """Keep the same per-sticker provenance as the daily archive."""
    return [{"caption": record.get("caption", ""), "source": record.get("source"),
             "sources": record.get("sources", []), "mode": record.get("mode"),
             "image_path": record.get("image_path"), "topic": record.get("topic"),
             "category": record.get("category", "GENERAL"),
             "reference_mode": record.get("reference_mode"),
             "media_credit": record.get("media_credit")}
            for record in records]


def migrate_ready_batches(client, generated, day, batch_size):
    """Create private review drafts as soon as rendered batches are committed."""
    directory = generated / day
    state_path = directory / "state.json"
    if not state_path.exists():
        return []
    state = json.loads(state_path.read_text())
    records = state.get("records", [])
    ranges = state.get("batches") or [
        {"start": start, "end": min(start + batch_size, len(records))}
        for start in range(0, len(records), batch_size)]
    expected_start = 0
    for item in ranges:
        start, end = item["start"], item["end"]
        if not (type(start) is int and type(end) is int and start == expected_start and start < end <= len(records)):
            raise ValueError("Invalid saved Factory batch range")
        expected_start = end
    if expected_start != len(records):
        raise ValueError("Saved Factory batches do not cover all rendered stickers")
    drafted = []
    for item in ranges:
        start, end = item["start"], item["end"]
        batch = records[start:end]
        target_id = str(uuid.uuid5(uuid.NAMESPACE_URL,
            f"stickerdock-admin-batch:{day}:{start}:{end}"))
        # A reviewer may have renamed, edited, published or rejected the draft.
        # Replaying the worker must never overwrite that decision.
        if client.find("admin_packs", "id", target_id):
            drafted.append(target_id)
            continue
        if any(not Path(record.get("file", "")).is_file() for record in batch):
            # Older pruned state can lack media. Keep its run count truthful;
            # never create a broken draft or regenerate by spending AI budget.
            continue
        archive_path = directory / f"batch-{start + 1:02d}-{end:02d}.zip"
        export_zip(archive_path, batch, day)
        archive = archive_path.read_bytes()
        from publish_factory_pack import validate_pack
        manifest = validate_pack(archive, public=False)
        references = batch_references(batch)
        if sum(len(pack["stickers"]) for pack in manifest["packs"]) != len(references):
            raise ValueError("Factory batch provenance count mismatch")
        digest = hashlib.sha256(archive).hexdigest()
        remote = f"admin/packs/{target_id}/{digest}.zip"
        client.upload(PRIVATE_BUCKET, remote, archive, "application/zip")
        title = manifest["packs"][0]["name"]
        name = (title if len(manifest["packs"]) == 1 else f"Daily {day} · batch {start + 1}-{end}")[:60]
        client.upsert("admin_packs", [{"id": target_id, "name": name,
            "creator": "StickerDock AI Factory",
            "rating": "MATURE" if any(pack["rating"] == "MATURE" for pack in manifest["packs"]) else "GENERAL",
            "status": "draft", "private_path": remote, "sha256": digest,
            "sticker_count": len(references), "references": references}], "id")
        drafted.append(target_id)
    return drafted


def safe_restore(data, destination):
    if data is None:
        return
    if len(data) > 75_000_000:
        raise ValueError("Remote state exceeds 75MB")
    with zipfile.ZipFile(__import__("io").BytesIO(data)) as archive:
        total = 0
        for info in archive.infolist():
            path = PurePosixPath(info.filename)
            if path.is_absolute() or ".." in path.parts or info.is_dir():
                raise ValueError("Unsafe factory state archive")
            total += info.file_size
            if total > 150_000_000 or info.file_size > 25_000_000:
                raise ValueError("Factory state archive is too large")
        archive.extractall(destination)


def state_archive(runtime):
    output = __import__("io").BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(runtime.rglob("*")):
            if (path.is_file() and not path.name.endswith(".lock") and
                    not ("generated-stickers" in path.relative_to(runtime).parts and path.suffix == ".zip")):
                archive.write(path, path.relative_to(runtime).as_posix())
    data = output.getvalue()
    if len(data) > 75_000_000:
        raise ValueError("Persistent factory state exceeds 75MB")
    return data


def prune_old_days(generated, now, keep_days=8):
    today = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
    oldest = (datetime.fromtimestamp(now, timezone.utc).date() - timedelta(days=keep_days)).isoformat()
    for path in generated.iterdir() if generated.exists() else []:
        if path.is_dir() and re.fullmatch(r"\d{4}-\d{2}-\d{2}", path.name) and path.name < oldest:
            shutil.rmtree(path)
        elif path.is_dir() and re.fullmatch(r"\d{4}-\d{2}-\d{2}", path.name) and path.name < today:
            for asset in path.iterdir():
                if asset.suffix in (".webp", ".zip"):
                    asset.unlink()


def relocate_state(runtime):
    """Remote state resumes under a new temporary runner directory each time."""
    generated = runtime / "generated-stickers"
    for state_path in generated.glob("*/state.json"):
        state = json.loads(state_path.read_text())
        for record in state.get("records", []):
            record["file"] = str(state_path.parent / Path(record["file"]).name)
            if record.get("image_path"):
                record["image_path"] = str(runtime / "reference-images" / Path(record["image_path"]).name)
        pending = state.get("pending")
        if pending and pending.get("image_path"):
            pending["image_path"] = str(runtime / "reference-images" / Path(pending["image_path"]).name)
        if pending and pending.get("image_paths"):
            pending["image_paths"] = [str(runtime / "reference-images" / Path(path).name)
                                      for path in pending["image_paths"]]
        atomic_json(state_path, state)


def trim_reference_cache(runtime, max_bytes=20_000_000):
    references = runtime / "reference-images"
    if not references.exists():
        return
    protected = set()
    for state_path in (runtime / "generated-stickers").glob("*/state.json"):
        pending = json.loads(state_path.read_text()).get("pending")
        if pending and pending.get("image_path"):
            protected.add(Path(pending["image_path"]).name)
        if pending:
            protected.update(Path(path).name for path in pending.get("image_paths", []))
    files = sorted((path for path in references.iterdir()
                    if path.is_file() and path.suffix.lower() in INBOX_EXTENSIONS),
                   key=lambda path: path.stat().st_mtime)
    total = sum(path.stat().st_size for path in files)
    for path in files:
        if total <= max_bytes:
            break
        if path.name not in protected:
            total -= path.stat().st_size
            path.unlink()
    manifest_path = references / "library.json"
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text())
        manifest["assets"] = [asset for asset in manifest.get("assets", [])
                              if (references / asset["file"]).is_file()]
        atomic_json(manifest_path, manifest)


def inbox_objects(client):
    for offset in range(0, 10000, 100):
        page = client.list_objects(PRIVATE_BUCKET, "inbox", 100, offset=offset)
        yield from page
        if len(page) < 100:
            break


def mirror_licensed_inputs(client, runtime):
    references = runtime / "reference-images"
    manifest_path = references / "library.json"
    if not manifest_path.exists():
        return
    manifest = json.loads(manifest_path.read_text())
    for asset in manifest.get("assets", []):
        if asset.get("license") not in ("CC0", "PUBLIC_DOMAIN") or asset.get("inbox_path"):
            continue
        path = references / Path(asset["file"]).name
        if not path.is_file() or path.suffix.lower() != ".png":
            continue
        data = path.read_bytes()
        if len(data) > 20_000_000:
            continue
        digest = hashlib.sha256(data).hexdigest()
        remote_path = "inbox/" + digest + ".png"
        client.upload(PRIVATE_BUCKET, SOURCE_METADATA_PREFIX + digest + ".json",
                      json.dumps({**asset, "source": "licensed-library"}).encode(), "application/json")
        client.upload(PRIVATE_BUCKET, remote_path, data, "image/png")
        asset["inbox_path"] = remote_path
        asset["content_sha256"] = digest
    atomic_json(manifest_path, manifest)


def inbox_metadata(client, digest, remote_path):
    data = client.download(PRIVATE_BUCKET, SOURCE_METADATA_PREFIX + digest + ".json",
                           missing_ok=True)
    if data is None:
        return {"source_url": "supabase://" + remote_path,
                "download_url": "supabase://" + remote_path,
                "license": "USER_OWNED", "title": Path(remote_path).name[:240],
                "credit": "Factory owner upload", "source": "manual-upload"}
    if len(data) > 16_000:
        raise ValueError("Source metadata is too large")
    metadata = json.loads(data)
    if not isinstance(metadata, dict) or metadata.get("license") not in SOURCE_LICENSES:
        raise ValueError("Invalid source metadata")
    result = {"source_url": str(metadata.get("source_url", ""))[:1000],
              "download_url": str(metadata.get("download_url", ""))[:1000],
              "license": metadata["license"],
              "title": str(metadata.get("title", Path(remote_path).name))[:240],
              "credit": str(metadata.get("credit", ""))[:400],
              "source": str(metadata.get("source", "uploaded-reference"))[:80]}
    if not result["source_url"]:
        result["source_url"] = "supabase://" + remote_path
    if not result["download_url"]:
        result["download_url"] = "supabase://" + remote_path
    return result


def sync_inbox(client, runtime, max_files=30):
    references = runtime / "reference-images"
    references.mkdir(parents=True, exist_ok=True)
    state_path = runtime / "inbox-state.json"
    state = json.loads(state_path.read_text()) if state_path.exists() else {"processed": []}
    processed = set(state.get("processed", []))
    manifest_path = references / "library.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {"assets": []}
    known_hashes = {digest for item in manifest.get("assets", [])
                    for digest in (item.get("sha256"), item.get("content_sha256")) if digest}
    added = 0
    considered = 0
    for item in inbox_objects(client):
        name = str(item.get("name", ""))
        remote_path = name if name.startswith("inbox/") else "inbox/" + name
        suffix = Path(remote_path).suffix.lower()
        if remote_path in processed or suffix not in INBOX_EXTENSIONS:
            continue
        if considered >= max_files:
            break
        considered += 1
        size = int((item.get("metadata") or {}).get("size") or 0)
        if size > 20_000_000:
            processed.add(remote_path)
            continue
        data = client.download(PRIVATE_BUCKET, remote_path)
        if not data or len(data) > 20_000_000:
            processed.add(remote_path)
            continue
        digest = hashlib.sha256(data).hexdigest()
        local = references / (digest + suffix)
        if digest not in known_hashes:
            try:
                metadata = inbox_metadata(client, digest, remote_path)
                if str(metadata.get("source", "")).startswith("pinterest"):
                    raise ValueError("Pinterest content is not a Factory input")
                local.write_bytes(data)
                if suffix in CLIP_EXTENSIONS:
                    from video_stickers import clip_frames
                    clip_frames(local)
                else:
                    with Image.open(local) as image:
                        if image.width * image.height > 20_000_000:
                            raise ValueError("Input image exceeds pixel limit")
                        image.verify()
                    with Image.open(local) as image:
                        image.load()
            except (OSError, ValueError, Image.DecompressionBombError):
                local.unlink(missing_ok=True)
                processed.add(remote_path)
                state["processed"] = sorted(processed)[-5000:]
                atomic_json(state_path, state)
                continue
            manifest.setdefault("assets", []).append({"file": local.name, "sha256": digest,
                                                       **metadata})
            known_hashes.add(digest)
            added += 1
        processed.add(remote_path)
        state["processed"] = sorted(processed)[-5000:]
        atomic_json(manifest_path, manifest)
        atomic_json(state_path, state)
    state["processed"] = sorted(processed)[-5000:]
    atomic_json(state_path, state)
    atomic_json(manifest_path, manifest)
    return added


def configure(config, runtime, now):
    config = dict(config)
    target = os.environ.get("FACTORY_DAILY_TARGET", "").strip()
    text_model = os.environ.get("GROQ_TEXT_MODEL", "").strip()
    config["daily_target"] = int(target or config.get("daily_target", 40))
    config["model"] = text_model or config.get("model", "openai/gpt-oss-20b")
    vision = os.environ.get("GROQ_VISION_MODEL", config.get("vision_model", "")).strip()
    if vision:
        config["vision_model"] = vision
    references = runtime / "reference-images"
    config["image_directory"] = str(references)
    if config.get("media_library", {}).get("enabled"):
        sync_media(config["media_library"], references, now)
        config["media_library"] = {**config["media_library"], "enabled": False}
    trim_reference_cache(runtime)
    files = list(references.iterdir()) if references.exists() else []
    has_images = any(path.suffix.lower() in IMAGE_EXTENSIONS for path in files)
    has_clips = any(path.suffix.lower() in CLIP_EXTENSIONS for path in files)
    modes = list(config.get("modes", ["illustration", "animated"]))
    if config.get("require_real_media"):
        manifest_path = references / "library.json"
        assets = json.loads(manifest_path.read_text()).get("assets", []) if manifest_path.exists() else []
        allowed = [item["file"] for item in assets if item.get("license") in {"CC0", "PUBLIC_DOMAIN", "USER_OWNED"}
                   and not str(item.get("source", "")).startswith("pinterest")
                   and "pinterest.com" not in str(item.get("source_url", ""))]
        config["allowed_media_files"] = allowed
        files = [path for path in files if path.name in allowed]
        has_images = any(path.suffix.lower() in IMAGE_EXTENSIONS for path in files)
        has_clips = any(path.suffix.lower() in CLIP_EXTENSIONS for path in files)
    if not vision or not has_images:
        modes = [mode for mode in modes if mode != "photo"]
    if not vision or not has_clips:
        modes = [mode for mode in modes if mode != "clip"]
    if config.get("require_real_media"):
        modes = [mode for mode in modes if mode in {"photo", "clip"}]
        if not modes:
            raise ValueError("Real-image generation needs licensed photos/clips and a vision model; template faces were not substituted")
    config["modes"] = modes or ["illustration", "animated"]
    rating = os.environ.get("FACTORY_RATING", config.get("rating", "GENERAL")).upper()
    if rating not in ("GENERAL", "MATURE"):
        raise ValueError("FACTORY_RATING must be GENERAL or MATURE")
    config["rating"] = rating
    return config


def persist_run(client, generated, config, now, result):
    day = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
    day_dir = generated / day
    state_path = day_dir / "state.json"
    state = json.loads(state_path.read_text()) if state_path.exists() else {}
    count = len(state.get("records", []))
    complete = day_dir / "daily-complete.zip"
    status = "complete" if complete.exists() else "running"
    if status != "complete" and state.get("provider_error", {}).get("http_status") in (401, 403):
        status = "failed"
        result = "Provider authentication rejected. Check worker credentials before retrying."
    if status != "complete" and int(state.get("requests", 0)) >= config.get("max_requests_per_day", 16) and not state.get("pending"):
        status = "failed"
        result = "Daily provider request budget exhausted. Saved stickers are kept; generation resumes next UTC day."
    metrics = run_metrics(state)
    if count:
        # The due check must resume failed ZIP uploads/admin migration even
        # when the provider is cooling down or the daily target is incomplete.
        metrics["postprocess_complete"] = False
    client.upsert("factory_runs", [{
        "day": day, "status": status, "target": config["daily_target"],
        "generated": count, "attempts": int(state.get("requests", 0)),
        "last_result": result[:500], "groq_metrics": metrics}], "day")
    if complete.exists():
        pack_id = str(uuid.uuid5(uuid.NAMESPACE_URL, "stickerdock-production:" + day))
        remote = "generated/" + day + "/daily-complete.zip"
        data = complete.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        existing = client.find("sticker_packs", "id", pack_id)
        pack_status = ("approved" if existing and existing.get("status") == "approved" and
                       existing.get("sha256") == digest else "pending")
        client.upload(PRIVATE_BUCKET, remote, data, "application/zip")
        references = day_dir / "references.json"
        if references.exists():
            client.upload(PRIVATE_BUCKET, "generated/" + day + "/references.json",
                          references.read_bytes(), "application/json")
        with zipfile.ZipFile(__import__("io").BytesIO(data)) as archive:
            manifest = json.loads(archive.read("manifest.json"))
        categories = sorted({pack.get("category", "GENERAL") for pack in manifest.get("packs", [])})
        client.upsert("sticker_packs", [{
            "id": pack_id, "day": day, "name": "Daily " + day,
            "creator": "StickerDock AI Factory", "rating": "MATURE" if any(p.get("rating") == "MATURE" for p in manifest["packs"]) else "GENERAL",
            "status": pack_status, "private_path": remote, "sha256": digest,
            "sticker_count": count, "metadata": {"modes": config["modes"],
                                                    "categories": categories}}], "id")
    return {"day": day, "status": status, "generated": count,
            "target": config["daily_target"], "result": result}


def run_metrics(state):
    metrics = dict(state.get("last_groq_metrics", {}))
    metrics["next_request_at"] = state.get("next_request_at", 0)
    metrics["pending_render"] = bool(state.get("pending"))
    if state.get("provider_error"):
        metrics["recovery"] = dict(state["provider_error"])
    return metrics


def close_previous_runs(client, today, now):
    """Close yesterday's unfinished counters without inventing missing media.

    The existing database constraint has no ``partial`` status. Persist a
    terminal failed row with a distinct partial outcome in metrics so the
    dashboard can present it accurately; retain counts and provider history.
    """
    closed = []
    for row in client.list_rows("factory_runs", "day.desc", 30):
        day = str(row.get("day", ""))
        if day >= today or row.get("status") != "running":
            continue
        count = int(row.get("generated") or 0)
        target = int(row.get("target") or 0)
        outcome = "partial" if count else "failed"
        metrics = dict(row.get("groq_metrics") or {})
        metrics["terminal_outcome"] = outcome
        metrics["closed_at"] = datetime.fromtimestamp(now, timezone.utc).isoformat()
        result = (f"UTC day ended with {count}/{target} stickers. Saved media and review drafts are retained."
                  if count else "UTC day ended without a saved sticker. Check provider and worker activity.")
        client.update("factory_runs", {"status": "failed", "last_result": result,
                                       "groq_metrics": metrics}, "day", day)
        closed.append(day)
    return closed


def persist_failure(client, config, now, error, generated=None):
    day = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
    target = int(config.get("daily_target", 40))
    state = {}
    if generated is not None:
        state_path = generated / day / "state.json"
        if state_path.exists():
            state = json.loads(state_path.read_text())
    waiting = isinstance(error, ProviderResponseError) or isinstance(error, ProviderError) and (error.status == 429 or error.status >= 500)
    if isinstance(error, ProviderError):
        state["provider_error"] = {"http_status": error.status, "retry_at": error.retry_at}
    exhausted = (int(state.get("requests", 0)) >= int(config.get("max_requests_per_day", 16))
                 and len(state.get("records", [])) < target and not state.get("pending"))
    waiting = waiting and not exhausted
    result = ("Daily provider request budget exhausted. Saved stickers are kept; generation resumes next UTC day."
              if exhausted else "Provider returned an invalid response. Progress saved; a bounded retry is scheduled."
              if isinstance(error, ProviderResponseError) and error.kind == "invalid_response"
              else "Provider temporarily unavailable. Progress saved; retry is scheduled after cooldown."
              if isinstance(error, ProviderResponseError) or isinstance(error, ProviderError) and error.status >= 500
              else "Provider rate limit reached. Progress saved; the next scheduled worker retries after cooldown."
              if waiting else "Provider authentication rejected. Check worker credentials before retrying."
              if isinstance(error, ProviderError) and error.status in (401, 403)
              else type(error).__name__ + ": worker failed; inspect Actions logs")
    metrics = run_metrics(state)
    if state.get("records") and not isinstance(error, (ProviderError, ProviderResponseError)):
        metrics["postprocess_complete"] = False
    row = {
        "day": day, "status": "running" if waiting else "failed", "target": target,
        "generated": len(state.get("records", [])), "attempts": int(state.get("requests", 0)),
        "last_result": result, "groq_metrics": metrics}
    client.upsert("factory_runs", [row], "day")
    return {"day": day, "status": "waiting" if waiting else "failed", "target": target,
            "generated": row["generated"], "result": result,
            **({"retry_at": error.retry_at} if isinstance(error, (ProviderError, ProviderResponseError)) else {})}


def run_production(config, runtime, client, now=None, generator=run_generator):
    now = __import__("time").time() if now is None else now
    # Existing installations without the additive admin migration keep their
    # configured schedule. Other failures stop generation rather than bypass pause.
    try:
        controls = client.find("factory_settings", "id", "default")
    except SupabaseError as error:
        if "HTTP 404" not in str(error):
            raise
        controls = None
    if controls:
        if controls.get("enabled") is not True:
            return {"status": "paused", "result": "Paused by the owner admin panel"}
        config = dict(config)
        config["daily_target"] = controls["daily_target"]
        config["batch_size"] = controls["batch_size"]
        config["rating"] = controls.get("rating", config.get("rating", "GENERAL"))
        if controls.get("topics"):
            config["topics"] = controls["topics"]
    runtime.mkdir(parents=True, exist_ok=True)
    safe_restore(client.download(PRIVATE_BUCKET, STATE_OBJECT, missing_ok=True), runtime)
    relocate_state(runtime)
    deferred = []
    if controls:
        # Audience changes wait until tomorrow once any ideas/artwork exist.
        controls = dict(controls)
        day = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
        state_path = runtime / "generated-stickers" / day / "state.json"
        state = json.loads(state_path.read_text()) if state_path.exists() else {}
        ratings = {record.get("rating", "GENERAL") for record in state.get("records", [])}
        if state.get("pending"):
            ratings.add(state["pending"].get("rating", "GENERAL"))
        if ratings:
            active_rating = "MATURE" if "MATURE" in ratings else "GENERAL"
            if active_rating != controls.get("rating", "GENERAL"):
                deferred.append("Audience change applies next UTC day because today's generation already started")
                controls["rating"] = active_rating
                if active_rating == "GENERAL":
                    controls["content_categories"] = [c for c in controls.get("content_categories", []) if c != "ADULT_HUMOR"] or ["EVERYDAY"]
                    controls["topics"] = [topic for topic in controls.get("topics", []) if not topic.strip().upper().startswith("ADULT_HUMOR")]
                    config["topics"] = controls["topics"] or ["EVERYDAY | Original everyday reactions in Hinglish"]
    try:
        generated = runtime / "generated-stickers"
        generated.mkdir(exist_ok=True)
        prune_old_days(generated, now)
        inbox_added = sync_inbox(client, runtime)
        configured = configure(config, runtime, now)
        if controls:
            # Admin controls take precedence over workflow defaults.
            configured["daily_target"] = controls["daily_target"]
            configured["batch_size"] = controls["batch_size"]
            configured["rating"] = controls.get("rating", configured["rating"])
            configured, brief = ensure_content_brief(client, configured, controls, now)
        mirror_licensed_inputs(client, runtime)
        result = generator(configured, generated,
                           default_font(), now)
        summary = persist_run(client, generated, configured, now, result)
        summary["inbox_added"] = inbox_added
        if controls:
            summary["content_source"] = brief["source_mode"]
            summary["warnings"] = deferred
            if controls.get("auto_migrate_completed") is True:
                summary["admin_draft_ids"] = migrate_ready_batches(
                    client, generated, summary["day"], configured["batch_size"])
                # Historical/legacy complete archives may have no individual
                # batch media available. Keep their existing migration route.
                summary["admin_draft_id"] = (summary["admin_draft_ids"][-1]
                    if summary["admin_draft_ids"] else auto_migrate_completed(client, generated, summary))
        if summary["generated"]:
            state = json.loads((generated / summary["day"] / "state.json").read_text())
            client.upsert("factory_runs", [{
                "day": summary["day"], "status": summary["status"], "target": configured["daily_target"],
                "generated": summary["generated"], "attempts": int(state.get("requests", 0)),
                "last_result": result[:500],
                "groq_metrics": {**run_metrics(state), "postprocess_complete": True}}], "day")
        try:
            summary["closed_previous_days"] = close_previous_runs(client, summary["day"], now)
        except (SupabaseError, ValueError, TypeError):
            # A reporting repair must not turn a saved and drafted batch into
            # an apparent generation failure. The next worker retries closure.
            summary.setdefault("warnings", []).append("Historical run status repair is pending")
        return summary
    except Exception as error:
        recovery = None
        try:
            recovery = persist_failure(client, locals().get("configured", config), now, error,
                                       runtime / "generated-stickers")
        except Exception:
            pass
        if recovery and recovery["status"] == "waiting":
            return recovery
        raise
    finally:
        client.upload(PRIVATE_BUCKET, STATE_OBJECT, state_archive(runtime), "application/zip")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--runtime", type=Path)
    args = parser.parse_args()
    require_production_environment()
    config = json.loads(args.config.read_text())
    if args.runtime:
        summary = run_production(config, args.runtime.resolve(), SupabaseFactory())
    else:
        with tempfile.TemporaryDirectory(prefix="stickerdock-production-") as temporary:
            summary = run_production(config, Path(temporary), SupabaseFactory())
    print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
