# StickerDock verification checklist

## Editable backup continuation

New Android editable backup tests cover originals/masks/saved recipes/drafts,
Undo/Redo/restart/Reset, unchanged portable v5 imports, archive routing, damaged
blobs/recipes and project-storage failure rollback/retry. Source `744a13d` passes
[Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39165983862).
Updated source `cf9d8b0` passes native/shared/backend tests, lint, build and
signing/alignment; [signed emulator acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39202677418)
fails on a test-script label mismatch (`Save` versus the system picker's `SAVE`),
after prior library/editor/sharing journeys and backup format/cancel/large-text
checks. The script is corrected; original-image validation also now fully
decodes pixels, with a forged-valid-checksum malformed WebP regression.
Screenshot inspection also found pill-shaped option buttons clipping text at
200% size. They are replaced by moderately rounded option cards in the scroll
area. Rerun required for complete DocumentsUI export/restore and persisted history.
Physical Samsung Files/Drive backup picker acceptance remains pending.

## October 19 — current continuation

- **Verified candidate `019c75f`:** [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39161490697)
  and [complete signed-APK acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39161487231)
  pass: **116 Android/12 shared/208 Python tests**, lint, signing and alignment.
  APK SHA-256 `6c9856e53b2d18bbff58ca0fae5086f5eeda3c1a944a45d2460732c892a8aa24`.
  Downloaded APK hash matches both release.json and acceptance.json. Candidate
  files are under ignored `work/StickerDock-0.11.0/`; stable remains 0.10.2.
- That exact APK passes 334-pack/10k-sticker browsing, animation, tablet,
  360dp/200% text, memory trim/restart, real MP4 preview, named photo save/cancel,
  brush undo/redo/cancel, durable draft/process restart, explicit Save/exact Reset,
  public code cold/warm/share delivery, and received-file cancel/import/restart.
  Scrolling PSS is **40,919→67,443 KB**. Create, editor draft, pack-code and
  received-file screenshots were inspected. Test artwork is synthetic/repo-owned.
- The received-file fixture uses a real scoped FileProvider URI from the app's
  share directory. It does not establish another app's document-grant behavior.
  Unit round-trip checks preserve artwork hashes, labels/tags and private ratings;
  inaccessible files remain retryable. Physical S24/WhatsApp/Business, live
  segmentation quality, published-code/HTTPS sharing and original AI artwork
  quality remain separate gates. A fresh local ADB check sees no device.

- `8eac289` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39159539977),
  including the provider/content-URI fixture corrections, native regressions,
  backend checks, lint and builds. [Signed APK interaction run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39159546406)
  passes brush undo/redo/cancel, durable draft/process restart, save/exact reset
  and public code cold/warm/share handling. It then fails at received-file import
  using a shell-created DocumentsProvider URI. Overall signed acceptance fails.
  The fixture now uses a scoped FileProvider URI matching pack sharing; import
  errors also retain failed files for retry and expose a reason. A matching new
  signed run is required. Create and brush screenshots from this run were inspected.
- `4945ce0` [failed](https://github.com/dvajjala-ui/stickerdock/actions/runs/39139552573)
  two unit tests: provider metadata/resources were missing from the unit-test
  environment, and an older share fixture used file URIs. Those test fixtures
  are corrected. Network/raw file URI rejection remains enforced for incoming
  shares. This failed source is not a verified APK candidate.

## 0.11.0 — pack-focused Android candidate, October 18

- Design and public-code source `1d1d95c` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39138509715).
  The earlier design [signed run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39138121727)
  reached the brush scenario but its exact-match scroll helper skipped the sticker
  whose accessibility label includes tags. The test now selects that visible
  sticker directly. Library/large-text screenshots from that run were inspected;
  this is not a successful brush scenario. Received-file changes at `4945ce0`
  and their complete signed acceptance are pending.

- Automatic-cutout source `377b97a` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39133885444):
  build, tests, lint and native alignment. Mask tests cover soft alpha, original
  transparency, retained recipes, draft/copy/deletion recovery and damaged-mask
  refusal. This does not prove live Google Play model availability or quality.
- Brush source at `7878bbb` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39134519338):
  112 Android/12 shared/208 Python tests plus lint/build. The [signed run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39134517018)
  passed build/signature checks and existing scenarios, but stopped before brush
  interaction: its locator matched both the search field and pack title. The
  fixture now searches `001` instead of the full title. This failed run does not
  establish brush interaction acceptance. A rerun also covers the subsequent
  typography/card/Create/editor-tool layout changes.

- **Verified core checkpoint `db6c979`:** [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39070097918)
  and [signed APK emulator acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39070094081)
  both pass: 105 Android/12 shared/208 Python tests, lint and signature/alignment.
  SHA-256 `a80e6ee90f4ddd2747e6fddd1860114d3d34e2333bcae29766984ec47afd8151`.
  The corrected photo test proves picker selection/cancel do not save, explicit
  named-pack save adds one pack/sticker, and restart preserves it. The 10k search
  regression proves pack matching is not capped by the 100-thumbnail query.
  Scrolling PSS 31,626→60,579 KB. The local ADB device list is empty; physical
  S24/WhatsApp acceptance remains unavailable. This evidence does not cover
  subsequent work beyond this exact commit.

- First native slice `2c6f9e8`: [debug CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39068155580)
  and [signed APK acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39068160560)
  pass. 105 Android + 12 shared tests; lint, signatures/alignment and Android 15
  emulator checks. APK SHA-256 `2f61718879a3c9bf937faaa32a670928fa6039293dff1f7e7e3bf171dfa77319`.
- That emulator run covers 334 packs/10,000 synthetic stickers, animated
  poster/detail behavior, 360dp/200% text, tablet layout, scrollable controls,
  restart preservation and actual MP4 preview without mutation. PSS grew from
  32,146 to 63,996 KB during scrolling. Screenshots inspected for library,
  large-text pack detail and WhatsApp target sheet. Flat-color performance
  fixtures are not content-quality evidence.
- Code slice `1ac4af2` adds the original-art worker and an emulator test
  for photo selection → named destination → cancel/save → restart. Its
  [signed run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39069066380)
  passed build/native/backend checks and the existing emulator checks, then
  failed on a test locator: photo DocumentsUI says "Images in Downloads", not
  "Files in Downloads". The locator is corrected; a rerun is required. This is
  not a passing photo-flow claim. [Branch CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39069301039)
  passes at `222cebd`.
- Local full Python suite: **208 tests pass**, no skips. Original generation uses
  mocked provider responses; spending-disabled dry run makes zero submissions.
  No live image generation, background extraction or generated-art quality claim.
- The photo fixture is the repository-owned app icon, not a user collection or
  a claim of catalog quality. Physical WhatsApp/Business installations, personal
  SAF folders, real-artwork product sign-off and iPhone parity remain separate.
- Candidate source remains in [PR #21](https://github.com/dvajjala-ui/stickerdock/pull/21);
  the stable release is still 0.10.2.

## 0.10.2 — published Android reliability release, October 17

- [Signed release CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39037711698)
  passes 99 Android/12 shared/189 Python tests, release lint, signed APK/AAB and
  ZIP alignment. [Published assets](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.10.2)
  include source/version/checksum metadata and the exact-APK acceptance report.
- SHA-256 `a0988bf2def68fdb3bea0a9b3a24eb156bb0a9b8e384db5a1f4c331abea567fa`
  matches the downloaded APK, release manifest, GitHub asset digest and report.
- AOSP Android 15: 334 packs/10,000 synthetic stickers; scrolling PSS
  32,581→47,958 KB; animated poster/detail behavior; 360dp/200% text; Android
  tablet two-pane layout; filter/WhatsApp target controls; memory trim and
  restart data preservation; actual MP4 streaming preview with no saved rows.
- Low-storage rollback is covered through the save API in native unit tests;
  the emulator disk was not filled. Preview interaction time includes automation
  and screenshots, not just encoding. These are synthetic fixture bounds.
- Local Admin browser flow tests and live 390px/1280px page checks pass, with
  normal session status, protected API rejection and a stable APK redirect.
- [Live Factory main run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39037827620)
  produces 5/40 stickers after schema/recovery fixes. Fresh-runner synthetic
  40/day and interrupted-upload recovery pass; live full-day capacity remains open.
- Physical WhatsApp/Business, huge personal-phone imports, iPhone parity/signing
  and advanced AI/automatic-cutout/project-backup requirements remain separate.

## 0.10.1 — current Android artifact and archive acceptance

- 97 Android + 12 shared tests and 178 Python/no skips pass. Release lint has
  0 errors/43 warnings. APK/AAB signatures, integrity and 16 KB ZIP/ELF alignment pass.
- Exact installed APK ACBB81AA… passes the creation/navigation/large-text checks
  below, including moving MP4 preview, explicit save and restart: 10/33 to 10/34.
- A real native Swift ZIP imports through Android's picker with exact bytes,
  MATURE rating, GENERAL category and name retained. Every old row survives
  restart; library becomes 11/35. Test fixture is synthetic integrity evidence.
- Android CI: https://github.com/dvajjala-ui/stickerdock/actions/runs/38907007546.
  The archive-export subtest passes in Apple run 38907007539; that whole run fails
  a separate malformed rotation fixture and is not counted as a full Apple pass.
- Current corrected Apple tests, signed iPhone distribution, physical WhatsApp,
  populated-library performance, native AI/cloud/editor parity remain open.
  Hash-bound evidence: work/release-0.10.1-acceptance/status.json and subfolders.

## 0.10.0 — historical Android / first Apple acceptance

- LOCAL ANDROID: 94 Android + 9 shared tests, 178 Python/no skips, lint
  0 errors/43 warnings, signed APK/AAB, signature and 16 KB ZIP/ELF alignment pass.
- NATIVE ANDROID: exact installed APK 640D715B… passes Create/import return
  navigation with toolbar/system Back, 360dp/1.3x text, initial real-photo content,
  disabled missing WhatsApp/Business target buttons, moving MP4 preview with no
  library additions, explicit save and restart. Old rows retained, 9/31 → 9/32.
  Reports/screenshots: work/release-0.10-acceptance/native/.
- HOSTED FIRST CANDIDATE e2af790: [Android](https://github.com/dvajjala-ui/stickerdock/actions/runs/38899086147)
  and [Apple](https://github.com/dvajjala-ui/stickerdock/actions/runs/38899085950)
  pass. Apple builds device/simulator frameworks and unsigned iPhone app, then
  passes 6 native integration tests and 1 navigation UI test. Tests cover actual
  photo encoding, archive round trip/restart, duplicate/invalid import preservation,
  traversal rejection, generic static/animated ZIP separation and WhatsApp payload.
- NEWER CANDIDATE: native GIF/MP4/rotated-video tests, actual codec validation on
  import, public-catalog integration and pinned SPM resolution need the matching
  latest Apple result. Prior green CI is not proof of these newer changes.
- NOT ACCEPTED: physical WhatsApp/Business/iPhone, Apple distribution signing,
  huge real-phone libraries, native AI/cloud parity and production readiness.

See REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md and RECOVERY_PROGRESS.md.

## Shared core / expanded media — signed 0.9.1 local acceptance passed

5 common tests + 94 Android tests, release lint (0 errors/43 warnings), signed
APK/AAB builds, signature, 16 KB alignment and archive integrity pass. The shared
mux reproduces five real native exports exactly.
Core-only Gradle configuration passes; Apple targets are conditional on macOS
and have not been compiled. No working-tree CI or iPhone client is claimed.
Full Python suite: 176 passed/no skips, including focal-framing checks.

The existing signed 0.9 APK separately imports 12 new real-photo/animated samples
from three independent source works with credits. Four explicit imports add
exactly four packs/12 stickers; exact saved hashes, decoded sizes/kinds, old-row
preservation and force-stop persistence pass. Report:
work/broader-reactions-emulator/acceptance/report.json.

The exact signed 0.9.1 APK upgrade retains all these samples and prior rows.
Real-photo grid/detail 512px preview inspection, new moving MP4 reaction creation,
unchanged library during preview, explicit existing animated-pack save, saved
bytes/dimensions/motion and force-stop persistence pass. Library: 9 packs/30
stickers before save, 9/31 after. Accepted crash buffer is empty. The first
picker-navigation automation failure remains a failed harness attempt.
Hash-bound evidence: work/release-0.9.1-acceptance/status.json and native/report.json.
This is Android 15 emulator acceptance; physical WhatsApp/Business, Apple and
large real-phone libraries remain unverified.

## Native creator 0.9.0 — signed local acceptance passed

92 local Android tests pass, including WebP native decoder checks, exact saved
preview bytes, duplicate/wrong-target guards, failed storage cleanup preserving
existing packs and GIF resource bounds. Final signed APK/AAB build, signature,
16 KB alignment and release lint (0 errors/39 warnings) pass. The exact installed
APK passes native WebM/MP4/GIF preview/save/restart, unchanged library during
preview, stale-caption preview invalidation, saved byte/hash/alpha/motion checks
and rotated portrait pixels/margins. Reports are hash-bound in
work/release-0.9-acceptance/status.json; see RECOVERY_PROGRESS.md. These are local
emulator checks, not physical WhatsApp/Business or iPhone acceptance.
The first signed creator
candidate failed actual WebM motion because timestamp thumbnails repeated one
frame; it is superseded by sequential decoding. Earlier System UI boot ANR prevented the first
emulator attempt before creator entry; that attempt is not a passed app test.
The first rotation fixture lacked metadata; its failed portrait assertion was
a fixture problem and is not counted as passing. The corrected fixture has an
actual display matrix and passes upright reference comparison.
Real-media worker Python suite: 173 passed/no skips; no new deployment or CI.

## Recovery 0.8.0 — local signed candidate verified

Editable projects/drafts, manual cutout and additive SQLite v8 have local source
coverage. The 78-test run covers legacy migration/scan metadata, transactional
recipe/artifact rollback, draft persistence, copy/move/Undo, export privacy,
bounded outlines and native alpha rendering. Final signed build, lint (0 errors/
35 warnings) and emulator caption/keyboard, 360dp/130% text, cutout alpha and
force-stop/cold-boot checks pass. Hash-bound reports and exact APK identity are
recorded in RECOVERY_PROGRESS.md. These synthetic fixtures do not establish meme
quality. Real photo/animated product evidence is the next scope under R29.
Physical WhatsApp/Business and iPhone remain pending; no current working-tree CI.

## Recovery 0.7.0 — local candidate

Use [RECOVERY_PROGRESS.md](RECOVERY_PROGRESS.md) for current local checks and
artifact identity. Automatic catalog import has been removed in source. New
coverage checks read-only refresh, native legacy preview rendering/tamper
rejection, optional public preview metadata, persistent provider cooldown/recovery
and mobile admin status. No new CI or hosted deployment is implied. Physical
Galaxy/WhatsApp checks remain pending; user currently has no phone available.

The local candidate now passes 65 Android tests and signed-emulator editor
undo/redo, discard protection, background process reclamation and saved artwork
after cold boot. Byte/checksum/native-preview verification replaces earlier
SQL-only save acceptance, which missed a zero-byte edited file. There is still
no new CI run for this uncommitted candidate. Draft recovery does not yet cover
force-stop/reboot, and saved editable originals remain pending.

## Final 0.6.0 candidate evidence

- CODE CI SUCCESS at d9cba2b89d082482bb4e66aa57714248ee0d5ab6:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38717657942.
  Python regressions, Android unit tests, lint/build and native alignment checks
  completed successfully. Vercel deployment for this code also succeeded.
- Signed local 0.6.0 APK SHA-256:
  16AA3FCEC9795F245A651845692630E132633842B421A08F6102A9642818FEBE.
  Existing signing certificate verified; local APK and AAB built successfully.
- Final hosted root opens Admin; Admin and Studio have no manual token input.
  Both pass 390px no-overflow/no-page-error checks. Account recovery route works.
- Public-copy metadata regression preserves decoded pixels/animation while
  removing EXIF/XMP/custom outer chunks. Private draft/provenance regression
  confirms submission conversion validates and does not publish automatically.
- SMTP delivery and physical-phone acceptance remain pending as described below.

## Private cloud library / owner sessions — October 15

- LIVE: migration applied; cloud_packs, cloud_assets, community_submissions,
  community_reports and cloud_request_budgets all verified with RLS enabled.
- LIVE: cloud account isolation, private upload/metadata, checksum download,
  rights-confirmed submission/withdrawal, safe deletion retry and cleanup passed:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38716261393.
- LIVE: Vercel deploy succeeded; unauthenticated cloud returns 401, owner console
  reads and submissions list return 200, public catalog still has one real-image
  pack. No synthetic test pack was published.
- LOCAL: 52 Android unit tests, release lint and signed APK/AAB build passed.
  Backend suite: 151 tests / four video-dependency skips, plus four session tests.
- WEB: 390px admin sign-in/recovery pages, no overflow and no JavaScript page errors.
- PENDING BY USER DECISION: SMTP production mail setup skipped. Verified-account
  sessions work; do not promise confirmation/reset mail for arbitrary new users.
- DEVICE PENDING: local import → cloud → another phone restore, pause/retry,
  background scheduling, public report/hide/withdraw and WhatsApp targets.
- SAFETY: mature packs stay offline; sync does not grant publication or training
  rights. Full cloud account/media erasure is currently an operator support flow.

## Pinterest and real images — October 15

- LIVE FINAL: four-sticker `Desi photo reactions` pack published, public ZIP hash
  verified, old emoji demo unpublished but preserved privately. Fresh catalog
  read-back now passes. Phone delivery remains a device acceptance gate.
- FINAL LOCAL: 142 Python tests run, 138 passed, four local video dependency
  skips. HOSTED: owner login, four real pack previews, public catalog, 390px layout
  and absence of browser page errors passed. Code CI through c9ae6a2 passed:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38710633232.

- Final local full Python suite before the storage-read follow-up: 140 tests run,
  136 passed, four local dependency skips. Focused storage/OAuth/worker/admin
  regressions after the freshness fix: 59 tests passed. Portrait crop and source
  preservation are covered; source files are not rewritten.
- Live publication exposed an authenticated storage read-back mismatch after
  overwriting the catalog. Authenticated private reads now use distinct cache
  nonce URLs plus no-cache headers; hosted publication must be reverified after
  this follow-up deployment.

- LIVE: corrected StickerDock app 1624615 is submitted/Trial pending. Its real
  website/privacy links are verified in Details. Temporary token probe returns
  HTTP 401/code 3; app credentials/redirects remain locked pending review.
- HOSTED: real About/Privacy/Support pages and the Pinterest admin screen pass
  the 390px browser smoke. API status reports not connected, not false success.
- LIVE: licensed-photo Creator request completed on attempt 1, result retrieved
  and visually reviewed. Worker run:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38709866164.

- LOCAL: 138 Python tests run, 134 passed, four local video dependency skips.
  New checks cover signed OAuth state/cookie validation, expiration/tampering,
  token refresh, no credential leakage or Pin-response persistence, legacy Pin
  conversion rejection, and real-media requirement without template fallback.
- BROWSER LOCAL: About/privacy/support pages, chosen contact, 390px layout,
  Pinterest navigation and preview-mode connection guard pass. Existing admin
  flow and background-refresh regressions pass.
- LIVE DASHBOARD: app 1624134 Trial access denied; old fields are locked.
  Corrected request is prepared, not yet approved. No API connection claim.
- DEVICE: phone import stays local; no ADB device connected. No backend copy of
  the user's imported WhatsApp collection is implied.

## Production-flow continuation — October 15

- HOSTED: authenticated owner login, health, overview, private media listing,
  existing Factory draft migration and signed ZIP checksum read-back pass.
  One reviewed GENERAL pack with five stickers is published. Phone acceptance
  is still pending.
- LOCAL FOLLOW-UP: background refresh regression passes with a held response
  while navigating/editing settings; stale responses preserve edits. All 18
  admin API tests pass.

- LOCAL: Python suite ran 129 tests: 125 passed, 4 video tests skipped because
  local FFmpeg/DejaVu dependencies are unavailable. JavaScript syntax checks pass.
- BROWSER LOCAL: Chromium exercised unsaved-edit protection, metadata/category
  save, adding stickers to an existing draft, direct AI-result preview and
  result-to-pack selection. Missing-schema setup was exercised with mocked
  responses. Both admin and Studio fit a 390px viewport without horizontal overflow.
- BROWSER LOCAL: Studio resized an 11,148,045-byte source to a 1,677,496-byte
  upload; mocked upload/queue/result/editor flow passed. This does not prove
  hosted generation or deployment.
- LOCAL RELEASE: signed 0.5.0 APK and AAB built. APK signature verification and
  AAB JAR signature verification pass. APK SHA-256:
  `B2A0404289CD9029F2DD206130FC62A24B68D98A65734E83D689AE3B455176F4`.
  The signing key is backed up outside Git and four signing secrets are configured
  in the GitHub `android-production` environment. Phone acceptance is still pending.
- CI PASS: current Android debug build/tests and signed release build both pass:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38703559085 and
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38703589173.
- LIVE: read-only readiness confirms all eight required tables and selected
  columns are available, with zero published packs:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38703586594.
- LIVE: new Vercel Hobby deployment at https://stickerdock.vercel.app responds
  200 for Studio/admin and 401 for unauthenticated admin API. Server secret setup
  and authenticated hosted smoke remain pending.
- LIVE: updated Factory worker generated five more stickers and persisted 10/40
  for October 15, using the RSS content fallback. This is a successful batch,
  not a completed 40/40 day or proof of Groq browser-search availability:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38704185175.

Switching from a debug-signed installation to this release-signed APK requires
backing up the library before uninstall/reinstall. Future release updates must
keep the same signing key.

## Core-flow additions — October 15

- LOCAL: 52 Android unit tests pass, debug APK 0.5.0 assembled, release lint passes. Focused native-graphics coverage checks safe/stale artwork replacement, identity/metadata retention, rotation/transparency, 31-photo splitting, duplicate cleanup, corrupt shared-ZIP continuation and WhatsApp preflight rejection of damaged bytes. Existing backup compatibility remains covered; the 10k library fixture checks bounded previews.
- DEVICE PENDING: photo picker/EXIF orientation, editor controls/keyboard/layout, Add/Open in personal/Business and actual WhatsApp cache refresh after edits/deletions.
- LIVE: October 15 public catalog GET returned NoSuchKey; reviewed publication and end-to-end automatic phone delivery remain pending.

## Factory/admin delivery additions — October 14

- LOCAL: Python admin-created schema-v5 ZIP restores in the Android library with
  exact sticker hashes and metadata. Catalog tracking checks deleted packs and
  changed hashes; only Supabase's missing-index response is treated as empty.
- LOCAL: owner auth rejection, source normalization, migration idempotency,
  provenance gates, immutable edits, stale versions, catalog locking,
  review/publish/unpublish and Factory pause behavior use fixtures/fake services.
- BROWSER PREVIEW: desktop/mobile create, migration, review/publication and queue
  controls exercised with synthetic data. Hosted operation remains pending.
- DEVICE PENDING: Factory feed on open/resume, refresh, import/open, offline local
  use and WhatsApp/Business acceptance on the Galaxy S24 Ultra.
- LOCAL: content briefs validate categories/source URLs, separate browser-search
  and RSS/owner fallback modes, gate adult humor behind private MATURE output,
  export category-separated packs and auto-migrate complete output idempotently.
- LIVE PENDING: add `GROQ_TRENDS_API_KEY`, apply the admin migration, run one
  scheduled workflow and inspect real browser-search sources and usage.

**Never mark runtime behavior as proven by GitHub Actions alone.**

| Test | Expected / current status |
|---|---|
| Android test/build | GitHub Actions `:app:testDebugUnitTest :app:assembleDebug`; check current run |
| ADB ZIP smoke tests | Python unittest, duplicate collapse, bounded ZIPs; check current run |
| Pack create/rename/delete | Persists after restart, files cleaned |
| SQLite v1–v6 to v7 | Non-destructive migration retains stickers/metadata and adds the resumable import journal |
| Categories and sticker metadata | Create/edit/filter categories; backup v5 round-trip preserves labels/tags; schemas 1–4 remain readable |
| Static input | PNG/JPEG/WebP → 512px WebP <=100KB; alpha preserved |
| Animated input | Existing WebP 512px <=500KB, frame >=8ms, duration <=10s |
| Animated previews | Grid animates smoothly, releases drawable |
| Duplicate import | Folder import dedups globally; manual packs may contain shared artwork across packs |
| SAF folder import | Select WhatsApp and WhatsApp Business folders; 11k files; no root |
| EXIF grouping | Recover name/publisher from authentic WebP EXIF when present; fallback sensible |
| Failure/pause | SQLite journal and sticker row commit together; resume removes orphan files; no existing user data deleted |
| Depth bound | Report omitted deep folders and direct the user to import them separately |
| 30-sticker boundary | Multiple same-named packs, each <=30 |
| Multi-ZIP | Up to 100 archives, preserve names/kind/rating for StickerDock backups |
| ADB | Never touches private app data or credentials; works without root when accessible |
| Pack link bookmarks | Share text/plain URLs and paste many; only allow sticker.ly/s/ codes |
| ZIP traversal/bombs | Reject traversal, oversized size, entries, duplicate filenames |
| WhatsApp / Business | Official provider accepts pack; per-app intent works |
| Two accounts in one WhatsApp | Do not claim routing without real test/official support |
| Public catalog | Only review-approved GENERAL packs downloaded after integrity verification |
| Private MATURE label | No automatic public uploading |
| Offline usage | Local features remain functional without internet |
| Process death | SQLite import journal, Pause/Resume/Discard UI and persisted SAF URI are implemented; kill/restart recovery still needs Galaxy S24 Ultra validation |
| Backup round trip | Static normalized WebP bytes, SHA-256, labels/tags and category remain exact; entries stage on disk within shared export/restore bounds |

Test against Samsung Galaxy S24 Ultra and an Android emulator before publishing APK as production ready.
