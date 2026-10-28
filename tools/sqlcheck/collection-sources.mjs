import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {PGlite} from '@electric-sql/pglite';
const db=new PGlite();
try{
  await db.exec('create role anon;create role authenticated;create role service_role;');
  for(let n=0;n<2;n++)for(const file of ['202610280001_media_intake.sql','202610280002_collection_control.sql','202610280003_daily_collection_plans.sql','202610280004_collection_sources.sql'])await db.exec(readFileSync(new URL('../../supabase/migrations/'+file,import.meta.url),'utf8'));
  for(const role of ['anon','authenticated']){
    assert.equal((await db.query('select has_table_privilege($1,$2,$3) allowed',[role,'collection_source_catalog','SELECT'])).rows[0].allowed,false);
    assert.equal((await db.query("select has_function_privilege($1,'claim_compatible_collection_job(uuid,jsonb)','EXECUTE') allowed",[role])).rows[0].allowed,false);
  }
  const worker='12345678-1234-1234-1234-123456789013',custom=[{id:'custom-1234567890abcdef',name:'Owned gallery',revision:'a'.repeat(64)}];
  await db.query(`insert into collection_jobs(request_id,topics,sources,image_limit) values('custom-request','["cats"]','["custom-1234567890abcdef"]',2),('reddit-request','["cats"]','["reddit"]',2)`);
  const legacy=(await db.query('select * from claim_collection_job($1)',[worker])).rows;assert.equal(legacy[0].request_id,'reddit-request');
  assert.equal((await db.query('select * from claim_collection_job($1)',[worker])).rows.length,0);
  const current=(await db.query('select * from claim_compatible_collection_job($1,$2)',[worker,JSON.stringify(custom)])).rows;assert.equal(current[0].request_id,'custom-request');
  assert.equal((await db.query('select * from claim_compatible_collection_job($1,$2)',[worker,JSON.stringify(custom)])).rows.length,0);
  assert.equal((await db.query("select name,revision from collection_source_catalog where id=$1",[custom[0].id])).rows[0].revision,custom[0].revision);
  assert.deepEqual((await db.query('select supported_sources from collection_workers where id=$1',[worker])).rows[0].supported_sources,[custom[0].id]);
  for(const invalid of [null,[],[{}],[...custom,...custom],[{...custom[0],revision:'secret'}]])await assert.rejects(db.query('select * from claim_compatible_collection_job($1,$2)',[worker,JSON.stringify(invalid)]));
  console.log('Source SQL: additive migration, private registry, capability claims, old-worker isolation, duplicate leases and malformed capability rejection passed');
}finally{await db.close();}
