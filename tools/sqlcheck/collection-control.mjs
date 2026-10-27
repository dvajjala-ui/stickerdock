import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {PGlite} from '@electric-sql/pglite';
const db=new PGlite();
try{
  await db.exec('create role anon;create role authenticated;create role service_role;');
  for(let n=0;n<2;n++)for(const file of ['202610280001_media_intake.sql','202610280002_collection_control.sql'])await db.exec(readFileSync(new URL('../../supabase/migrations/'+file,import.meta.url),'utf8'));
  for(const role of ['anon','authenticated'])for(const table of ['collection_jobs','collection_plans']){
    assert.equal((await db.query('select has_table_privilege($1,$2,$3) allowed',[role,table,'SELECT'])).rows[0].allowed,false);
  }
  const id='12345678-1234-1234-1234-123456789012',worker='12345678-1234-1234-1234-123456789013';
  await db.query(`insert into collection_jobs(id,request_id,topics,sources,image_limit) values($1,'request-example','["cats"]','["reddit"]',2)`,[id]);
  assert.equal((await db.query('select * from claim_collection_job($1)',[worker])).rows[0].attempts,1);
  assert.equal((await db.query('select * from claim_collection_job($1)',[worker])).rows.length,0);
  const report={status:'RUNNING',saved:1,delivered:0};
  assert.equal((await db.query('select * from report_collection_job($1,$2,$3)',[id,id,report])).rows.length,0);
  assert.equal((await db.query('select * from report_collection_job($1,$2,$3)',[id,worker,report])).rows[0].saved,1);
  await db.query("update collection_jobs set lease_until=now()-interval '1 second'");
  assert.equal((await db.query('select * from report_collection_job($1,$2,$3)',[id,worker,report])).rows.length,0);
  assert.equal((await db.query('select * from claim_collection_job($1)',[id])).rows[0].attempts,2);
  await db.query("update collection_jobs set cancel_requested=true,lease_until=now()-interval '1 second'");
  await db.query('select * from claim_collection_job($1)',[worker]);
  assert.equal((await db.query('select status from collection_jobs')).rows[0].status,'CANCELLED');
  console.log('Collection SQL: idempotent migration, private grants, exclusive leases, expiry/reclaim, stale worker rejection and cancellation passed');
}finally{await db.close();}
