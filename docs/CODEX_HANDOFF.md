# StickerDock development handoff — updated October 2026

## Goal and historical decisions
User has two WhatsApp accounts on Samsung S24 Ultra and finds Sticker.ly difficult to organize. Requirements evolved from local pack manager → animated/static categorization → ZIP sharing and recovery → Sticker.ly migration of existing accessible stickers → source-file backup through ADB/Android SAF → public bookmarks and curated community. User also requested mature stickers: currently local MATURE label and private ZIP sharing, but no open sexual-content catalog.

## Key technical details
- Kotlin + Jetpack Compose; JDK17, SDK35, Gradle8.9.
- StickerStore: SQLite schema v4, packs.kind, packs.rating and sha index with additive migrations.
- StickerFiles: normalize static images to 512px WebP <=100KB, accept validated animated WebP <=500KB/10s; optional global hash dedup.
- FolderStickerImporter: DocumentFile walk bounded by number/bytes/depth, reads public Android/media selected directory, groups by StickerPackMetadata EXIF fields, creates static or animated packs and auto-splits into 30-sticker chunks. Worker thread, not yet resumable.
- PackZipImporter: bounded .zip/.wastickers files, EXIF grouping; bulk selection of 100 files via ActivityResultContracts.OpenMultipleDocuments.
- StickerBackup: schema v3, backwards restoration, includes kind/rating; PackSharing via Android FileProvider.
- StickerlyLinks: offline SQLite bookmark registry and link sharing, not content scraping.
- CommunityCatalog: read-only verified public catalog GENERAL-only; `catalog/packs.json` empty.
- `tools/adb_sticker_export.py`: optional no-root desktop ADB pull of WhatsApp and Business media directories; 250-file/45MB ZIP batches for app ingestion.
- WhatsAppStickerProvider uses 4 documented provider endpoints and per-pack install intents. Needs real-device acceptance verification.

## What Sticker.ly does/does not supply
Sticker.ly's Android app is proprietary (the published open-source attribution page concerns third-party libraries); no official full-account or entire-catalog export API was verified. Public pack links like `https://sticker.ly/s/RZ7GSI` are supported by the bookmark workflow only. Historical unofficial wrappers include `theabbie/sticker.ly` (MIT) and archived `ciwga/Stickerly`; do not rely on them as durable endpoints, bypass protected endpoints, or automatically rehost creators' assets. User says they have a modified APK, but it has not been uploaded/inspected and it is not assumed to contain server-side sticker assets.

## Public source references (licenses)
- WhatsApp official third-party sticker docs (BSD): https://github.com/WhatsApp/stickers/blob/main/Android/README.md
- StickerVault's user-selected folder backup and EXIF recovery (GPL-3.0), architecture inspiration ONLY: https://github.com/mintnchocochip/stickervault
- Sticker.ly user-content terms: https://sticker.ly/terms-of-use

## Validation state
Prior commit `94ad982` passed Android CI with earlier feature set; later commits have pending builds until a newer green run is inspected. Device/emulator validation has **not** happened. Never represent compilation or CI as proof of real-world WhatsApp behavior.

## Next tasks
Check current `main` Actions build and fix errors. Add UI import progress/resume for 10k files, test EXIF/thumbnail handling, inspect provider contract from official sample, unit/instrumented tests of SQLite migration and user-selected directory import. Then investigate metadata preservation and generic .wastickers variants, and private consenting export workflows. Public community moderation and adult policy must be designed before accepting user uploads.

## Operational caution
User data stays private unless they explicitly share; ADB script stays local. No unnecessary cloud storage, upload or expensive infrastructure. Respect Sticker.ly content ownership and users' privacy.
