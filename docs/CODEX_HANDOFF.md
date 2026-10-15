# StickerDock development handoff — updated October 15, 2026

## Pinterest and real-media continuation

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
