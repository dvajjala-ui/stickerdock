# StickerDock implementation status

Updated October 13, 2026. “Complete” means implemented and covered by automated
tests/builds. It does not replace live account, device or provider testing.

## Product split

1. **StickerDock Android app** — the user-facing library. It has no Groq or
   Supabase service-role credential. It organizes categorized packs, imports
   user-accessible sticker files, creates/shares pack ZIPs, installs packs into
   WhatsApp/WhatsApp Business and reads approved public packs.
2. **Sticker Factory service** — a separate private GitHub Actions scheduler. It
   is the only component that calls Groq. It combines news-title references,
   Commons media, manual uploads and Pinterest inputs, renders batches, and
   stores state/pending packs in Supabase.
3. **AI meme editor** — shown as **Coming soon** in Android. A manual-editor
   Vercel preview is implemented. Future AI requests will be queued to the
   private Factory so the Studio never needs the Groq key.

## Complete in source

### Android library

- Category model plus non-destructive SQLite v6 migration. Existing packs become
  `General`; new packs can use General, Reactions, Hinglish, Movies, News,
  Animals, Love, Work, Festivals or Other. Sticker rows now also have optional
  user-visible labels and normalized tags. Robolectric covers upgrades from
  database versions 1 through 5 without losing packs or stickers.
- Category/topic filters, static/animated filters, pack search, global
  sticker-level search, per-sticker label/tag editing, category editing and
  metadata-preserving backup schema v5. Older backup schemas 1–4 remain readable.
- Static/animated pack creation, rename/delete, sticker copy/move/remove,
  content-hash deduplication and pack ZIP sharing. Copy/move preserves sticker
  label and tag metadata.
- PNG/JPEG/WebP imports, phone folder import through Android's system picker,
  multi-ZIP import and local backup/restore. User-visible source filenames are
  retained as sticker labels when available from the picker, ZIP or folder scan.
- User-accessible WhatsApp and WhatsApp Business media-folder migration.
- Large folder scans expose live counts, save an app-private checkpoint every 25
  recognized files, persist the selected SAF read URI where Android permits it,
  and expose Resume/Discard controls after interruption. Re-scans skip recorded
  source URIs and global SHA-256 deduplication makes a small unflushed tail safe.
- Sticker.ly public link bookmarks and import of user-supplied exports/ZIPs.
  There is no automatic Sticker.ly account/catalog download.
- Read-only approved public catalog and GENERAL/MATURE local labels.
- Disabled **AI meme creator — Coming soon** entry.

### Private Sticker Factory

- Forty-sticker daily target, five per Groq request, three-hour scheduling,
  saved cooldowns, request caps, deduplication and crash-safe persistent state.
- Restores file paths on fresh runners, paginates the private inbox and keeps a
  bounded local reference cache. Licensed library inputs are mirrored into the
  private inbox for manual editing.
- Original local illustration/pulse rendering, supplied-photo captioning, and
  GIF/MP4/WebM/MOV to animated WebP conversion.
- Hinglish topic rotation plus recent RSS title references.
- Supabase SQL tables, RLS, private/public buckets, persistent factory state,
  manual inbox, pending pack records and explicit GENERAL-only publish workflow.
- Publication gate that refuses MATURE packs and Pinterest/reference-only pixels.
- Publisher verifies sticker dimensions, checksums, sizes and media type; repeat
  generation keeps deterministic ZIP checksums and preserves prior approval.
- Public pack size and animation timing match Android import limits.
- Invalid inbox media are skipped, and failed runs retain prior progress and
  usage metrics. Pinterest scans have attempt/time budgets and bounded staging.
- Signed Android APK/AAB workflow with configurable public catalog URL.

### Image and video sources

- Manual private Supabase/Creator Studio upload with exact-hash deduplication.
- Wikimedia Commons keyless discovery restricted to CC0/public-domain media.
- Official Pinterest authorized-board pagination, rolling request budget,
  bounded CDN downloads, image/video support where Pinterest grants URLs,
  SQLite retry/deduplication and six-hour GitHub scheduling.
- Optional public-topic Pinterest adapter, disabled by default and labeled
  `REFERENCE_ONLY`.
- Per-Pin `USER_OWNED`, `CC0` or `PUBLIC_DOMAIN` declarations for eligible
  Pinterest inputs.

### Creator Studio preview

- Responsive browser editor with local image upload and private inbox browsing.
- Manual caption, drag positioning, font size/width, fill/outline, crop mode,
  uppercase toggle and PNG download.
- Upload of edited output to the private factory inbox.
- Server-side Vercel functions keep Supabase service credentials out of browser
  code. The AI button is disabled and labeled Coming soon.
- Edited inbox images retain their original rights metadata. Uploaded images
  are decoded and verified before they enter the factory.

## Partially complete — needs live verification or configuration

- **Production deployment:** code and SQL are ready; a Supabase project still
  needs the migration, GitHub secrets/variables and first smoke run.
- **Groq capacity:** request budgeting and rate-limit headers are implemented;
  the selected production models and real daily allowance need one full live day.
- **Pinterest account:** the connector is ready; it still needs an approved app,
  OAuth token and numeric authorized board IDs. Official video URLs may require
  separate Pinterest approval.
- **Creator Studio:** manual editing and unit tests are ready, but it remains a
  preview and is intentionally not linked from Android. The AI request queue is
  pending.
- **Android release:** CI builds debug; signed release needs the owner's keystore
  secrets and a configured Supabase catalog URL.
- **WhatsApp integration:** provider code exists and compiles; acceptance in
  current WhatsApp and WhatsApp Business builds needs a real-device test.
- **Large folder migration:** progress/checkpoint/resume logic compiles and the
  automated build is green, but Android process-kill recovery, provider URI
  persistence and 10k+ file behavior still need real Galaxy S24 Ultra testing.

## Still pending

- Apply the Supabase migration and configure repository secrets/variables.
- Run the private factory for a full day, review a completed pack, publish it,
  import it into Android and record actual Groq usage/rate limits.
- Connect the Pinterest account and complete one official-board smoke sync.
- Test folder import, animated previews and WhatsApp/Business pack installation
  on the Galaxy S24 Ultra.
- Validate resumable progress/process-death recovery for very large Android
  folder imports on the Galaxy S24 Ultra, including cancel/retry and lost URI
  permission behavior.
- Add authenticated community submission, malware scanning, rights review,
  report/block/moderation and age controls before accepting public uploads.
- Add a Supabase request/result queue from Creator Studio to the private Factory,
  test live Groq vision and Supabase access, then replace the Coming soon button.
- Add a dedicated image-generation provider if fully synthetic AI imagery is
  required. Groq currently supplies captions/reasoning; Pillow and supplied media
  create the visual output.
- Complete store/release work: privacy policy, app identity, signing, Play listing
  and production device regression tests.
- Add storage usage alerts and a chosen remote retention policy before scaling
  Pinterest to 90/100 media per day. Free Supabase storage is finite; local cache
  bounds do not delete remote media or guarantee unlimited free hosting.

## Credentials needed to activate production

GitHub Actions secrets: `GROQ_API_KEY`, `SUPABASE_URL`,
`SUPABASE_SERVICE_ROLE_KEY`, and later `PINTEREST_ACCESS_TOKEN`. Android signing
uses its own four keystore secrets. These values are deployment settings and are
not source files or APK fields.

See [production deployment](PRODUCTION_DEPLOYMENT.md) and
[Pinterest/Creator setup](PINTEREST_AND_CREATOR_STUDIO.md) for the exact steps.
