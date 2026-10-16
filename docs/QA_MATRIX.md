# StickerDock verification checklist

## 0.10.0 — Android accepted locally; first Apple build/tests passed

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
