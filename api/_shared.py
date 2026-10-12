from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
from pathlib import PurePosixPath
import re
import urllib.error
import urllib.parse
import urllib.request

PRIVATE_BUCKET = "sticker-factory-private"
MAX_REQUEST = 6_000_000
MAX_PROVIDER_RESPONSE = 8_000_000
IMAGE_TYPES = {"image/png": ".png", "image/jpeg": ".jpg", "image/webp": ".webp"}


class StudioError(ValueError):
    pass


def authorized(headers) -> bool:
    expected = os.environ.get("STUDIO_ACCESS_TOKEN", "").strip()
    supplied = str(headers.get("X-StickerDock-Token", ""))
    return bool(expected and supplied and hmac.compare_digest(expected, supplied))


def read_json(handler) -> dict:
    try:
        length = int(handler.headers.get("Content-Length", "0"))
    except ValueError as error:
        raise StudioError("Invalid request length") from error
    if not 1 <= length <= MAX_REQUEST:
        raise StudioError("Request is empty or too large")
    try:
        value = json.loads(handler.rfile.read(length))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise StudioError("Invalid JSON request") from error
    if not isinstance(value, dict):
        raise StudioError("Expected a JSON object")
    return value


def send_json(handler, status: int, value: dict):
    body = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Cache-Control", "no-store")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def options(handler):
    handler.send_response(204)
    handler.send_header("Access-Control-Allow-Headers", "Content-Type, X-StickerDock-Token")
    handler.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()


def request(url: str, method="GET", data=None, headers=None, limit=MAX_PROVIDER_RESPONSE):
    outbound = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(outbound, timeout=45) as response:
            body = response.read(limit + 1)
    except urllib.error.HTTPError as error:
        raise StudioError(f"Provider returned HTTP {error.code}") from None
    if len(body) > limit:
        raise StudioError("Provider response is too large")
    return body


def decode_image(data_url: str, max_bytes=4_000_000) -> tuple[bytes, str, str]:
    match = re.fullmatch(r"data:(image/(?:png|jpeg|webp));base64,([A-Za-z0-9+/=\r\n]+)", data_url)
    if not match:
        raise StudioError("Expected a PNG, JPEG, or WebP image")
    try:
        data = base64.b64decode(match.group(2), validate=True)
    except ValueError as error:
        raise StudioError("Invalid image encoding") from error
    mime = match.group(1)
    if not 12 <= len(data) <= max_bytes:
        raise StudioError("Image is empty or too large")
    valid = ((mime == "image/png" and data.startswith(b"\x89PNG\r\n\x1a\n")) or
             (mime == "image/jpeg" and data.startswith(b"\xff\xd8\xff")) or
             (mime == "image/webp" and data.startswith(b"RIFF") and data[8:12] == b"WEBP"))
    if not valid:
        raise StudioError("Image bytes do not match their media type")
    return data, mime, IMAGE_TYPES[mime]


def safe_text(value, name: str, maximum: int, required=False) -> str:
    text = " ".join(str(value or "").split())
    if (required and not text) or len(text) > maximum or any(ord(char) < 32 for char in text):
        raise StudioError(f"Invalid {name}")
    return text


def supabase_request(path: str, method="GET", data=None, content_type=None,
                     limit=MAX_PROVIDER_RESPONSE):
    url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()
    if not url.startswith("https://") or not key:
        raise StudioError("Supabase is not configured")
    headers = {"apikey": key, "Authorization": "Bearer " + key}
    if content_type:
        headers["Content-Type"] = content_type
    if method == "POST" and path.startswith("/storage/v1/object/"):
        headers["x-upsert"] = "true"
    return request(url + path, method=method, data=data, headers=headers, limit=limit)


def storage_path(path: str) -> str:
    pure = PurePosixPath(path)
    if pure.is_absolute() or ".." in pure.parts or not pure.parts:
        raise StudioError("Invalid storage path")
    return urllib.parse.quote(PRIVATE_BUCKET, safe="") + "/" + urllib.parse.quote(str(pure), safe="/")


def upload_user_image(data_url: str, title: str = "Creator Studio upload") -> dict:
    data, mime, extension = decode_image(data_url)
    digest = hashlib.sha256(data).hexdigest()
    object_path = "inbox/" + digest + extension
    supabase_request("/storage/v1/object/" + storage_path(object_path), method="POST", data=data,
                     content_type=mime)
    metadata = json.dumps({
        "source": "creator-studio", "source_url": "creator-studio://manual",
        "download_url": "creator-studio://manual", "license": "USER_OWNED",
        "title": safe_text(title, "title", 240) or "Creator Studio upload",
        "credit": "Factory owner upload",
    }, separators=(",", ":")).encode()
    supabase_request("/storage/v1/object/" + storage_path(
        "source-metadata/" + digest + ".json"), method="POST", data=metadata,
        content_type="application/json")
    return {"path": object_path, "sha256": digest}


def list_inbox() -> list[dict]:
    payload = json.dumps({"prefix": "inbox", "limit": 100, "offset": 0,
                          "sortBy": {"column": "updated_at", "order": "desc"}}).encode()
    raw = supabase_request("/storage/v1/object/list/" + urllib.parse.quote(PRIVATE_BUCKET),
                           method="POST", data=payload, content_type="application/json",
                           limit=1_000_000)
    parsed = json.loads(raw)
    if not isinstance(parsed, list):
        raise StudioError("Supabase returned an invalid inbox")
    result = []
    for item in parsed:
        name = str(item.get("name", ""))
        path = name if name.startswith("inbox/") else "inbox/" + name
        if re.fullmatch(r"inbox/[0-9a-f]{64}\.(?:png|jpe?g|webp)", path):
            result.append({"path": path, "name": path.rsplit("/", 1)[-1],
                           "size": int((item.get("metadata") or {}).get("size") or 0)})
    return result


def fetch_inbox(path: str) -> str:
    if not re.fullmatch(r"inbox/[0-9a-f]{64}\.(?:png|jpe?g|webp)", path):
        raise StudioError("Invalid inbox image path")
    data = supabase_request("/storage/v1/object/authenticated/" + storage_path(path),
                            limit=3_000_000)
    extension = path.rsplit(".", 1)[-1]
    mime = "image/png" if extension == "png" else "image/webp" if extension == "webp" else "image/jpeg"
    decode_image("data:" + mime + ";base64," + base64.b64encode(data).decode(), 3_000_000)
    return "data:" + mime + ";base64," + base64.b64encode(data).decode()
