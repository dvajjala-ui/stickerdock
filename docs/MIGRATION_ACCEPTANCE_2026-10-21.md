# Android folder migration acceptance — October 21, 2026

This is signed AOSP Android 15 evidence, not Samsung or physical WhatsApp
acceptance. Tested APK SHA-256:
`d984ad1daf61a030eb7512e86b103f8adc2f85e38582d8682d4dcb7ea8d3997b`.
Its app source is `a2d22c1308f7f46e4162b062a76e464165e16821`; later workflow-only
changes did not change the app. It is separate from the published APK hash in
QA_MATRIX.md. No production account, network upload or personal collection was used.

## Actual journey and results

The disposable emulator started with 338 packs / 10,003 saved stickers and two
artwork projects from release acceptance. A new local test folder contained
1,200 static WebP source images, four moving WebP, eight explicit duplicates,
three invalid/oversized recognized files and one ignored text file. EXIF carried
three static group names and one motion group name; folders were nested.

Android DocumentsUI refused the storage root. The test selected its permitted
subfolder and confirmed **Allow**. Pause through the app retained a session with
69 processed files (53 added / 16 duplicate). Resume advanced that same session.
The test force-stopped the process after 94 processed files; reopening retained
95 committed outcomes and the same persisted tree URI. Resume worked without
reselecting the folder or requesting another grant. Completion removed the import
journal and pending session.

The completed import saved **597 stickers** in **22 nonempty packs**, with exactly
four animated stickers in a separate animated pack. Static groups split at 30;
all three EXIF names were recovered. Near-identical solid RGB source images
normalized to identical lossy WebP, so 1,200 different source colors were not
1,200 distinct exported stickers. The saved count is measured, not advertised as
a content/catalog benchmark. No invalid/oversized test filename became a sticker.

Every pre-existing pack, sticker and artwork-project row remained unchanged.
All 597 imported files matched their database SHA-256 values. SQLite
`PRAGMA integrity_check` returned `ok`. No saved sticker was deleted to run or
repeat the test.

## Evidence and test limits

Local evidence: `work/folder-import-acceptance/report.json`, `pause.json`,
`restart.json` where available, before/after checkpoint databases, final.db,
fixture.json/tar and screenshots 02–05. Test scripts/logs are retained under
`work/`; generated databases and media are intentionally outside Git.

The harness initially compared Python tuples with JSON lists and compared the
active import pack's mutable version as if it were an immutable row. Both were
corrected using preserved checkpoints; no app code/data was repaired. A raw
database copy taken while the importer was writing was inconsistent. Final
checks copy after worker completion and force-stop, then require integrity and
full baseline-row preservation. Do not use a raw live SQLite copy as proof of
on-device corruption or as a backup.

Repeat on a physical Samsung with its actual DocumentsProvider, personal and
Business roots, permission loss, storage exhaustion, folder changes, cancel and
process death. This AOSP run verifies the normal SAF flow and journal recovery;
it cannot establish Samsung access or routing between WhatsApp accounts.
