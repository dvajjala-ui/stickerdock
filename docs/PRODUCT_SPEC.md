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
Sticker.ly user-authorized share-link import, global dedup, tags, folders, animated WebP, search over stickers, sync, advanced account workflows.

## Architecture
Native Kotlin / Jetpack Compose, SQLiteOpenHelper, internal files; no network permission, tracker, cloud, or login. Schema: packs(id,name,version,created_at), stickers(id,pack_id,file_name,sha256,created_at). No destructive schema migrations. Installation requests never silently modify another app.

## Risks
Android build and provider contract are not yet validated on device. Do not claim Samsung Dual Messenger or secondary in-app-account targeting works. ZIP restore is not transactional. Do not scrape Sticker.ly or read another app's private database. Maintain author rights and user privacy.
