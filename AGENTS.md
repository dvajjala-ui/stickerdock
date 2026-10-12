# Codex working instructions — StickerDock

**This is an active native Android project, not a planning-only exercise.** Keep coding in this repository. Read `README.md`, `docs/PHONE_MIGRATION.md`, `docs/CODEX_HANDOFF.md`, and `docs/QA_MATRIX.md` before editing.

## Requirements
The user wants a high-quality alternative to Sticker.ly with simple organization for two WhatsApp accounts, personal migration, static vs animated segregation, offline/private mature packs, direct pack sharing, and an eventual moderated general-audience public community. Never claim that all content visible on Sticker.ly is available to rehost, or that their modified APK contains the full Sticker.ly catalog.

## Implemented source
Compose UI, SQLite schema migrations, pack CRUD, PNG/JPEG/WebP import, WebP animations, EXIF sticker-pack metadata reader, phone folder access with SAF/DocumentFile, large-folder dedup, multi-ZIP import, ADB export ZIP batcher, link bookmarks, pack-sharing FileProvider, read-only curated public gallery, content rating labels, backup/restore and WhatsApp ContentProvider. CI invokes Kotlin/JUnit and Python smoke tests.

## Immediate next work
1. Fetch latest `main`, check GitHub Actions, fix any compiler/test issues. Do not claim QA success without passing run link.
2. Test SAF folder import on a Galaxy S24 Ultra, including personal and Business roots, thousands of WebP, duplicates, EXIF grouping, invalid files, cancel/retry and process death. Add scan progress + resumable import.
3. Test WhatsApp ContentProvider exactly against the official sample; test distinct WhatsApp consumer/Business installations. No assertion that two accounts inside one WhatsApp can be targeted independently.
4. Improve UI layout, large-library thumbnail caching, per-sticker tags, filenames, pack labels, global search and sorting.
5. Add optional approved personal Sticker.ly export ingestion; do not scrape/rehost third-party catalogs or reverse engineer locked/private endpoints.
6. Community uploads require rights review, identity, moderation/report/block flows, age protections, file scanning and privacy review; not part of current backend-free MVP.

## Guardrails
No secret keys or users' sticker collections in Git. Do not run or rely on a modified APK without inspecting it and user consent. No paid renders/cloud deployments without user approval. Preserve schema/data migrations and backup format compatibility. StickerVault's GPL-3.0 work is useful research but must not be copied into MIT source absent relicensing/compliance. Never silently delete existing data on import errors.
