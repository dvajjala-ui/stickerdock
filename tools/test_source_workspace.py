import base64
import copy
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from PIL import Image
from api import _sources as sources
from api import _collection_control as control
from tools.hosted_collection import restore, save


class Store:
    def __init__(self):self.objects={};self.rows={}
    def upload(self,bucket,path,data,content_type):self.objects[path]=data
    def download(self,bucket,path,missing_ok=False):
        if missing_ok:return self.objects.get(path)
        return self.objects[path]
    def find(self,table,column,value):return copy.deepcopy(self.rows.get(value))
    def _request(self,method,path,data=None,headers=None):
        if method=='GET':return json.dumps([r for r in self.rows.values() if 'generation_eligible=eq.true' not in path or r.get('generation_eligible') and not r.get('archived')]).encode()
        body=json.loads(data)
        if method=='POST':
            row=body[0];row.update(version=1,title='',tags=[],archived=False);self.rows.setdefault(row['id'],row);return json.dumps([row]).encode()
        from urllib.parse import parse_qs,urlsplit
        q=parse_qs(urlsplit(path).query);identity=q['id'][0][3:];row=self.rows[identity]
        if row['version']!=int(q['version'][0][3:]):return b'[]'
        row.update(body);return json.dumps([row]).encode()


def image():
    out=io.BytesIO();Image.new('RGB',(320,240),'#ab7432').save(out,'PNG')
    return 'data:image/png;base64,'+base64.b64encode(out.getvalue()).decode()


class SourceWorkspaceTest(unittest.TestCase):
    def test_pinterest_reference_cannot_be_admitted_or_used_even_with_a_rights_claim(self):
        store=Store();row=sources.upload(store,{'image':image(),'title':'Original reaction','rights':'REFERENCE_ONLY'})['item']
        store.rows[row['id']]['platform']='pinterest'
        with self.assertRaisesRegex(ValueError,'live references only'):
            sources.admit(store,{'id':row['id'],'version':row['version'],'license':'USER_OWNED',
                'permission_note':'I claim I made this image.','quality_checked':True})
        with self.assertRaisesRegex(ValueError,'live references only'):
            sources.approved_credit(store,{**row,'platform':'pinterest','rights_status':'APPROVED','generation_eligible':True})

    def test_source_filters_include_registered_platforms_and_keep_the_exact_database_filter(self):
        options=[{'id':'flickr','name':'Flickr public tag feed'},{'id':'custom-1234567890abcdef','name':'Owned gallery'}]
        with patch.object(sources,'rows',return_value=[]) as query,patch.object(control,'source_options',return_value=options):
            result=sources.dispatch(Store(),{'action':'sources_list','platform':'flickr'})
            self.assertEqual(query.call_args.args[2]['platform'],'eq.flickr')
            self.assertEqual([s['id'] for s in result['source_options']],['flickr','custom-1234567890abcdef','upload','licensed','legacy'])
    def test_corrupt_cleanup_verifies_quarantine_and_preserves_referenced_sources(self):
        from tools.source_workspace_maintenance import maintain
        class AuditStore(Store):
            def list_rows(self,table):return [{'source_path':'inbox/protected.png'}] if table=='admin_packs' else []
            def list_objects(self,bucket,prefix,limit,offset=0):return [{'name':'broken.png','metadata':{'size':3}},{'name':'protected.png','metadata':{'size':3}}] if offset==0 else []
            def remove(self,bucket,paths):
                for p in paths:del self.objects[p]
        store=AuditStore();store.objects={'inbox/broken.png':b'bad','inbox/protected.png':b'bad'}
        result=maintain(store,True)
        self.assertEqual(result['removed'],1);self.assertEqual(result['protected'],1)
        self.assertNotIn('inbox/broken.png',store.objects);self.assertIn('inbox/protected.png',store.objects)
        self.assertTrue(any(p.startswith('quarantine/broken-sources/') for p in store.objects))

    def test_approval_enters_factory_and_creator_with_exact_provenance_then_revocation_removes_cached_input(self):
        from tools.creator_request_worker import creator_source_metadata
        store=Store();row=sources.upload(store,{'image':image(),'title':'My cat reaction','rights':'REFERENCE_ONLY'})['item']
        self.assertFalse(row['generation_eligible'])
        with self.assertRaises(ValueError):sources.creator_source(store,{'id':row['id']})
        row=sources.admit(store,{'id':row['id'],'version':row['version'],'license':'USER_OWNED',
            'permission_note':'This is my own original photograph.','quality_checked':True})['item']
        with tempfile.TemporaryDirectory() as temp:
            self.assertEqual(sources.sync_factory_sources(store,Path(temp)),1)
            manifest=json.loads((Path(temp)/'reference-images/library.json').read_text())
            self.assertEqual(manifest['assets'][0]['original_sha256'],row['original_sha256'])
            self.assertTrue(manifest['assets'][0]['admission_verified'])
            selected=sources.creator_source(store,{'id':row['id']});self.assertTrue(selected['path'].startswith('inbox/'))
            self.assertEqual(creator_source_metadata(store,row['stored_sha256'],selected['path'])['source_intake_id'],row['id'])
            sources.dispatch(store,{'action':'source_revoke','id':row['id'],'version':row['version']})
            self.assertEqual(sources.sync_factory_sources(store,Path(temp)),0)
            self.assertEqual(json.loads((Path(temp)/'reference-images/library.json').read_text())['assets'],[])
            with self.assertRaises(ValueError):sources.creator_source(store,{'id':row['id']})
            with self.assertRaises(ValueError):creator_source_metadata(store,row['stored_sha256'],selected['path'])

    def test_approval_requires_evidence_quality_and_fresh_version_and_rejects_tampered_pixels(self):
        store=Store();row=sources.upload(store,{'image':image(),'title':'Cat','rights':'REFERENCE_ONLY'})['item']
        spec={'id':row['id'],'version':row['version'],'license':'CC0','permission_note':'Creator CC0 permission at original source.','quality_checked':True}
        for change in [{'quality_checked':False},{'permission_note':''},{'version':0},{'license':'UNKNOWN'}]:
            with self.assertRaises(ValueError):sources.admit(store,{**spec,**change})
        store.objects[row['storage_path']]=b'broken'
        with self.assertRaisesRegex(ValueError,'checksum'):sources.admit(store,spec)

    def test_edit_invalidates_version_bound_admission_and_trash_restore_never_reapproves(self):
        store=Store();row=sources.upload(store,{'image':image(),'title':'Cat','rights':'USER_OWNED','quality_checked':True})['item']
        self.assertTrue(row['generation_eligible'])
        changed=control.admin_dispatch(store,{'action':'intake_update','id':row['id'],'version':row['version'],'title':'Updated','topic':'cat','tags':[],'rights_status':'APPROVED'})['item']
        with self.assertRaises(ValueError):sources.approved_credit(store,changed)
        trashed=sources.dispatch(store,{'action':'source_trash','id':changed['id'],'version':changed['version']})['item']
        restored=sources.dispatch(store,{'action':'source_trash','id':trashed['id'],'version':trashed['version'],'archived':False})['item']
        self.assertFalse(restored['generation_eligible']);self.assertIn(row['storage_path'],store.objects)

    def test_hosted_checkpoint_preserves_pending_originals_but_excludes_sessions_and_credentials(self):
        store=Store()
        with tempfile.TemporaryDirectory() as temp,tempfile.TemporaryDirectory() as target:
            root=Path(temp);(root/'profiles').mkdir();(root/'profiles/cookies').write_text('private')
            (root/'service-key.json').write_text('secret');(root/'downloads').mkdir();pending=root/'downloads/pending.webp';pending.write_bytes(b'pending')
            (root/'downloads/verified.webp').write_bytes(b'already in private storage')
            (root/'collections.json').write_text(json.dumps({'data':{'jobs':[{'status':'PARTIAL','items':[{'file':str(pending)}]}]}}))
            save(store,root);restore(store,target)
            self.assertEqual((Path(target)/'downloads/pending.webp').read_bytes(),b'pending')
            self.assertFalse((Path(target)/'service-key.json').exists());self.assertFalse((Path(target)/'profiles').exists())
            self.assertFalse((Path(target)/'downloads/verified.webp').exists())
