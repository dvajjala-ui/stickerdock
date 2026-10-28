"""Queue enabled Admin plans; HandNavigate polls outbound and performs downloads."""
import json
import argparse
import time
import uuid
from datetime import datetime
from zoneinfo import ZoneInfo
from urllib.parse import urlencode
from api._collection_control import enqueue_daily
from tools.supabase_factory import SupabaseFactory

def wait_for_completion(client,submissions,timeout=1200,interval=10):
    identities=[str(uuid.UUID(row['job']['id'])) for row in submissions]
    if not identities:return []
    if len(identities)>100:raise ValueError('Too many daily collections to monitor')
    path='/rest/v1/collection_jobs?'+urlencode({'select':'id,status,image_limit,delivered',
        'id':'in.('+','.join(identities)+')','limit':100})
    deadline=time.monotonic()+timeout;last=None
    while time.monotonic()<deadline:
        states=json.loads(client._request('GET',path) or b'[]')
        if len(states)!=len(identities):raise ValueError('A scheduled collection is missing; inspect Admin')
        progress=json.dumps(states,sort_keys=True)
        if progress!=last:print(json.dumps({'collection_progress':states}),flush=True);last=progress
        failed=[r for r in states if r['status'] not in ('QUEUED','RUNNING','WAITING_FOR_LOGIN','VERIFIED_STORAGE')]
        if failed:raise ValueError('Scheduled collection did not finish successfully; inspect source status in Admin')
        if all(r['status']=='VERIFIED_STORAGE' and r['delivered']==r['image_limit'] for r in states):return states
        time.sleep(interval)
    raise ValueError('Scheduled collection wait timed out. Requests remain available in Admin; check the worker connection before retrying.')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wait',action='store_true',help='Fail the workflow if queued work does not reach verified storage')
    parser.add_argument('--qualification',action='store_true',help='Collect two real guest images to qualify the hosted worker')
    parser.add_argument('--wait-timeout',type=int,default=1200)
    args=parser.parse_args()
    if not 1<=args.wait_timeout<=3600:raise ValueError('Wait timeout must be 1–3600 seconds')
    client=SupabaseFactory()
    if args.qualification:
        from api._collection_control import enqueue
        jobs=[enqueue(client,{'request_id':'hosted-qualification-'+uuid.uuid4().hex,
            'topics':['funny reaction'],'sources':['pinterest','reddit','imgflip'],'image_limit':2})]
    else:
        jobs=enqueue_daily(client,datetime.now(ZoneInfo('Asia/Kolkata')).strftime('%Y-%m-%d'))
    print(json.dumps({'queued':sum(not r['reused'] for r in jobs),'note':'No new jobs when today\'s stored-image budget is already satisfied or no daily plans are enabled.',
        'jobs':[{'id':r['job']['id'],'status':r['job']['status'],'limit':r['job']['image_limit'],'topic_origin':r['job'].get('topic_origin','saved'),'reused':r['reused']} for r in jobs]}),flush=True)
    if args.wait:wait_for_completion(client,jobs,timeout=args.wait_timeout)

if __name__=='__main__':main()
