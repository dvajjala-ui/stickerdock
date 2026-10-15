import base64
import hashlib
import io
import json
import unittest
import uuid
from unittest.mock import patch, MagicMock
from PIL import Image
from api import _cloud as cloud
from api._shared import StudioError


OWNER = str(uuid.uuid4())
OTHER = str(uuid.uuid4())
PACK = str(uuid.uuid4())


def art():
    out = io.BytesIO()
    Image.new("RGBA", (512, 512), (10, 100, 220, 255)).save(out, "WEBP", lossless=True)
    return out.getvalue()


class Client:
    def __init__(self):
        self.data = {}; self.assets = {}; self.uploads = []
    def rpc(self, action, payload):
        return [{"allowed": True, "reserved": True}]
    def upload(self, bucket, path, data, mime):
        self.uploads.append(path)
    def _request(self, method, path, data=None, headers=None):
        if method == "GET":
            if "cloud_assets?" in path: return json.dumps([{"sha256": s} for s in self.assets]).encode()
            if "community_submissions?" in path: return b"[]"
            if "cloud_packs?" in path:
                owner = path.split("owner_id=eq.")[1].split("&")[0]
                return json.dumps([v for (o, _), v in self.data.items() if o == owner]).encode()
        if method == "POST" and path == "/rest/v1/cloud_packs":
            row = json.loads(data); self.data[(row["owner_id"], row["id"])] = row; return b""
        if method == "PATCH": return b"[]"
        raise AssertionError(path)


class CloudLibraryTest(unittest.TestCase):
    def manifest(self, **kw):
        return {"id": PACK, "name": "Private reactions", "kind": "STATIC", "rating": "GENERAL",
                "category": "unknown", "stickers": [{"sha256": hashlib.sha256(art()).hexdigest(), "label": "hello", "tags": "reaction"}], **kw}
    def test_unknown_and_uncategorized_fall_back_to_all(self):
        for category in ("unknown", "OTHER", "GENERAL", "ALL", ""):
            self.assertEqual(cloud.validate_manifest(self.manifest(category=category))["category"], "ALL")
        self.assertEqual(cloud.validate_manifest(self.manifest(category="MOVIES"))["category"], "MOVIES")
    def test_mature_never_enters_cloud(self):
        with self.assertRaisesRegex(StudioError, "Mature"):
            cloud.validate_manifest(self.manifest(rating="MATURE"))
    def test_duplicate_and_invalid_hash_rejected(self):
        m = self.manifest(); m["stickers"] *= 2
        with self.assertRaises(StudioError): cloud.validate_manifest(m)
        m["stickers"] = [{"sha256": "../../escape"}]
        with self.assertRaises(StudioError): cloud.validate_manifest(m)
    def test_sticker_decode_size_and_kind_validation(self):
        cloud.check_artwork(art(), "STATIC")
        for data, kind in ((b"invalid", "STATIC"), (art(), "ANIMATED"), (b"a"*100001, "STATIC")):
            with self.assertRaises(StudioError): cloud.check_artwork(data, kind)
    def test_commit_requires_assets_and_retry_is_idempotent(self):
        client = Client(); payload = {"action": "commit", "manifest": self.manifest()}
        with self.assertRaisesRegex(StudioError, "Upload"): cloud.dispatch(payload, OWNER, client)
        client.assets[hashlib.sha256(art()).hexdigest()] = True
        first = cloud.dispatch(payload, OWNER, client)
        self.assertEqual(first, cloud.dispatch(payload, OWNER, client))
        self.assertEqual(first["pack"]["category"], "ALL")
        changed = {"action": "commit", "manifest": self.manifest(name="Edited")}
        with self.assertRaisesRegex(StudioError, "another device"): cloud.dispatch(changed, OWNER, client)
    def test_other_user_cannot_read_or_share_pack(self):
        client = Client(); client.assets[hashlib.sha256(art()).hexdigest()] = True
        cloud.dispatch({"action": "commit", "manifest": self.manifest()}, OWNER, client)
        self.assertEqual(cloud.dispatch({"action": "list"}, OTHER, client)["packs"], [])
        for action in ("download", "share", "withdraw"):
            with self.assertRaisesRegex(StudioError, "not found"):
                cloud.dispatch({"action": action, "id": PACK, "sha256": hashlib.sha256(art()).hexdigest()}, OTHER, client)
        self.assertEqual(cloud.dispatch({"action": "delete", "id": PACK}, OTHER, client), {"status": "deleted"})
        self.assertIn((OWNER, PACK), client.data)
    def test_upload_path_is_derived_from_verified_user_and_checksum(self):
        client = Client(); data = art(); digest = hashlib.sha256(data).hexdigest()
        cloud.dispatch({"action": "upload", "kind": "STATIC", "sha256": digest,
                        "data": base64.b64encode(data).decode(), "path": "other-user/escape"}, OWNER, client)
        self.assertEqual(client.uploads, [f"users/{OWNER}/{digest}.webp"])
    def test_unconfirmed_or_invalid_sessions_fail_closed(self):
        with self.assertRaises(PermissionError): cloud.authenticate({})
        with patch.object(cloud, "auth_call", return_value={"id": OWNER}):
            with self.assertRaises(PermissionError): cloud.authenticate({"Authorization": "Bearer invalid"})
    def test_signup_confirmation_message_does_not_implicitly_sign_in(self):
        with patch.object(cloud, "auth_call", return_value={"user": {"id": OWNER}}):
            result = cloud.auth_action({"action": "signup", "email": "someone@example.com", "password": "long-passphrase"})
        self.assertNotIn("access_token", result)
        self.assertIn("confirm", result["message"])
    def test_rejected_snapshot_can_be_updated_and_resubmitted(self):
        client = MagicMock(); client.rpc.return_value = [{"allowed": True}]
        manifest = cloud.validate_manifest(self.manifest(stickers=[
            {"sha256": hashlib.sha256(bytes([i])).hexdigest(), "label": "Reaction", "tags": ""} for i in range(3)]))
        pack = {"id": PACK, "name": manifest["name"], "manifest": manifest, "fingerprint": "a"*64}
        def query(_client, table, _query):
            return [pack] if table == "cloud_packs" else [{"id": str(uuid.uuid4()), "status": "rejected"}]
        with patch.object(cloud, "rows", side_effect=query):
            result = cloud.dispatch({"action": "share", "id": PACK, "rights_confirmed": True, "adult_confirmed": True}, OWNER, client)
        self.assertEqual(result["status"], "pending")
        self.assertEqual(client.upsert.call_args.args[1][0]["manifest"], manifest)
        client.upload.assert_not_called()


if __name__ == "__main__": unittest.main()
