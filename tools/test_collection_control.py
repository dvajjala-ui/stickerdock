import json
import unittest
import uuid
from unittest.mock import patch
from api import _collection_control as c
from api import _media_intake as intake
from test_media_intake import Storage

class CollectionControlTest(unittest.TestCase):
    def test_capable_worker_uses_compatible_claim_while_legacy_contract_stays_supported(self):
        client=Storage();worker=str(uuid.uuid4())
        capabilities=[{'id':'pinterest','name':'Pinterest'},
                      {'id':'imgflip','name':'Imgflip','revision':'a'*64}]
        with patch.object(client,'rpc',create=True,return_value=[]) as rpc:
            self.assertEqual(c.worker_dispatch(client,{'action':'claim','worker_id':worker}),{'job':None})
            rpc.assert_called_once_with('claim_collection_job',{'p_worker_id':worker})
            rpc.reset_mock()
            c.worker_dispatch(client,{'action':'claim','worker_id':worker,'supported_sources':capabilities})
            rpc.assert_called_once_with('claim_compatible_collection_job',
                {'p_worker_id':worker,'p_sources':capabilities})

    def test_invalid_worker_capabilities_never_reach_claim_rpc(self):
        client=Storage();worker=str(uuid.uuid4())
        invalid=[None,[],['imgflip'],[{'id':'../escape','name':'Bad'}],
                 [{'id':'imgflip','name':''}],[{'id':'imgflip','name':'Imgflip','revision':'bad'}],
                 [{'id':'imgflip','name':'One'},{'id':'imgflip','name':'Two'}]]
        with patch.object(client,'rpc',create=True) as rpc:
            for sources in invalid:
                with self.subTest(sources=sources),self.assertRaises(ValueError):
                    c.worker_dispatch(client,{'action':'claim','worker_id':worker,'supported_sources':sources})
            rpc.assert_not_called()

    def test_unclaimable_new_source_is_rejected_before_creating_a_job(self):
        client=Storage();payload={'topics':['cats'],'sources':['pinterest','imgflip'],
            'image_limit':20,'request_id':'capability-test'}
        # Two workers supporting one site each cannot claim this combined job.
        with patch.object(client,'find',return_value=None),patch.object(c,'rows',return_value=[
            {'supported_sources':['pinterest','reddit']},{'supported_sources':['imgflip']}]), \
                patch.object(client,'_request') as request:
            with self.assertRaisesRegex(ValueError,'No configured HandNavigate worker'):
                c.enqueue(client,payload)
            request.assert_not_called()
        with patch.object(client,'find',return_value=None),patch.object(c,'rows',return_value=[
            {'supported_sources':['pinterest','reddit','imgflip']}]), \
                patch.object(client,'_request',return_value=b'[{"id":"accepted","topics":["cats"],"sources":["pinterest","imgflip"],"image_limit":20}]'):
            self.assertEqual(c.enqueue(client,payload)['job']['id'],'accepted')

    def test_existing_request_replay_remains_available_when_capable_worker_is_offline(self):
        client=Storage();job={'id':'saved','topics':['cats'],'sources':['imgflip'],'image_limit':20}
        with patch.object(client,'find',return_value=job),patch.object(c,'rows') as workers:
            result=c.enqueue(client,{**job,'request_id':'existing-imgflip'})
            self.assertTrue(result['reused']);workers.assert_not_called()

    def test_registered_sources_are_available_without_accepting_arbitrary_remote_recipes(self):
        client=Storage();source={'id':'custom-1234567890abcdef','name':'Owned gallery'}
        with patch.object(c,'source_options',return_value=[*c.SOURCE_OPTIONS,source]):
            self.assertEqual(c.spec({'topics':['cats'],'sources':[source['id']],'image_limit':2},client)['sources'],[source['id']])
            with self.assertRaisesRegex(ValueError,'configured'):
                c.spec({'topics':['cats'],'sources':['custom-unknown'],'image_limit':2},client)

    def test_worker_advertises_only_bounded_descriptors_and_uses_compatible_claim(self):
        client=Storage();worker=str(uuid.uuid4());source={'id':'custom-1234567890abcdef','name':'Owned gallery','revision':'a'*64,'cookie':'must not reach database','searchUrl':'https://private.example'}
        with patch.object(client,'rpc',create=True,return_value=[]) as rpc:
            self.assertEqual(c.worker_dispatch(client,{'action':'claim','worker_id':worker,'supported_sources':[source]}),{'job':None})
            self.assertEqual(rpc.call_args.args,('claim_compatible_collection_job',{'p_worker_id':worker,'p_sources':[{k:source[k] for k in ('id','name','revision')}]}))
            for invalid in ([],[source,source],[{**source,'id':'https://site.example'}],[{**source,'revision':'secret'}]):
                with self.assertRaises(ValueError):c.worker_dispatch(client,{'action':'claim','worker_id':worker,'supported_sources':invalid})

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
