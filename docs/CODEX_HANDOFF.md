# Codex handoff — StickerDock

## User's original objective
Sticker.ly is hard to organize, and user has two WhatsApp accounts. The goal is a superior sticker manager with one independent sticker library, simple import/export, search, bulk organization, backups, and possible migration from the user's own Sticker.ly sticker packs.

## Architecture and decisions
Android native Kotlin + Compose; local SQLite and on-device private files, no backend required for MVP. Import via Android document picker. Static images normalized to WhatsApp constraints. Build pack export using documented ContentProvider and ENABLE_STICKER_PACK intent. Direct multi-account selection inside WhatsApp is not an established public API.

## Status
Source-only prototype; compile/CI and device tests not yet completed. Check Kotlin source against SDK and fix build failures first.

## Immediate priorities
1. Run `gradle :app:assembleDebug` using JDK17, Gradle8.9, Android SDK35.
2. Fix compilation and include tests.
3. Test local CRUD/import/dedup/copy/move, ZIP backup integrity and restore.
4. Verify ContentProvider URI and metadata contract against WhatsApp official sample.
5. Test consumer WhatsApp and WhatsApp Business separately.
6. Investigate Samsung Dual Messenger, in-app Switch Account, and supported user-visible fallbacks.
7. Then build tags/search/bulk operations and legally permissible user-authorized Sticker.ly import.

## Constraints
No payments, cloud deployment, scraping private account libraries, unauthorized access to other Android apps, or silent installs. Do not lose user data through destructive migrations. User's S24 Ultra is eventual QA target.

## Open question
Whether user operates two in-app WhatsApp accounts, WhatsApp+Business, or Samsung Dual Messenger. Do not block offline MVP on answer.

## Success criterion
A real, working APK that organizes hundreds of personally owned stickers and installs eligible packs where supported by official WhatsApp APIs.
