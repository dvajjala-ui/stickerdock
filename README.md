# StickerDock — Android sticker manager

Offline-first native Android app for managing your own stickers independently of WhatsApp and Sticker.ly. Built using Kotlin and Jetpack Compose, with SQLite and app-private files.

## MVP scope
Create/rename/delete packs; import PNG/JPEG/static WebP with automatic normalization; local SHA-256 duplicate detection; copy/move/remove stickers; pack search; ZIP backup/restore; user-confirmed WhatsApp or WhatsApp Business sticker-pack install.

**Status:** source prototype, not a validated APK. CI build and real-device testing required. The official sticker import API cannot select a second account inside a single WhatsApp installation. Sticker.ly bulk account export has not been implemented.

## Build
Open the repository in Android Studio. Use JDK17, Android SDK35, and Gradle8.9. Run `gradle :app:assembleDebug` after installing Gradle, or build from Android Studio. Wrapper scripts/JAR are not included. The GitHub Actions workflow uses installed Gradle.

## Development
Start with [AGENTS.md](AGENTS.md), [Codex handoff](docs/CODEX_HANDOFF.md), [product spec](docs/PRODUCT_SPEC.md) and [QA plan](docs/QA_MATRIX.md). No credentials or backend required. Android system document picker avoids broad file-system permissions. Respect sticker authors' rights.
