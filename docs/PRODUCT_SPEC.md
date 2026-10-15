# StickerDock product specification

StickerDock is an offline-first Android sticker manager for large personal collections and multiple WhatsApp installations. WhatsApp is an export destination; StickerDock stores its own independent user-owned library.

## MVP
- Create, rename, search and delete packs.
- Import authorized PNG/JPEG/static WebP assets via Android system picker.
- Normalize static stickers to 512x512 WebP <=100KB with alpha preservation; tray 96x96 PNG.
- Maximum 30 stickers per pack, SHA-256 per-pack deduplication, copy/move/remove stickers.
- Local SQLite plus private sticker files.
- Export/import validated bounded ZIP archives with checksums.
- Export 3–30-item packs via documented WhatsApp ContentProvider and explicit user confirmation.
- Target consumer WhatsApp and WhatsApp Business separately. Two accounts within the same consumer app are not individually targetable through this API.

## Later
The initial later list is retained here with current status: user-authorized Sticker.ly share-link bookmarks (implemented; actual files still require an authorized export), global folder/photo dedup (implemented), tags (implemented), folders (phone-folder ingestion implemented; user-created folder hierarchy pending), animated WebP (implemented; device acceptance pending), sticker search (implemented), sync (pack version/notifications implemented; WhatsApp controls refresh), advanced account workflows (separate personal/Business implemented; in-app-account selection unsupported).

## Current creation and discovery flow
Library → Make stickers from photos → select images → automatically split compatible packs → edit/organize → Add to WhatsApp or Business. Existing collections use Import → folder or ZIP, with resumable scanning. The New tab automatically downloads bounded batches of unseen approved public revisions into the same local library. Static artwork editing supports rotate, mirror, zoom/crop, positioning and captions; background removal and animated artwork editing remain pending.

## Architecture
Native Kotlin / Jetpack Compose, SQLiteOpenHelper schema v7 and private internal files, without a tracker or Android login. HTTPS Internet access retrieves approved catalog packs from public Supabase storage; private Factory/admin services stay separate. Pack kind/rating/category, sticker labels/tags and a folder-import journal extend the initial schema. Backup schema v5 reads schemas 1–4. Migrations preserve existing data. First WhatsApp installation requires explicit user confirmation.

## Risks
Provider acceptance is not yet validated on device. Do not claim Samsung Dual Messenger or secondary in-app-account targeting works. ZIP restore validates staged content and rolls back newly created packs on handled failures; process death during restore is still a device/recovery concern. Do not scrape Sticker.ly or read another app's private database. Maintain author rights and user privacy. REQUIREMENTS_LEDGER.md preserves all user requirements and status history; CORE_FLOW_REVIEW_2026-10-15.md records current core-flow gaps/evidence.
