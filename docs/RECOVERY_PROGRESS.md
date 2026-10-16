# StickerDock recovery progress

Updated October 16, 2026. This is the current implementation record for
[the product recovery plan](PRODUCT_RECOVERY_PLAN_2026-10-15.md). The plan's
research findings remain a historical baseline.

## 0.10.1 — privacy-preserving interchange and expanded native verification

Pre-merge review found and repaired two additional correctness gaps. Cloud
`inventory` and `sharing_status` belonged in authenticated dispatch, not the
sign-in helper; owner-scoped pagination/status, budget and HTTP authentication
regressions now pass. The complete Python suite is 182 tests/no skips. CI adds
a pinned development-only Ruff undefined-name check (F821/F822/F823); it passes
across api/tools. Native iOS ZIP reads now compare ZIPFoundation's returned CRC
against each entry's checksum before accepting a manifest or generic WebP.
Two new native regressions corrupt valid JSON/declared media CRC and require
error plus unchanged existing library/media. They require their own Apple run.

The previous f91cdd1 candidate passes all 13 native/1 UI tests and optimized
Release packaging at https://github.com/dvajjala-ui/stickerdock/actions/runs/38911846626.
Independent read-back confirms internal version 0.10.1/build 13, icon/privacy
assets, CRC and absent signing. This is historical evidence once review fixes
change source; it is not acceptance of later code. User now explicitly requests
merging reviewed changes to main. PR17 was already merged October15; PR18 is
the only open PR at the start of this continuation. Local browser access is
disabled in Work settings; HTTP Admin/Studio return200, session status reports
unsigned, and protected admin POST without credentials returns401 as expected.

Signed candidate code 13 uses the existing certificate. APK SHA-256:
`ACBB81AA99B4E52D9CEA6EC68D8D73BCF7CD735DBB940FE6D030C48CDA7EB620`;
AAB: `DA17AF13A164195FC821C7A109EFD15AA9B534A5468E75BC24C43A0145E22374`.
Artifacts are `work/StickerDock-0.10.1-recovery.apk` and `.aab`.
97 Android + 12 common tests, 178 Python/no skips, lint 0 errors/43 warnings,
signature/integrity and 16 KB ZIP/ELF checks pass. Android CI for b5b33c6:
https://github.com/dvajjala-ui/stickerdock/actions/runs/38907007546.

Exact installed-APK navigation/real-MP4 preview/save/restart acceptance passes:
10 packs/33 to 10/34 stickers, every prior row retained. Export is 232,716 bytes,
20 frames/2.5 seconds with alpha and movement. The next actual Swift-exported
ZIP passes Android's system picker, retaining raw bytes/name/MATURE/GENERAL and
all earlier rows across restart (11/35). This is interoperability evidence from
synthetic software fixtures, not proof of finished sticker content quality.
Reports: `work/release-0.10.1-acceptance/status.json`, `native/report.json`,
`apple-to-android/report.json`. Physical WhatsApp remains untested.

This work fixes upper/lower UUID compatibility and lossy manifest ZIP fallback
that could discard a private rating. Portable category spelling stays GENERAL
for All; invalid v5 input remains rejected. The first failed synthetic import
and invalid FUNNY-category attempt are retained as failure evidence.

iPhone GIF and MP4 production checks now pass independently. Apple run
38907007539 passes 12 native tests and its UI test but fails the rotation check:
the test remux command had silently omitted rotation metadata. The fixture now
has an independently verified 90-degree matrix, and the native test asserts
the source track transform before requiring portrait output. Matching full CI
is still required. Earlier decoder-budget and movie-probe bugs were fixed
without relaxing output size, movement, frame-count or byte requirements.

CI packages an unsigned IPA as well as the device app/framework and xcresult.
It requires Apple signing before installation; no Apple account, credentials,
paid model, deployment or public content publication was created.

## 0.10.0 — historical native iPhone / Android navigation candidate

The user asks for a full Markdown/feature audit, iPhone implementation and better
flows. [The acceptance audit](REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md) maps all
requirement IDs to evidence and remaining gaps. Historical automatic-download
spec text is superseded; opening/resuming never saves public packs.

Android now has Library/Create/Discover navigation, with imports/backups inside
Create. Import returns to its originating screen via toolbar or system Back.
Pack content appears before export utilities; a compact sticky action opens the
consumer/Business target sheet. Existing handlers, SQLite v8 and ZIP v5 remain.

Signed candidate **0.10.0/code 12**, existing signing certificate:

| Artifact | SHA-256 |
| --- | --- |
| `work/StickerDock-0.10.0-recovery.apk` | `640D715B1510B86AAD59CF72A581A453F91A0E52B0127DD46BBDB22F9F8B5C57` |
| `work/StickerDock-0.10.0-recovery.aab` | `8497C43316E6C7726CD0885292AD719235772A551AEA04B6F5C7BD2D70B79680` |

94 Android + 9 common tests and 178 Python tests/no skips pass. Release lint:
0 errors/43 warnings. APK/AAB signatures/archive integrity, 16 KB ZIP alignment
and both 64-bit native-library ELF load alignments pass. Exact installed-APK
read-back matches the handed-off hash.

Android 15 emulator acceptance passes Create/import navigation, initial viewport
real-photo artwork, 360dp/1.3x text layouts, disabled missing WhatsApp/Business
targets, moving native MP4 preview, explicit save and force-stop persistence.
The library grows from 9 packs/31 to 9/32 stickers; all old rows remain intact.
New export: 248,534 bytes, 20 frames/2.5 seconds, alpha and motion verified.
Evidence: `work/release-0.10-acceptance/status.json`, `native/report.json` and
screenshots beside it. Physical phone/app integration is not established.

The [native iPhone implementation](../ios/README.md) uses SwiftUI and the same
Kotlin media/container core, with private atomic storage, photo/GIF/video creation,
caption variants/crop, photo subject-removal source, favorites, ZIP interchange,
reviewed catalog and explicit WhatsApp pasteboard handoff. It does not yet match
every Android editor/cloud feature or implement a native Groq assistant.

No Mac is available locally. Repository metadata was verified public, allowing
standard free GitHub macOS runners. [Draft PR #18](https://github.com/dvajjala-ui/stickerdock/pull/18)
preserves the recovery work in a reviewable branch and disables Vercel automatic
deployment for that branch. First candidate e2af790 passes
[Apple device/simulator framework + unsigned app build + 6 native/1 UI tests](https://github.com/dvajjala-ui/stickerdock/actions/runs/38899085950)
and [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/38899086147).
First Apple artifact: `work/StickerDock-apple-first-pass.zip`, SHA-256
`AAF1C0904B27F8925716DC86C71B019F6EE805905C477BD565DAD33DEDF32E75`.
Newer motion/rotation/live-catalog checks and the exact dependency lock require
their own latest CI result; do not apply the first green run to later source.

Apple signing/TestFlight/App Store, real-device WhatsApp, full parity, diverse
content, native AI, operational/security/performance acceptance remain gates.
No model purchase, provider key creation, deployment or public content change
occurred. Public catalog read-back returned HTTP 200/one GENERAL pack; that is
availability evidence, not a completed varied sticker catalog.

## 0.9.1 — shared Kotlin core, sharper previews and broader real-media trials

[Architecture/product decision](CROSS_PLATFORM_AND_PRODUCT_DIRECTION.md) selects
incremental Kotlin Multiplatform reuse. Android now consumes `sticker-core` for
WebP container/metadata logic, clip recipes and export limits. Five common tests
and all 94 Android tests pass; release lint (0 errors/43 warnings) and signed
APK/AAB builds pass. Core-only configuration works without the Android project.
No current-tree GitHub CI is claimed. Apple framework targets/manual CI are prepared on
macOS, not compiled here. There is no installable iPhone client yet.

The signed candidate is **0.9.1 / versionCode 11**, using the existing certificate
`49db762c7aed56b272f38b49820fd288a9353a6d68b14510afd7ddeae51b5389`.
Signature, 16 KB APK alignment and APK/AAB archive integrity checks pass.

| Artifact | SHA-256 |
| --- | --- |
| `work/StickerDock-0.9.1-recovery.apk` | `7ED1D2AC4C09E3395ABD36D757D68D13AA9978ADB827EBF37BDD2D476CF0C63E` |
| `work/StickerDock-0.9.1-recovery.aab` | `FAD368E9E74270E563A575C40848EED3D9CF6B117284245705DA0917755F78B9` |

The large photo grid/detail previews now request 512px instead of enlarging a
256px thumbnail. The cache separates resolution/file revisions and counts pixel
bytes against an 8 MB default budget. Tests verify separate thumbnail/full-size
entries, changed-file refresh and bounded memory.

Exact installed-APK read-back matches the handed-off hash. Android 15 emulator
acceptance verifies the sharper real-photo preview and a newly created moving
MP4 reaction (20 frames/2.5 seconds/210,888 bytes), explicit save to an existing
animated pack and persistence after force-stop. The library grows from 9 packs/
30 stickers to 9/31, with every prior row and all 12 trial sample hashes retained.
Previewing alone leaves the library unchanged. SQLite stays v8 and ZIP schema 5.
Evidence: `work/release-0.9.1-acceptance/status.json` and
`work/release-0.9.1-acceptance/native/report.json`; screenshots are beside the
native report. First automation attempts that missed picker rows under Android's
navigation bar are retained as failed harness attempts, not accepted app tests.
The accepted run has no app crash-buffer entries. Physical WhatsApp/Business,
large-phone-library acceptance, Apple compilation and iPhone remain pending.

The shared mux rebuilds all five earlier native reaction exports byte-for-byte;
`work/shared-core-probe/report.json` records hashes and the shared JAR identity.
The photo/clip CLI adds validated focal-point framing after visual inspection
caught a square crop cutting into a subject's face. Latest full Python suite:
**176 passed, no skips**. No paid model, new key or deployment was used.

Three independent additional source works produce **12 actual-photo/animated
trial stickers**, with retained originals, source licenses and changes notices.
The corrected sample bundle is
`work/real-media-breadth-2026-10-16/Real-Reaction-Trial-with-Credits.zip`.
Extract it, then explicitly import the four inner pack ZIPs. It is not bundled
into the app and has not been published to the community.

The exact signed 0.9 baseline imported all 12 through the native system picker;
saved hashes/decoded dimensions/kinds, prior-row preservation and force-stop
persistence pass. Its emulator library deliberately grows from 5/18 to 9 packs/
30 stickers. Report: `work/broader-reactions-emulator/acceptance/report.json`.
The 0.9.1 upgrade then preserves all these samples and validates the shared core
through native creation/save. Neither emulator run establishes physical
WhatsApp/iPhone behavior or broad catalog-quality completion.

## 0.9.0 — native reaction creator, signed local candidate verified

R29–R31 broaden the work around actual reaction footage. See
[real-media research and implementation record](REAL_MEDIA_CREATOR_PLAN_2026-10-16.md)
for all eight inspected repositories, Pinterest/Sticker.ly visual references,
Groq capability/cost boundaries and real-media trial evidence.

New native source lets the user select a video/GIF through Android's picker,
choose a 0.5–4 second section, framing, 6/8/10 fps and optional outlined caption,
generate a moving preview locally, then explicitly save to a new/existing
animated pack. Changed settings invalidate the accepted preview. Source inputs
are read-only; originals are never uploaded by this creator. No provider key or
additional binary dependency is required. SQLite stays v8 and ZIP stays schema 5.

Android decodes media and compresses static frames; a small independent WebP
RIFF/ANIM/ANMF writer combines full-canvas frames. Limits cover 20 MB source,
ten-minute source duration, short output, 40 rendered frames, 512px dimensions,
500 KB export, no-motion rejection, cancellation and GIF decoded-canvas budget.
New-pack save failures remove only the new failed pack; existing packs survive.
Saved animations are exports; editable video-source projects and cloud AI
caption suggestions are not implemented in this screen yet.

The earlier `171F51E8…` APK is superseded: actual WebM creation showed Android's
timestamp-thumbnail API repeating the first frame on this Android 15 emulator.
No-motion rejection blocked the export. Source now uses bounded sequential
MediaExtractor/MediaCodec decoding, stride/crop-aware YUV sampling and explicit
rotation. Earlier picker/input test failures were harness navigation problems
(offscreen files, an orphan DocumentsUI task and tapping a floating field label).
Those incomplete attempts are retained and are not passing acceptance. No app
data was cleared.

The sequential-decoder replacement now passes **92 Android tests**, release
lint (**0 errors/39 warnings**) and signed APK/AAB builds; signature, 16 KB
alignment and both archive CRC checks pass. APK SHA-256:
`4F37F9AD739343DEB58DDFCF81EF01FBAA81639FF704AC90F98D80A6BD8D5C72`.
AAB SHA-256:
`A25D367DAEE176556D766EBB45E99B3F3CF7397B918C7B3521F3C96FACFBBF3F`.
Verified copies: `work/StickerDock-0.9.0-recovery.apk` and
`work/StickerDock-0.9.0-recovery.aab`. VersionCode 10; release certificate remains
`49DB762C7AED56B272F38B49820FD288A9353A6D68B14510AFD7DDEAE51B5389`.
`work/release-0.9-acceptance/status.json` binds the installed APK, native reports,
test counts and source-file hashes. The pulled installed APK matches the copy.
Native WebM/MP4/GIF acceptance of this exact replacement passes in
`work/creator-0.9-emulator/sequential-native/report.json`: source selection,
captioned moving previews, changed-caption invalidation, preview leaves the
library unchanged, new/existing animated-pack save, byte/checksum/alpha/duration
validation and force-stop persistence. Three deliberate saves change 4 packs/
13 stickers to 5/16. All outputs are 20 frames/2.5 seconds at 512px; WebM is
205,186 bytes, MP4 178,798 and GIF 495,472.

`rotation-report.json` in that same folder passes actual 90-degree display
metadata, moving preview, keep-full-frame portrait margins, save and force-stop.
The first output frame matches the upright FFmpeg reference with mean absolute
RGB error 1.37/255; output is 181,452 bytes/20 frames/2.5 seconds. The earlier
rotation fixture lacked display metadata and therefore correctly stayed
landscape; that failed fixture assertion is retained separately. Both deliberate
extra saves remain, bringing the emulator library to 5 packs/18 stickers without
deleting or changing its earlier collection. The final crash buffer is empty.

Four new unit tests cover
plane positions/strides/crop, thumbnail allocation bounds, rotation and
limited/full-range BT.601/BT.709 color handling. Video input is limited to
4 megapixels; there is no HDR tone mapping or arbitrary-codec guarantee.

Worker media changes are local: outlined-photo/clip rendering, motion context
via six-frame contact sheets and improved caption instructions. Full Python
suite **173 passed, no skips**. No real Groq request, deployment, new CI or new
catalog publication was performed for these changes.

The creator is accepted locally on an Android 15 AOSP emulator, not as a fully
production-ready app. Physical WhatsApp/Business, other codec/device combinations
and iPhone remain unverified. One licensed source with caption variants proves
the media pipeline, not the requested catalog breadth. Native Groq suggestions,
editable video-source projects and varied real-reaction discovery remain next.
Trial derivatives retain Rattyexalt/CC BY-SA 4.0 attribution in
`work/creator-0.9-emulator/sequential-native/credits.json`; none were published or
bundled into the APK.

## 0.8.0 continuation — signed local candidate

Source adds private editable starting stickers/recipes, durable draft autosave,
Library Resume edit, manual cutout, and atomic image/recipe saves. Database v8
is additive; pack ZIP schema remains 5. Starting images and unsaved drafts never
enter ZIPs, public packs or cloud sync. Copy retains the saved recipe; move also
retains a draft; deletion/Undo handle project data. Details:
[LOCAL_ARTWORK_PROJECTS.md](LOCAL_ARTWORK_PROJECTS.md).

The final local rebuild passes **78 Android tests**, release lint (**0 errors,
35 warnings**), and signed APK/AAB builds. This includes migration
from every schema 1–7, v7 labels/tags/paused scan preservation, saved recipe/reset,
close/reopen draft recovery, atomic commit rollback, stale-save rejection,
copy/move/delete/Undo, corrupt-original protection, ZIP privacy, bounded outlines
and native alpha rendering. APK signature and 16 KB ZIP alignment verify. The
candidate is **0.8.0 / versionCode 9**, signed with the existing release certificate.
The earlier `9BED5649…` and `482DE306…` candidates are superseded: emulator checks
caught footer and keyboard/header inset failures. The final rebuild explicitly
sets keyboard resize on the caption dialog, since Compose 1.7.8 only does this
automatically below Android 12. The signed release retains the same header bounds
before/after opening the keyboard; release acceptance passes below.
0.7.0 evidence below is historical.

| Final local artifact | Identity |
| --- | --- |
| APK | `work/StickerDock-0.8.0-recovery.apk` (verified copy of release output) |
| APK SHA-256 | `D1A379C3B251C153BC156107BA53AA6F3C6DCB75996CB1045FBF59170B5E7801` |
| AAB SHA-256 | `3C5261961B7D18E269EB400EC187880CE803989BDB47BE885C0DAD36F6811A79` |
| Signing certificate SHA-256 | `49DB762C7AED56B272F38B49820FD288A9353A6D68B14510AFD7DDEAE51B5389` |

The emulator's v7→v8 update retained **2 packs / 7 stickers**. Final APK acceptance
passes in `work/release-0.8-acceptance/`; each report records its APK hash:

- `captions/report.json`: type with keyboard, unchanged header position, save,
  reopen, replace caption, reopen, Reset to exact starting bytes.
- `layout-retry/report.json`: 360dp/130% text, traced outline, Apply enabled,
  actual Start over/Cancel interactions, saved image unchanged; settings restored.
- `cutout-retry/report.json`: traced/tapped outlines, undo/redo, keep draft,
  force-stop/Resume, alpha-checked 512px WebP, editable saved outline, remove to
  exact source bytes.
- `projects/report.json`: autosave, unchanged saved export, force-stop and cold
  emulator boot, Resume with history, save/reopen editable recipe, exact Reset,
  cleared draft and retained sticker identity/counts.

The installed APK was pulled and checksum-matched before testing. The initial
layout/cutout harness attempts did not complete: selectors matched library cover
images as well as the pack grid. Navigation now waits for Pack options and text
labels take priority over duplicate cover descriptions. Those incomplete runs
are retained and are not counted as passing acceptance. Earlier
`work/projects-emulator-0.8-final/`, `work/cutout-0.8-verified/` and
`work/layout-0.8-verified/` reports cover superseded iterations, not this final APK.
No app data was cleared; the earlier damaged red fixture remains untouched.

These are synthetic data-integrity/interaction checks, not real-meme content
acceptance. October 16 direction broadens the next implementation around real
reaction photos/footage, animated creation and actual Pinterest/Sticker.ly style
references (R29/R30). The user does not accept emoji/shape fixtures as product
content. No physical WhatsApp/Business, iPhone, new CI or deployment is claimed.

Latest main fetch remains `c5871d7`. On October 16 the latest scheduled Factory
[workflow completed successfully](https://github.com/dvajjala-ui/stickerdock/actions/runs/38851239886).
Its output count and recovery behavior were not inspected in this pass, so this
does not establish daily capacity or deployment of the local recovery changes.

## 0.7.0 previous local testing candidate

| Change | Current evidence | Remaining acceptance |
| --- | --- | --- |
| Remove automatic library additions | SOURCE + LOCAL + EMULATOR: launch/resume/refresh use read-only CatalogDiscovery; actual live catalog browse/preview/dismiss/restart leave SQLite at zero packs and stickers | Physical-device regression |
| Preview before saving | SOURCE + LOCAL + EMULATOR: expanded preview sheet with persistent Save footer; explicit save creates one pack/four stickers; restart retains it; reopening does not duplicate | Physical-device regression and user visual acceptance |
| Discover artwork and search | SOURCE: responsive artwork cards, creator/topic search, Static/Animated filters, saved state, retry/offline/empty states | Visual acceptance; old entries lack type/count metadata until republished |
| Public preview contract | LOCAL: reviewed publication produces a static 576×384 contact sheet, checksum, counts and types, keeping catalog v1 compatibility | Deployment and a reviewed publication using the new fields |
| Older pack previews | LOCAL: bounded archive reads render up to six stickers without importing or modifying originals; tampering rejected | Real network and phone memory/lifecycle behavior |
| Factory rate-limit recovery | LOCAL: actual generator path with a simulated 429, restored state on another worker directory, no call during cooldown, then successful completion; authentication failures remain failed | Deploy worker, observe a real provider cooldown/recovery; actual account quota not increased |
| Mobile admin recovery UI | LOCAL BROWSER: owner session login, waiting/retry time, readable daily-run cards, background refresh protects edits, 390px layout, no page errors | Hosted verification after deployment |
| Existing private cloud work | Preserved; included in working-tree local validation | Existing cloud device gates remain |
| Welcome, navigation and editing layout | EMULATOR: neutral surfaces/indigo accent and consistent canvas icons; compact welcome; full sticker sheets; bounded editor preview with no heading/control overlap; compact populated library shows both test packs without an overlapping FAB | User visual acceptance and wider device/font matrix |
| Local photo creation/editing | EMULATOR: Android picker imports three synthetic PNGs offline; rotate, mirror and caption render. Earlier save/process-restart SQL checks were insufficient: cold boot exposed a zero-byte edited file | Replaced by byte/checksum/native-preview/cold-boot acceptance below; physical photo/EXIF and WhatsApp acceptance |
| Safer file commits | LOCAL + EMULATOR: imports, restore, transfers and artwork edits sync and checksum-verify immutable files before committing SQLite; failed writes keep existing row/artwork. Transfers reject damaged sources; tray updates are atomic and synced. Signed release edit survives a cold emulator boot with matching nonempty bytes/checksum and native preview | Physical-device/storage failure matrix |
| Recoverable editing | LOCAL + EMULATOR: 50-step bounded undo/redo, grouped slider/caption changes, undoable reset, discard protection, saved-instance draft/history and open editor survive background process reclamation | Force-stop/reboot draft recovery and editable originals remain future work |

Opening Discover can retrieve catalog metadata and small public thumbnail files.
It does not save full packs. An older catalog pack without preview metadata can
download its ZIP when the user opens its preview; this only renders temporary
preview artwork. Saving and adding to WhatsApp are separate actions.

Preview files are cached by immutable content hash with a 24-file bound. ZIP
bytes, entry counts, expanded bytes, image dimensions and selected image hashes
are bounded/checked. No database/backup schema migration is required for these
changes. Existing user packs are retained.

## 0.7.0 previous local verification

- Final local Android run: **65 unit tests, zero failures/errors**, release lint
  (**0 errors, 38 warnings**), signed APK and signed AAB build passed. APK 16 KB
  ZIP alignment check passed.
  New coverage includes grouped/bounded undo history and saved-state round trips,
  write failure retaining originals, and rejecting a damaged sticker before move.
- Browser fixture regression: normal owner sign-in without an owner-token field;
  cookie-session API requests carry no manual token; password field clears;
  pending background reads do not block navigation or reset settings edits;
  waiting state/retry time, mobile layout and explicit-save catalog copy pass.
- Full Python suite: **166 tests passed, zero skipped** with the existing FFmpeg
  runtime on PATH. Includes rate-limit persistence across restart/day rollover,
  authentication failure, catalog previews and review/publication guards.
  Use `.venv/Scripts/python.exe`; the system interpreter lacks `requests`. A
  Windows temporary-file replace lock occurred once; the complete rerun passed.
- Synthetic mobile admin screenshot: `work/admin-recovery-fixture.png`. Its
  10/40 count is test data, not a production generation result.
- Android 15 AOSP emulator (`stickerdock_recovery`, x86_64, WHPX, 1536 MB,
  two cores, software graphics) runs the signed release candidate. Automated
  real-catalog checks pass in `tools/emulator_recovery_smoke.py`; output is in
  `work/recovery-emulator/report.json` with five native screenshots. This test
  requires an empty isolated rooted emulator and never clears existing data.
- Read-only SQLite assertions distinguish browsing from saving. Actual public
  pack previews load; explicit Save persists **1 pack / 4 stickers**. Eight
  lifecycle/save/idempotency checks pass. No WhatsApp account was configured.
- Additional native photo test uses three synthetic original PNGs with emulator
  Wi-Fi/mobile data disabled. Picker → import → rotate/mirror → caption → save →
  process restart passes. Counts are then **2 packs / 7 stickers**. Metadata and
  identity checks are in `work/photo-edit-result.json`; fixtures/screenshots stay
  in ignored `work/`, not Git. **That earlier report is not cold-boot artwork
  acceptance:** the red test edit was later found empty on disk. The old report
  checks SQL identity only and must not be used as proof that edited files survive
  reboot. `tools/emulator_editor_smoke.py` adds actual byte/checksum/native-preview
  checks, undo/discard and background process recreation, with a separate
  `verify-cold-start` stage after restarting the emulator.
- New signed-candidate editor regression passes both stages. The PID changes
  after Android reclaims the background process; the open draft, mirror state,
  undo and redo survive. Save retains sticker identity and pack counts (**2 / 7**).
  The exported bytes match SQLite SHA-256 before and after a cold emulator boot,
  and the native decoder shows the edited artwork. Evidence:
  `work/editor-emulator/report.json` and three screenshots. Compact populated
  library screenshot: `work/emulator-library-compact.png`.
- Failed-save/retry check also passes on the signed emulator: temporarily remove
  write permission from the synthetic fixture's pack directory, tap Save, verify
  the original row/bytes/checksum and draft/undo remain, restore permissions, then
  retry successfully with the same sticker identity. No collection is cleared.
  Evidence: `work/editor-save-retry/report.json`, two screenshots and the ignored
  local runner `work/check_editor_save_retry.py`.
- No new GitHub Actions run is attached to this uncommitted working tree. Earlier
  green CI is historical evidence only. Local passing checks are not complete QA.
  Latest main was fetched again: `c5871d7`. The latest hosted Factory run remains
  [failed](https://github.com/dvajjala-ui/stickerdock/actions/runs/38727851141);
  [older Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/38717657942)
  covers `d9cba2b`, not this local candidate.

## 0.7.0 signed artifact — historical

- Version: **0.7.0**, versionCode **8**, applicationId `app.stickerdock.duo`.
- APK: `app/build/outputs/apk/release/app-release.apk`.
- Convenient local copy: `work/StickerDock-0.7.0-recovery.apk`.
- SHA-256: `0B623743F63656181EEE311A9BED171C50C856423F9E6D38D85B9EE36D8C3FD9`.
- APK signature verifies using v2, with the existing release certificate
  SHA-256 `49db762c7aed56b272f38b49820fd288a9353a6d68b14510afd7ddeae51b5389`.
- AAB: `app/build/outputs/bundle/release/app-release.aab`; jarsigner reports
  “jar verified” with self-signed/no-timestamp and bundle ZIP-stream-order warnings.
  This is not Play Console acceptance.
- Updating a release installation should preserve data with the same application
  ID/signing certificate. A debug-signed install cannot be replaced directly by
  this release signature; export a backup before any user-chosen uninstall.

## Outstanding gates and next slice

The user does not currently have the Galaxy available and authorized alternative
testing or skipping physical-phone checks. An official Android emulator is now
installed and running successfully; these emulator checks do not establish
WhatsApp/Business acceptance or Samsung SAF/process-death behavior.

No production deployment, migration, public content publication, paid API call,
password reset, key rotation or deletion of existing collections occurred in this
implementation pass. Owner recovery email still needs its existing SMTP gate.

Next plan slice: automatic segmentation, full-resolution starting photos,
project-aware private backup, broader image inputs and large-library behavior.
The native AI creator, animated editor, broader curated
catalog, iPhone application, shared quota coordinator and full community remain
unfinished. A 429 waiting state does not guarantee 40 generated stickers per day.

## File durability correction

A cold emulator boot exposed an edited sticker with a persisted SQLite row and
checksum but a zero-byte file. The previous implementation closed its output
stream before the SQLite commit but did not explicitly sync the artwork. Source
now calls `FileDescriptor.sync()` and verifies the stored checksum before the
database can reference new bytes. Errors abort before replacing the original row
or deleting the original file. This ordering follows Android's
[file descriptor sync contract](https://developer.android.com/reference/java/io/FileDescriptor#sync()).
It is not a claim of tested resilience to every hardware/storage failure.

The damaged synthetic red test fixture is retained as evidence; no collection
was cleared and no attempt is made to silently delete or reconstruct user data.
Editing drafts use Android saved instance state: recreation/background process
reclamation are in scope, while force-stop/reboot draft autosave and keeping an
editable original after Save require the later project-document model.
