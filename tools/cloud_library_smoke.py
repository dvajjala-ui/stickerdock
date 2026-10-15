"""Live smoke with isolated disposable accounts and synthetic images only.

Never prints credentials or publishes a pack. Requires existing CI server secret.
"""
import base64
import hashlib
import io
import json
import os
import secrets
import urllib.error
import urllib.request
import uuid
from PIL import Image
from supabase_factory import SupabaseFactory, PRIVATE_BUCKET


BASE = "https://stickerdock.vercel.app"


def web(payload, token=None, path="/api/cloud", fail=False):
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=json.dumps(payload).encode(), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            data = json.loads(response.read(4_000_000))
    except urllib.error.HTTPError as error:
        if fail: return {"status": error.code}
        raise RuntimeError(f"Hosted {payload['action']} failed HTTP {error.code}; body omitted") from None
    if fail: raise AssertionError("Unauthorized operation succeeded")
    return data


def main():
    client = SupabaseFactory()
    users, paths = [], []
    tests = []
    try:
        web({"action": "list"}, fail=True); tests.append("anonymous denied")
        sessions = []
        for _ in range(2):
            password = secrets.token_urlsafe(40)
            email = "stickerdock-smoke-" + uuid.uuid4().hex + "@example.com"
            user = json.loads(client._request("POST", "/auth/v1/admin/users", data=json.dumps(
                {"email": email, "password": password, "email_confirm": True}).encode(),
                headers={"Content-Type": "application/json"}))
            users.append(user["id"])
            result = web({"action": "login", "email": email, "password": password})
            sessions.append(result["access_token"])
        tests.append("confirmed account sign-in")
        first, second = sessions
        manifest = {"id": str(uuid.uuid4()), "name": "Disposable cloud smoke", "category": "unknown",
                    "rating": "GENERAL", "kind": "STATIC", "stickers": []}
        originals = {}
        for i in range(3):
            output = io.BytesIO(); Image.new("RGBA", (512, 512), (50+i*30, 100, 200, 255)).save(output, "WEBP", lossless=True)
            data = output.getvalue(); digest = hashlib.sha256(data).hexdigest(); originals[digest] = data
            web({"action": "upload", "kind": "STATIC", "sha256": digest, "data": base64.b64encode(data).decode()}, first)
            paths.append(f"users/{users[0]}/{digest}.webp")
            manifest["stickers"].append({"sha256": digest, "label": "Synthetic smoke", "tags": "test"})
        saved = web({"action": "commit", "manifest": manifest}, first)["pack"]
        assert saved["category"] == "ALL" and saved["revision"] == 1
        assert web({"action": "commit", "manifest": manifest}, first)["pack"]["revision"] == 1
        tests.append("private images and categorized idempotent metadata")
        digest = manifest["stickers"][0]["sha256"]
        restored = web({"action": "download", "id": manifest["id"], "sha256": digest}, first)
        assert base64.b64decode(restored["data"]) == originals[digest]
        assert web({"action": "list"}, second)["packs"] == []
        web({"action": "download", "id": manifest["id"], "sha256": digest}, second, fail=True)
        tests.append("checksum restore and second-user isolation")
        web({"action": "share", "id": manifest["id"]}, first, fail=True)
        assert web({"action": "share", "id": manifest["id"], "rights_confirmed": True, "adult_confirmed": True}, first)["status"] == "pending"
        assert web({"action": "list"}, first)["submissions"][0]["status"] == "pending"
        assert web({"action": "withdraw", "id": manifest["id"]}, first)["status"] == "withdrawn"
        tests.append("rights gate, private review submission and withdrawal")
        for _ in range(2): assert web({"action": "delete", "id": manifest["id"]}, first)["status"] == "deleted"
        assert web({"action": "list"}, first)["packs"] == []
        tests.append("cloud deletion and safe retry")
        print(json.dumps({"passed": tests, "published": False, "personal_data_used": False}))
    finally:
        if paths:
            client._request("DELETE", "/storage/v1/object/" + PRIVATE_BUCKET,
                data=json.dumps({"prefixes": paths}).encode(), headers={"Content-Type": "application/json"})
        for user_id in users:
            client._request("DELETE", "/auth/v1/admin/users/" + user_id)
        print("Disposable account and image cleanup complete")


if __name__ == "__main__": main()
