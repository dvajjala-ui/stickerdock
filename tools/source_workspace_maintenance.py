"""Index legacy source images; remove only verified corrupt, unreferenced bytes."""
from __future__ import annotations
import argparse
import base64
import hashlib
import io
import json
import uuid
import zipfile
from pathlib import PurePosixPath
from PIL import Image
from api._collection_control import rows, write
from api._media_intake import dispatch
from tools.supabase_factory import PRIVATE_BUCKET, SupabaseFactory


def maintain(client, cleanup=False):
    protected=json.dumps(client.list_rows('admin_packs'),sort_keys=True)+json.dumps(client.list_rows('creator_requests'),sort_keys=True)
    summary={'indexed':0,'existing':0,'corrupt':0,'removed':0,'protected':0,'oversized':0}
    def objects():
        for offset in range(0,10000,100):
            page=client.list_objects(PRIVATE_BUCKET,'inbox',100,offset=offset)
            yield from page
            if len(page)<100:break
    # Materialize identities before cleanup; removing a row must not shift the
    # storage-list offset and skip the next image.
    for entry in list(objects()):
        name=str(entry.get('name',''));path=name if name.startswith('inbox/') else 'inbox/'+name
        if PurePosixPath(path).suffix.lower() not in {'.png','.jpg','.jpeg','.webp'}:continue
        if int((entry.get('metadata') or {}).get('size') or 0)>3000000:
            summary['oversized']+=1;continue
        raw=client.download(PRIVATE_BUCKET,path)
        digest=hashlib.sha256(raw).hexdigest()
        try:
            with Image.open(io.BytesIO(raw)) as image:
                if image.width*image.height>20000000:raise ValueError('Pixel bound exceeded')
                if getattr(image,'is_animated',False):continue
                image.verify()
            with Image.open(io.BytesIO(raw)) as image:image.load()
        except (OSError,ValueError,Image.DecompressionBombError):
            summary['corrupt']+=1
            if path in protected or digest in protected:summary['protected']+=1;continue
            if cleanup:
                # Recoverable private quarantine, verified before removing the
                # corrupt inbox copy. Existing packs/history are never reset.
                quarantine='quarantine/broken-sources/'+digest+'.zip'
                bundle=io.BytesIO()
                with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as archive:archive.writestr('original.bin',raw)
                client.upload(PRIVATE_BUCKET,quarantine,bundle.getvalue(),'application/zip')
                if client.download(PRIVATE_BUCKET,quarantine)!=bundle.getvalue():raise ValueError('Quarantine verification failed')
                client.remove(PRIVATE_BUCKET,[path]);summary['removed']+=1
            continue
        meta_raw=client.download(PRIVATE_BUCKET,'source-metadata/'+digest+'.json',missing_ok=True)
        try:meta=json.loads(meta_raw or b'{}')
        except ValueError:meta={}
        if meta.get('intake_id'):continue
        if client.find('media_intake_items','id',str(uuid.uuid5(uuid.NAMESPACE_URL,'handnavigate:'+digest))):
            summary['existing']+=1;continue
        original_source=str(meta.get('source',''))
        platform='licensed' if original_source=='licensed-library' else 'upload' if original_source in {'creator-studio','manual-upload'} else 'legacy'
        link=meta.get('source_url','');download=meta.get('download_url','')
        fallback='https://stickerdock.vercel.app/admin#sources'
        image='data:image/'+('jpeg' if path.lower().endswith(('.jpg','.jpeg')) else 'png' if path.lower().endswith('.png') else 'webp')+';base64,'+base64.b64encode(raw).decode()
        # This index does not grant new reuse permission or enable generation.
        result=dispatch({'action':'store','collection_id':str(uuid.uuid5(uuid.NAMESPACE_URL,'stickerdock:legacy-index')),
            'platform':platform,'topic':str(meta.get('title') or 'Earlier source image')[:240],
            'source_url':link if link.startswith('https://') else fallback,
            'download_url':download if download.startswith('https://') else fallback,
            'original_sha256':digest,'image':image},client)
        if result['reused']:summary['existing']+=1
        else:
            row=result['item'];write(client,'media_intake_items',{'title':str(meta.get('title') or 'Earlier source image')[:160]},row['id'],row['version']);summary['indexed']+=1
    summary['broken_candidates']=0
    for offset in range(0,10000,100):
        page=rows(client,'media_intake_items',limit=100,offset=offset)
        for row in page:
            data=client.download(PRIVATE_BUCKET,row['storage_path'])
            try:
                if hashlib.sha256(data).hexdigest()!=row['stored_sha256']:raise ValueError('Checksum mismatch')
                with Image.open(io.BytesIO(data)) as image:image.verify()
            except (OSError,ValueError,Image.DecompressionBombError):
                summary['broken_candidates']+=1
                if cleanup and not row.get('archived'):
                    write(client,'media_intake_items',{'archived':True,'generation_eligible':False},row['id'],row['version'])
        if len(page)<100:break
    return summary


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--cleanup-broken',action='store_true');args=p.parse_args()
    print(json.dumps(maintain(SupabaseFactory(),args.cleanup_broken),sort_keys=True))
