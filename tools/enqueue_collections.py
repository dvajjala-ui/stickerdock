"""Queue enabled Admin plans; HandNavigate polls outbound and performs downloads."""
import json
from datetime import datetime
from zoneinfo import ZoneInfo
from api._collection_control import enqueue_daily
from tools.supabase_factory import SupabaseFactory

if __name__=='__main__':
    jobs=enqueue_daily(SupabaseFactory(),datetime.now(ZoneInfo('Asia/Kolkata')).strftime('%Y-%m-%d'))
    print(json.dumps({'queued':len(jobs),'note':'No new jobs when today\'s stored-image budget is already satisfied or no daily plans are enabled.',
        'jobs':[{'id':r['job']['id'],'status':r['job']['status'],'reused':r['reused']} for r in jobs]}))
