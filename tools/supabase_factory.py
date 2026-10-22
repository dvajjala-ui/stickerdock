"""Small Supabase REST client and shared factory storage helpers."""
from __future__ import annotations

import json
import os
from pathlib import Path, PurePosixPath
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid
from contextlib import contextmanager


PRIVATE_BUCKET = "sticker-factory-private"
PUBLIC_BUCKET = "sticker-factory-public"


class SupabaseError(RuntimeError):
    pass


class SupabaseFactory:
    def __init__(self, url=None, service_key=None):
        self.url = (url or os.environ.get("SUPABASE_URL", "")).rstrip("/")
        self.key = service_key or os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
        if not self.url.startswith("https://") or not self.key:
            raise ValueError("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required")

    def _request(self, method, path, data=None, headers=None, missing_ok=False):
        auth_headers = {"apikey": self.key}
        if not self.key.startswith("sb_"):
            auth_headers["Authorization"] = "Bearer " + self.key
        request = urllib.request.Request(
            self.url + path,
            data=data,
            method=method,
            headers={**auth_headers, **(headers or {})},
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                result = response.read(75_000_001)
                if len(result) > 75_000_000:
                    raise SupabaseError("Supabase response exceeds 75MB")
                return result
        except urllib.error.HTTPError as error:
            if missing_ok and error.code == 404:
                return None
            if missing_ok and error.code == 400:
                try:
                    detail = json.loads(error.read(4096))
                except (ValueError, OSError):
                    detail = {}
                if isinstance(detail, dict) and (detail.get("code") in ("NoSuchKey", "not_found") or detail.get("message") == "Object not found"):
                    return None
            raise SupabaseError(f"Supabase HTTP {error.code}; response body omitted") from None

    @staticmethod
    def _object_path(bucket, path):
        clean = PurePosixPath(path)
        if clean.is_absolute() or ".." in clean.parts or not clean.parts:
            raise ValueError("Unsafe storage object path")
        return urllib.parse.quote(bucket, safe="") + "/" + urllib.parse.quote(str(clean), safe="/")

    @staticmethod
    def _uuid(value):
        try:
            return str(uuid.UUID(str(value)))
        except (ValueError, TypeError, AttributeError) as error:
            raise ValueError("Invalid UUID") from error

    def download(self, bucket, path, missing_ok=False):
        endpoint = self._object_path(bucket, path)
        # State, credentials and the catalog are mutable. An authenticated URL
        # alone is not a freshness guarantee after an overwrite at the edge.
        return self._request("GET", "/storage/v1/object/authenticated/" + endpoint +
                             "?cacheNonce=" + uuid.uuid4().hex,
                             headers={"Cache-Control": "no-cache"}, missing_ok=missing_ok)

    def download_public(self, path, missing_ok=False):
        endpoint = self._object_path(PUBLIC_BUCKET, path)
        return self._request("GET", "/storage/v1/object/public/" + endpoint,
                             missing_ok=missing_ok)

    def upload(self, bucket, path, data, content_type="application/octet-stream"):
        if len(data) > 75_000_000:
            raise ValueError("Storage upload exceeds 75MB")
        endpoint = self._object_path(bucket, path)
        self._request("POST", "/storage/v1/object/" + endpoint, data=data, headers={
            "Content-Type": content_type, "x-upsert": "true",
            "Cache-Control": "max-age=0" if path == "catalog/packs.json" else "max-age=3600"})

    def remove(self, bucket, paths):
        if not isinstance(paths, list) or not 1 <= len(paths) <= 1000:
            raise ValueError("Invalid storage removal batch")
        for path in paths:
            self._object_path(bucket, path)
        payload = json.dumps({"prefixes": paths}, separators=(",", ":")).encode()
        self._request("DELETE", "/storage/v1/object/" + urllib.parse.quote(bucket, safe=""),
                      data=payload, headers={"Content-Type": "application/json"})

    def list_objects(self, bucket, prefix, limit=100, offset=0):
        payload = json.dumps({"prefix": prefix, "limit": min(limit, 100),
                              "offset": offset, "sortBy": {"column": "name", "order": "asc"}}).encode()
        data = self._request("POST", "/storage/v1/object/list/" + urllib.parse.quote(bucket),
                             data=payload, headers={"Content-Type": "application/json"})
        parsed = json.loads(data or b"[]")
        if not isinstance(parsed, list):
            raise SupabaseError("Invalid object-list response")
        return parsed

    def upsert(self, table, rows, conflict):
        data = json.dumps(rows, separators=(",", ":")).encode()
        path = "/rest/v1/" + urllib.parse.quote(table) + "?on_conflict=" + urllib.parse.quote(conflict)
        self._request("POST", path, data=data, headers={
            "Content-Type": "application/json",
            "Prefer": "resolution=merge-duplicates,return=minimal"})

    def update(self, table, values, column, value):
        path = ("/rest/v1/" + urllib.parse.quote(table) + "?" +
                urllib.parse.quote(column) + "=eq." + urllib.parse.quote(str(value)))
        self._request("PATCH", path, data=json.dumps(values).encode(), headers={
            "Content-Type": "application/json", "Prefer": "return=minimal"})

    def find(self, table, column, value):
        path = ("/rest/v1/" + urllib.parse.quote(table) + "?" +
                urllib.parse.quote(column) + "=eq." + urllib.parse.quote(str(value)) + "&limit=1")
        rows = json.loads(self._request("GET", path) or b"[]")
        if not isinstance(rows, list):
            raise SupabaseError("Invalid database response")
        return rows[0] if rows else None

    def list_rows(self, table, order="updated_at.desc", limit=100):
        if table not in {"factory_runs", "sticker_packs", "creator_requests", "admin_packs", "admin_events", "content_briefs"}:
            raise ValueError("Unsupported table")
        path = "/rest/v1/" + table + "?select=*&order=" + urllib.parse.quote(order) + "&limit=" + str(min(limit, 200))
        rows = json.loads(self._request("GET", path) or b"[]")
        if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
            raise SupabaseError("Invalid database list")
        return rows

    def update_versioned(self, table, values, row_id, version):
        if table != "admin_packs":
            raise ValueError("Unsupported versioned table")
        path = "/rest/v1/admin_packs?id=eq." + self._uuid(row_id) + "&version=eq." + str(int(version))
        rows = json.loads(self._request("PATCH", path, data=json.dumps(values).encode(), headers={
            "Content-Type": "application/json", "Prefer": "return=representation"}) or b"[]")
        if not rows:
            raise ValueError("Pack changed in another session; refresh before editing")
        return rows[0]

    def rpc(self, function, payload):
        if not re.fullmatch(r"[a-z][a-z0-9_]{0,63}", str(function or "")):
            raise ValueError("Invalid RPC name")
        data = self._request("POST", "/rest/v1/rpc/" + function,
                             data=json.dumps(payload, separators=(",", ":")).encode(),
                             headers={"Content-Type": "application/json"})
        parsed = json.loads(data or b"[]")
        if not isinstance(parsed, list):
            raise SupabaseError("Invalid RPC response")
        return parsed

    def claim_creator_request(self, worker_id):
        worker = " ".join(str(worker_id or "").split())
        if not 1 <= len(worker) <= 120:
            raise ValueError("Invalid worker id")
        rows = self.rpc("claim_creator_request", {"p_worker_id": worker})
        if not rows:
            return None
        if len(rows) != 1 or not isinstance(rows[0], dict):
            raise SupabaseError("Invalid creator request claim response")
        return rows[0]

    def update_creator_request(self, request_id, values):
        request_id = self._uuid(request_id)
        allowed = {"status", "result_path", "error_message", "completed_at", "worker_id"}
        if not isinstance(values, dict) or not values or not set(values).issubset(allowed):
            raise ValueError("Invalid creator request update")
        self.update("creator_requests", values, "id", request_id)


def public_catalog_base(url):
    return url.rstrip("/") + "/storage/v1/object/public/" + PUBLIC_BUCKET + "/catalog/"


@contextmanager
def catalog_lease(client):
    owner = str(uuid.uuid4())
    claimed = client.rpc("acquire_catalog_lease", {"p_owner": owner})
    if not claimed or claimed[0].get("acquired") is not True:
        raise ValueError("Another publication is active; retry shortly")
    try:
        yield
    finally:
        client.rpc("release_catalog_lease", {"p_owner": owner})


def require_production_environment():
    missing = [name for name in ("GROQ_API_KEY", "SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY")
               if not os.environ.get(name, "").strip()]
    if missing:
        raise ValueError("Missing required production configuration: " + ", ".join(missing))
