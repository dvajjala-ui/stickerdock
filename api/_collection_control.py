"""Shared Admin collection controls and leased outbound HandNavigate work."""
from __future__ import annotations
import hashlib
import json
import uuid
from datetime import datetime, timezone
from urllib.parse import urlencode
from api._shared import StudioError, safe_text

SOURCES = ('pinterest', 'reddit')
TERMINAL = {'VERIFIED_STORAGE','PARTIAL','AUTH_REQUIRED','DELIVERY_FAILED','FAILED','CANCELLED','INTERRUPTED'}

def now():
    return datetime.now(timezone.utc).isoformat()

def spec(payload):
    topics = payload.get('topics')
    sources = payload.get('sources')
    if not isinstance(topics,list) or not 1 <= len(topics) <= 10:
        raise StudioError('Choose 1–10 topics')
    topics = list(dict.fromkeys(safe_text(t,'topic',240,required=True) for t in topics))
    if not isinstance(sources,list) or not 1 <= len(sources) <= 10 or any(not isinstance(s,str) or s not in SOURCES for s in sources):
        raise StudioError('Choose Pinterest or Reddit')
    limit = payload.get('image_limit',20)
    if isinstance(limit,bool) or not isinstance(limit,int) or not 1 <= limit <= 100:
        raise StudioError('Image count must be 1–100 total')
    return {'topics':topics,'sources':list(dict.fromkeys(sources)),'image_limit':limit}

def rows(client,table,filters=None,limit=50,offset=0):
    query={'select':'*','order':'created_at.desc','limit':limit, 'offset':offset, **(filters or {})}
    result=json.loads(client._request('GET','/rest/v1/'+table+'?'+urlencode(query)) or b'[]')
    if not isinstance(result,list):
        raise StudioError('Invalid collection database response')
    return result

def write(client,table,values,row_id=None,version=None):
    query = '?id=eq.'+str(uuid.UUID(str(row_id))) if row_id else ''
    if row_id and version is not None:
        if isinstance(version,bool) or not isinstance(version,int) or version<1:
            raise StudioError('Refresh this item before editing')
        query+='&version=eq.'+str(version)
        values={**values,'version':version+1,'updated_at':now()}
    result=json.loads(client._request('PATCH' if row_id else 'POST','/rest/v1/'+table+query,
        data=json.dumps(values).encode(),headers={'Content-Type':'application/json','Prefer':'return=representation'}) or b'[]')
    if not result:
        raise StudioError('This item changed in another session; refresh and try again')
    return result[0]

def enqueue(client,payload):
    values=spec(payload)
    key=safe_text(payload.get('request_id'),'request id',128,required=True)
    if len(key)<8:
        raise StudioError('Invalid collection request ID')
    old=client.find('collection_jobs','request_id',key)
    if old:
        if any(old.get(k)!=v for k,v in values.items()):
            raise StudioError('Request ID belongs to different parameters')
        return {'job':old,'reused':True}
    result=json.loads(client._request('POST','/rest/v1/collection_jobs?on_conflict=request_id',
        data=json.dumps({**values,'request_id':key}).encode(),headers={'Content-Type':'application/json',
        'Prefer':'resolution=ignore-duplicates,return=representation'}) or b'[]')
    job=result[0] if result else client.find('collection_jobs','request_id',key)
    if not job or any(job.get(k)!=v for k,v in values.items()):
        raise StudioError('Collection submission conflict; refresh')
    return {'job':job,'reused':not bool(result)}

def enqueue_daily(client,day):
    # UTC day matches the existing planner and Actions daily schedule.
    datetime.strptime(day,'%Y-%m-%d')
    plans=rows(client,'collection_plans',{'daily':'eq.true','archived':'eq.false'},limit=100)
    return [enqueue(client,{**p,'request_id':'daily:'+day+':'+p['id']+':'+hashlib.sha256(
        json.dumps(spec(p),sort_keys=True).encode()).hexdigest()[:16]}) for p in plans]

def worker_dispatch(client,payload):
    worker=str(uuid.UUID(str(payload.get('worker_id',''))))
    if payload['action']=='claim':
        result=client.rpc('claim_collection_job',{'p_worker_id':worker})
        return {'job':result[0] if result else None}
    job_id=str(uuid.UUID(str(payload.get('id',''))))
    report=payload.get('report')
    if not isinstance(report,dict) or report.get('status') not in TERMINAL|{'RUNNING','WAITING_FOR_LOGIN'}:
        raise StudioError('Invalid progress report')
    for field in ('saved','delivered'):
        if isinstance(report.get(field),bool) or not isinstance(report.get(field),int) or not 0<=report[field]<=100:
            raise StudioError('Invalid image count')
    if report['delivered']>report['saved'] or len(json.dumps(report))>20000:
        raise StudioError('Invalid collection progress')
    row=client.find('collection_jobs','id',job_id)
    if report['status']=='VERIFIED_STORAGE' and (not row or report['delivered']!=row['image_limit']):
        raise StudioError('Collection is incomplete')
    result=client.rpc('report_collection_job',{'p_id':job_id,'p_worker_id':worker,'p_report':report})
    if not result:
        raise StudioError('Worker lease expired or changed; stop this local run')
    return {'job':result[0]}

def admin_dispatch(client,payload):
    action=payload['action']
    if action=='collection_dashboard':
        offset=payload.get('offset',0)
        if isinstance(offset,bool) or not isinstance(offset,int) or not 0<=offset<=1000000:
            raise StudioError('Invalid page')
        filters={'archived':'eq.'+str(payload.get('archived') is True).lower()}
        if payload.get('platform') in SOURCES:
            filters['platform']='eq.'+payload['platform']
        if payload.get('topic'):
            # Exact topic filter avoids injecting PostgREST filter syntax.
            filters['topic']='eq.'+safe_text(payload['topic'],'topic',240,required=True)
        items=rows(client,'media_intake_items',filters,25,offset)
        return {'items':items,'next_offset':offset+25 if len(items)==25 else None,
            'plans':rows(client,'collection_plans',limit=100),'jobs':rows(client,'collection_jobs',limit=30),
            'sources':list(SOURCES),'daily_schedule':'09:53 Asia/Kolkata (GitHub Actions; timing may be delayed)',
            'worker_required':True}
    if action=='collection_create':
        return enqueue(client,payload)
    if action=='collection_plan_save':
        values={**spec(payload),'name':safe_text(payload.get('name'),'plan name',100,required=True),
            'daily':payload.get('daily') is True,'archived':payload.get('archived') is True}
        return {'plan':write(client,'collection_plans',values,payload.get('id'),payload.get('version'))}
    if action=='collection_plan_archive':
        return {'plan':write(client,'collection_plans',{'archived':payload.get('archived') is not False},payload.get('id'),payload.get('version'))}
    if action in ('collection_cancel','collection_retry'):
        row=client.find('collection_jobs','id',str(uuid.UUID(str(payload.get('id','')))))
        if not row:
            raise StudioError('Collection not found')
        if action=='collection_retry':
            if row['status'] not in TERMINAL-{'VERIFIED_STORAGE'}:
                raise StudioError('Only incomplete collections can be resumed')
            values={'status':'QUEUED','worker_id':None,'lease_until':None,'cancel_requested':False,'error':None,'finished_at':None}
        else:
            if row['status'] in TERMINAL:
                return {'job':row}
            values={'cancel_requested':True}
            if row['status']=='QUEUED':
                values['status']='CANCELLED'
        # Conditional status protects a claim/completion racing the Admin click.
        path='/rest/v1/collection_jobs?'+urlencode({'id':'eq.'+row['id'],'status':'eq.'+row['status']})
        result=json.loads(client._request('PATCH',path,data=json.dumps({**values,'updated_at':now()}).encode(),
            headers={'Content-Type':'application/json','Prefer':'return=representation'}) or b'[]')
        if not result:
            raise StudioError('Collection changed; refresh before retrying')
        return {'job':result[0]}
    if action=='intake_update':
        values={'title':safe_text(payload.get('title',''),'title',160),
            'topic':safe_text(payload.get('topic'),'topic',240,required=True)}
        tags=payload.get('tags',[])
        if not isinstance(tags,list) or len(tags)>20:
            raise StudioError('Use up to 20 tags')
        values['tags']=list(dict.fromkeys(safe_text(t,'tag',40,required=True) for t in tags))
        review=payload.get('rights_status','UNREVIEWED')
        if review not in ('UNREVIEWED','APPROVED','REJECTED'):
            raise StudioError('Invalid review status')
        values.update(rights_status=review,generation_eligible=False)
        return {'item':write(client,'media_intake_items',values,payload.get('id'),payload.get('version'))}
    if action=='intake_archive':
        return {'item':write(client,'media_intake_items',{'archived':payload.get('archived') is not False},payload.get('id'),payload.get('version'))}
    if action=='intake_preview':
        from api._media_intake import dispatch
        return dispatch({'action':'get','id':payload.get('id')},client)
    raise StudioError('Unknown collection control')
