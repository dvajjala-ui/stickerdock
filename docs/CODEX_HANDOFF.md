# StickerDock development handoff — updated October 17, 2026

## Latest — Gmail account email setup prepared

The user now requests Gmail/Apps Script setup for `dvajjala@gmail.com`, superseding
the older SMTP deferral below. Chrome confirms that Google account is signed in.
`integrations/google-mail/Code.gs` and the explicit send-only manifest are paired
with `/api/auth_email`, a signed Supabase hook adapter. No email password is
needed. Setup and limitations are in GMAIL_ACCOUNT_EMAIL.md. Secrets, Google's
OAuth grant, deployment and end-to-end timing/delivery still require completion;
do not mark email delivery operational from source tests. Supabase HTTP hooks
have five seconds, so verify cold-start behavior before enabling this hook.
The existing owner allowlist/form spelling `dvjjala@gmail.com` is a separate
identity and has not been changed to the Gmail sender without confirmation.

## Latest — Android 0.10.2 published, release delivery and Factory recovery

Main source `fb7dea9` is published as
[Android 0.10.2/build 14](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.10.2).
The [signed release run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39037711698)
passes 99 Android + 12 shared tests, 189 Python tests, release lint, APK/AAB
signature/ZIP checks and exact signed-APK emulator acceptance. APK SHA-256:
`a0988bf2def68fdb3bea0a9b3a24eb156bb0a9b8e384db5a1f4c331abea567fa`.
The release manifest and acceptance report bind that hash to source and run.

The 334-pack/10,000-sticker synthetic fixture passes scrolling, cached animated
posters/detail playback, 360dp/200% text, tablet two-pane layout, filter/target
sheets, memory trim/restart preservation and real MP4 preview without saving.
PSS is 32,581 KB before scrolling and 47,958 KB afterward. Preview interaction
takes 36.2 seconds including picker/UI assertions/screenshots; it is not an
isolated encoder benchmark. No physical phone/WhatsApp acceptance is inferred.
Early failed fixture/setup and dismissal-timing runs are not counted as passes.

Factory strict schemas and persisted bounded output/network retries retain daily
budgets and provider cooldowns. Cheap 15-minute preflight checks leave successful
batches three hours apart and resume failed completed-output uploads/migration
without asking AI again. [The live main run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39037827620)
generated and persisted 5/40 stickers for October 17. A complete normal-cooldown
40/40 day and broader content acceptance remain pending; do not force generation.

README and Admin expose stable latest APK/release/checksum links. Relevant main
Android pushes run the signed publication workflow; changed releases require a
new Android version. Existing production signing secrets are reused. Vercel
serves the updated admin with a working public download redirect; normal
unsigned session status and protected API 401 behavior are verified. Local
browser fixtures and live 390px/1280px page checks pass. Owner mutations were not
repeated in production. See ANDROID_RELEASES.md for distribution and limits.

PR #20 was merged while verification was running; the compact Library and final
test-driver fixes were subsequently integrated into main before this release.
The older sections below are historical, including their draft/deployment claims.

## Latest — signed Android 0.10.1 and iPhone client verification

Work is on `codex/iphone-client-and-navigation`, [draft PR #18](https://github.com/dvajjala-ui/stickerdock/pull/18).
It includes the previously local recovery changes; no production deployment or
merge occurred. Vercel auto-deployment is disabled for this branch. Read
REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md before claiming requirements complete.

Android 0.10.1/code 13: 97 Android + 12 shared tests, 178 Python/no skips, lint
0 errors/43 warnings, signed APK/AAB and signature/16 KB ZIP+ELF checks pass.
`work/StickerDock-0.10.1-recovery.apk` SHA-256:
`ACBB81AA99B4E52D9CEA6EC68D8D73BCF7CD735DBB940FE6D030C48CDA7EB620`.
Exact installed APK passes Create/import toolbar+system Back, 360dp/1.3x text,
content-first real-photo grid, missing WhatsApp/Business target sheet, moving MP4
preview, explicit save and restart with every prior row retained (10 packs/33 to
10/34 stickers). Evidence: work/release-0.10.1-acceptance/status.json and native/.
Actual Swift-exported ZIP then imports through the Android picker byte-exactly,
retaining MATURE, GENERAL, name and every old row (11/35 after import/restart).
The native archive-export test passed on b5b33c6; that whole Apple run failed a
separate incorrectly generated rotation fixture. Its corrected successor must
pass independently. Android CI b5b33c6 passes at
https://github.com/dvajjala-ui/stickerdock/actions/runs/38907007546.

Portable schema-5 categories use GENERAL for the All label. Shared writers map
unknown private aliases to GENERAL; strict Android readers still reject invalid
v5 categories. UUID readers accept both cases, writers emit lower case. A ZIP
with a manifest never silently falls back to lossy generic import. Earlier
failed synthetic imports remain in emulator QA data, not counted as passes.

The native iOS 17+ source in ios/ has Discover/Create/Library, Photos/Files,
photo/GIF/video creation, crop/caption variants, photo Vision subject removal,
atomic local store, favorites, bounded ZIP interchange and explicit catalog save.
It uses the actual shared WebP core via a bounded bulk Base64 adapter. Official
WhatsApp handoff uses the general pasteboard, local-only/60 seconds, then opens
whatsapp://stickerPack. Opening never proves installation. No iOS Business routing
or in-app account selection is asserted.

Public-repository standard macOS CI solves the unavailable-local-Mac build gate.
First source e2af790: unsigned device app + frameworks + 6 native tests/1 UI test
pass at https://github.com/dvajjala-ui/stickerdock/actions/runs/38899085950.
Android CI passes at https://github.com/dvajjala-ui/stickerdock/actions/runs/38899086147.
Newer motion/rotation/live-catalog checks and exact pinned transitive dependencies
require their own latest run result. First artifact is retained locally as
work/StickerDock-apple-first-pass.zip; its SHA matches GitHub's digest.

Still incomplete: physical WhatsApp/Business/iPhone, Apple distribution signing,
iOS cloud/account adapter, native Groq assistant, durable editable video projects,
full original/project backup, broader reviewed content and full UI/performance/
security/operational acceptance. Do not call the whole product production ready.

## 0.9.1 — historical shared-core and sample handoff

See CROSS_PLATFORM_AND_PRODUCT_DIRECTION.md. All eight references were inspected;
Kotlin Multiplatform is now selected for incremental sharing, with native media/
file/WhatsApp ports. `sticker-core` is consumed by Android, with 5 common tests
plus 94 Android tests passing, release lint (0 errors/43 warnings), signed builds
and a core-only configuration check. Shared animation mux output is byte-exact
for five previously verified real-media exports. The Mac framework workflow is
manual and unrun; no iPhone client or Apple acceptance exists. Current artifact:
`work/StickerDock-0.9.1-recovery.apk`, SHA-256
`7ED1D2AC4C09E3395ABD36D757D68D13AA9978ADB827EBF37BDD2D476CF0C63E`.
Signature/alignment pass. Exact installed-APK emulator acceptance verifies
real-photo 512px previews, new moving MP4 creation, explicit existing-pack save,
prior-data preservation and force-stop persistence (9 packs/30 to 9/31 stickers).
Hash-bound evidence: `work/release-0.9.1-acceptance/status.json`. No current-tree
GitHub CI or physical WhatsApp/Business acceptance is claimed.

Photo/clip CLI focal points prevent center crops cutting off subjects. Python:
176 passed/no skips. Three more source works yield 12 human/animal reaction
samples with credits; their four packs import into signed 0.9 and survive restart
with exact persisted bytes. Report: work/broader-reactions-emulator/acceptance/.
No model purchase, additional API key, cloud deployment or publication occurred.
Next: native Groq assistance/batch creation, automatic cutout/manual correction,
durable video projects, favorites/recents and content-led shared-screen work.

## 0.9.0 — historical native reaction creator handoff

User reaffirmed Groq-first assistance with two existing roles (trend research and
media/caption creation), not a required OpenAI purchase. R31 records this.
All eight supplied repos and actual Pinterest/Sticker.ly media previews were
inspected; decisions and hashes: REAL_MEDIA_CREATOR_PLAN_2026-10-16.md.

New independent native GIF/video creator source uses Android decoders/bitmap
compression plus a spec-based WebP animation container; no third-party binary.
Picker → trim/framing/caption/fps → animated preview → explicit new/existing
animated-pack save. Local-only media processing, safe failure/duplicate guards,
no-motion checks and GIF pre-decode resource bounds. Schema stays SQLite 8/ZIP 5.
92 local Android tests, final signed APK/AAB build, signature and 16 KB alignment
pass. Release lint has 0 errors/39 warnings. Real WebM testing caught a repeated
first-frame bug in Android's timestamp-thumbnail API; source now decodes forward
with MediaExtractor/MediaCodec, with crop/stride/color/rotation checks. The exact
replacement passes native WebM/MP4/GIF preview, caption invalidation, explicit
new/existing-pack save, byte/alpha/motion checks and force-stop persistence.
A real rotated portrait clip also passes upright pixel comparison and margins.
Verified copy: work/StickerDock-0.9.0-recovery.apk, SHA-256 4F37F9AD…; exact
identity/reports/limits are in RECOVERY_PROGRESS.md and
work/release-0.9-acceptance/status.json. No physical WhatsApp or iPhone acceptance.

Worker photo/clip composition and six-frame Groq vision context are local,
not deployed. Full Python suite: 173 passed/no skips. Real licensed source trial
archives imported into signed 0.8, native moving preview verified. Do not confuse
one clip/caption variants with finished catalog quality or live Groq acceptance.

Next implementation: native Groq caption/trim suggestions with consent and
server-side credentials/budgets; durable editable video projects; broader
rights-cleared human/animal/contextual reaction sources and populated discovery
UI acceptance. Preserve the verified local baseline and explicit-save behavior.

## Recovery continuation — 0.8.0 validation

Current source adds local editable projects/durable drafts and manual cutout.
SQLite v8 is an additive migration from v7; ZIP schema 5 and cloud manifests
continue to contain saved stickers only. The 512px starting sticker and recipes
stay private on this device. See LOCAL_ARTWORK_PROJECTS.md for commit/deletion/
copy/Undo semantics and RECOVERY_PROGRESS.md for current build/device evidence.
Final local rebuild passes 78 Android tests, release lint (0 errors/35 warnings),
signed APK/AAB, signature and 16 KB alignment checks. Final signed release passes
caption/keyboard, 360dp/130% text cutout footer, transparent cutout export and
force-stop/cold-boot draft recovery. Exact artifact and hash-bound reports are in
RECOVERY_PROGRESS.md; copy is work/StickerDock-0.8.0-recovery.apk. Superseded
0.8.0 hashes must not be distributed. No deployment or current working-tree CI.

October 16 steering: user rejects the scope remaining confined to synthetic
fixtures. Broaden around real WhatsApp reaction photos, contextual memes and
animated footage; research all eight supplied repositories and actual public
Pinterest/Sticker.ly previews. Source/architecture changes are authorized while
existing data/migration/privacy requirements remain. R29/R30 capture this. No
third-party app/catalog ownership or media redistribution rights are inferred.

## Recovery continuation — local 0.7.0 candidate

See [RECOVERY_PROGRESS.md](RECOVERY_PROGRESS.md) for the current code/artifact
and acceptance record. This supersedes automatic-import descriptions below:
Discover refresh is read-only, previews are separate, and saving requires an
explicit user action. Catalog v1 accepts optional checksum-verified artwork
previews. Factory 429 state persists with a visible cooldown and resumes across
workers; provider authentication failures remain failures. New code is local,
not deployed. Existing cloud-sync changes are preserved. Phone unavailable;
device/WhatsApp acceptance remains pending.

Continuation: signed 0.7.0 now adds bounded undo/redo, saved-instance editor
recovery, discard protection and a compact populated library. Cold emulator
restart exposed a zero-byte edited file despite valid SQL; imports/restores/
transfers/edits now sync and verify artwork before database commits. The new
signed candidate passes 65 Android tests, lint, background process recovery and
byte/checksum/native-preview checks after cold boot. See RECOVERY_PROGRESS.md
for exact artifact identity and limits; old SQL-only save evidence is superseded.

## Cloud library and owner sign-in — October 15, 0.6.0

R19–R21 are implemented in source and deployed on Vercel. See
`docs/CLOUD_LIBRARY.md` for flows, limits and acceptance gates. Additive migration
202610150001 applied; five private tables have RLS enabled and no client grants.
Unknown cloud categories become ALL; native fallback label is All. Original
local files and SQLite/ZIP schema compatibility are preserved.

Verified live cloud smoke at code 384a96f:
https://github.com/dvajjala-ui/stickerdock/actions/runs/38716261393
covers two disposable confirmed accounts, private images, metadata, idempotent
commit, checksum restore, isolation, rights gate, submission/withdrawal, deletion
and cleanup. No personal files or public test publication. Final retry/UI/metadata changes pass code CI at d9cba2b:
https://github.com/dvajjala-ui/stickerdock/actions/runs/38717657942.
Do not confuse this with phone acceptance.

Admin is live-only and now asks for normal owner email/password, not a pasted
owner token. Secure HttpOnly cookies support refresh/logout; legacy token remains
for operational scripts. Owner allowlist defaults to dvjjala@gmail.com. Password
reset and account confirmation use /account; Supabase Site URL was corrected
from localhost to https://stickerdock.vercel.app/account. User explicitly asked
to skip SMTP setup, so production account-email delivery remains pending. Do not
disable confirmation or claim new-user mail delivery is ready. No real owner
password was created or changed by the agent.

Local validation: 52 Android unit tests and release lint passed; full Python
suite 151 run / 147 passed / 4 local video skips, plus 4 owner-session tests.
Hosted admin sign-in and account recovery page pass at 390px without page errors
or horizontal overflow. Signed 0.6.0 APK/AAB use existing release certificate. Final APK SHA-256:
16AA3FCEC9795F245A651845692630E132633842B421A08F6102A9642818FEBE.
Root opens Admin; Studio at /studio shares the same owner browser session.
No ADB device connected; WhatsApp, large-library sync, and physical-phone
acceptance remain pending. Pinterest approval and full daily Factory capacity
also retain their existing external/operational gates.

## Pinterest and real-media continuation

Final LIVE publication: `Desi photo reactions` is published with four reviewed
CC0-derived stickers, including the successful Groq request result. The old
five-emoji demo is unpublished and remains a private draft. Public ZIP checksum
read-back passed; one pack is in the catalog. Fresh authenticated read-back
resolved the earlier mismatch. Mutable index uploads now use max-age=0 while
immutable pack ZIPs retain cacheability. Galaxy import/WhatsApp acceptance is
still pending. No Android binary/source changes were needed for this catalog.

Final follow-up adds fresh authenticated storage reads (cache nonce + no-cache)
after a real catalog overwrite/read-back mismatch. This also protects mutable
worker state and OAuth connection reads. Focused 59 backend regressions pass;
verify hosted publication after deploying this follow-up. A four-sticker real
photo draft includes three reviewed reactions and one completed Groq vision
result. Public old-demo removal is being reconciled; no originals were deleted.

Latest live evidence: new Pinterest app **1624615** is submitted and Trial pending;
its Details show the corrected real About/Privacy URLs and transparent revised
description. Its temporary limited token was tested once and rejected with
HTTP 401/code 3 (consumer type unsupported). App secret and redirect settings are
locked until review. Do not report Pinterest as connected. Daily official health
check ran at https://github.com/dvajjala-ui/stickerdock/actions/runs/38709208606;
the job completed but correctly reported blocked credentials. GitHub app ID and
daily-check enable variable are configured.

Real source pipeline: three verified CC0/public-domain cat photos seeded in the
bounded queue; manual queue proceeds when discovery fails. The latest Factory
run respects existing cooldown at 10/40:
https://github.com/dvajjala-ui/stickerdock/actions/runs/38709211736.
The hosted Creator queue completed a real licensed-photo/Groq vision request on
its first attempt:
https://github.com/dvajjala-ui/stickerdock/actions/runs/38709866164.
Portrait photos now crop to a large 460×330 reaction area rather than a narrow
strip; source bytes remain unchanged. The original local library is still private
to the phone, not synced to our server.

See PINTEREST_RECONNECTION.md; this supersedes older Pinterest-downloader notes.
App 1624134 is Trial denied and its details are locked; the dashboard does not
provide a specific reason. Corrected application fields are prepared with real
`/about`, `/privacy`, `/support` URLs and the owner's chosen contact
`dvjjala@gmail.com`. Submission/approval must be verified separately.

Admin now includes official read-only OAuth, live boards/Pin links, cursor paging,
status/check and disconnect. No Pin content is persisted or passed into AI.
OAuth callback uses signed ten-minute state and a secure cookie; credentials
stay server-side. Configure PINTEREST_APP_ID/SECRET after approval. The scheduled
Pinterest workflow now checks health daily; legacy archiving entry is retired.
Existing remote assets were not silently deleted. Legacy Pinterest sources are
excluded from new Factory/Creator conversion.

Production config now uses photo/clip modes with `require_real_media=true` and
licensed-source filtering; no automatic template-face substitution. Existing
illustration records remain historical output. The user wants real-image meme
reactions; public Sticker.ly examples were reviewed as references, not imported.
Phone imports remain local; no ADB phone was connected. Optional private cloud
library sync remains pending in R18; user chose web references for this pass.

Validation: 138 Python tests run, 134 passed and four local video dependency
skips. OAuth checks cover cookie/state expiry/tampering, refresh, no credential
leaks, and no Pin persistence. Admin flow/background regression and public-page/
Pinterest mobile preview checks pass. Hosted new-flow validation still required.

## Latest production-flow continuation

Hosted owner login, health/overview and private media reads pass at
https://stickerdock.vercel.app/admin with protected Production secrets. The owner
token backup is outside Git at
`%USERPROFILE%/.codex/toolchains/stickerdock/private-web/owner-access-token.txt`;
Windows ACLs restrict it to the current user and SYSTEM. Never print it in logs.
Existing Factory draft migration, signed ZIP checksum verification, review and
publication pass. One five-sticker GENERAL pack is now published. Phone delivery
and WhatsApp acceptance remain unverified.

Hosted usability review found background reads could block navigation/reset
forms. Background refresh is now nonblocking, ignores stale responses, leaves
settings/generation forms alone, and has a request timeout. Overview reads run
concurrently with four workers. The focused browser regression and 18 admin API
tests pass locally; deploy this follow-up and verify the hosted behavior.

This section supersedes older pending-signing and admin feature notes below.

- Admin now supports adding inbox/AI result images into an existing static draft,
  category editing, direct AI-result preview and result-to-pack selection.
  Unsaved edits are protected when closing, paging or changing review status.
- Setup & health checks distinguish absent server config/schema from empty data.
  The read-only production-readiness workflow uses existing GitHub secrets;
  neither endpoint nor workflow applies migrations or publishes output.
- Factory batch controls match the generator's 3–10 limit. Audience changes wait
  until the next UTC day after generation starts; archive-derived ratings prevent
  mixed audience output from being mislabeled. CLI publication uses immutable
  hash-bearing filenames. Studio bounds large AI source uploads before encoding.
- Python: 129 tests run, 125 passed, 4 local video dependency skips. Chromium local
  admin/Studio flow checks passed at 390px. Mocked API responses are not hosted
  acceptance. Existing Android source passed CI at
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38699209731.
- Signed release APK/AAB 0.5.0 built locally; APK signature verifies. SHA-256:
  `B2A0404289CD9029F2DD206130FC62A24B68D98A65734E83D689AE3B455176F4`.
  Four signing secrets are configured in the GitHub `android-production`
  environment. Private key backup is outside the repository under
  `%USERPROFILE%/.codex/toolchains/stickerdock/private-release`; credentials are
  protected with Windows DPAPI. Preserve/export a secure backup before changing
  machines. Back up app data before switching from a debug-signed installation.
- LIVE: current debug CI and signed release CI passed at
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38703559085 and
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38703589173.
- LIVE: readiness run 38703586594 verified all eight required tables/selected
  columns are available and zero packs are published. Do not reapply migrations
  just because older notes below still describe them as pending.
- LIVE: new Vercel Hobby project https://stickerdock.vercel.app is deployed from
  `d5e4f6c`; Studio/admin pages return 200, unauthenticated admin API returns 401.
  User approved adding the Supabase server key and owner token as protected
  Production secrets. Saved CLI auth returns 403; use the working browser session.
  Complete secret setup/redeploy and authenticated hosted smoke before claiming
  remotely usable backend features.
- LIVE: Factory run 38704185175 generated five stickers, persisted 10/40 for
  October 15 and used RSS content discovery. Normal-cooldown daily completion,
  reviewed publication and phone acceptance remain gates. Do not claim public
  AI delivery or WhatsApp acceptance from local tests.

## Current Android continuation

October 15 follow-up — candidate 0.5.0:

- Direct photo-to-pack creation from Library/Import; compatible grouping, global dedup and 30-sticker split. Shared photos use the same path; corrupt shared archives do not stop following files.
- Native static artwork editor: rotate, mirror, zoom/crop, reposition and top/bottom captions. Safe replacement retains sticker identity/metadata, validates stale saves and uses a new immutable filename/version. Background removal and animated artwork editing remain pending.
- Copy/Move can create a new compatible destination pack inline. Existing packs use Open WhatsApp/Business; new packs use the official Add flow. Export preflight identifies invalid/missing/damaged media before launch.
- Bounded single-query pack previews replace full per-pack loads; selection-aware refresh cancels stale loads. Catalog import tracking uses restore's exact IDs and serializes manual/automatic imports. Photo ImageDecoder respects EXIF rotation.
- Final local Android unit suite: 52 tests, zero failures/errors. Debug APK assembly and release lint passed (lint retains non-blocking dependency/KTX/icon suggestions). APK SHA-256: 1D48391B4F6F25323685662A068184602E3CE07EC9238E856707029C2574DE49.
- LIVE read-only catalog check: HTTP 400 with NoSuchKey/Object not found on October 15. Approved catalog publication is still pending, so no automatic live delivery is claimed. No ADB device was connected; WhatsApp/Business phone acceptance is still pending. No migrations/publication/signing changes made in this follow-up.
- CORE_FLOW_REVIEW_2026-10-15.md maps all ten current requirements to source flow, limitations and remaining acceptance. REQUIREMENTS_LEDGER.md retains request/status history. Privacy/network wording now matches automatic catalog downloads.

Previous 0.4.0 continuation (historical):

- Pulled main a9d5883 and retained the polished adaptive launcher UI.
- Candidate 0.4.0 adds official per-target WhatsApp whitelist status, Add/Sync actions, activity-result validation errors and provider change notifications. Version increments already existed; WhatsApp controls cache refresh timing. Real personal/Business device acceptance remains pending.
- Approved catalog revisions now import automatically in bounded batches of three on open/resume. Deleted revisions are remembered so automatic delivery does not recreate user-deleted packs. New tab removes internal factory/source language and the disabled Studio card.
- Requirements and unfinished work are tracked in REQUIREMENTS_LEDGER.md. Preserve IDs/history and add subsequent user requests there.
- Final local verification: 46 Android unit tests passed, debug APK assembled successfully (0.4.0), and git diff --check passed. APK SHA-256: BD148E3E0FE52D12A4AC20E80BA70B90F1D040E80DDE3BB753B28C5D632BBB49. GitHub build 38697171985 subsequently passed. No new deployment, database apply or public publication is implied.

## Goal and historical decisions

StickerDock is a Kotlin/Jetpack Compose Android sticker manager for organizing a
user-owned library independently from WhatsApp and Sticker.ly. Requirements grew
from local static/animated pack management into categorized search/import/export,
WhatsApp/Business migration, resumable very-large-folder import, curated public
packs, and a separate private AI Sticker Factory/Creator Studio.

The Android app must never contain Groq or Supabase service-role credentials.
Private generation/storage lives in GitHub Actions + Supabase. Public content is
explicitly reviewed and GENERAL-only; MATURE content remains private/local. Do
not automatically mirror Sticker.ly/Pinterest creator catalogs or treat platform
access as reuse permission.

## Key Android technical details

- Kotlin + Jetpack Compose; JDK 17, Android SDK 36, Gradle 8.11.1.
- Debug/release defaults now use the production Supabase catalog. Home exposes
  “From the Factory” and refreshes on open/resume. Catalog ID/hash mappings avoid
  repeat downloads. Supabase's precise HTTP-400/NoSuchKey empty-index response
  is handled without hiding other service errors. See `ADMIN_PORTAL.md` for the
  implemented owner web app and its unapplied database migration/deployment gates.
- SQLite schema v7 with pack kind/rating/category, sticker labels/tags, SHA-256
  indexes, a transactional folder-import journal and additive migrations.
- Backup schema v5; restoration remains compatible with schemas 1–4.
- Static normalization: 512px WebP <=100KB. Valid animated WebP <=500KB / 10s.
- Categorized pack/sticker search, static/animated filtering, global sticker
  search, copy/move/remove, metadata editing and content-hash deduplication.
- FolderStickerImporter uses bounded SAF traversal, SQLite-journaled processed
  URIs/outcomes and explicit Pause/Resume/Discard. It persists selected read URIs
  where Android permits and reports folders omitted at the depth bound. Real
  process-death/provider-permission QA is still pending.
- Backup restore stages bounded entries on disk and preserves validated WebP bytes
  instead of recompressing static stickers. Full exports preflight against the
  same 500-pack/15,000-sticker/1.5 GB restore bounds.
- PackZipImporter supports bounded ZIP/.wastickers/StickerDock archives and bulk
  selection via `OpenMultipleDocuments`.
- `tools/adb_sticker_export.py` is an optional no-root desktop helper for a user's
  authorized WhatsApp/Business media folders.
- `WhatsAppStickerProvider` implements the documented provider contract/install
  intents but still needs current real-device acceptance testing.
- `CommunityCatalog` is read-only; public catalog remains review-gated.

## Sticker.ly / third-party boundaries

Sticker.ly has no verified official full-account or entire-catalog export API for
this project. Public Sticker.ly links are bookmarks/navigation only. Historical
unofficial wrappers must not be treated as durable or as permission to bypass
protected endpoints/rehost creators' content. A user-mentioned modified APK has
not been inspected and must not be assumed to contain server-side catalog assets.

Useful public references:

- WhatsApp official third-party stickers docs:
  https://github.com/WhatsApp/stickers/blob/main/Android/README.md
- StickerVault folder-backup/EXIF ideas (GPL-3.0 architecture inspiration only):
  https://github.com/mintnchocochip/stickervault
- Sticker.ly terms: https://sticker.ly/terms-of-use

## Production architecture

### Supabase

Production project:

- project ref: `qqicsllktmcmvmzplsxl`
- URL: `https://qqicsllktmcmvmzplsxl.supabase.co`

Both production migrations are already applied and verified:

1. `202610120001_sticker_factory.sql`
2. `202610130001_creator_requests.sql`

Verified state:

- RLS enabled on `factory_runs`, `sticker_packs`, `creator_requests`;
- `factory_runs` / `creator_requests` table access is service-role only;
- client reads of `sticker_packs` are limited to approved GENERAL rows;
- `claim_creator_request(text)` is SECURITY INVOKER with empty search path,
  service-role-only EXECUTE and atomic `FOR UPDATE SKIP LOCKED` claiming;
- `sticker-factory-private` is private;
- `sticker-factory-public` is public;
- no broad client `storage.objects` SELECT/list policy exists;
- all expected `updated_at` triggers exist.

The production secret is the current opaque `sb_secret_...` API-key format.
Server-side clients support this by sending `apikey` without incorrectly treating
it as a JWT bearer token; legacy service-role JWT support remains for backwards
compatibility.

### GitHub production secrets / models

Validated GitHub Actions secrets:

- `GROQ_API_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`

New source support expects one additional Actions secret:

- `GROQ_TRENDS_API_KEY` for the once-daily browser-search content brief. It is
  separate from generation so the owner may use a second Groq account/quota.

Do not print, copy into source, or expose either value. The Supabase project URL
is non-secret and is set directly in workflows. Current defaults:

- Groq text: `openai/gpt-oss-20b`
- Groq vision: `qwen/qwen3.8-27b`

Both models authenticated successfully in a real GitHub Actions smoke test.

### Sticker Factory

`tools/production_worker.py` + `tools/factory-production.json` provide the private
persistent Factory. State is saved in Supabase so fresh GitHub runners resume the
same UTC day. Generated packs remain private/pending until explicit review.
With the unapplied October 14 admin migration, the first run of the day also
caches a private content brief, rotates selected categories, exports Android
category metadata and can auto-migrate complete output to an Admin draft.

Live evidence completed October 13, 2026:

- controlled real Groq batch generated **5/5** GENERAL illustration stickers;
- `factory_runs` persisted `complete` for that controlled target;
- private ZIP checksum matched the `sticker_packs` record;
- the pack stayed `pending` and the public bucket remained empty;
- follow-up run with the real production config resumed on a fresh runner from
  5 to **10/40**;
- persistent runtime state contained all 10 records;
- two reference-library assets synced;
- attempts reached 2 and the public bucket remained empty.

Do not force the remaining 30 by bypassing cooldown. The recurring Factory
schedule is enabled by default unless `STICKER_FACTORY_ENABLED=false`, but no
normal-cooldown 40/40 day has been reviewed yet.
Do not publish the five-sticker smoke pack.

### Creator Studio / private AI queue

`studio/` + `api/` are the private browser editor/serverless API source. Android
still shows AI meme creator as Coming soon.

A real isolated production Creator smoke passed:

1. verified queue was empty before injecting test work;
2. created one unique private source PNG + USER_OWNED provenance;
3. queued one `creator_requests` row;
4. real worker atomically claimed it exactly once;
5. Groq vision generated the caption path;
6. local renderer produced a bounded static private WebP;
7. result SHA/path and provenance back to source/request were verified;
8. request reached `complete` with one attempt;
9. test request/source/result/provenance were deleted afterward.

Merged PR #6 (`feat/creator-studio-live-ai`) added:

- fixes `api/_shared.py` for `sb_secret_...` auth;
- enables the browser AI prompt/button;
- uploads a local source to the private inbox before queueing when needed;
- queues `/api/captions`, polls status, fetches the private result, and loads it
  back into the editor;
- schedules **Creator request worker** every 10 minutes;
- uses one cheap Supabase preflight so empty queues skip Python/package/font
  setup;
- can drain up to five queued requests in one serialized active run;
- preserves all previous Studio API tests and adds new-secret/legacy auth tests.

Validation for that source:

- complete Python helper suite: **75 tests passed**;
- headless Chromium manual editor smoke passed: upload, canvas/caption/style,
  drag, 31,012-byte PNG download, token guard, no page/console errors and 0px
  horizontal mobile overflow;
- Chromium AI network-boundary smoke passed: source upload → queue → status →
  private result fetch → result loaded/download enabled;
- restored/preserved Studio API regression module passed after checking no older
  coverage had been deleted.

PR #6 is merged at `d22a5ce6dfa37a02c4bb9596b7c7bbb5c994b2cb`.

Hosted Vercel is **not yet verified**. No public deployment URL was discoverable.
To deploy the merged source, configure the Vercel/server environment:

- `STUDIO_ACCESS_TOKEN`
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`

Then run one authenticated hosted Chromium/API smoke before treating Studio as a
remotely available feature. Android may remain Coming soon until that exposure is
intentional.

### Storage monitor

The production read-only storage monitor passed live:

- private bucket: **7 objects / 4,204,527 bytes (~4.01 MB)**;
- public bucket: **0 objects**;
- >30-day private generated retention candidates: **none**;
- no deletion occurred.

The actual Supabase plan storage quota is not configured, so the run was
report-only and calculated no usage percentage. Next step is to set the real
`SUPABASE_STORAGE_QUOTA_MB`, review warning/failure thresholds, then decide when
to set `STORAGE_MONITOR_ENABLED=true`.

### Pinterest / media sources

`tools/media_library.py` supports bounded CC0/public-domain Wikimedia discovery.
`tools/pinterest_autosync` / `tools/pinterest_worker.py` support official authorized
Pinterest boards with rolling budgets, retry/dedup and private provenance.
Pinterest defaults to `REFERENCE_ONLY`; public publishing refuses those derived
pixels unless explicit compatible rights metadata exists.

Pinterest's developer dashboard currently shows Trial access pending for
`apphidwarkesh`, and the supplied token was rejected by API v5. The public-topic
adapter is configured as a private `REFERENCE_ONLY` fallback while the official
connector is paused. Official board sync still requires Pinterest approval and a
fresh OAuth token; board IDs can then be discovered automatically.

`tools/video_stickers.py` converts authorized GIF/MP4/WebM/MOV inputs to bounded
captioned animated WebP using FFmpeg. This does not imply device-side animated
preview/install has been accepted on a real phone.

## Remaining production gates

These are the meaningful remaining gates; do not redo already-passed migrations,
credential checks, 5/5 Factory smoke, 10/40 resume smoke, Creator queue smoke, or
first storage report unless a later change specifically invalidates them.

1. Let the Factory finish one **normal-cooldown 40/40** UTC day; review real Groq
   metrics and the completed private pack.
2. Publish one reviewed GENERAL completed-day pack, confirm public catalog/object,
   then import that exact pack into Android.
3. Deploy the merged Creator Studio source to Vercel, configure its three server
   environment values, and run the hosted authenticated browser/API smoke.
4. Configure the actual Supabase storage quota/thresholds, then optionally enable
   daily storage monitoring.
5. After Pinterest approves the app, generate a fresh token, re-enable the
   official connector and run one official-board smoke sync.
6. Configure Android signing/catalog release settings.
7. On Galaxy S24 Ultra test WhatsApp + WhatsApp Business install, animated
   previews, folder URI persistence, cancel/retry and forced process-death
   recovery with 10k+ files.
8. Finish privacy policy, app identity, Play listing and release regression.
9. Before accepting public submissions, build authenticated submission, malware
   scanning, rights review, reporting/block/moderation and age controls.

## Operational caution

User data stays private unless explicitly shared. ADB stays local. Avoid
unnecessary cloud uploads or expensive infrastructure. Do not expose Groq or
Supabase privileged keys in Android/browser/client code. Respect third-party
content ownership and treat Pinterest/Sticker.ly access separately from reuse
rights.
