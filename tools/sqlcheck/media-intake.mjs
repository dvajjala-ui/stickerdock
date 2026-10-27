import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';
const db=new PGlite(),sql=readFileSync(new URL('../../supabase/migrations/202610280001_media_intake.sql',import.meta.url),'utf8');
try{
  await db.exec('create role anon; create role authenticated; create role service_role;');
  await db.exec(sql);await db.exec(sql);
  const flags=(await db.query("select relrowsecurity from pg_class where oid='public.media_intake_items'::regclass")).rows;
  assert.equal(flags[0].relrowsecurity,true);
  for(const role of ['anon','authenticated'])for(const permission of ['SELECT','INSERT','UPDATE','DELETE']){
    assert.equal((await db.query('select has_table_privilege($1,$2,$3) as allowed',[role,'public.media_intake_items',permission])).rows[0].allowed,false);
  }
  const value={id:'12345678-1234-1234-1234-123456789012',collection:'12345678-1234-1234-1234-123456789013',sha:'a'.repeat(64)};
  await db.query(`insert into media_intake_items (id,collection_id,platform,topic,source_url,download_url,original_sha256,stored_sha256,storage_path)
    values ($1,$2,'reddit','cats','https://www.reddit.com/comments/example/','https://i.redd.it/example.jpg',$3,$3,$4)`,[value.id,value.collection,value.sha,'handnavigate/candidates/'+value.sha+'.webp']);
  const row=(await db.query('select * from media_intake_items')).rows[0];assert.equal(row.generation_eligible,false);assert.equal(row.rights_status,'UNREVIEWED');
  await assert.rejects(db.query('update media_intake_items set generation_eligible=true where id=$1',[value.id]),/media_intake_review_required/);
  console.log('Media intake migration: idempotent, RLS/private grants, unreviewed defaults and generation-admission constraint verified');
}finally{await db.close();}
