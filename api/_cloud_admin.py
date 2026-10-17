"""Moderator access only to user-opted-in snapshots, never private libraries."""
import base64
import io
import json
import uuid
import zipfile
import hashlib
from api._cloud import rows, identity, asset_path, now, check_artwork, sharing_status
from tools.supabase_factory import PRIVATE_BUCKET, catalog_lease


def public_artwork(data):
    """Remove EXIF/XMP/custom metadata without recompressing pixels or animations.

    RIFF/VP8X layout: https://developers.google.com/speed/webp/docs/riff_container
    Private originals remain untouched; publication gets its own checksum.
    """
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP" or int.from_bytes(data[4:8], "little") + 8 != len(data):
        raise ValueError("Invalid WebP container")
    output = bytearray(b"WEBP")
    allowed = {b"VP8X", b"ICCP", b"ANIM", b"ANMF", b"ALPH", b"VP8 ", b"VP8L"}
    offset = 12
    while offset < len(data):
        if offset + 8 > len(data):
            raise ValueError("Truncated WebP chunk")
        tag = data[offset:offset+4]
        size = int.from_bytes(data[offset+4:offset+8], "little")
        end = offset + 8 + size + size % 2
        if end > len(data):
            raise ValueError("Invalid WebP chunk size")
        if tag in allowed:
            chunk = bytearray(data[offset:end])
            if tag == b"VP8X":
                if size != 10:
                    raise ValueError("Invalid extended WebP header")
                chunk[8] &= ~0x0C  # Clear EXIF and XMP presence bits.
            output.extend(chunk)
        offset = end
    return b"RIFF" + len(output).to_bytes(4, "little") + output


def dispatch(client, payload):
    from api._admin import store_draft, audit
    action = payload.get("action")
    if action == "community_list":
        submissions = rows(client, "community_submissions", "status=neq.withdrawn&order=updated_at.desc&limit=100")
        ids = ",".join(identity(s["admin_pack_id"]) for s in submissions if s.get("admin_pack_id"))
        decisions = rows(client, "admin_packs", f"id=in.({ids})&select=id,status&limit=100") if ids else []
        decision_map = {row["id"]: row for row in decisions}
        return {"submissions": [{**s, "effective_status": sharing_status(client, s, decision_map)} for s in submissions],
                "reports": rows(client, "community_reports", "status=eq.open&order=created_at.desc&limit=100")}
    if action == "report_resolve":
        report_id = identity(payload.get("id"))
        client.update("community_reports", {"status": "resolved"}, "id", report_id)
        audit(client, "community.report_resolved", report_id)
        return {"resolved": True}
    submission = client.find("community_submissions", "id", identity(payload.get("id")))
    if not submission or submission["status"] == "withdrawn":
        raise ValueError("Submission was withdrawn or is unavailable")
    if action == "community_detail":
        offset = max(0, int(payload.get("offset", 0)))
        items = []
        for item in submission["manifest"]["stickers"][offset:offset + 5]:
            data = client.download(PRIVATE_BUCKET, asset_path(submission["owner_id"], item["sha256"]))
            items.append({**item, "preview": "data:image/webp;base64," + base64.b64encode(data).decode()})
        return {"submission": submission, "stickers": items, "offset": offset}
    if action == "community_reject":
        if submission.get("admin_pack_id"):
            raise ValueError("Manage the review draft to reject or unpublish it")
        client.update("community_submissions", {"status": "rejected", "updated_at": now()}, "id", submission["id"])
        audit(client, "community.rejected", submission["id"])
        return {"rejected": True}
    if action == "community_draft":
        if submission.get("admin_pack_id"):
            return {"id": submission["admin_pack_id"]}
        pack = submission["manifest"]
        archive_pack = {**pack, "category": "GENERAL" if pack["category"] == "ALL" else pack["category"], "stickers": []}
        references = []
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
            for sticker in pack["stickers"]:
                name = str(uuid.uuid4()) + ".webp"
                data = client.download(PRIVATE_BUCKET, asset_path(submission["owner_id"], sticker["sha256"]))
                check_artwork(data, pack["kind"])
                data = public_artwork(data)
                check_artwork(data, pack["kind"])
                archive.writestr("packs/" + pack["id"] + "/" + name, data)
                archive_pack["stickers"].append({**sticker, "name": name, "sha256": hashlib.sha256(data).hexdigest()})
                references.append({"source": "user-submission", "submission_id": submission["id"],
                    "source_sha256": sticker["sha256"],
                    "rights_statement": submission["rights_statement"], "media_credit": {"license": "USER_OWNED"}})
            manifest = {"schema": 5, "packs": [archive_pack]}
            archive.writestr("manifest.json", json.dumps(manifest))
        with catalog_lease(client):
            current = client.find("community_submissions", "id", submission["id"])
            if not current or current["status"] == "withdrawn":
                raise ValueError("Submission was withdrawn during review")
            if current.get("admin_pack_id"):
                return {"id": current["admin_pack_id"]}
            result = store_draft(client, output.getvalue(), manifest, references, pack["name"], "StickerDock community")
            client.update("community_submissions", {"status": "draft", "admin_pack_id": result["id"], "updated_at": now()}, "id", submission["id"])
        audit(client, "community.draft_created", submission["id"])
        return result
    raise ValueError("Unknown community action")
