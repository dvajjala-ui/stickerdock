# Restore real artwork covers in the public Discover catalog

The reviewed three-sticker **Everyday Animal Reactions**, **Big Mood Animals**,
**Moving Animal Moods** and **Workday Reactions** packs are live; their public ZIPs
passed independent checksum and manifest readback. The production
`sticker-factory-public` Storage bucket currently allows ZIP and JSON, but
rejects `image/webp`. Publication therefore succeeded with no optional cover;
Android and iPhone show a designed text card and load the sticker artwork when
the user opens the pack.

## Prepared change

Apply only
[`202610230001_public_pack_previews.sql`](../supabase/migrations/202610230001_public_pack_previews.sql)
to the production Supabase project. It adds `image/webp` to this one bucket's
MIME allowlist if missing. It preserves other MIME types, visibility and size
limits, and is idempotent. Do not rerun the October 12 base Factory migration
as a shortcut: that migration upserts the original ZIP/JSON allowlist.

`cd tools/sqlcheck && npm ci --ignore-scripts && npm test` passes locally for
the additive SQL, including a second application, an unrelated private bucket,
an existing extra MIME type and a null/unrestricted allowlist. This is source
validation, not evidence that production has changed. Local browser use is
currently disabled in this Work session, and no production database credential
is present in the process environment; the production migration remains open.

## Live acceptance after applying it

1. Read back `storage.buckets` for `sticker-factory-public`: `public=true`,
   `file_size_limit=75000000`, and `allowed_mime_types` includes ZIP, JSON and
   WebP. Confirm the private bucket is unchanged.
2. For each already-published Admin pack, verify status `published`, current
   version and the exact archive SHA-256 before each backfill:
   `8deca0ea-2d16-576c-8f1f-ad8670e3bab0` →
   `b53b772af38d23ed4a0e7b9673f6df5fd448f28fecf3b725b24be482461ed16d`;
   `b9f56de8-c509-53d8-bfdb-29f91c0839ae` →
   `3e4c40eb9036e4b5051c30c65c7ff6cebaa66735890124a42b802f971bdac65f`.
   Also include the animated pack
   `47f225c3-258a-576c-91f3-24776cbf9eb6` →
   `c22c5c63db1bc772c79aba05a68b57e343f2c21a952fa4f86cea4f33240351fc`
   and Workday Reactions
   `81356871-c817-538e-8a62-573b20beed97` →
   `b7c370afeef27f41400f30f37ef88a6cce43912f920170c929707687a1526dfe`.
   Republish each exact current version once through Admin's existing publish
   action. It permits an already-published pack and regenerates the optional
   cover without changing the reviewed ZIP. Do not unpublish or alter artwork.
3. Read each public `catalog/packs.json` entry back. Its `preview` object must
   name a checksum-addressed WebP. Download that exact public WebP and verify
   its SHA-256, static format and 576×384 dimensions. Recheck each ZIP SHA and
   pack count (five at the latest check) so the cover fix did not alter delivery.
4. In the released Android build, verify the card shows the artwork without
   downloading the ZIP until the user opens the pack. Apple simulator source
   and a physical iPhone/WhatsApp installation remain separate acceptance gates.

If the preview upload still fails, the publisher retains a valid text-covered
catalog entry. Inspect the safe server-side failure stage; do not claim the
bucket fix succeeded from a SQL command alone.
