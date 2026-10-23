import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const sql = readFileSync(new URL('../../supabase/migrations/202610230001_public_pack_previews.sql', import.meta.url), 'utf8');
const db = new PGlite();
try {
  await db.exec(`create schema storage;
    create table storage.buckets (
      id text primary key, public boolean not null,
      file_size_limit bigint, allowed_mime_types text[]);
    insert into storage.buckets values
      ('sticker-factory-public', true, 75000000,
       array['application/zip','application/json','image/avif']),
      ('sticker-factory-private', false, 75000000,
       array['application/zip','image/webp']);`);
  await db.exec(sql);
  await db.exec(sql);
  let rows = (await db.query('select * from storage.buckets order by id')).rows;
  assert.deepEqual(rows[0].allowed_mime_types, ['application/zip', 'image/webp']);
  assert.equal(rows[0].public, false);
  assert.deepEqual(rows[1].allowed_mime_types,
    ['application/zip', 'application/json', 'image/avif', 'image/webp']);
  assert.equal(rows[1].public, true);
  assert.equal(Number(rows[1].file_size_limit), 75000000);

  // A null allowlist means unrestricted MIME types; the migration preserves it.
  await db.exec("update storage.buckets set allowed_mime_types = null where id = 'sticker-factory-public'");
  await db.exec(sql);
  rows = (await db.query("select allowed_mime_types from storage.buckets where id = 'sticker-factory-public'")).rows;
  assert.equal(rows[0].allowed_mime_types, null);
  console.log('Public preview migration: additive, idempotent, private bucket and null allowlist preserved');
} finally {
  await db.close();
}
