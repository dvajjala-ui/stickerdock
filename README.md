# StickerDock — Android sticker manager

An offline-first, Kotlin/Jetpack Compose app for managing large sticker libraries independently of WhatsApp and Sticker.ly.

## Implemented source features
- Static and animated packs are **strictly separate**. PNG/JPEG/static WebP converts to 512px WebP; existing animated WebP is inspected for 512px size, frame durations and <=10s duration with a 500KB max.
- SQLite-backed pack CRUD with incremental schema migrations, local SHA-256 deduplication, copy/move/delete, pack search and static/animated filters.
- **Local content labels**: GENERAL or MATURE, retained by backup and direct share; **public catalog only exposes GENERAL**.
- Bulk import of user-supplied ZIP/.wastickers image archives with path, count and decompression limits. Receives actual shared images/ZIP via Android share sheet (not a Sticker.ly link).
- Full-library backup/restore with previous-schema compatibility, single-pack portable StickerDock ZIP sharing through Android FileProvider.
- Consumer WhatsApp and WhatsApp Business pack installation intents/provider. **Does not claim secondary in-app WhatsApp account routing.**
- Read-only, manually approved **Community packs** catalog from `catalog/packs.json`, with SHA-256-verified downloads. No public packs have been approved/published yet; see [public catalog README](catalog/README.md).
- GitHub Actions Android debug APK/test workflow and pure Kotlin animated WebP parser tests.

## Current status
**Active source prototype, not yet certified release.** GitHub Actions is used to check the build. Android device QA remains required; a successful CI build is not proof of WhatsApp integration. No production cloud backend and no anonymous content uploads.

## Limitations
- No official Sticker.ly account-wide export API is documented; a full account download is **not implemented**. See [migration guide](docs/STICKERLY_MIGRATION.md).
- Animated **WebP** input is supported; GIF/MP4 transcoding has not been implemented.
- Google Play does not permit primarily pornographic user-content apps. Private MATURE labels do not authorize public explicit uploads. [Safety design](docs/PUBLIC_SHARING_AND_SAFETY.md).
- Curated community publishing presently requires a maintainer-approved PR, not a public upload API.

## Build
Open with Android Studio, JDK 17, SDK 35 and Gradle 8.9. Run `gradle :app:testDebugUnitTest :app:assembleDebug` (the workflow installs Gradle; wrapper binaries are not currently committed).

## Developer documentation
[AGENTS.md](AGENTS.md) · [Codex handoff](docs/CODEX_HANDOFF.md) · [Product spec](docs/PRODUCT_SPEC.md) · [QA matrix](docs/QA_MATRIX.md) · [Sticker.ly migration](docs/STICKERLY_MIGRATION.md) · [Community safety](docs/PUBLIC_SHARING_AND_SAFETY.md)

StickerDock does not read private app data, auto-publish personal stickers, or require a subscription for local features.
