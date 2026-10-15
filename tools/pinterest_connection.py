"""Official read-only Pinterest connection. Never caches Pin/Board data or media."""
from __future__ import annotations

import base64
from http.cookies import SimpleCookie
import hashlib
import hmac
import json
import os
import re
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request

from tools.supabase_factory import PRIVATE_BUCKET, SupabaseFactory, catalog_lease

CONNECTION_PATH = "integrations/pinterest-connection.json"
CALLBACK = "https://stickerdock.vercel.app/api/pinterest_callback"
SCOPES = "boards:read,pins:read"
COOKIE = "__Host-stickerdock-pinterest"
DEFAULT_APP_ID = "1624615"


def provider(path, token=None, form=None):
    if not path.startswith("/") or ".." in path:
        raise ValueError("Invalid Pinterest endpoint")
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if form is not None:
        app_id, secret = os.environ.get("PINTEREST_APP_ID", DEFAULT_APP_ID), os.environ.get("PINTEREST_APP_SECRET", "")
        if not app_id.isdigit() or not secret:
            raise ValueError("Pinterest app credentials are not configured")
        headers.update({"Authorization": "Basic " + base64.b64encode((app_id + ":" + secret).encode()).decode(),
                        "Content-Type": "application/x-www-form-urlencoded"})
    request = urllib.request.Request("https://api.pinterest.com/v5" + path,
        data=urllib.parse.urlencode(form).encode() if form is not None else None, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            data = response.read(1_000_001)
    except urllib.error.HTTPError as error:
        messages = {401: "Pinterest token is expired or invalid; reconnect after approval.",
                    403: "Pinterest denied access. Verify Trial approval and read scopes.",
                    429: "Pinterest rate limit reached; try later."}
        raise ValueError(messages.get(error.code, "Pinterest is temporarily unavailable.")) from None
    if len(data) > 1_000_000:
        raise ValueError("Pinterest response is too large")
    result = json.loads(data)
    if not isinstance(result, dict):
        raise ValueError("Invalid Pinterest response")
    return result


def load_connection(client):
    raw = client.download(PRIVATE_BUCKET, CONNECTION_PATH, missing_ok=True)
    return json.loads(raw) if raw else {}


def save_tokens(client, result):
    access = result.get("access_token")
    if not isinstance(access, str) or not 10 <= len(access) <= 4096:
        raise ValueError("Pinterest returned no usable access token")
    expires = result.get("expires_in", 3600)
    if type(expires) is not int or not 0 < expires <= 366 * 86400:
        raise ValueError("Invalid Pinterest token expiration")
    row = {"access_token": access, "refresh_token": result.get("refresh_token", ""),
           "expires_at": int(time.time()) + expires, "connected_at": int(time.time())}
    client.upload(PRIVATE_BUCKET, CONNECTION_PATH, json.dumps(row).encode(), "application/json")


def access_token(client):
    row = load_connection(client)
    if row.get("disconnected"):
        return ""
    if row.get("access_token"):
        if row.get("expires_at", 0) <= time.time() + 90:
            if not row.get("refresh_token"):
                raise ValueError("Pinterest token expired; reconnect.")
            with catalog_lease(client):
                row = load_connection(client)
                if row.get("disconnected"):
                    return ""
                if row.get("expires_at", 0) <= time.time() + 90:
                    result = provider("/oauth/token", form={"grant_type": "refresh_token", "refresh_token": row["refresh_token"]})
                    result.setdefault("refresh_token", row["refresh_token"])
                    save_tokens(client, result)
                    row = load_connection(client)
        return row["access_token"]
    return os.environ.get("PINTEREST_ACCESS_TOKEN", "").strip()


def connection_status(client, check=False):
    row = load_connection(client)
    configured = bool(row.get("access_token") or os.environ.get("PINTEREST_ACCESS_TOKEN")) and not row.get("disconnected")
    result = {"configured": configured, "oauth_ready": bool(os.environ.get("PINTEREST_APP_ID", DEFAULT_APP_ID) and os.environ.get("PINTEREST_APP_SECRET")),
              "app_id": os.environ.get("PINTEREST_APP_ID", DEFAULT_APP_ID), "status": "configured" if configured else "not_connected",
              "website": "https://stickerdock.vercel.app/about", "privacy": "https://stickerdock.vercel.app/privacy",
              "callback": CALLBACK, "scopes": SCOPES, "mode": "live_reference_only", "message":
              "Official read-only browsing. Pin content is not stored or used to create stickers."}
    if check and configured:
        try:
            provider("/boards?page_size=1", access_token(client))
            result.update(status="connected", message="Official API connection verified.")
        except ValueError as error:
            result.update(status="blocked", message=str(error))
    return result


def browse(client, payload):
    token = access_token(client)
    if not token:
        raise ValueError("Connect an approved Pinterest app first")
    board = payload.get("board_id")
    if board is not None and not re.fullmatch(r"\d{5,25}", str(board)):
        raise ValueError("Invalid board ID")
    query = {"page_size": 25}
    bookmark = payload.get("bookmark")
    if bookmark:
        if not isinstance(bookmark, str) or len(bookmark) > 2000:
            raise ValueError("Invalid pagination cursor")
        query["bookmark"] = bookmark
    data = provider(("/boards/" + board + "/pins" if board else "/boards") + "?" + urllib.parse.urlencode(query), token)
    items = []
    for item in data.get("items", [])[:25]:
        identity = str(item.get("id", ""))
        if not re.fullmatch(r"\d{5,25}", identity):
            continue
        items.append({"id": identity, "name": str((item.get("title") if board else item.get("name")) or "")[:240],
                      "url": "https://www.pinterest.com/pin/" + identity + "/" if board else ""})
    return {"items": items, "bookmark": data.get("bookmark"), "pins": bool(board)}


def oauth_start():
    app_id = os.environ.get("PINTEREST_APP_ID", DEFAULT_APP_ID)
    signing_key = os.environ.get("STUDIO_ACCESS_TOKEN", "")
    if not app_id.isdigit() or not os.environ.get("PINTEREST_APP_SECRET") or len(signing_key) < 32:
        raise ValueError("Approve the Pinterest app, then configure PINTEREST_APP_ID and PINTEREST_APP_SECRET on the server")
    nonce = secrets.token_urlsafe(32)
    value = str(int(time.time())) + "." + nonce
    state = value + "." + hmac.new(signing_key.encode(), value.encode(), hashlib.sha256).hexdigest()
    url = "https://www.pinterest.com/oauth/?" + urllib.parse.urlencode({"client_id": app_id,
        "redirect_uri": CALLBACK, "response_type": "code", "scope": SCOPES, "state": state})
    return url, f"{COOKIE}={state}; Secure; HttpOnly; SameSite=Lax; Path=/; Max-Age=600"


def oauth_complete(query, cookies, client):
    values = urllib.parse.parse_qs(query)
    state, code = values.get("state", [""])[0], values.get("code", [""])[0]
    cookie = SimpleCookie()
    try:
        cookie.load(cookies or "")
        stored = cookie[COOKIE].value
        created, nonce, signature = state.split(".")
        value = created + "." + nonce
        expected = hmac.new(os.environ.get("STUDIO_ACCESS_TOKEN", "").encode(), value.encode(), hashlib.sha256).hexdigest()
        valid = len(os.environ.get("STUDIO_ACCESS_TOKEN", "")) >= 32 and len(nonce) >= 32 and 0 <= time.time() - int(created) <= 600
        valid = valid and hmac.compare_digest(stored, state) and hmac.compare_digest(expected, signature)
    except (KeyError, ValueError):
        valid = False
    if not valid or not code or len(code) > 4096:
        raise ValueError("Pinterest authorization expired or was declined. Start again from Admin.")
    with catalog_lease(client):
        save_tokens(client, provider("/oauth/token", form={"grant_type": "authorization_code", "code": code,
                    "redirect_uri": CALLBACK}))


if __name__ == "__main__":
    # Daily health check stores neither Pins nor Boards nor provider credentials in logs.
    status = connection_status(SupabaseFactory(), check=True)
    print(json.dumps({key: status[key] for key in ("configured", "status", "message", "mode")}))
