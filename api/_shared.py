from __future__ import annotations

import base64
import hashlib
import io
import json
import os
from pathlib import PurePosixPath
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid
from PIL import Image, ImageCms, ImageOps

PRIVATE_BUCKET = "sticker-factory-private"
MAX_REQUEST = 4_300_000  # Keep JSON/base64 below the hosted function request bound.
MAX_PROVIDER_RESPONSE = 8_000_000
IMAGE_TYPES = {"image/png": ".png", "image/jpeg": ".jpg", "image/webp": ".webp"}
CREATOR_SOURCE_RE = re.compile(r"inbox/[0-9a-f]{64}\.(?:png|jpe?g|webp)")
CREATOR_MAX_OUTSTANDING = 25


class StudioError(ValueError):
    pass


def same_origin(headers) -> bool:
    """Reject foreign browser origins; the shared workspace has no login gate.

    Direct HTTP clients remain supported. This is a browser request boundary,
    not authentication or a substitute for private phone-account authorization.
    """
    if headers.get("Sec-Fetch-Site") == "cross-site":
        return False
    origin = headers.get("Origin", "")
    if not origin:
        return True
    host = headers.get("Host", "")
    if host and origin == "https://" + host:
        return True
    try:
        parsed = urllib.parse.urlsplit(origin)
    except ValueError:
        return False
    return (parsed.scheme == "http" and parsed.hostname in {"localhost", "127.0.0.1", "::1"}
            and origin == "http://" + host)


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


def send_json(handler, status: int, value: dict, extra_headers=None):
    body = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Cache-Control", "no-store")
    handler.send_header("Content-Length", str(len(body)))
    for key, value in (extra_headers or {}).items():
        handler.send_header(key, value)
    handler.end_headers()
    handler.wfile.write(body)


def options(handler):
    handler.send_response(204)
    handler.send_header("Access-Control-Allow-Headers", "Content-Type")
    handler.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()


def request(url: str, method="GET", data=None, headers=None, limit=MAX_PROVIDER_RESPONSE,
            missing_ok=False):
    outbound = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(outbound, timeout=45) as response:
            body = response.read(limit + 1)
    except urllib.error.HTTPError as error:
        if missing_ok and error.code == 404:
            return None
        if missing_ok and error.code == 400:
            try:
                detail = json.loads(error.read(4096))
            except (ValueError, OSError):
                detail = {}
            if isinstance(detail, dict) and (detail.get("code") in ("NoSuchKey", "not_found") or
                                            detail.get("message") == "Object not found"):
                return None
        raise StudioError(f"Provider returned HTTP {error.code}") from None
    if len(body) > limit:
        raise StudioError("Provider response is too large")
    return body


def decode_image(data_url: str, max_bytes=3_000_000) -> tuple[bytes, str, str]:
    if not isinstance(data_url, str):
        raise StudioError("Expected a PNG, JPEG, or WebP image")
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
    try:
        with Image.open(io.BytesIO(data)) as image:
            expected_format = {"image/png": "PNG", "image/jpeg": "JPEG", "image/webp": "WEBP"}[mime]
            if (image.format != expected_format or image.width * image.height > 20_000_000 or
                    getattr(image, "is_animated", False)):
                raise StudioError("Use a static image under 20 million pixels")
            image.verify()
        with Image.open(io.BytesIO(data)) as image:
            image.load()
    except (OSError, ValueError, Image.DecompressionBombError) as error:
        if isinstance(error, StudioError):
            raise
        raise StudioError("The image is invalid or corrupted") from None
    return data, mime, IMAGE_TYPES[mime]


def normalize_upload_image(data: bytes) -> bytes:
    """Store pixels only, applying orientation and removing location/EXIF text."""
    try:
        with Image.open(io.BytesIO(data)) as image:
            image.load()
            rotated = ImageOps.exif_transpose(image)
            mode = "RGBA" if "A" in rotated.getbands() or "transparency" in rotated.info else "RGB"
            pixels = rotated.convert(mode)
            profile = rotated.info.get("icc_profile")
            if profile:
                try:
                    srgb = ImageCms.createProfile("sRGB")
                    original = ImageCms.ImageCmsProfile(io.BytesIO(profile))
                    converted = ImageCms.profileToProfile(pixels.convert("RGB"), original, srgb, outputMode="RGB")
                    if mode == "RGBA":
                        converted.putalpha(pixels.getchannel("A"))
                    pixels = converted
                except (OSError, ValueError, ImageCms.PyCMSError):
                    # An invalid optional profile must not make a valid source unusable.
                    pass
            clean = Image.new(mode, pixels.size)
            clean.paste(pixels)
            for quality in (90, 75, 60):
                output = io.BytesIO()
                clean.save(output, format="WEBP", quality=quality, method=4)
                if len(output.getvalue()) <= 3_000_000:
                    return output.getvalue()
            while max(clean.size) > 512:
                clean = ImageOps.contain(clean, (max(512, clean.width * 3 // 4),
                                                 max(512, clean.height * 3 // 4)), Image.Resampling.LANCZOS)
                output = io.BytesIO()
                clean.save(output, format="WEBP", quality=75, method=4)
                if len(output.getvalue()) <= 3_000_000:
                    return output.getvalue()
    except (OSError, ValueError, Image.DecompressionBombError) as error:
        raise StudioError("The image could not be prepared for upload") from error
    raise StudioError("The image is too detailed to store as a sticker source")


def safe_text(value, name: str, maximum: int, required=False) -> str:
    text = " ".join(str(value or "").split())
    if (required and not text) or len(text) > maximum or any(ord(char) < 32 for char in text):
        raise StudioError(f"Invalid {name}")
    return text


def supabase_request(path: str, method="GET", data=None, content_type=None,
                     limit=MAX_PROVIDER_RESPONSE, missing_ok=False):
    url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()
    if not url.startswith("https://") or not key:
        raise StudioError("Supabase is not configured")
    headers = {"apikey": key}
    # New sb_secret_* API keys are opaque secrets, not JWT bearer tokens.
    # Legacy service_role JWTs keep Authorization for backwards compatibility.
    if not key.startswith("sb_secret_"):
        headers["Authorization"] = "Bearer " + key
    if content_type:
        headers["Content-Type"] = content_type
    if method == "POST" and path.startswith("/storage/v1/object/"):
        headers["x-upsert"] = "true"
    return request(url + path, method=method, data=data, headers=headers, limit=limit,
                   missing_ok=missing_ok)


def storage_path(path: str) -> str:
    pure = PurePosixPath(path)
    if pure.is_absolute() or ".." in pure.parts or not pure.parts:
        raise StudioError("Invalid storage path")
    return urllib.parse.quote(PRIVATE_BUCKET, safe="") + "/" + urllib.parse.quote(str(pure), safe="/")


def source_metadata(source_path):
    if not isinstance(source_path, str) or not CREATOR_SOURCE_RE.fullmatch(source_path):
        raise StudioError("Invalid source image path")
    digest = source_path.split("/")[-1].split(".")[0]
    data = supabase_request("/storage/v1/object/authenticated/" + storage_path(
        "source-metadata/" + digest + ".json"), limit=16_000, missing_ok=True)
    if data is None:
        return {"source": "creator-studio", "license": "REFERENCE_ONLY",
                "source_url": "supabase://" + source_path, "download_url": "supabase://" + source_path}
    metadata = json.loads(data)
    if not isinstance(metadata, dict) or metadata.get("license") not in (
            "CC0", "PUBLIC_DOMAIN", "USER_OWNED", "REFERENCE_ONLY"):
        raise StudioError("Invalid source rights metadata")
    return {key: metadata[key] for key in ("source", "source_url", "download_url", "license", "credit", "title", "purpose")
            if key in metadata}


def normalize_request_id(value) -> str:
    try:
        parsed = uuid.UUID(str(value or ""))
    except (ValueError, AttributeError, TypeError) as error:
        raise StudioError("Invalid request id") from error
    return str(parsed)


def creator_outstanding_count() -> int:
    status_filter = urllib.parse.quote("in.(queued,running)")
    path = ("/rest/v1/creator_requests?status=" + status_filter +
            "&select=id&limit=" + str(CREATOR_MAX_OUTSTANDING + 1))
    raw = supabase_request(path, limit=64_000)
    rows = json.loads(raw or b"[]")
    if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
        raise StudioError("Supabase returned an invalid Creator queue")
    return len(rows)


def queue_creator_request(prompt, source_path, request_id=None) -> dict:
    prompt_text = safe_text(prompt, "prompt", 500, required=True)
    if not isinstance(source_path, str) or not CREATOR_SOURCE_RE.fullmatch(source_path):
        raise StudioError("Invalid source image path")
    supplied_id = request_id is not None
    request_id = normalize_request_id(request_id) if supplied_id else str(uuid.uuid4())
    if supplied_id:
        try:
            existing = creator_request_status(request_id)
        except StudioError as error:
            if str(error) != "Creator request was not found":
                raise
        else:
            if existing["prompt"] != prompt_text or existing["source_path"] != source_path:
                raise StudioError("Request id belongs to a different Creator job")
            return {"id": request_id, "status": existing["status"]}
    if creator_outstanding_count() >= CREATOR_MAX_OUTSTANDING:
        raise StudioError("Creator queue is busy; try again after current requests finish")
    payload = json.dumps([{
        "id": request_id,
        "source_path": source_path,
        "prompt": prompt_text,
        "status": "queued",
    }], separators=(",", ":")).encode()
    try:
        supabase_request("/rest/v1/creator_requests", method="POST", data=payload,
                         content_type="application/json", limit=16_000)
    except StudioError as error:
        if not supplied_id or "HTTP 409" not in str(error):
            raise
        # Concurrent submission with the same request id: only one INSERT can
        # win the primary-key race. Return that exact job if its inputs match.
        existing = creator_request_status(request_id)
        if existing["prompt"] != prompt_text or existing["source_path"] != source_path:
            raise StudioError("Request id belongs to a different Creator job") from None
        return {"id": request_id, "status": existing["status"]}
    return {"id": request_id, "status": "queued"}


def creator_request_status(request_id) -> dict:
    request_id = normalize_request_id(request_id)
    fields = "id,status,source_path,prompt,result_path,error_message,attempts,created_at,updated_at"
    path = ("/rest/v1/creator_requests?id=eq." + urllib.parse.quote(request_id) +
            "&select=" + urllib.parse.quote(fields, safe=",") + "&limit=1")
    raw = supabase_request(path, limit=64_000)
    rows = json.loads(raw or b"[]")
    if not isinstance(rows, list):
        raise StudioError("Supabase returned an invalid request status")
    if not rows:
        raise StudioError("Creator request was not found")
    row = rows[0]
    if not isinstance(row, dict) or row.get("status") not in (
            "queued", "running", "complete", "failed", "cancelled"):
        raise StudioError("Supabase returned an invalid request status")
    return {key: row.get(key) for key in (
        "id", "status", "source_path", "prompt", "result_path", "error_message",
        "attempts", "created_at", "updated_at")}


def upload_user_image(data_url: str, title: str = "Creator Studio upload", source_path=None,
                      rights="REFERENCE_ONLY") -> dict:
    if rights not in ("USER_OWNED", "REFERENCE_ONLY"):
        raise StudioError("Choose whether this image can be reused or is reference-only")
    original, _, _ = decode_image(data_url)
    data = normalize_upload_image(original)
    mime, extension = "image/webp", ".webp"
    digest = hashlib.sha256(data).hexdigest()
    object_path = "inbox/" + digest + extension
    metadata = {
        "source": "creator-studio", "source_url": "creator-studio://manual",
        "download_url": "creator-studio://manual", "license": rights,
        "purpose": "SOURCE" if rights == "USER_OWNED" else "REFERENCE",
        "title": safe_text(title, "title", 240) or "Creator Studio upload",
        "credit": "Factory owner upload",
    }
    if source_path is not None:
        metadata.update(source_metadata(source_path))
        metadata["derived_from"] = source_path
        metadata["purpose"] = "RESULT"
    metadata = json.dumps(metadata, separators=(",", ":")).encode()
    supabase_request("/storage/v1/object/" + storage_path(
        "source-metadata/" + digest + ".json"), method="POST", data=metadata,
        content_type="application/json")
    supabase_request("/storage/v1/object/" + storage_path(object_path), method="POST", data=data,
                     content_type=mime)
    return {"path": object_path, "sha256": digest}


def list_inbox(offset=0, limit=100) -> dict:
    if type(offset) is not int or type(limit) is not int or not 0 <= offset <= 10_000 or not 1 <= limit <= 100:
        raise StudioError("Invalid media page")
    payload = json.dumps({"prefix": "inbox", "limit": limit, "offset": offset,
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
        if CREATOR_SOURCE_RE.fullmatch(path):
            result.append({"path": path, "name": path.rsplit("/", 1)[-1],
                           "size": int((item.get("metadata") or {}).get("size") or 0)})
    return {"items": result, "next_offset": offset + len(parsed) if len(parsed) == limit else None}


def preview_inbox(path: str) -> dict:
    if not CREATOR_SOURCE_RE.fullmatch(str(path or "")):
        raise StudioError("Invalid inbox image path")
    metadata = source_metadata(path)
    if str(metadata.get("source", "")).startswith("pinterest"):
        raise StudioError("Pinterest references cannot be archived or converted. Upload an independent source image instead.")
    data = supabase_request("/storage/v1/object/authenticated/" + storage_path(path), limit=3_000_000)
    extension = path.rsplit(".", 1)[-1]
    mime = "image/png" if extension == "png" else "image/webp" if extension == "webp" else "image/jpeg"
    decode_image("data:" + mime + ";base64," + base64.b64encode(data).decode(), 3_000_000)
    with Image.open(io.BytesIO(data)) as original:
        image = ImageOps.exif_transpose(original)
        image.thumbnail((220, 220), Image.Resampling.LANCZOS)
        clean = Image.new("RGBA" if "A" in image.getbands() or "transparency" in image.info else "RGB", image.size)
        clean.paste(image.convert(clean.mode))
        output = io.BytesIO()
        clean.save(output, "WEBP", quality=75, method=4)
    return {"path": path, "name": str(metadata.get("title") or path.rsplit("/", 1)[-1])[:240],
            "license": metadata.get("license", "REFERENCE_ONLY"),
            "purpose": metadata.get("purpose", "REFERENCE" if metadata.get("license") == "REFERENCE_ONLY" else "SOURCE"),
            "preview": "data:image/webp;base64," + base64.b64encode(output.getvalue()).decode()}


def fetch_inbox(path: str) -> str:
    if not CREATOR_SOURCE_RE.fullmatch(str(path or "")):
        raise StudioError("Invalid inbox image path")
    if str(source_metadata(path).get("source", "")).startswith("pinterest"):
        raise StudioError("Pinterest references cannot be archived or converted. Upload an independent source image instead.")
    data = supabase_request("/storage/v1/object/authenticated/" + storage_path(path),
                            limit=3_000_000)
    extension = path.rsplit(".", 1)[-1]
    mime = "image/png" if extension == "png" else "image/webp" if extension == "webp" else "image/jpeg"
    decode_image("data:" + mime + ";base64," + base64.b64encode(data).decode(), 3_000_000)
    return "data:" + mime + ";base64," + base64.b64encode(data).decode()
