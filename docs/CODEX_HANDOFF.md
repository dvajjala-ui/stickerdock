# StickerDock development handoff — updated October 2026

## Goal and historical decisions
User has two WhatsApp accounts on Samsung S24 Ultra and finds Sticker.ly difficult to organize. Requirements evolved from local pack manager → animated/static categorization → ZIP sharing and recovery → Sticker.ly migration of existing accessible stickers → source-file backup through ADB/Android SAF → public bookmarks and curated community. User also requested mature stickers: currently local MATURE label and private ZIP sharing, but no open sexual-content catalog.

## Key technical details
- Kotlin + Jetpack Compose; JDK17, SDK35, Gradle8.9.
- StickerStore: SQLite schema v5, packs.kind, packs.rating, packs.category and sha index with additive migrations.
- StickerFiles: normalize static images to 512px WebP <=100KB, accept validated animated WebP <=500KB/10s; optional global hash dedup.
- FolderStickerImporter: DocumentFile walk bounded by number/bytes/depth, reads public Android/media selected directory, groups by StickerPackMetadata EXIF fields, creates static or animated packs and auto-splits into 30-sticker chunks. It now keeps an app-private processed-URI checkpoint, persists session pack IDs/counters, reports live progress, and supports explicit Resume/Discard after interruption. The last unflushed tail is safe to retry through global SHA-256 dedup. Real-device process-death QA is still pending.
- PackZipImporter: bounded .zip/.wastickers files, EXIF grouping; bulk selection of 100 files via ActivityResultContracts.OpenMultipleDocuments.
- StickerBackup: schema v4, backwards restoration, includes kind/rating/category; PackSharing via Android FileProvider.
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
Production hosting code now uses GitHub Actions + Supabase: scheduled persistent
worker, private inbox/state/pending storage, SQL/RLS migration, manual GENERAL-only
publish gate, configurable Android catalog base and protected signed release
candidate workflow. See `docs/PRODUCTION_DEPLOYMENT.md`. No Supabase project or
production schedule is active until its owner adds the documented GitHub secrets,
applies the SQL migration and enables the repository variable.

The production branch also integrates the user-supplied Pinterest AutoSync v2
logic as `tools/pinterest_autosync`: authorized-board API pagination, rolling
request budgets, SQLite retry/dedup, bounded CDN downloads and optional
`pinterest-dl` topic discovery. `tools/pinterest_worker.py` persists small state in
Supabase, offloads downloaded files to the private inbox, and preserves per-source
rights metadata. Pinterest defaults to `REFERENCE_ONLY`; the public publisher
rejects those derived packs. `studio/` plus `api/` is a Vercel-ready manual meme
editor and inbox browser; its AI control and Android entry are Coming soon. Groq
remains only in the private Factory. See `docs/PINTEREST_AND_CREATOR_STUDIO.md`.

The production branch at `8d623b8` passed [Android CI run 38254739016](https://github.com/dvajjala-ui/stickerdock/actions/runs/38254739016).
The optimization review added grouped Android counts/direct sticker lookup,
shared clip-render decoding, invalid-input skipping, source-rights inheritance
in Studio, finite Pinterest attempts/staging and matching public archive/animation
bounds. The folder-resume extension at `0b20cb0` passed Android CI run
`38379410428`, including Python smoke tests, Kotlin/unit tests, debug APK and
optimized release. Device/emulator validation has **not** happened. Never
represent compilation or CI as proof of real-world WhatsApp or process-death behavior.
The `feat/daily-groq-stickers` branch adds an optional private desktop scheduler,
Groq caption/vision client, rotating Hinglish topics, supplied-photo memes, static
illustrations and simple animated WebP pulse rendering, rate-limit headers/usage,
crash recovery and schema-3 ZIP export. See `docs/DAILY_AI_STICKERS.md`.
It also fixes over-escaped backup WebP allowlist regexes and adds regression tests.
Free-input extension: `tools/media_library.py` has bounded CC0/public-domain
Commons discovery, direct media queue, provenance/dedup and optional authorized
Pinterest board ingestion with per-pin reuse declarations. `tools/video_stickers.py`
converts local GIF/MP4/WebM/MOV first 2s to captioned animated WebP with FFmpeg;
factory `clip` mode uses its first frame for Groq vision. No new paid provider.
See `docs/FREE_MEDIA_FACTORY.md`; live sources/Groq remain blocked by proxy policy.
Generation target defaults to 40/day in batches of five; 100/day is configurable.
No schedule/key is installed or deployed. Live calls remain blocked by environment
network policy; actual account quotas, vision support and phone QA are unverified.

Prior commit `94ad982` passed Android CI with earlier feature set. Device/emulator validation has **not** happened. Never represent compilation or CI as proof of real-world WhatsApp behavior.

## Next tasks
Validate the new SAF progress/resume path on the Galaxy S24 Ultra with personal and Business roots, 10k+ WebP, duplicates, invalid files, cancel/retry and forced process death. Add targeted SQLite migration and user-selected directory import tests where feasible without pretending JVM tests replace SAF/device behavior. Then test EXIF/thumbnail handling, inspect provider behavior against the official WhatsApp sample, investigate metadata preservation and generic .wastickers variants, and improve tags/filenames/global sticker search. Public community moderation and adult policy must be designed before accepting user uploads.

## Operational caution
User data stays private unless they explicitly share; ADB script stays local. No unnecessary cloud storage, upload or expensive infrastructure. Respect Sticker.ly content ownership and users' privacy.