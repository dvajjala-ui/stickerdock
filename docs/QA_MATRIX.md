# StickerDock verification checklist

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
