# StickerDock verification checklist

## Pinterest and real images — October 15

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
