import json
import unittest
import uuid
from unittest.mock import patch
from api import _collection_control as c
from api import _media_intake as intake
from test_media_intake import Storage

class CollectionControlTest(unittest.TestCase):
    def test_daily_schedule_uses_atomic_budget_rpc_and_existing_planner_with_saved_fallback(self):
        client=Storage();plan={'id':str(uuid.uuid4()),'topics':['saved cats'],'sources':['reddit'],'image_limit':20,'use_planner':True}
        with patch.object(c,'rows',return_value=[plan]),patch.object(client,'find',return_value={'topic_plans':[{'topic':'fresh dogs'}]}),patch.object(client,'rpc',create=True,return_value=[]) as rpc:
            self.assertEqual(c.enqueue_daily(client,'2026-10-28'),[])
            self.assertEqual(rpc.call_args.args[1]['p_topics'],['fresh dogs'])
            self.assertEqual(rpc.call_args.args[1]['p_topic_origin'],'planner')
            self.assertEqual(rpc.call_args.args[1]['p_budget'],20)
        with patch.object(c,'rows',return_value=[plan]),patch.object(client,'find',return_value=None),patch.object(client,'rpc',create=True,return_value=[]) as rpc:
            c.enqueue_daily(client,'2026-10-28')
            self.assertEqual(rpc.call_args.args[1]['p_topics'],['saved cats'])
            self.assertEqual(rpc.call_args.args[1]['p_topic_origin'],'fallback')
    def test_invalid_topics_sources_and_limits_never_reach_the_database(self):
        base={'topics':['cats'],'sources':['reddit'],'image_limit':20}
        for change in ({'topics':[]},{'topics':[{}]},{'sources':['unknown']},{'sources':'reddit'},{'image_limit':True},{'image_limit':101}):
            with self.assertRaises(ValueError):c.spec({**base,**change})
        self.assertEqual(c.spec({**base,'topics':['cats','cats']})['topics'],['cats'])

    def test_request_identity_conflict_is_rejected(self):
        client=Storage();existing={'id':str(uuid.uuid4()),'topics':['dogs'],'sources':['reddit'],'image_limit':20}
        with patch.object(client,'find',return_value=existing):
            with self.assertRaisesRegex(ValueError,'different parameters'):
                c.enqueue(client,{'topics':['cats'],'sources':['reddit'],'image_limit':20,'request_id':'example-request'})

    def test_versioned_edit_uses_compare_and_swap_and_preserves_original_source(self):
        client=Storage();called={}
        def request(method,path,data,headers):
            called.update(method=method,path=path,values=json.loads(data));return b'[{"id":"saved"}]'
        with patch.object(client,'_request',side_effect=request):
            c.admin_dispatch(client,{'action':'intake_update','id':str(uuid.uuid4()),'version':3,'title':'Cat','topic':'cats','tags':['happy'],'rights_status':'APPROVED'})
        self.assertIn('version=eq.3',called['path']);self.assertEqual(called['values']['version'],4)
        self.assertNotIn('source_url',called['values']);self.assertFalse(called['values']['generation_eligible'])

    def test_empty_versioned_write_reports_conflict(self):
        client=Storage()
        with patch.object(client,'_request',return_value=b'[]'):
            with self.assertRaisesRegex(ValueError,'another session'):
                c.write(client,'collection_plans',{'name':'cats'},str(uuid.uuid4()),2)

    def test_existing_items_require_a_valid_version_before_any_database_write(self):
        client=Storage()
        with patch.object(client,'_request') as request:
            for version in (None,True,0,2147483647):
                with self.assertRaises(ValueError):c.write(client,'collection_plans',{'name':'cats'},str(uuid.uuid4()),version)
            request.assert_not_called()

    def test_worker_cannot_claim_completion_without_verified_image_count(self):
        client=Storage()
        with patch.object(client,'find',return_value={'image_limit':20}):
            with self.assertRaisesRegex(ValueError,'incomplete'):
                c.worker_dispatch(client,{'action':'report','worker_id':str(uuid.uuid4()),'id':str(uuid.uuid4()),'report':{'status':'VERIFIED_STORAGE','saved':20,'delivered':19}})

    def test_private_token_hash_authentication_and_origin_rejection(self):
        import hashlib
        client=Storage();token='a'*48
        client.objects['integrations/handnavigate-worker.json']=json.dumps({'token_sha256':hashlib.sha256(token.encode()).hexdigest()}).encode()
        with patch.dict('os.environ',{'HANDNAVIGATE_INGEST_TOKEN':''}),patch.object(intake,'SupabaseFactory',return_value=client),patch.object(client,'download',return_value=client.objects['integrations/handnavigate-worker.json']):
            self.assertTrue(intake.authorized({'Authorization':'Bearer '+token}))
            self.assertFalse(intake.authorized({'Authorization':'Bearer '+'b'*48}))
            self.assertFalse(intake.authorized({'Authorization':'Bearer '+token,'Origin':'https://evil.example'}))
