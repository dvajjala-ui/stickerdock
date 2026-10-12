from __future__ import annotations

import hashlib
import os
import tempfile
from pathlib import Path
from urllib.parse import urljoin, urlparse

import requests

from .models import Candidate

IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp", ".gif"}
VIDEO_EXT = {".mp4", ".mov", ".mkv", ".webm", ".ts"}
CONTENT_TYPES = {
    "image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp",
    "image/gif": ".gif", "video/mp4": ".mp4", "video/quicktime": ".mov",
    "video/webm": ".webm",
}


class UnsupportedMedia(Exception):
    pass


class StorageLimitReached(Exception):
    pass


def validate_cdn_url(url: str):
    parsed = urlparse(url)
    host = (parsed.hostname or "").lower()
    if (parsed.scheme != "https" or not (host == "pinimg.com" or host.endswith(".pinimg.com"))
            or parsed.username or parsed.password):
        raise ValueError("Media URL is not an HTTPS Pinterest CDN URL")


def official_download(url: str, staging: Path, kind: str, max_bytes: int, session=None) -> Path:
    """Stream a bounded Pinterest CDN download and validate each redirect."""
    http = session or requests.Session()
    current = url
    for _ in range(5):
        validate_cdn_url(current)
        with http.get(current, stream=True, allow_redirects=False, timeout=(10, 40),
                      headers={"User-Agent": "StickerDock-PinterestSync/2.0"}) as response:
            if response.status_code in (301, 302, 303, 307, 308):
                redirect = response.headers.get("Location")
                if not redirect:
                    raise ValueError("Redirect response missing Location")
                current = urljoin(current, redirect)
                continue
            response.raise_for_status()
            content_type = response.headers.get("Content-Type", "").split(";")[0].lower().strip()
            path_ext = Path(urlparse(current).path).suffix.lower()
            if "mpegurl" in content_type or path_ext == ".m3u8":
                raise UnsupportedMedia("Pinterest returned an HLS playlist")
            extension = CONTENT_TYPES.get(content_type)
            if extension is None and path_ext in IMAGE_EXT | VIDEO_EXT:
                extension = path_ext
            if extension is None:
                raise UnsupportedMedia("Unrecognized Pinterest media type")
            actual_kind = "video" if extension in VIDEO_EXT else "image"
            if actual_kind != kind:
                raise UnsupportedMedia("Pinterest media kind did not match metadata")
            length = response.headers.get("Content-Length")
            if length and int(length) > max_bytes:
                raise StorageLimitReached("File exceeds configured size before download")
            destination = staging / ("asset" + extension)
            total = 0
            with destination.open("wb") as output:
                for chunk in response.iter_content(chunk_size=1024 * 256):
                    if not chunk:
                        continue
                    total += len(chunk)
                    if total > max_bytes:
                        raise StorageLimitReached("File exceeded configured size while downloading")
                    output.write(chunk)
            if total == 0:
                raise IOError("Downloaded file is empty")
            return destination
    raise ValueError("Too many media redirects")


def unofficial_download(native, staging: Path, max_bytes: int) -> tuple[Path, str]:
    if native is None:
        raise UnsupportedMedia("Pinterest item must be rediscovered")
    from pinterest_dl import PinterestDL
    PinterestDL.download_media(media=[native], output_dir=str(staging), download_streams=True)
    files = [path for path in staging.rglob("*")
             if path.is_file() and path.suffix.lower() in IMAGE_EXT | VIDEO_EXT]
    videos = [path for path in files if path.suffix.lower() in VIDEO_EXT]
    pictures = [path for path in files if path.suffix.lower() in IMAGE_EXT]
    chosen = (max(videos, key=lambda path: path.stat().st_size) if videos else
              max(pictures, key=lambda path: path.stat().st_size) if pictures else None)
    if not chosen:
        raise UnsupportedMedia("Pinterest downloader returned no supported media")
    if chosen.stat().st_size > max_bytes:
        raise StorageLimitReached("Unofficial download exceeded configured size")
    return chosen, "video" if chosen in videos else "image"


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def save_candidate(candidate: Candidate, db, base_dir: Path, limits: dict, http=None) -> tuple[str, str]:
    max_bytes = int(float(limits["max_file_mb"]) * 1024 * 1024)
    storage_cap = int(float(limits["max_total_storage_gb"]) * 1024 ** 3)
    if db.recorded_storage_bytes() >= storage_cap:
        raise StorageLimitReached("Local storage cap reached")
    staging_root = base_dir / ".staging"
    staging_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="pin_", dir=staging_root) as temporary:
        staging = Path(temporary)
        if candidate.source == "official":
            if not candidate.media_url:
                raise UnsupportedMedia("Pinterest API supplied no downloadable media URL")
            original = official_download(candidate.media_url, staging, candidate.kind, max_bytes, http)
            kind = candidate.kind
        else:
            original, kind = unofficial_download(candidate.native, staging, max_bytes)
        if original.stat().st_size == 0:
            raise UnsupportedMedia("Empty Pinterest media")
        if db.recorded_storage_bytes() + original.stat().st_size > storage_cap:
            raise StorageLimitReached("Saving file would exceed local storage limit")
        content_hash = sha256_of(original)
        existing = db.file_for_hash(content_hash)
        if existing and (base_dir / existing["relative_path"]).is_file():
            db.update_media_kind(candidate.key, kind)
            db.mark_done(candidate.key, existing["relative_path"], content_hash,
                         existing["file_bytes"], duplicate=True)
            return "duplicate", existing["relative_path"]
        category = "videos" if kind == "video" else "images"
        destination_dir = base_dir / category
        destination_dir.mkdir(parents=True, exist_ok=True)
        destination = destination_dir / f"{content_hash[:24]}{original.suffix.lower()}"
        os.replace(original, destination)
        relative = str(destination.relative_to(base_dir))
        db.register_hash(content_hash, relative, destination.stat().st_size)
        db.update_media_kind(candidate.key, kind)
        db.mark_done(candidate.key, relative, content_hash, destination.stat().st_size)
        return "saved", relative
