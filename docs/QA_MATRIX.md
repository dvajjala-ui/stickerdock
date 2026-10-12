# StickerDock verification checklist

**Never mark runtime behavior as proven by GitHub Actions alone.**

| Test | Expected / current status |
|---|---|
| Android test/build | GitHub Actions `:app:testDebugUnitTest :app:assembleDebug`; check current run |
| ADB ZIP smoke tests | Python unittest, duplicate collapse, bounded ZIPs; check current run |
| Pack create/rename/delete | Persists after restart, files cleaned |
| SQLite v1/v2/v3/v4 to v5 | Non-destructive migration retains stickers, content labels and assigns General category |
| Categories | Create/edit/filter categories; backup v4 round-trip; older backups restore as General |
| Static input | PNG/JPEG/WebP → 512px WebP <=100KB; alpha preserved |
| Animated input | Existing WebP 512px <=500KB, frame >=8ms, duration <=10s |
| Animated previews | Grid animates smoothly, releases drawable |
| Duplicate import | Folder import dedups globally; manual packs may contain shared artwork across packs |
| SAF folder import | Select WhatsApp and WhatsApp Business folders; 11k files; no root |
| EXIF grouping | Recover name/publisher from authentic WebP EXIF when present; fallback sensible |
| Failure/cancel | No file corruption; no unintended deletion; report partial imports |
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
| Process death | Large-import recovery remains to implement |

Test against Samsung Galaxy S24 Ultra and an Android emulator before publishing APK as production ready.
