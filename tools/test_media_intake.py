import base64
import copy
import hashlib
import io
import json
import os
import unittest
import uuid
from unittest.mock import patch
from PIL import Image
from api import _media_intake as intake
from handnavigate_client import HandNavigate, brief_topics


class Storage:
    def __init__(self):
        self.objects, self.rows = {}, {}
        self.writes = 0
    def find(self, table, column, value):
        return copy.deepcopy(self.rows.get(value))
    def upload(self, bucket, path, data, content_type):
        self.objects[path] = data
        self.writes += 1
    def download(self, bucket, path):
        return self.objects[path]
    def _request(self, method, path, data, headers):
        row = json.loads(data)[0]
        self.rows.setdefault(row["id"], row)
        return json.dumps([self.rows[row["id"]]]).encode()


def payload():
    output = io.BytesIO()
    Image.new("RGB", (400, 300), "#18b344").save(output, "PNG")
    return {"action": "store", "collection_id": str(uuid.uuid4()), "platform": "reddit", "topic": "happy dogs",
        "title": "Happy dog", "tags": ["openverse", "reported-license:CC0"],
        "source_url": "https://www.reddit.com/comments/example/", "download_url": "https://i.redd.it/example.png",
        "original_sha256": hashlib.sha256(output.getvalue()).hexdigest(),
        "image": "data:image/png;base64," + base64.b64encode(output.getvalue()).decode()}


class MediaIntakeTest(unittest.TestCase):
    def test_worker_daily_enqueue_uses_server_day_and_saved_plans(self):
        from datetime import datetime
        from zoneinfo import ZoneInfo
        client=Storage()
        with patch('api._collection_control.enqueue_daily',return_value=[]) as enqueue:
            self.assertEqual(intake.dispatch({'action':'enqueue_daily','topics':['ignored'],'image_limit':10000},client),{'jobs':[]})
        enqueue.assert_called_once_with(client,datetime.now(ZoneInfo('Asia/Kolkata')).strftime('%Y-%m-%d'))

    def test_history_pages_expose_only_duplicate_identity_and_provenance(self):
        from unittest.mock import Mock
        from urllib.parse import parse_qs, urlsplit
        client=Mock()
        client._request.return_value=json.dumps([{'original_sha256':'a'*64,'source_url':'https://example.com/source',
                                                 'storage_path':'private/path','rights_status':'UNREVIEWED'}]).encode()
        result=intake.dispatch({'action':'history','offset':200},client)
        self.assertEqual(result,{'items':[{'sha256':'a'*64,'source':'https://example.com/source'}],'next_offset':None})
        method,url=client._request.call_args.args
        self.assertEqual(method,'GET')
        query=parse_qs(urlsplit(url).query)
        self.assertEqual(query['select'],['original_sha256,source_url'])
        self.assertEqual(query['limit'],['200']);self.assertEqual(query['offset'],['200'])

    def test_history_rejects_invalid_offsets_before_database_access(self):
        from unittest.mock import Mock
        for offset in (True,-1,20001,'0',0.5):
            client=Mock()
            with self.assertRaises(ValueError):intake.dispatch({'action':'history','offset':offset},client)
            client._request.assert_not_called()

    def test_server_token_is_required_and_browser_origins_are_rejected(self):
        with patch.dict(os.environ, {"HANDNAVIGATE_INGEST_TOKEN": "a" * 40}):
            self.assertFalse(intake.authorized({}))
            self.assertFalse(intake.authorized({"Authorization": "Bearer wrong"}))
            self.assertTrue(intake.authorized({"Authorization": "Bearer " + "a" * 40}))
            self.assertFalse(intake.authorized({"Authorization": "Bearer " + "a" * 40, "Origin": "https://evil.example"}))

    def test_actual_pixels_and_full_provenance_are_stored_outside_factory_inbox(self):
        client, spec = Storage(), payload()
        result = intake.dispatch(spec, client)
        row = result["item"]
        self.assertEqual(row["rights_status"], "UNREVIEWED")
        self.assertFalse(row["generation_eligible"])
        self.assertTrue(row["storage_path"].startswith("handnavigate/candidates/"))
        self.assertEqual(row["source_url"], spec["source_url"])
        self.assertEqual(row["topic"], spec["topic"])
        self.assertEqual(row["title"], "Happy dog")
        self.assertEqual(row["tags"], ["openverse", "reported-license:CC0"])
        fetched = intake.dispatch({"action": "get", "id": row["id"]}, client)
        raw = base64.b64decode(fetched["image"].split(",", 1)[1])
        self.assertEqual(hashlib.sha256(raw).hexdigest(), row["stored_sha256"])
        with Image.open(io.BytesIO(raw)) as image:
            image.load()
            self.assertEqual(image.size, (400, 300))

    def test_repeated_delivery_reuses_the_exact_database_row_without_another_write(self):
        client, spec = Storage(), payload()
        first = intake.dispatch(spec, client)
        second = intake.dispatch({**spec, "collection_id": str(uuid.uuid4())}, client)
        self.assertEqual(first["item"]["id"], second["item"]["id"])
        self.assertTrue(second["reused"])
        self.assertEqual(client.writes, 1)

    def test_gallery_thumbnail_keeps_provenance_and_original_download_digest(self):
        client=Storage();row=intake.dispatch(payload(),client)['item']
        thumbnail=intake.dispatch({'action':'thumbnail','id':row['id']},client)
        with Image.open(io.BytesIO(base64.b64decode(thumbnail['image'].split(',')[1]))) as image:
            image.load();self.assertLessEqual(max(image.size),360)
        original=intake.dispatch({'action':'get','id':row['id']},client)
        self.assertEqual(hashlib.sha256(base64.b64decode(original['image'].split(',')[1])).hexdigest(),row['stored_sha256'])
        self.assertEqual(thumbnail['item']['source_url'],row['source_url'])

    def test_changed_provenance_cannot_overwrite_existing_identity(self):
        client, spec = Storage(), payload()
        intake.dispatch(spec, client)
        with self.assertRaisesRegex(ValueError, "conflicts"):
            intake.dispatch({**spec, "source_url": "https://wrong.example/"}, client)
        self.assertEqual(client.writes, 1)

    def test_invalid_url_or_image_never_writes_storage(self):
        for values in ({"source_url": "http://unsafe.example/"}, {"image": "data:image/png;base64,AAAA"},
                       {"tags": ["x" * 41]}, {"tags": ["ok"] * 21}):
            client = Storage()
            with self.assertRaises(ValueError):
                intake.dispatch({**payload(), **values}, client)
            self.assertEqual(client.writes, 0)

    def test_stickerdock_caller_has_a_stable_daily_request_and_changing_parameters(self):
        client = HandNavigate("http://127.0.0.1:47832", "a" * 40)
        with patch.object(client, "call", side_effect=lambda path, body: body):
            first = client.collect(["cats"], ["reddit"], day="2026-10-28")
            self.assertEqual(first, client.collect(["cats"], ["reddit"], day="2026-10-28"))
            self.assertNotEqual(first["requestId"], client.collect(["dogs"], ["reddit"], day="2026-10-28")["requestId"])
            self.assertEqual(first["limit"], 20)
            self.assertEqual(first["destination"], "stickerdock")

    def test_caller_rejects_unencrypted_remote_service(self):
        with self.assertRaises(ValueError):
            HandNavigate("http://remote.example", "a" * 40)

    def test_existing_planner_topic_plans_are_used_without_an_ai_call(self):
        self.assertEqual(brief_topics({"topic_plans": [{"topic": "cats"}, {"topic": "dogs"}, {"topic": "cats"}]}), ["cats", "dogs"])

    def test_caller_waits_past_acceptance_and_returns_a_failed_or_verified_outcome(self):
        client = HandNavigate("http://127.0.0.1:47832", "a" * 40)
        with patch.object(client, "call", side_effect=[{"status": "RUNNING"}, {"status": "VERIFIED_STORAGE"}]):
            self.assertEqual(client.wait(str(uuid.uuid4()), interval=0)["status"], "VERIFIED_STORAGE")
        with patch.object(client, "call", return_value={"status": "PARTIAL"}):
            self.assertEqual(client.wait(str(uuid.uuid4()), interval=0)["status"], "PARTIAL")
