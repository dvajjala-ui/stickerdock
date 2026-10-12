"""Small Supabase REST client and shared factory storage helpers."""
from __future__ import annotations

import json
import os
from pathlib import Path, PurePosixPath
import urllib.error
import urllib.parse
import urllib.request


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
        request = urllib.request.Request(self.url + path, data=data, method=method, headers={
            "apikey": self.key, "Authorization": "Bearer " + self.key,
            **(headers or {})})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                result = response.read(75_000_001)
                if len(result) > 75_000_000:
                    raise SupabaseError("Supabase response exceeds 75MB")
                return result
        except urllib.error.HTTPError as error:
            if missing_ok and error.code == 404:
                return None
            raise SupabaseError(f"Supabase HTTP {error.code}; response body omitted") from None

    @staticmethod
    def _object_path(bucket, path):
        clean = PurePosixPath(path)
        if clean.is_absolute() or ".." in clean.parts or not clean.parts:
            raise ValueError("Unsafe storage object path")
        return urllib.parse.quote(bucket, safe="") + "/" + urllib.parse.quote(str(clean), safe="/")

    def download(self, bucket, path, missing_ok=False):
        endpoint = self._object_path(bucket, path)
        return self._request("GET", "/storage/v1/object/authenticated/" + endpoint,
                             missing_ok=missing_ok)

    def download_public(self, path, missing_ok=False):
        endpoint = self._object_path(PUBLIC_BUCKET, path)
        return self._request("GET", "/storage/v1/object/public/" + endpoint,
                             missing_ok=missing_ok)

    def upload(self, bucket, path, data, content_type="application/octet-stream"):
        if len(data) > 75_000_000:
            raise ValueError("Storage upload exceeds 75MB")
        endpoint = self._object_path(bucket, path)
        self._request("POST", "/storage/v1/object/" + endpoint, data=data, headers={
            "Content-Type": content_type, "x-upsert": "true"})

    def list_objects(self, bucket, prefix, limit=100):
        payload = json.dumps({"prefix": prefix, "limit": min(limit, 100),
                              "offset": 0, "sortBy": {"column": "name", "order": "asc"}}).encode()
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


def public_catalog_base(url):
    return url.rstrip("/") + "/storage/v1/object/public/" + PUBLIC_BUCKET + "/catalog/"


def require_production_environment():
    missing = [name for name in ("GROQ_API_KEY", "SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY")
               if not os.environ.get(name, "").strip()]
    if missing:
        raise ValueError("Missing required production configuration: " + ", ".join(missing))
