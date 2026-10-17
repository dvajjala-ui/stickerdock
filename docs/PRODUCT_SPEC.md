# StickerDock product specification

StickerDock is an offline-first sticker manager for large personal collections.
Android is locally tested; the iPhone client is under implementation with a shared
Kotlin media/rules core and native platform adapters. WhatsApp is an export
destination; StickerDock stores its own independent user-owned library. See
REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md for current gaps and acceptance.

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
Create → choose photos/GIF/video → edit → preview → explicitly save to a compatible
pack → explicitly add that pack to WhatsApp. Existing collections use Import →
folder or ZIP, with resumable Android scanning. Discover lists reviewed public
packs and bounded artwork previews; full downloads and library saving require
explicit actions. Opening/resuming never imports packs. Static artwork supports
rotate, mirror, zoom/crop, positioning, captions and manual traced cutout on
Android. Automatic segmentation and durable animated editing remain incomplete;
platform-specific source is not proof of tested parity.

## Architecture
Android uses native Kotlin/Jetpack Compose, SQLiteOpenHelper schema v8 and
private internal files. Kotlin Multiplatform shares media validation/container
and clip rules; iPhone adapters are native. Local operation requires no account;
private cloud is optional/authenticated. HTTPS retrieves approved public catalog
metadata/previews; private Factory/admin services stay separate. Backup schema
v5 reads schemas 1–4. Migrations preserve existing data. Each WhatsApp handoff
requires explicit user action; API differences and acceptance are documented
per platform.

## Risks
Provider acceptance is not yet validated on device. Do not claim Samsung Dual Messenger or secondary in-app-account targeting works. ZIP restore validates staged content and rolls back newly created packs on handled failures; process death during restore is still a device/recovery concern. Do not scrape Sticker.ly or read another app's private database. Maintain author rights and user privacy. REQUIREMENTS_LEDGER.md preserves all user requirements and status history; CORE_FLOW_REVIEW_2026-10-15.md records current core-flow gaps/evidence.
