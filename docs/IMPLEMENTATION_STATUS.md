# StickerDock implementation status

Updated October 13, 2026. “Complete” means implemented and covered by automated
tests/builds; live-provider items are called out separately. It does not replace
real-device acceptance or store-release testing.

## Product split

1. **StickerDock Android app** — the user-facing library. It has no Groq or
   Supabase service-role credential. It organizes categorized packs, imports
   user-accessible sticker files, creates/shares pack ZIPs, installs packs into
   WhatsApp/WhatsApp Business and reads approved public packs.
2. **Sticker Factory service** — a separate private GitHub Actions scheduler. It
   is the only component that calls Groq for the daily Factory. It combines
   news-title references, Commons media, manual uploads and Pinterest inputs,
   renders batches, and stores state/pending packs in Supabase.
3. **Creator Studio AI editor** — still shown as **Coming soon** in Android. The
   private browser Studio supports manual editing plus an asynchronous
   Studio → Factory request/result queue. The real Supabase/Groq vision path has
   passed live testing. The browser AI control and queue-aware Creator worker are
   merged to `main`; hosted Vercel deployment is still unverified.

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
- Disabled **AI meme creator — Coming soon** Android entry.

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
- Private `creator_requests` queue with no public RLS policy, token-gated Studio
  enqueue/status API, atomic service-role claim using `FOR UPDATE SKIP LOCKED`,
  bounded retries and a GitHub worker. Outstanding queued/running work is capped
  at 25 requests so the private Studio cannot build an unbounded backlog. The
  worker checks the queue every 10 minutes, skips dependency setup when idle, and
  can drain up to five requests in one active run. Running claims older than 20
  minutes are recovered: attempts below five are requeued, while stale
  fifth-attempt claims are failed instead of remaining permanently stuck.
- Creator request results stay in the private inbox. The worker verifies the
  source object's SHA-256 path, reuses the existing Groq vision + local renderer,
  preserves source rights, treats missing rights metadata as `REFERENCE_ONLY`,
  and fails before Groq on invalid provenance.
- Read-only Supabase storage monitor recursively totals private/public bucket
  objects, writes a GitHub job summary, warns/fails at configurable quota
  percentages, and identifies old private `generated/YYYY-MM-DD` artifacts as
  manual retention candidates. Scans are bounded and never delete remote data.
- Retention policy: source inbox/provenance stays until explicit owner cleanup;
  approved public packs stay until deliberately delisted; private generated
  daily artifacts older than the configured retention window (30 days by
  default) are reported for human review rather than automatically removed.
- Publication gate that refuses MATURE packs and Pinterest/reference-only pixels.
- Publisher verifies sticker dimensions, checksums, sizes and media type; repeat
  generation keeps deterministic ZIP checksums and preserves prior approval.
- Public pack size and animation timing match Android import limits.
- Invalid inbox media are skipped, and failed runs retain prior progress and
  usage metrics. Pinterest scans have attempt/time budgets and bounded staging.
- Signed Android APK/AAB workflow with configurable public catalog URL.

### Live Supabase / Groq verification

- All three production migrations were applied successfully on October 13, 2026
  after successful `supabase db push --dry-run` checks. The third migration adds
  recoverable Creator-worker leases and a partial running-request index.
- Post-deployment verification confirmed RLS is enabled on `factory_runs`,
  `sticker_packs` and `creator_requests`.
- `factory_runs` and `creator_requests` expose table privileges only to
  `service_role`; `sticker_packs` exposes client SELECT only and RLS limits those
  reads to approved `GENERAL` rows.
- `claim_creator_request(text)` is `SECURITY INVOKER`, uses an empty search path,
  grants EXECUTE only to `service_role`, retains `FOR UPDATE SKIP LOCKED`, and
  contains the verified 20-minute stale-lease recovery with the five-attempt
  ceiling. The live deployment reported zero stale running requests after apply.
  A separate live behavior smoke (`38401538953`) inserted a 21-minute-old
  synthetic running request at attempt 1; the real RPC reclaimed it with a fresh
  lease at attempt 2, and the smoke row was deleted immediately afterward.
- `sticker-factory-private` is private, `sticker-factory-public` is public, and
  no broad `storage.objects` SELECT policy exists for client roles.
- All three `updated_at` triggers are present.
- GitHub production `SUPABASE_SERVICE_ROLE_KEY` and `GROQ_API_KEY` were verified
  without exposing their values. The Supabase key is the new `sb_secret_...`
  type; both the Factory and hosted API clients support that format without
  incorrectly treating it as a JWT bearer token. Legacy service-role JWT support
  remains for backwards compatibility.
- A controlled live Factory run generated **5/5** GENERAL illustration stickers,
  persisted a complete `factory_runs` row, stored a checksum-verified private ZIP
  and a pending private pack, and left the public bucket empty.
- A follow-up run using the real production configuration resumed persisted state
  from 5 to **10/40**, retained all 10 records across a fresh runner, synced two
  reference-library assets and left the public bucket empty. This validated
  restart/resume behavior without bypassing the normal production cooldown.
- A real Creator request passed queue → atomic claim → Groq vision → private WebP
  → provenance verification → completed status. The isolated smoke request and
  its source/result objects were deleted afterward.
- The merged Creator-worker preflight was exercised separately against real
  Supabase and confirmed the live key is `sb_secret_...`, the apikey-only path
  succeeds and the Creator queue was empty at test time.
- Groq authentication and current production models were verified live:
  `openai/gpt-oss-20b` for text and `qwen/qwen3.8-27b` for vision.
- The storage monitor completed a live report-only scan: the private bucket had
  **7 objects / 4,204,527 bytes (~4.01 MB)**, the public bucket had **0 objects**,
  and there were no private generated artifacts older than the 30-day retention
  window. No quota percentage was calculated because the actual plan quota has
  not been configured, and the monitor did not delete anything.

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

### Creator Studio source

- Responsive browser editor with local image upload and private inbox browsing.
- Manual caption, drag positioning, font size/width, fill/outline, crop mode,
  uppercase toggle and PNG download.
- Upload of edited output to the private factory inbox.
- Server-side Python functions keep Supabase service credentials out of browser
  code. `STUDIO_ACCESS_TOKEN` protects private Studio API requests and must be
  32–256 characters; short or oversized deployment tokens fail closed.
- The browser AI control is enabled in merged source. For a local image it first
  creates a private inbox source, enqueues `/api/captions`, polls the request
  status and loads the private result back into the editor when complete.
- Edited inbox images retain their original rights metadata. Uploaded images are
  decoded and verified before they enter the factory.
- Headless Chromium verified local image load, canvas rendering, caption styling,
  drag positioning, PNG download, token guard and 390px mobile layout with zero
  horizontal overflow.
- A second Chromium flow against the merged live-AI source verified the AI UI
  sequence with deterministic API-boundary responses: source upload → queue →
  status → result fetch → result loaded/download-enabled, with zero browser
  errors and no mobile overflow.
- The complete Python helper suite passed with the Creator source changes, and the
  pre-existing Studio API regression tests were preserved while adding new-secret
  and legacy-auth coverage.

## Partially complete — needs live verification or configuration

- **Hosted Creator Studio deployment:** source and real backend queue/worker are
  validated, but no production Vercel URL or Vercel environment configuration has
  been verified yet. Configure a random 32–256 character `STUDIO_ACCESS_TOKEN`,
  `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` server-side and run a hosted
  browser smoke before treating Studio as remotely available.
- **Full-day Factory capacity:** the real Factory generated/resumed to 10/40 and
  Groq models authenticate successfully. Keep the recurring Factory schedule
  disabled until one normal-cooldown 40-sticker day completes and actual
  usage/rate-limit behavior is reviewed.
- **Pinterest account:** the connector is ready; it still needs an approved app,
  OAuth token and numeric authorized board IDs. Official video URLs may require
  separate Pinterest approval.
- **Storage monitoring thresholds:** the first live read-only scan passed. The
  actual Supabase plan quota is still unknown to the workflow, so configure
  `SUPABASE_STORAGE_QUOTA_MB` and review the intended warning/failure thresholds
  before setting `STORAGE_MONITOR_ENABLED=true` for daily checks.
- **Android release:** the `android-production` environment exists. A live
  presence-only probe found the four signing secrets unset:
  `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
  `ANDROID_KEY_PASSWORD`. `CATALOG_BASE_URL` no longer blocks release because the
  workflow defaults to the deployed Supabase public catalog while retaining an
  optional environment override. The probe stopped before keystore decoding or
  Gradle/signing, so the remaining blocker is signing configuration rather than a
  known build/signature failure.
- **WhatsApp integration:** provider code exists and compiles; acceptance in
  current WhatsApp and WhatsApp Business builds needs a real-device test.
- **Large folder migration:** progress/checkpoint/resume logic compiles and the
  automated build is green, but Android process-kill recovery, provider URI
  persistence and 10k+ file behavior still need real Galaxy S24 Ultra testing.

## Still pending

- Deploy/configure the already-merged Creator Studio live-AI source on Vercel and
  perform one authenticated hosted-browser queue/result smoke. Android can remain
  Coming soon until that hosted path is intentionally exposed.
- Let the private Factory complete a normal-cooldown 40-sticker day, review the
  completed pack, publish one approved GENERAL pack, import it into Android and
  record actual Groq usage/rate limits.
- Connect the Pinterest account and complete one official-board smoke sync.
- Configure `SUPABASE_STORAGE_QUOTA_MB` from the actual project plan and, after
  reviewing the first successful report-only scan, set
  `STORAGE_MONITOR_ENABLED=true` for daily checks.
- Configure the four Android signing secrets, build a signed APK/AAB candidate and
  install it on the target Galaxy S24 Ultra.
- Test folder import, animated previews and WhatsApp/Business pack installation
  on the Galaxy S24 Ultra.
- Validate resumable progress/process-death recovery for very large Android
  folder imports on the Galaxy S24 Ultra, including cancel/retry and lost URI
  permission behavior.
- Add authenticated community submission, malware scanning, rights review,
  report/block/moderation and age controls before accepting public uploads.
- Add a dedicated image-generation provider if fully synthetic AI imagery is
  required. Groq currently supplies captions/reasoning; Pillow and supplied media
  create the visual output.
- Complete store/release work: privacy policy, app identity, signing, Play listing
  and production device regression tests.

## Credentials/configuration state

GitHub Actions currently has validated `GROQ_API_KEY` and
`SUPABASE_SERVICE_ROLE_KEY` secrets. The Supabase project URL is non-secret and is
set directly in the relevant production workflows. Database migration maintenance
can use `SUPABASE_DB_PASSWORD`; it is not needed by the Android app. Pinterest
later requires `PINTEREST_ACCESS_TOKEN` plus board variables.

The `android-production` environment currently needs four signing values to be
configured: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`. The signed-release workflow uses
the production Supabase catalog by default; `CATALOG_BASE_URL` is now an optional
override rather than a required release value.

The hosted Creator Studio additionally requires a random 32–256 character
`STUDIO_ACCESS_TOKEN`, `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` in the
Vercel/server environment. Those hosted settings have not yet been verified.
Credentials are deployment settings and must not be embedded in source files or
the APK.

See [production deployment](PRODUCTION_DEPLOYMENT.md),
[live production evidence](LIVE_PRODUCTION_EVIDENCE_2026-10-13.md), and
[Pinterest/Creator setup](PINTEREST_AND_CREATOR_STUDIO.md) for the exact steps.
