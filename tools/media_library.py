"""Free reference library: bounded CC0/public-domain downloads with provenance.

No Pinterest scraping, account login, paid providers or generated-image API.
Explicit direct-file queue items may also be marked user-owned by their owner.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import html
import io
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image, ImageOps

LICENSES = {"CC0", "PUBLIC_DOMAIN", "USER_OWNED"}
MEDIA_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".webm", ".mov"}


def https_url(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("Expected a public HTTPS media URL")
    return url


def fetch(url, limit=20_000_000, headers=None):
    request = urllib.request.Request(https_url(url), headers={
        "User-Agent": "StickerDock/0.1 (personal reference library)", **(headers or {})})
    with urllib.request.urlopen(request, timeout=30) as response:
        https_url(response.geturl())
        data = response.read(limit + 1)
    if len(data) > limit:
        raise ValueError("Media response exceeds download size limit")
    return data


def atomic_json(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2))
    temporary.replace(path)


def commons_candidates(query):
    args = {"action": "query", "format": "json", "generator": "search",
            "gsrsearch": query, "gsrnamespace": 6, "gsrlimit": 10,
            "prop": "imageinfo", "iiprop": "url|extmetadata", "iiurlwidth": 512}
    data = json.loads(fetch("https://commons.wikimedia.org/w/api.php?" +
                            urllib.parse.urlencode(args), 1_000_000))
    if "error" in data:
        raise ValueError("Commons API rejected search")
    candidates = []
    for page in data.get("query", {}).get("pages", {}).values():
        info = (page.get("imageinfo") or [{}])[0]
        metadata = info.get("extmetadata", {})
        license_name = metadata.get("LicenseShortName", {}).get("value", "").strip().lower()
        if license_name in ("cc0", "cc0 1.0"):
            license_id = "CC0"
        elif license_name == "public domain":
            license_id = "PUBLIC_DOMAIN"
        else:
            continue  # no guess based on visibility, tags or a search snippet
        download = info.get("thumburl", info.get("url", ""))
        suffix = Path(urllib.parse.urlsplit(download).path).suffix.lower()
        if suffix not in (".png", ".jpg", ".jpeg", ".webp"):
            continue
        candidates.append({"title": page.get("title", "Reference")[:240],
                           "source_url": info.get("descriptionurl", ""),
                           "download_url": download, "license": license_id,
                           "license_url": metadata.get("LicenseUrl", {}).get("value", ""),
                           "credit": html.unescape(re.sub("<[^>]+>", "", metadata.get("Artist", {}).get("value", "")))[:400]})
    return candidates


def pinterest_candidates(config):
    """Official authorized board reader; no partner search or public-page scrape.

    A board may contain other people's saved Pins. Only explicitly licensed pin
    IDs become downloads; everything else remains a reference bookmark.
    """
    board = str(config["pinterest_board_id"])
    if not board.isdigit():
        raise ValueError("Pinterest board ID must be numeric")
    token = os.environ.get("PINTEREST_ACCESS_TOKEN", "").strip()
    if not token:
        raise ValueError("Pinterest board access requires an authorized access token")
    data = json.loads(fetch(f"https://api.pinterest.com/v5/boards/{board}/pins?page_size=25",
                            1_000_000, {"Authorization": "Bearer " + token}))
    allowed = config.get("pinterest_pin_licenses", {})
    candidates, bookmarks = [], []
    for pin in data.get("items", [])[:25]:
        pin_id = str(pin.get("id", ""))
        if not pin_id.isdigit():
            continue
        source = f"https://www.pinterest.com/pin/{pin_id}/"
        title = str(pin.get("title") or pin.get("description") or "Pinterest reference")[:240]
        bookmarks.append({"title": title, "source_url": source})
        license_id = allowed.get(pin_id)
        if license_id not in LICENSES:
            continue
        images = pin.get("media", {}).get("images", {})
        choices = [item for item in images.values() if isinstance(item, dict) and item.get("url")]
        if not choices:
            continue
        selected = max(choices, key=lambda item: int(item.get("width") or 0))
        candidates.append({"title": title, "source_url": source,
                           "download_url": selected["url"], "license": license_id})
    return candidates, bookmarks


def save_asset(item, root):
    if item.get("license") not in LICENSES:
        raise ValueError("Media must have a supported reuse license")
    source = https_url(item["source_url"])
    url = https_url(item["download_url"])
    suffix = Path(urllib.parse.urlsplit(url).path).suffix.lower()
    if suffix not in MEDIA_EXTENSIONS:
        raise ValueError("Direct file must be an image or short-video format")
    raw = fetch(url)
    raw_hash = hashlib.sha256(raw).hexdigest()
    # Sanitize static downloads so HTML masquerading as JPEG never enters inputs.
    if suffix in (".png", ".jpg", ".jpeg", ".webp"):
        with Image.open(io.BytesIO(raw)) as original:
            if original.width * original.height > 20_000_000:
                raise ValueError("Image exceeds pixel limit")
            if getattr(original, "is_animated", False):
                raise ValueError("Queue animated input as GIF/video instead")
            image = ImageOps.exif_transpose(original).convert("RGBA")
            image.thumbnail((1024, 1024))
            output = io.BytesIO()
            image.save(output, format="PNG")
            raw, suffix = output.getvalue(), ".png"
    filename = raw_hash + suffix
    path = root / filename
    if not path.exists():
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_bytes(raw)
        temporary.replace(path)
    return {"file": filename, "sha256": raw_hash, "source_url": source,
            "download_url": url, "license": item["license"],
            "license_url": item.get("license_url", ""),
            "title": str(item.get("title", "Reference"))[:240],
            "credit": str(item.get("credit", ""))[:400]}


def sync(config, root, now=None):
    """One bounded hourly sync; failures count and cooldown survives midnight."""
    import fcntl
    now = time.time() if now is None else now
    day = datetime.fromtimestamp(now, timezone.utc).date().isoformat()
    daily = config.get("daily_download_limit", 6)
    per_run = config.get("max_downloads_per_run", 2)
    interval = config.get("interval_seconds", 3600)
    if (type(daily) is not int or not 1 <= daily <= 30 or type(per_run) is not int
            or not 1 <= per_run <= 5 or type(interval) is not int or interval < 60):
        raise ValueError("Invalid download budget")
    root.mkdir(parents=True, exist_ok=True)
    state_path = root / "library.json"
    with (root / ".library.lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return {"status": "busy", "added": 0}
        state = json.loads(state_path.read_text()) if state_path.exists() else {
            "day": day, "attempts": 0, "next_sync_at": 0, "assets": [], "query_index": 0}
        if state["day"] != day:
            state["day"], state["attempts"] = day, 0
        if now < state["next_sync_at"] or state["attempts"] >= daily:
            return {"status": "cooldown or daily cap", "added": 0}
        # Reserve one network cycle before discovery; invalid sources cannot
        # cause unlimited repeated searches. Asset attempts consume extra slots.
        state["attempts"] += 1
        state["next_sync_at"] = now + interval
        atomic_json(state_path, state)
        added = 0
        try:
            candidates = list(config.get("queue", []))
            queries = config.get("commons_queries", [])
            if queries:
                query = queries[state["query_index"] % len(queries)]
                state["query_index"] += 1
                candidates += commons_candidates(query)
            if config.get("pinterest_board_id"):
                if state["attempts"] < daily:
                    state["attempts"] += 1
                    atomic_json(state_path, state)
                    pins, bookmarks = pinterest_candidates(config)
                    candidates += pins
                    state["pinterest_bookmarks"] = bookmarks
            seen_urls = set(state.get("seen_urls", [])) | {asset["download_url"] for asset in state["assets"]}
            seen_hashes = {asset["sha256"] for asset in state["assets"]}
            attempted = 0
            for item in candidates:
                if item.get("download_url") in seen_urls or item.get("license") not in LICENSES:
                    continue
                if attempted >= per_run or state["attempts"] >= daily:
                    break
                state["attempts"] += 1
                attempted += 1
                atomic_json(state_path, state)
                asset = save_asset(item, root)
                seen_urls.add(asset["download_url"])
                state["seen_urls"] = sorted(seen_urls)
                if asset["sha256"] not in seen_hashes:
                    state["assets"].append(asset)
                    seen_hashes.add(asset["sha256"])
                    added += 1
        except urllib.error.HTTPError as error:
            if error.code == 429:
                from daily_stickers import retry_delay
                state["next_sync_at"] = max(state["next_sync_at"], now + retry_delay(error.headers, now))
            state["last_status"] = f"HTTP {error.code}; no response body logged"
        except (OSError, ValueError, KeyError, TypeError):
            state["last_status"] = "Unavailable or invalid media source"
        else:
            state["last_status"] = "completed"
        atomic_json(state_path, state)
        return {"status": state["last_status"], "added": added,
                "attempts": state["attempts"], "library_size": len(state["assets"])}


def provenance(path):
    manifest = path.parent / "library.json"
    if not manifest.exists():
        return {"source": "user-supplied", "file": path.name}
    state = json.loads(manifest.read_text())
    return next((item for item in state.get("assets", []) if item["file"] == path.name),
                {"source": "user-supplied", "file": path.name})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output", default=Path("reference-images"), type=Path)
    args = parser.parse_args()
    settings = json.loads(args.config.read_text())
    print(json.dumps(sync(settings.get("media_library", settings), args.output)))
