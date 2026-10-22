import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

// PGlite executes PostgreSQL SQL locally. This checks the migration and its
// access contract; it does not claim a production Supabase migration happened.
const db = new PGlite();
try {
  await db.exec(`create role anon; create role authenticated; create role service_role;
    create schema auth; create table auth.users(id uuid primary key);`);
  const sql = readFileSync(new URL('../../supabase/migrations/202610220001_personal_ai_captions.sql', import.meta.url), 'utf8');
  await db.exec(sql);
  const owner = 'b0881607-79fb-478e-a531-4a5555069f95';
  const other = 'e86450f8-bd03-4b6a-ac98-65def9f26f3f';
  const ids = ['1d2dcb41-2a23-4db8-810c-b42104050e01',
    '1d2dcb41-2a23-4db8-810c-b42104050e02',
    '1d2dcb41-2a23-4db8-810c-b42104050e03',
    '1d2dcb41-2a23-4db8-810c-b42104050e04'];
  const sha = 'a'.repeat(64);
  await db.query('insert into auth.users(id) values($1),($2)', [owner, other]);
  const queue = (id, who = owner, digest = sha) => db.query(
    'select * from public.queue_personal_ai_caption($1::uuid,$2::uuid,$3,$4)',
    [who, id, digest, 'Short funny reply']);

  assert.deepEqual((await queue(ids[0])).rows[0],
    { accepted: true, reason: 'created', job_status: 'uploading' });
  assert.deepEqual((await queue(ids[0])).rows[0],
    { accepted: true, reason: 'replay', job_status: 'uploading' });
  assert.equal((await queue(ids[0], other)).rows[0].reason, 'identity_conflict');
  assert.equal((await queue(ids[0], owner, 'b'.repeat(64))).rows[0].accepted, false);
  const source = (await db.query('select source_path from public.personal_ai_jobs where id=$1', [ids[0]])).rows[0].source_path;
  assert.equal(source, `users/${owner}/ai/sources/${ids[0]}-${sha}.webp`);

  assert.equal((await db.query('select * from public.activate_personal_ai_caption($1::uuid,$2::uuid)',
    [owner, ids[0]])).rows[0].job_status, 'queued');
  assert.equal((await db.query("select * from public.claim_personal_ai_caption('worker')")).rows[0].id, ids[0]);
  assert.equal((await db.query("select * from public.reserve_personal_ai_provider_call($1::uuid,'worker')",
    [ids[0]])).rows[0].allowed, true);
  assert.equal((await db.query("select * from public.reserve_personal_ai_provider_call($1::uuid,'worker')",
    [ids[0]])).rows[0].allowed, false);
  assert.equal((await db.query("select * from public.finish_personal_ai_caption($1::uuid,'worker','Seriously?',null)",
    [ids[0]])).rows[0].updated, true);
  assert.equal((await db.query("select * from public.finish_personal_ai_caption($1::uuid,'worker','Again?',null)",
    [ids[0]])).rows[0].updated, false);

  for (const id of ids.slice(1, 3)) assert.equal((await queue(id)).rows[0].accepted, true);
  assert.equal((await queue(ids[3])).rows[0].reason, 'daily_limit');
  assert.equal((await db.query('select * from public.cancel_personal_ai_caption($1::uuid,$2::uuid)',
    [owner, ids[1]])).rows[0].job_status, 'cancelled');
  assert.equal((await db.query('select * from public.cancel_personal_ai_caption($1::uuid,$2::uuid)',
    [other, ids[2]])).rows.length, 0);
  assert.equal((await db.query('select * from public.activate_personal_ai_caption($1::uuid,$2::uuid)',
    [owner, ids[1]])).rows[0].job_status, 'cancelled');

  await db.exec('set role anon');
  for (const statement of [
    'select * from public.personal_ai_jobs',
    'select * from public.personal_ai_provider_reservations',
    `select * from public.queue_personal_ai_caption('${owner}'::uuid,'${ids[3]}'::uuid,'${sha}','hello')`,
    `select * from public.cancel_personal_ai_caption('${owner}'::uuid,'${ids[0]}'::uuid)`]) {
    await assert.rejects(db.query(statement), /permission denied/);
  }
  console.log('Private AI migration: SQL, grants, owner isolation, replay, claim, budget and cancellation passed');
} finally {
  await db.close();
}
