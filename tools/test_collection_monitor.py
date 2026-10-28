import json
import unittest
import uuid
from unittest.mock import Mock,patch
from tools.enqueue_collections import wait_for_completion

class CollectionMonitorTest(unittest.TestCase):
    def setUp(self):
        self.identity=str(uuid.uuid4());self.submissions=[{'job':{'id':self.identity}}]
    def test_waits_for_verified_storage_rather_than_accepted_queue(self):
        client=Mock();states=[{'id':self.identity,'status':'RUNNING','delivered':0,'image_limit':20},{'id':self.identity,'status':'VERIFIED_STORAGE','delivered':20,'image_limit':20}]
        client._request.side_effect=[json.dumps([s]).encode() for s in states]
        with patch('tools.enqueue_collections.time.sleep'):
            self.assertEqual(wait_for_completion(client,self.submissions),[states[-1]])
        self.assertEqual(client._request.call_count,2)
    def test_partial_delivery_and_missing_jobs_fail_instead_of_claiming_daily_success(self):
        for state in ([],[{'id':self.identity,'status':'PARTIAL','delivered':19,'image_limit':20}]):
            client=Mock();client._request.return_value=json.dumps(state).encode()
            with self.assertRaises(ValueError):wait_for_completion(client,self.submissions)
    def test_no_jobs_needs_no_poll_and_expired_deadline_does_not_delete_requests(self):
        client=Mock();self.assertEqual(wait_for_completion(client,[]),[])
        with self.assertRaisesRegex(ValueError,'remain available'):wait_for_completion(client,self.submissions,timeout=0)
        client._request.assert_not_called()
