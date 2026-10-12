#!/usr/bin/env python3
"""Run Pinterest AutoSync in GitHub Actions and persist its state in Supabase."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile

from pinterest_autosync.config import validate_config
from pinterest_autosync.database import Database
from pinterest_autosync.runner import ProcessLock, run_scan
from production_worker import safe_restore, state_archive
from supabase_factory import PRIVATE_BUCKET, SupabaseFactory

STATE_OBJECT = "state/pinterest-runtime.zip"
METADATA_PREFIX = "source-metadata/"
ALLOWED_LICENSES = {"CC0", "PUBLIC_DOMAIN", "USER_OWNED", "REFERENCE_ONLY"}


def csv_values(value: str, pattern: str | None = None, maximum: int = 20) -> list[str]:
    values = [item.strip() for item in value.split(",") if item.strip()]
    if len(values) > maximum:
        raise ValueError("Too many Pinterest values")
    if pattern and any(not re.fullmatch(pattern, item) for item in values):
        raise ValueError("Invalid Pinterest configuration value")
    return values


def rights_map(value: str) -> dict[str, str]:
    if not value.strip():
        return {}
    parsed = json.loads(value)
    if not isinstance(parsed, dict) or len(parsed) > 1000:
        raise ValueError("PINTEREST_PIN_RIGHTS_JSON must be an object")
    result = {}
    for pin_id, license_id in parsed.items():
        if not re.fullmatch(r"\d{5,25}", str(pin_id)) or license_id not in ALLOWED_LICENSES - {"REFERENCE_ONLY"}:
            raise ValueError("Invalid Pinterest per-Pin rights declaration")
        result[str(pin_id)] = license_id
    return result


def configure(config: dict) -> tuple[dict, dict[str, str]]:
    config = json.loads(json.dumps(config))
    boards = csv_values(os.environ.get("PINTEREST_BOARD_IDS", ""), r"\d{5,25}")
    token = os.environ.get("PINTEREST_ACCESS_TOKEN", "").strip()
    config["official"]["boards"] = [{"id": board, "enabled": True} for board in boards]
    config["official"]["enabled"] = bool(token and boards)

    unofficial = os.environ.get("PINTEREST_UNOFFICIAL_ENABLED", "").lower() == "true"
    topics = csv_values(os.environ.get("PINTEREST_TOPICS", ""), maximum=20)
    if any(len(topic) > 100 for topic in topics):
        raise ValueError("Pinterest topic is too long")
    config["unofficial"]["enabled"] = unofficial
    config["unofficial"]["topics"] = [{"query": topic, "enabled": True} for topic in topics]
    if unofficial and not topics:
        raise ValueError("PINTEREST_TOPICS is required for unofficial discovery")
    if not config["official"]["enabled"] and not unofficial:
        raise ValueError("Configure authorized Pinterest boards or explicitly enable topics")

    daily = os.environ.get("PINTEREST_DAILY_TARGET", "").strip()
    per_run = os.environ.get("PINTEREST_PER_RUN", "").strip()
    if daily:
        config["limits"]["daily_downloads"] = int(daily)
    if per_run:
        config["limits"]["max_downloads_per_scan"] = int(per_run)
    return validate_config(config), rights_map(os.environ.get("PINTEREST_PIN_RIGHTS_JSON", ""))


def metadata_for(row, license_id: str) -> dict:
    raw = json.loads(row["raw_json"] or "{}")
    title = str(raw.get("title") or raw.get("description") or "Pinterest reference")[:240]
    return {
        "source": "pinterest-" + row["source"],
        "source_url": row["pin_url"] or "https://www.pinterest.com/",
        "download_url": row["media_url"] or row["pin_url"] or "https://www.pinterest.com/",
        "license": license_id,
        "title": title,
        "credit": str(raw.get("creator_name") or raw.get("dominant_color") or "Pinterest source")[:400],
        "source_ref": row["source_ref"],
        "pin_id": row["pin_id"],
    }


def upload_downloads(client, runtime: Path, pin_rights: dict[str, str]) -> int:
    uploaded_path = runtime / "pinterest-uploaded.json"
    uploaded = set(json.loads(uploaded_path.read_text()).get("hashes", [])) \
        if uploaded_path.exists() else set()
    count = 0
    with Database(runtime / "pinterest.sqlite3") as database:
        for row in database.ready_for_upload():
            digest = str(row["content_hash"] or "")
            relative = str(row["relative_path"] or "")
            local = runtime / "downloads" / relative
            if not re.fullmatch(r"[0-9a-f]{64}", digest) or not local.is_file():
                continue
            if digest in uploaded:
                if local.stat().st_size:
                    local.write_bytes(b"")
                    database.offload_file(digest)
                continue
            data = local.read_bytes()
            if not data or len(data) > 20_000_000 or hashlib.sha256(data).hexdigest() != digest:
                continue
            suffix = local.suffix.lower()
            if suffix not in {".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".webm", ".mov"}:
                continue
            license_id = pin_rights.get(str(row["pin_id"]), "REFERENCE_ONLY")
            object_path = "inbox/" + digest + suffix
            content_type = ("video/mp4" if suffix in {".mp4", ".mov"} else
                            "video/webm" if suffix == ".webm" else
                            "image/gif" if suffix == ".gif" else
                            "image/png" if suffix == ".png" else
                            "image/webp" if suffix == ".webp" else "image/jpeg")
            client.upload(PRIVATE_BUCKET, object_path, data, content_type)
            client.upload(PRIVATE_BUCKET, METADATA_PREFIX + digest + ".json",
                          json.dumps(metadata_for(row, license_id), separators=(",", ":")).encode(),
                          "application/json")
            uploaded.add(digest)
            uploaded_path.write_text(json.dumps({"hashes": sorted(uploaded)}, indent=2))
            local.write_bytes(b"")
            database.offload_file(digest)
            count += 1
    return count


def run_hosted(config: dict, runtime: Path, client) -> dict:
    runtime.mkdir(parents=True, exist_ok=True)
    safe_restore(client.download(PRIVATE_BUCKET, STATE_OBJECT, missing_ok=True), runtime)
    configured, pin_rights = configure(config)
    try:
        with ProcessLock(runtime / "pinterest.lock"):
            stats = run_scan(configured, runtime)
            stats["uploaded_to_inbox"] = upload_downloads(client, runtime, pin_rights)
            return stats
    finally:
        client.upload(PRIVATE_BUCKET, STATE_OBJECT, state_archive(runtime), "application/zip")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--runtime", type=Path)
    args = parser.parse_args()
    config = json.loads(args.config.read_text())
    client = SupabaseFactory()
    if args.runtime:
        result = run_hosted(config, args.runtime.resolve(), client)
    else:
        with tempfile.TemporaryDirectory(prefix="stickerdock-pinterest-") as temporary:
            result = run_hosted(config, Path(temporary), client)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
