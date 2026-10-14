# Code audit and implementation — October 14, 2026

Scope: repository docs, Android storage/import/backup/provider/catalog/UI paths,
Python worker portability and regression suite, and current GitHub Actions status.
This is a focused source audit, not release certification or a complete security audit.

## Implemented locally

- Windows helper failures: replaced POSIX-only locks in daily generation/media
  sync with nonblocking Windows/POSIX locks. Added competing-process and
  exception-release regression tests. Files close to release OS locks on failure.
- Windows rendering: shared local font selection preserves the Linux DejaVu
  default and uses Windows Arial Bold; `STICKERDOCK_FONT` supports explicit fonts.
  Tests compare resolved paths rather than Windows short/long path spellings.
- Windows setup/export: added the missing time-zone dependency and replaced an
  unencodable console arrow that caused the ADB export CLI to return failure
  after writing its archives.
- Android import/transfer: indexed SQL existence/count queries replace loading
  complete sticker rows to check duplicates and capacity. An existing duplicate
  in a full destination now returns Duplicate, preserving the source on a move.
  Added Kotlin coverage for scoped counts/hashes and full-target duplicate moves.
- Android previews: bitmap and animated-drawable decoding run on the IO dispatcher.
  Static previews use a bounded 32-entry sampled-image cache, while animated
  drawables retain explicit start/stop disposal.
- Build/docs: wrapper distribution matches CI's Gradle 8.11.1; README uses SDK 36;
  QA matrix reflects database v7/backup v5. Corrected the README schedule statement.

## Audit gaps implemented in the continuation

1. Backup restore now stages entries on disk instead of retaining an entire archive
   in memory. Export preflight and restore share 500-pack, 15,000-sticker and
   1.5 GB bounds; the existing per-pack share flow is the batching path above them.
2. Validated static and animated WebP bytes are restored without recompression.
   Failure rolls back only packs newly created by that restore. A round-trip test
   checks exact bytes, SHA-256, category, label and tags.
3. Folder import now checks a Pause/lifecycle signal between every entry. SQLite
   schema v7 journals source URI, result and byte count in the same database
   transaction as the sticker row. Legacy checkpoints migrate forward, and resume
   removes files left without database rows by a process interruption.
4. The importer reports directories skipped at its depth bound and tells the user
   to import those folders separately.
5. Provider database helpers are scoped and closed before cursors/descriptors are
   returned. Tests repeat metadata/sticker queries and asset opens 25 times and
   assert the official sample column contract.
6. Search waits 300 ms, rejects stale results and no longer races refresh queries.
   Static thumbnails use bounded sampling/caching, and packs can sort by newest,
   name or sticker count.
7. The handoff now records the enabled-by-default Factory schedule, merged PR #6,
   current Android build versions and the production checks that remain unverified.

## Validation and pending work

- Remote fetch succeeded; checkout was clean at `2d7fad7` and matched origin/main
  before edits.
- Latest Android CI observed: successful for `21a1fd1`,
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38412413441 .
  It does not validate this uncommitted patch or the complete current main SHA.
- Local Python result: all 91 tests passed, including the four FFmpeg-dependent
  media tests using the locally available bundled FFmpeg binary.
  Provider calls in the test suite use fixtures/mocks; no live AI generation or
  production storage changes were performed.
- `git diff --check` passed.
- Local Android validation passed with 41 Kotlin/JUnit tests and no failures or
  skips. `lintRelease`, debug/release APK assembly and the release AAB build passed.
  The unsigned release APK also passed 16 KB ZIP alignment verification. Lint has
  no errors; its remaining warnings are dependency-update, KTX-style and launcher-
  icon housekeeping notices. No new CI result exists for these uncommitted edits.
- Galaxy S24 Ultra: SAF personal/Business roots, duplicates, invalid files,
  thousands of images, cancel/retry, persisted permission and process death.
- WhatsApp/Business: real provider acceptance and animated playback/install.
  Independent routing to two accounts inside one installation remains unsupported.
- Release: signing/catalog settings, privacy policy, Play identity/listing and
  regression checks; hosted Studio verification and approved Pinterest setup.
- Community: authenticated submission, rights review, file scanning, reporting,
  blocking, moderation and age protections before accepting public uploads.

Remaining work requires an actual Galaxy S24 Ultra, installed WhatsApp variants,
release credentials or external service configuration; source work above does not
claim those runtime/production gates have passed.
