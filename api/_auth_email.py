"""Verified Supabase email hook -> signed, bounded Google MailApp relay.

No browser credentials, service-role access, templates or raw payload logging.
"""
import base64
import hashlib
import hmac
import json
import os
import re
import time
import urllib.parse
import urllib.request

MAX_BODY = 20_000
ACCOUNT_URL = "https://stickerdock.vercel.app/account"
SUPABASE_URL = "https://qqicsllktmcmvmzplsxl.supabase.co"
ACTIONS = {"signup", "recovery", "magiclink", "invite", "email_change", "reauthentication"}


def verify_webhook(body, headers, secret, now=None):
    """Standard Webhooks v1; signature covers the original, unparsed bytes."""
    if not secret:
        raise RuntimeError("Mail hook not configured")
    if not 1 <= len(body) <= MAX_BODY:
        raise ValueError("Invalid request")
    identifier = str(headers.get("webhook-id", ""))
    stamp = str(headers.get("webhook-timestamp", ""))
    signature = str(headers.get("webhook-signature", ""))
    if not re.fullmatch(r"[A-Za-z0-9_=-]{1,200}", identifier) or not re.fullmatch(r"\d{1,12}", stamp):
        raise ValueError("Invalid signature")
    if abs((time.time() if now is None else now) - int(stamp)) > 300:
        raise ValueError("Expired request")
    encoded = secret.removeprefix("v1,").removeprefix("whsec_")
    try:
        key = base64.b64decode(encoded, validate=True)
    except ValueError:
        raise RuntimeError("Mail hook not configured") from None
    if len(key) < 32:
        raise RuntimeError("Mail hook not configured")
    signed = identifier.encode() + b"." + stamp.encode() + b"." + body
    expected = base64.b64encode(hmac.digest(key, signed, "sha256")).decode()
    if not any(hmac.compare_digest(item, "v1," + expected) for item in signature.split()):
        raise ValueError("Invalid signature")
    return identifier


def _email(value):
    if not isinstance(value, str) or len(value) > 254 or not re.fullmatch(r"[^\s@,;<>]+@[^\s@,;<>]+\.[^\s@,;<>]+", value):
        raise ValueError("Invalid recipient")
    return value


def messages(event):
    """Use fixed verification/redirect hosts; ignore user-controlled metadata."""
    user, data = event["user"], event["email_data"]
    action = data["email_action_type"]
    if action not in ACTIONS:
        raise ValueError("Unsupported account email")
    recipients = [(user["email"], data.get("token_hash"), data.get("token", ""))]
    if action == "email_change":
        recipients = [(user["new_email"], data.get("token_hash"), data.get("token_new") or data.get("token", ""))]
        if data.get("token_hash_new"):
            recipients.append((user["email"], data["token_hash_new"], data.get("token", "")))
    result = []
    for recipient, token_hash, code in recipients:
        if action != "reauthentication" and (not isinstance(token_hash, str) or not re.fullmatch(r"[a-fA-F0-9]{40,128}", token_hash)):
            raise ValueError("Invalid verification token")
        if code and (not isinstance(code, str) or not re.fullmatch(r"\d{6,10}", code)):
            raise ValueError("Invalid verification code")
        if action == "reauthentication" and not code:
            raise ValueError("Missing verification code")
        query = urllib.parse.urlencode({"token": token_hash, "type": action, "redirect_to": ACCOUNT_URL})
        link = "" if action == "reauthentication" else SUPABASE_URL + "/auth/v1/verify?" + query
        result.append({"to": _email(recipient), "action": action, "link": link, "code": code})
    return result


def relay_envelope(message, identifier, secret, now=None):
    if len(secret) < 32:
        raise RuntimeError("Mail relay not configured")
    payload = base64.b64encode(json.dumps(message, separators=(",", ":"), ensure_ascii=False).encode()).decode()
    request_id = hashlib.sha256((identifier + ":" + message["to"] + ":" + message["action"]).encode()).hexdigest()
    stamp = int(time.time() if now is None else now)
    signed = f"{stamp}.{request_id}.{payload}".encode()
    return {"timestamp": stamp, "id": request_id, "payload": payload,
            "signature": base64.b64encode(hmac.digest(secret.encode(), signed, "sha256")).decode()}


def deliver(event, identifier):
    url = os.environ.get("GOOGLE_MAIL_RELAY_URL", "")
    secret = os.environ.get("GOOGLE_MAIL_RELAY_SECRET", "")
    if not re.fullmatch(r"https://script\.google\.com/macros/s/[A-Za-z0-9_-]+/exec", url):
        raise RuntimeError("Mail relay not configured")
    deadline = time.monotonic() + 4  # Supabase HTTP hooks have a five-second budget.
    for message in messages(event):
        envelope = relay_envelope(message, identifier, secret)
        request = urllib.request.Request(url, data=json.dumps(envelope).encode(),
            headers={"Content-Type": "application/json"}, method="POST")
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError("Mail relay timed out")
        with urllib.request.urlopen(request, timeout=min(3, remaining)) as response:
            # Google redirects ContentService responses to script.googleusercontent.
            result = json.loads(response.read(4096))
        if not isinstance(result, dict) or result.get("ok") is not True:
            raise RuntimeError("Mail relay failed")
