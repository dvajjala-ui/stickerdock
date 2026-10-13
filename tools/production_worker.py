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

from daily_stickers import atomic_json, run as run_generator
from media_library import sync as sync_media
from supabase_factory import PRIVATE_BUCKET, SupabaseFactory, require_production_environment
from video_stickers import CLIP_EXTENSIONS

STATE_OBJECT = "state/runtime.zip"
SOURCE_METADATA_PREFIX = "source-metadata/"
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"}
INBOX_EXTENSIONS = IMAGE_EXTENSIONS | CLIP_EXTENSIONS
SOURCE_LICENSES = {"CC0", "PUBLIC_DOMAIN", "USER_OWNED", "REFERENCE_ONLY"}


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
    if not vision or not has_images:
        modes = [mode for mode in modes if mode != "photo"]
    if not vision or not has_clips:
        modes = [mode for mode in modes if mode != "clip"]
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
    client.upsert("factory_runs", [{
        "day": day, "status": status, "target": config["daily_target"],
        "generated": count, "attempts": int(state.get("requests", 0)),
        "last_result": result[:500], "groq_metrics": state.get("last_groq_metrics", {})}], "day")
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
        client.upsert("sticker_packs", [{
            "id": pack_id, "day": day, "name": "Daily " + day,
            "creator": "StickerDock AI Factory", "rating": config["rating"],
            "status": pack_status, "private_path": remote, "sha256": digest,
            "sticker_count": count, "metadata": {"modes": config["modes"]}}], "id")
    return {"day": day, "status": status, "generated": count,
            "target": config["daily_target"], "result": result}


def persist_failure(client, config, now, error, generated=None):
    day = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
    target = int(config.get("daily_target", 40))
    state = {}
    if generated is not None:
        state_path = generated / day / "state.json"
        if state_path.exists():
            state = json.loads(state_path.read_text())
    client.upsert("factory_runs", [{
        "day": day, "status": "failed", "target": target,
        "generated": len(state.get("records", [])), "attempts": int(state.get("requests", 0)),
        "last_result": type(error).__name__ + ": worker failed; inspect Actions logs",
        "groq_metrics": state.get("last_groq_metrics", {})}], "day")


def run_production(config, runtime, client, now=None, generator=run_generator):
    now = __import__("time").time() if now is None else now
    runtime.mkdir(parents=True, exist_ok=True)
    safe_restore(client.download(PRIVATE_BUCKET, STATE_OBJECT, missing_ok=True), runtime)
    relocate_state(runtime)
    try:
        generated = runtime / "generated-stickers"
        generated.mkdir(exist_ok=True)
        prune_old_days(generated, now)
        inbox_added = sync_inbox(client, runtime)
        configured = configure(config, runtime, now)
        mirror_licensed_inputs(client, runtime)
        result = generator(configured, generated,
                           Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"), now)
        summary = persist_run(client, generated, configured, now, result)
        summary["inbox_added"] = inbox_added
        return summary
    except Exception as error:
        try:
            persist_failure(client, locals().get("configured", config), now, error,
                            runtime / "generated-stickers")
        except Exception:
            pass
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
