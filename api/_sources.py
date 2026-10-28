"""One source library and explicit, provenance-bound Factory admission."""
from __future__ import annotations
import base64
import hashlib
import json
import re
import uuid
from api._shared import StudioError, decode_image, safe_text
from api._collection_control import rows, write
from api._media_intake import dispatch as intake
from tools.supabase_factory import PRIVATE_BUCKET

LICENSES = {'CC0', 'PUBLIC_DOMAIN', 'USER_OWNED'}


def receipt_path(identity):
    return 'source-reviews/' + str(uuid.UUID(str(identity))) + '.json'


def approved_credit(client, row):
    if not row or row.get('archived') or row.get('rights_status') != 'APPROVED' or not row.get('generation_eligible'):
        raise StudioError('Source has not been approved for generation')
    raw = client.download(PRIVATE_BUCKET, receipt_path(row['id']))
    credit = json.loads(raw)
    if (credit.get('license') not in LICENSES or credit.get('candidate_version') != row['version']
            or credit.get('stored_sha256') != row['stored_sha256'] or credit.get('intake_id') != row['id']):
        raise StudioError('Source approval changed; review this image again')
    return credit


def admitted_image(client, row):
    credit = approved_credit(client, row)
    raw = client.download(PRIVATE_BUCKET, row['storage_path'])
    if hashlib.sha256(raw).hexdigest() != row['stored_sha256']:
        raise StudioError('Source checksum mismatch')
    decode_image('data:image/webp;base64,' + base64.b64encode(raw).decode())
    return raw, credit


def admit(client, payload):
    identity = str(uuid.UUID(str(payload.get('id', ''))))
    row = client.find('media_intake_items', 'id', identity)
    if not row or row.get('archived') or row.get('version') != payload.get('version'):
        raise StudioError('Refresh this source before approving it')
    license = payload.get('license')
    if license not in LICENSES or payload.get('quality_checked') is not True:
        raise StudioError('Confirm reusable rights and visual quality before generation')
    note = safe_text(payload.get('permission_note'), 'permission evidence', 600, required=True)
    if len(note) < 12:
        raise StudioError('Describe ownership or the original permission/license evidence')
    original = client.download(PRIVATE_BUCKET, row['storage_path'])
    if hashlib.sha256(original).hexdigest() != row['stored_sha256']:
        raise StudioError('Source checksum mismatch')
    decode_image('data:image/webp;base64,' + base64.b64encode(original).decode())
    credit = {'source': 'handnavigate' if row['platform'] in {'pinterest','reddit','imgflip'} else row['platform'],
              'platform': row['platform'], 'source_url': row['source_url'], 'download_url': row['download_url'],
              'license': license, 'title': row.get('title') or row['topic'], 'purpose': 'SOURCE',
              'credit': note, 'permission_note': note, 'intake_id': identity,
              'stored_sha256': row['stored_sha256'], 'original_sha256': row['original_sha256'],
              'candidate_version': row['version'] + 1, 'admission_verified': True}
    # A failed compare-and-swap cannot enable an orphan approval receipt: readers
    # require the exact row version, checksum and generation flag together.
    client.upload(PRIVATE_BUCKET, receipt_path(identity), json.dumps(credit).encode(), 'application/json')
    saved = write(client, 'media_intake_items', {'rights_status':'APPROVED', 'generation_eligible':True}, identity, row['version'])
    return {'item': saved}


def creator_source(client, payload):
    row = client.find('media_intake_items', 'id', str(uuid.UUID(str(payload.get('id','')))))
    raw, credit = admitted_image(client, row)
    path = 'inbox/' + row['stored_sha256'] + '.webp'
    client.upload(PRIVATE_BUCKET, 'source-metadata/' + row['stored_sha256'] + '.json', json.dumps(credit).encode(), 'application/json')
    client.upload(PRIVATE_BUCKET, path, raw, 'image/webp')
    return {'path': path, 'name': credit['title'], 'size':len(raw)}


def upload(client, payload):
    raw, _, _ = decode_image(payload.get('image'))
    digest = hashlib.sha256(raw).hexdigest()
    title = safe_text(payload.get('title'), 'title', 160, required=True)
    result = intake({'action':'store', 'collection_id':str(uuid.uuid5(uuid.NAMESPACE_URL,'stickerdock:uploads')),
                     'platform':'upload', 'topic':safe_text(payload.get('topic') or title,'topic',240,required=True),
                     'source_url':'https://stickerdock.vercel.app/admin#sources',
                     'download_url':'https://stickerdock.vercel.app/admin#sources',
                     'original_sha256':digest, 'image':payload['image']},client)
    row=result['item']
    if not result['reused']:
        row=write(client,'media_intake_items',{'title':title},row['id'],row['version'])
    if payload.get('rights') == 'USER_OWNED' and not row.get('generation_eligible'):
        row=admit(client,{'id':row['id'],'version':row['version'],'license':'USER_OWNED',
                         'permission_note':'Owner confirms this uploaded image is their own reusable artwork.',
                         'quality_checked':payload.get('quality_checked') is True})['item']
    return {'item':row, 'reused':result['reused']}


def dispatch(client, payload):
    action=payload['action']
    if action=='sources_list':
        offset=payload.get('offset',0)
        if type(offset) is not int or not 0<=offset<=1000000:raise StudioError('Invalid source page')
        filters={'archived':'eq.'+str(payload.get('archived') is True).lower()}
        platform=payload.get('platform','')
        if platform:
            if not re.fullmatch(r'[a-z][a-z0-9_-]{1,39}',platform):raise StudioError('Invalid source filter')
            filters['platform']='eq.'+platform
        state=payload.get('state','')
        if state=='ready':filters['generation_eligible']='eq.true'
        elif state in {'UNREVIEWED','REJECTED','APPROVED'}:filters['rights_status']='eq.'+state
        query=safe_text(payload.get('query',''),'search',100)
        if query:
            query=re.sub(r'[^\w\s-]',' ',query).strip()
            if query:filters['or']='(title.ilike.*'+query+'*,topic.ilike.*'+query+'*)'
        items=rows(client,'media_intake_items',filters,25,offset)
        return {'items':items,'next_offset':offset+25 if len(items)==25 else None}
    if action=='source_upload_candidate':return upload(client,payload)
    if action=='source_admit':return admit(client,payload)
    if action=='source_use':return creator_source(client,payload)
    if action in {'source_trash','source_revoke'}:
        identity=str(uuid.UUID(str(payload.get('id',''))))
        values={'generation_eligible':False}
        if action=='source_trash':values['archived']=payload.get('archived') is not False
        return {'item':write(client,'media_intake_items',values,identity,payload.get('version'))}
    raise StudioError('Unknown source action')


def sync_factory_sources(client, runtime):
    """Reconcile approvals every run, including previously cached/revoked inputs."""
    from pathlib import Path
    root=Path(runtime)/'reference-images';root.mkdir(parents=True,exist_ok=True)
    manifest_path=root/'library.json'
    manifest=json.loads(manifest_path.read_text()) if manifest_path.exists() else {'assets':[]}
    candidates=[]
    for offset in range(0,10000,100):
        page=rows(client,'media_intake_items',{'generation_eligible':'eq.true','archived':'eq.false'},100,offset)
        candidates.extend(page)
        if len(page)<100:break
    assets=[]
    for row in candidates:
        raw,credit=admitted_image(client,row)
        file=row['stored_sha256']+'.webp'
        (root/file).write_bytes(raw)
        assets.append({**credit,'file':file,'sha256':row['stored_sha256']})
    # Keep other licensed sources. Remove revoked intake entries from the active
    # manifest without deleting historical bytes or previously generated artwork.
    manifest['assets']=assets+[a for a in manifest.get('assets',[]) if not a.get('intake_id') and a.get('file') not in {v['file'] for v in assets}]
    temporary=manifest_path.with_suffix('.tmp')
    temporary.write_text(json.dumps(manifest,ensure_ascii=False),encoding='utf-8')
    temporary.replace(manifest_path)
    return len(assets)
