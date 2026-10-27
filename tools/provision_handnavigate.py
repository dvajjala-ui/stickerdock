"""Apply only reviewed additive intake migrations using protected Actions secrets."""
import hashlib
import json
import os
from pathlib import Path
import psycopg
from tools.supabase_factory import PRIVATE_BUCKET, SupabaseFactory

def main():
    project='qqicsllktmcmvmzplsxl'
    password=os.environ['SUPABASE_DB_PASSWORD']
    # Prefer an explicitly configured connection; probe only Supabase's documented
    # shared pooler hosts for this project, never arbitrary credential destinations.
    hosts=[os.environ['SUPABASE_DB_HOST']] if os.environ.get('SUPABASE_DB_HOST') else [
        'db.'+project+'.supabase.co', *['aws-'+str(i)+'-'+r+'.pooler.supabase.com'
        for r in ('ap-south-1','ap-southeast-1','us-east-1','eu-central-1','us-west-1','ap-south-2','ap-northeast-1','eu-west-1','us-east-2','ap-southeast-2','eu-west-2','ca-central-1','sa-east-1') for i in (0,1)]]
    conn=None
    for host in hosts:
        if not (host=='db.'+project+'.supabase.co' or host.endswith('.pooler.supabase.com')):
            raise ValueError('Use the documented Supabase connection host')
        try:
            conn=psycopg.connect(host=host,port=5432,dbname='postgres',user='postgres' if host.startswith('db.') else 'postgres.'+project,
                password=password,sslmode='require',connect_timeout=4,options='-c statement_timeout=30000')
            print('Connected to database host:',host)
            break
        except psycopg.OperationalError:
            continue  # Never print connection strings, passwords or server error bodies.
    if conn is None:
        raise RuntimeError('Database unavailable. Configure SUPABASE_DB_HOST with the project Connect session-pooler hostname.')
    with conn:
        for file in ('202610280001_media_intake.sql','202610280002_collection_control.sql'):
            conn.execute((Path(__file__).resolve().parents[1]/'supabase'/'migrations'/file).read_text())
            print('Applied additive migration:',file)
    token=os.environ['HANDNAVIGATE_INGEST_TOKEN']
    if len(token)<32:
        raise ValueError('Worker credential missing')
    client=SupabaseFactory()
    client.upload(PRIVATE_BUCKET,'integrations/handnavigate-worker.json',json.dumps({
        'token_sha256':hashlib.sha256(token.encode()).hexdigest()}).encode(),'application/json')
    print('Private worker credential hash configured. No plaintext stored in the database.')

if __name__=='__main__':
    main()
