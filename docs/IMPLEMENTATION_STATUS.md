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
   Vercel preview and a private asynchronous Studio → Factory request/result
   queue are implemented. The queue keeps Groq and Supabase service credentials
   server-side. Its backend path has passed a live provider smoke test; the
   worker remains manual-only while the hosted Studio/auth rollout is still gated.

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
- Private `creator_requests` queue with no public RLS policy, token-gated Studio
  enqueue/status API, atomic service-role claim using `FOR UPDATE SKIP LOCKED`,
  bounded retries and a manual-only GitHub worker that processes at most one
  request per dispatch.
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

### Live Supabase deployment

- Both production migrations were applied successfully on October 13, 2026 after
  a successful `supabase db push --dry-run`.
- Post-deployment verification confirmed RLS is enabled on `factory_runs`,
  `sticker_packs` and `creator_requests`.
- `factory_runs` and `creator_requests` expose table privileges only to
  `service_role`; `sticker_packs` exposes client SELECT only and RLS limits those
  reads to approved `GENERAL` rows.
- `claim_creator_request(text)` is `SECURITY INVOKER`, uses an empty search path,
  and grants EXECUTE only to `service_role`.
- `sticker-factory-private` is private, `sticker-factory-public` is public, and
  no broad `storage.objects` SELECT policy exists for client roles.
- All three `updated_at` triggers are present.

### Live production smoke evidence — October 13, 2026

- The merged `main` commit `00767afa` passed the normal Android debug workflow.
- A controlled real Groq + Supabase Factory run generated 5/5 GENERAL stickers,
  persisted `factory_runs=complete`, stored a pending private pack with a verified
  19,715-byte ZIP, and left the public bucket empty.
- A second run used the real `tools/factory-production.json` and resumed the
  persisted state from 5 to 10/40 instead of starting over. The runtime archive
  contained all 10 records, the live reference library contained two assets,
  and the public bucket remained empty.
- Creator Studio passed a real headless-Chromium interaction smoke: local image
  upload, canvas/caption rendering, style changes, caption drag, PNG download,
  missing-token guard and a 390px mobile viewport with zero horizontal overflow.
  AI controls remained disabled.
- The Creator request backend passed an isolated live queue → atomic claim → Groq
  vision → local render → private result test using the same queue helper as the
  Studio API. It completed in one attempt and produced a valid 512×512 static
  WebP with verified provenance. The test row and all four temporary storage
  objects were removed afterward.
- The real storage monitor reported seven private objects totaling 4,204,527
  bytes, zero public objects and no retention candidates. The quota variable is
  not configured yet, so percentage-based warning/failure thresholds could not
  be evaluated.

Detailed run IDs and exact outputs are recorded in
[`LIVE_PRODUCTION_EVIDENCE_2026-10-13.md`](LIVE_PRODUCTION_EVIDENCE_2026-10-13.md).

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
  code. `STUDIO_ACCESS_TOKEN` protects private Studio API requests.
- Authenticated AI queue/status server routes are implemented. The queue helper,
  live Supabase claim, Groq vision and private result path are now provider-tested;
  the visible AI control remains disabled until the deployed Vercel token-gated
  HTTP route is verified and rollout is intentionally enabled.
- Edited inbox images retain their original rights metadata. Uploaded images
  are decoded and verified before they enter the factory.

## Partially complete — needs live verification or configuration

- **Production activation:** migrations, Factory smoke/resume, Creator backend and
  the first storage report are live-verified. The recurring Factory schedule is
  still deliberately gated, and one complete 40-sticker day plus review/publish/
  Android import is still required before treating the pipeline as fully proven.
- **GitHub/Vercel configuration:** live Actions runs confirm the Groq and Supabase
  service-role credentials used by the workers are present and usable. The
  storage quota variable is not configured, Android signing/catalog environment
  readiness still needs verification, and the hosted Vercel Studio token-gated
  routes still need a real deployed HTTP smoke test.
- **Groq capacity:** two live Factory requests and one live Creator vision request
  succeeded. The selected production models and real daily allowance still need
  one full live day and quota-header review.
- **Pinterest account:** the connector is ready; it still needs an approved app,
  OAuth token and numeric authorized board IDs. Official video URLs may require
  separate Pinterest approval.
- **Creator Studio AI rollout:** the queue, atomic worker claim, provenance checks
  and Groq vision result path are live-verified. Keep the worker manual-only and
  the UI disabled until the deployed Vercel authenticated enqueue/status route is
  tested and an explicit rollout decision is made.
- **Storage monitoring:** the first real report succeeded. Configure
  `SUPABASE_STORAGE_QUOTA_MB` from the actual Supabase plan before enabling the
  daily monitor so usage percentages and 70/90-percent thresholds are meaningful.
- **Android release:** merged-main CI builds successfully; signed release still
  needs verification of the `android-production` keystore secrets and catalog URL.
- **WhatsApp integration:** provider code exists and compiles; acceptance in
  current WhatsApp and WhatsApp Business builds needs a real-device test.
- **Large folder migration:** progress/checkpoint/resume logic compiles and the
  automated build is green, but Android process-kill recovery, provider URI
  persistence and 10k+ file behavior still need real Galaxy S24 Ultra testing.

## Still pending

- Run the private Factory for a complete 40-sticker UTC day, inspect actual Groq
  quota/rate-limit metrics, review the completed pack, publish it deliberately,
  import it into Android and verify the resulting public catalog end-to-end.
- Verify the deployed Creator Studio `/api/*` routes with `STUDIO_ACCESS_TOKEN`;
  only after that decide whether to expose the AI control or schedule its worker.
- Configure `SUPABASE_STORAGE_QUOTA_MB` from the actual project plan and enable
  `STORAGE_MONITOR_ENABLED=true` only after the percentage thresholds are known.
- Verify the `android-production` signing secrets/catalog variable, build a signed
  APK/AAB candidate and install it on the target Galaxy S24 Ultra.
- Connect the Pinterest account and complete one official-board smoke sync.
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

## Credentials needed to activate production

GitHub Actions secrets: `GROQ_API_KEY`, `SUPABASE_URL`,
`SUPABASE_SERVICE_ROLE_KEY`, and later `PINTEREST_ACCESS_TOKEN`. Database migration
maintenance can use `SUPABASE_DB_PASSWORD`; it is not needed by the Android app.
Android signing uses its own four keystore secrets. The Vercel Creator Studio
additionally uses `STUDIO_ACCESS_TOKEN` plus the Supabase URL/service-role key
server-side. These values are deployment settings and are not source files or APK
fields.

See [production deployment](PRODUCTION_DEPLOYMENT.md),
[live production evidence](LIVE_PRODUCTION_EVIDENCE_2026-10-13.md), and
[Pinterest/Creator setup](PINTEREST_AND_CREATOR_STUDIO.md) for the exact steps.
