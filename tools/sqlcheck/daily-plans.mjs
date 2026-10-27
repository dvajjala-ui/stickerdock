import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {PGlite} from '@electric-sql/pglite';
const db=new PGlite();
try{
  await db.exec('create role anon;create role authenticated;create role service_role;');
  for(let n=0;n<2;n++)for(const file of ['202610280001_media_intake.sql','202610280002_collection_control.sql','202610280003_daily_collection_plans.sql'])await db.exec(readFileSync(new URL('../../supabase/migrations/'+file,import.meta.url),'utf8'));
  assert.equal((await db.query("select has_function_privilege('anon','enqueue_daily_collection(uuid,date,jsonb,text,integer)','EXECUTE') allowed")).rows[0].allowed,false);
  const day='2026-10-28',a='12345678-1234-1234-1234-123456789012',b='12345678-1234-1234-1234-123456789013';
  await db.query(`insert into collection_plans(id,name,topics,sources,image_limit,daily) values($1,'cats','["cats"]','["reddit"]',20,true),($2,'dogs','["dogs"]','["pinterest"]',20,true)`,[a,b]);
  const first=(await db.query("select * from enqueue_daily_collection($1,$2,'[\"fresh cats\"]','planner',20)",[a,day])).rows[0];
  assert.equal(first.job.image_limit,20);assert.equal(first.job.topic_origin,'planner');assert.equal(first.reused,false);
  const repeat=(await db.query("select * from enqueue_daily_collection($1,$2,'[\"changed planner\"]','planner',20)",[a,day])).rows[0];assert.equal(repeat.reused,true);assert.equal(repeat.job.id,first.job.id);assert.deepEqual(repeat.job.topics,['fresh cats']);
  assert.equal((await db.query("select * from enqueue_daily_collection($1,$2,'[\"dogs\"]','saved',20)",[b,day])).rows.length,0);
  await db.query("update collection_jobs set status='CANCELLED'");
  // Nineteen rows already stored in the India day leave just one new slot.
  for(let n=0;n<19;n++){
    const sha=n.toString(16).padStart(64,'0');await db.query(`insert into media_intake_items(id,collection_id,platform,topic,source_url,download_url,original_sha256,stored_sha256,storage_path,created_at) values(gen_random_uuid(),$1,'reddit','cats','https://example.com/post','https://example.com/photo',$2,$2,$3,'2026-10-28T01:00:00+05:30')`,[a,sha,'handnavigate/candidates/'+sha+'.webp']);
  }
  const last=(await db.query("select * from enqueue_daily_collection($1,$2,'[\"dogs\"]','fallback',20)",[b,day])).rows[0];assert.equal(last.job.image_limit,1);assert.equal(last.job.topic_origin,'fallback');
  assert.equal((await db.query("select use_planner from collection_plans where id=$1",[a])).rows[0].use_planner,false);
  console.log('Daily plans SQL: additive migration, private grants, shared reservations, 19/20 remainder and immutable daily retries passed');
}finally{await db.close();}
