import base64
import hashlib
import io
import json
import unittest
import uuid
from unittest.mock import patch, MagicMock
from PIL import Image
from api import _cloud as cloud
from api._cloud_admin import public_artwork, dispatch as moderate
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
            if "cloud_assets?" in path: return json.dumps([{"sha256": s} for s, ready in self.assets.items() if ready]).encode()
            if "community_submissions?" in path: return b"[]"
            if "cloud_packs?" in path:
                owner = path.split("owner_id=eq.")[1].split("&")[0]
                return json.dumps([v for (o, _), v in self.data.items() if o == owner]).encode()
        if method == "POST" and path == "/rest/v1/cloud_packs":
            row = json.loads(data); self.data[(row["owner_id"], row["id"])] = row; return b""
        if method == "PATCH": return b"[]"
        raise AssertionError(path)


class CloudLibraryTest(unittest.TestCase):
    def test_sync_plan_recovers_missing_backup_and_server_upload_receipts(self):
        client = Client(); manifest = self.manifest()
        digest = manifest["stickers"][0]["sha256"]
        client.assets[digest] = True
        cloud.dispatch({"action": "commit", "manifest": manifest}, OWNER, client)
        current = cloud.dispatch({"action": "sync_plan", "manifest": dict(reversed(list(manifest.items()))), "revision": 0}, OWNER, client)
        self.assertEqual(current["state"], "current", "A lost client checkpoint should adopt the matching server snapshot")
        self.assertEqual(current["pack"]["revision"], 1)
        client.data.clear(); client.assets[digest] = False
        missing = cloud.dispatch({"action": "sync_plan", "manifest": manifest, "revision": 1}, OWNER, client)
        self.assertEqual(missing, {"state": "upload", "revision": 0, "missing_hashes": [digest]})
        self.assertEqual(client.data, {}, "Planning must never commit a pack with missing images")
        client.assets[digest] = True
        restored = cloud.dispatch({"action": "commit", "manifest": manifest, "revision": 0}, OWNER, client)
        self.assertEqual(restored["pack"]["revision"], 1)

    def test_plan_and_deletion_preserve_newer_cloud_edits(self):
        client = Client(); manifest = self.manifest()
        client.assets[manifest["stickers"][0]["sha256"]] = True
        cloud.dispatch({"action": "commit", "manifest": manifest}, OWNER, client)
        client.data[(OWNER, PACK)].update(revision=2, fingerprint="changed-on-other-phone")
        for action in ("sync_plan", "delete"):
            with self.assertRaises(cloud.CloudConflict):
                cloud.dispatch({"action": action, "id": PACK, "manifest": manifest, "revision": 1}, OWNER, client)
        self.assertEqual(client.data[(OWNER, PACK)]["revision"], 2)

    def test_effective_sharing_status_follows_moderator_decision(self):
        client = MagicMock()
        submission = {"status": "draft", "admin_pack_id": str(uuid.uuid4())}
        for status, expected in (("published", "published"), ("reviewed", "reviewed"), ("rejected", "rejected"), ("archived", "rejected")):
            client.find.return_value = {"status": status}
            self.assertEqual(cloud.sharing_status(client, submission), expected)
        self.assertEqual(cloud.sharing_status(client, {**submission, "status": "withdrawn"}), "withdrawn")
        self.assertEqual(cloud.sharing_status(client, None), "private")

    def test_review_draft_rejection_allows_new_snapshot_but_stale_share_does_not(self):
        client = MagicMock(); client.rpc.return_value = [{"allowed": True}]
        client.find.return_value = {"status": "rejected"}
        manifest = cloud.validate_manifest(self.manifest(stickers=[
            {"sha256": hashlib.sha256(bytes([i])).hexdigest()} for i in range(3)]))
        pack = {"id": PACK, "name": manifest["name"], "manifest": manifest, "fingerprint": "current"}
        submission = {"status": "draft", "admin_pack_id": str(uuid.uuid4())}
        def query(_client, table, _query): return [pack] if table == "cloud_packs" else [submission]
        with patch.object(cloud, "rows", side_effect=query):
            request = {"action": "share", "id": PACK, "rights_confirmed": True, "adult_confirmed": True, "fingerprint": "stale"}
            with self.assertRaises(cloud.CloudConflict): cloud.dispatch(request, OWNER, client)
            client.upsert.assert_not_called()
            self.assertEqual(cloud.dispatch({**request, "fingerprint": "current"}, OWNER, client)["status"], "pending")
        self.assertIsNone(client.upsert.call_args.args[1][0]["admin_pack_id"])

    def test_revision_conflict_http_contract(self):
        from api.cloud import handler
        request = handler.__new__(handler)
        request.headers = {}; request.wfile = io.BytesIO()
        statuses = []
        request.send_response = statuses.append
        request.send_header = lambda *_: None
        request.end_headers = lambda: None
        with patch("api.cloud.read_json", return_value={"action": "commit"}), patch("api.cloud.authenticate", return_value=OWNER), patch("api.cloud.dispatch", side_effect=cloud.CloudConflict("Restore first")):
            request.do_POST()
        self.assertEqual(statuses, [409])
        self.assertEqual(json.loads(request.wfile.getvalue())["code"], "revision_conflict")
    def test_submission_draft_is_private_valid_and_preserves_provenance(self):
        from test_admin_api import AdminStore
        from supabase_factory import PRIVATE_BUCKET, PUBLIC_BUCKET
        from publish_factory_pack import validate_pack
        client = AdminStore()
        stickers = []
        for i in range(3):
            output = io.BytesIO()
            Image.new("RGBA", (512, 512), (i*50, 100, 220, 255)).save(output, "WEBP", lossless=True, exif=b"Exif\x00\x00PRIVATE-LOCATION")
            data = output.getvalue(); digest = hashlib.sha256(data).hexdigest()
            client.upload(PRIVATE_BUCKET, cloud.asset_path(OWNER, digest), data, "image/webp")
            stickers.append({"sha256": digest, "label": "Reaction", "tags": ""})
        manifest = cloud.validate_manifest(self.manifest(stickers=stickers))
        submission_id = str(uuid.uuid4())
        client.rows[("community_submissions", submission_id)] = {"id": submission_id, "owner_id": OWNER,
            "name": manifest["name"], "manifest": manifest, "status": "pending", "admin_pack_id": None,
            "rights_statement": "Adult; redistribution rights confirmed"}
        result = moderate(client, {"action": "community_draft", "id": submission_id})
        row = client.rows[("admin_packs", result["id"])]
        archive = client.download(PRIVATE_BUCKET, row["private_path"])
        self.assertEqual(len(validate_pack(archive)["packs"][0]["stickers"]), 3)
        self.assertEqual(row["references"][0]["source_sha256"], stickers[0]["sha256"])
        self.assertEqual(row["status"], "draft")
        self.assertTrue(any(table == "community_submissions" and values.get("status") == "draft" and
                            values.get("admin_pack_id") == result["id"]
                            for table, values, _, _ in client.updates))
        with __import__('zipfile').ZipFile(io.BytesIO(archive)) as contents:
            for name in contents.namelist():
                if name.endswith('.webp'): self.assertNotIn(b"PRIVATE-LOCATION", contents.read(name))
        self.assertFalse(any(bucket == PUBLIC_BUCKET for bucket, _ in client.objects))
    def test_public_copy_strips_metadata_without_changing_pixels_or_animation(self):
        frames = [Image.new("RGBA", (512, 512), color) for color in ("red", "blue")]
        for animated in (False, True):
            output = io.BytesIO()
            frames[0].save(output, "WEBP", lossless=True, exif=b"Exif\x00\x00PRIVATE-GPS", xmp=b"PRIVATE-CREATOR",
                           save_all=animated, append_images=frames[1:] if animated else [], duration=100, loop=0)
            original = output.getvalue(); clean = public_artwork(original)
            self.assertNotIn(b"PRIVATE", clean)
            self.assertIn(b"PRIVATE", original)
            cloud.check_artwork(clean, "ANIMATED" if animated else "STATIC")
            with Image.open(io.BytesIO(original)) as before, Image.open(io.BytesIO(clean)) as after:
                self.assertEqual(before.n_frames, after.n_frames)
                for i in range(before.n_frames):
                    before.seek(i); after.seek(i)
                    self.assertEqual(before.convert("RGBA").tobytes(), after.convert("RGBA").tobytes())
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
