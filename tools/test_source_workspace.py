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
        if method=='GET':return json.dumps([r for r in self.rows.values() if r.get('generation_eligible') and not r.get('archived')]).encode()
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
    def test_approval_enters_factory_and_creator_with_exact_provenance_then_revocation_removes_cached_input(self):
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
            sources.dispatch(store,{'action':'source_revoke','id':row['id'],'version':row['version']})
            self.assertEqual(sources.sync_factory_sources(store,Path(temp)),0)
            self.assertEqual(json.loads((Path(temp)/'reference-images/library.json').read_text())['assets'],[])
            with self.assertRaises(ValueError):sources.creator_source(store,{'id':row['id']})

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
