# StickerDock verification checklist

## October 30 source inbox reset

The [legacy inbox dry run](https://github.com/dvajjala-ui/stickerdock/actions/runs/41032552792)
fingerprinted 123 older images: 105 removable and 18 protected. Its matching
[apply run](https://github.com/dvajjala-ui/stickerdock/actions/runs/41033043732)
removed 105 images plus 105 metadata files and verified that 18 protected
images remained. [Read-only re-audit](https://github.com/dvajjala-ui/stickerdock/actions/runs/41033527637)
counted 18 protected and zero removable images. The [storage scan](https://github.com/dvajjala-ui/stickerdock/actions/runs/41033530549)
measured 42.09 MB total. Local Python discovery passed 329 tests (7 skipped) after
the workbench source selection change. JavaScript syntax and diff checks passed.
The [source-bound Web/API run](https://github.com/dvajjala-ui/stickerdock/actions/runs/41034225813)
passed, including the real-browser fixture for an empty workbench, choosing a
reviewed image for a pack, and opening it for AI generation. The hosted Admin
also showed the new empty workbench prompt and zero Sources rows after deploy.
These checks did not create or publish a real sticker from a newly uploaded
image; that end-to-end owner acceptance remains open.

[Dry run](https://github.com/dvajjala-ui/stickerdock/actions/runs/41021463086)
fingerprinted 174 unapproved private candidate rows/files. The matching
[apply run](https://github.com/dvajjala-ui/stickerdock/actions/runs/41021593352)
deleted all 174 and verified the candidate storage paths were absent.
Production Admin returned zero active and zero archived intake rows and zero
active daily plans. The [follow-up storage scan](https://github.com/dvajjala-ui/stickerdock/actions/runs/41021728479)
measured 62.73 MB. This does not validate Pinterest/Reddit hosted collection,
image quality, rights, or any physical WhatsApp device flow.


## October 29 Android pack navigation regression

[Signed release run 40888997829](https://github.com/dvajjala-ui/stickerdock/actions/runs/40888997829) built and signed version 0.12.0, then failed the real Android 15 emulator test while opening an animated pack. Its screenshot shows the list card title squeezed beside a large WhatsApp action; the automated tap on the title resolved to the card center and stayed in Library. The fix gives the pack title its own tap target and moves the WhatsApp action below the sticker previews so the name has room. The emulator check now expects the current combined animation/selection hint. A new source-bound signed run must pass before calling 0.12.0 released.

The same screenshot showed “Personal WhatsApp active” even though the home screen used fixed `true`/`false` installation values. Library and Settings now use the existing package check used by WhatsApp handoff and refresh it when the app resumes. This removes a misleading installed-app claim and keeps the target switcher in sync with the device.

## October 29 HandNavigate source continuation

[Actual Railway Flickr acceptance](HANDNAVIGATE_FLICKR_2026-10-29.md): 20/20 downloaded/stored candidates, twenty independent checksum/decoded-image read-backs, successful bounded batch exit, and a read-only hosted Admin check with all twenty previews and no 390px overflow/page errors. The daily plan was changed only after that acceptance. [Source-filter Web/API CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/40760718870) passes; local backend 327/327. This does not prove multiple natural scheduled days, third-party reuse rights, source-to-sticker generation quality or new phone acceptance.

## Published Android 0.11.6 and source-text screening — October 28

[Android 0.11.6/build 21](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.6)
is published from `5130e24c1469c4f5e4d6323809f16cdc315393cf` after
[signed-release CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/40644511402)
passed. An independent public download was 3,952,171 bytes, SHA-256
`c458d979c78947375ac450a632bf6fe8926ffbb48134f4d10f046dc331501e41`.
This matches `release.json`, `SHA256SUMS.txt` and `acceptance.json`. The exact
signed APK passed 334-pack/10,000-sticker, 200% text/Favorites, photo/video,
import and editable-backup acceptance. Native test reports show 143 Android
and 12 shared-core tests with no failures or skips. Physical WhatsApp/Business
and signed iPhone acceptance remain separate.

PR #28 merged as `fa70eff` after [Web/API CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/40645187893)
passed 315 Python tests without skips, real local OCR and concurrent PostgreSQL
checks, and [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/40645188050)
passed. The [production read-only OCR audit](https://github.com/dvajjala-ui/stickerdock/actions/runs/40647029975)
screened 74 saved photos: 38 flagged for text review, 36 with no confident text
detected. It made no generation calls or storage writes. These are OCR decisions
for the saved snapshot, not rights approval or proof of humorous finished output.

## October 28 PR #26 merge

Merged as `04a687e` after [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/40641023505)
and [Web/API CI including real concurrent PostgreSQL checks](https://github.com/dvajjala-ui/stickerdock/actions/runs/40641023512)
passed for `ca4071e`. The [production provisioning run](https://github.com/dvajjala-ui/stickerdock/actions/runs/40641030640)
applied the additive admission RPC and verified its private grants before merge.
The [architecture review](ARCHITECTURE_REVIEW_2026-10-28.md) records the new
capability-contract regression checks and remaining installed-worker/native gates.

## October 27 EGM core increment

The [new core increment](EGM_CORE_HARDENING_2026-10-27.md) adds failure/replay,
concurrent admission and filtered-search coverage. Local results and the pushed
PR's CI must be recorded separately. No new signed release, physical-device
acceptance, production migration or hosted operation is implied by source fixes.

Local: 283 Python / 140 Android / 12 shared tests pass without skips; debug APK
and release lint pass (zero errors / 44 warnings / six informational findings).
Real PostgreSQL 17 concurrent admission/retry/replay/grant checks pass, along with
existing PGlite migrations and JS/Python static checks. Fresh-runner regressions
cover midnight delivery, interrupted remote status, acknowledged stale snapshots,
corrupt-history quarantine and orphan blocking without starving current work.

## October 28 HandNavigate collection workspace

Real hosted Admin/Supabase checks now cover 20 Pinterest and four Reddit images downloaded by HandNavigate itself, unique stored candidates, blob/row read-back and image checksum verification. The hosted Admin passed daily topic-plan save, title edit, an exact downloaded-file digest, archive/restore, decoded library previews and desktop/mobile layout. [Evidence and remaining boundaries](HANDNAVIGATE_ADMIN_2026-10-28.md).

The final functional [Web/API revision passed CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/40581384063), and [daily queue execution passed](https://github.com/dvajjala-ui/stickerdock/actions/runs/40580811653) without creating extra work after the day's stored-image quota was met. These checks do not qualify new Android/iPhone releases, a separate server installation, unattended source access across multiple days, source-rights admission, caption quality or automatic publication.

## October 23 original-art pack and current native source

The [Workday Reactions pack](../assets/drafts/workday-reactions/README.md)
contains three fictional adult AI-art reactions. The checked-in archive passed
the native pack/reference validators and light/dark 128 px visual inspection.
Hosted Admin imported the exact ZIP as a private draft; its signed private
download matched SHA-256
`eb56a3be1a17211796aea379cf6f76d8a01f4aaacfb0bc4d23e2d53dd01a5393`.
After creator labeling and review, the public catalog listed five packs. An
independent public ZIP download matched the new entry's SHA-256
`b7c370afeef27f41400f30f37ef88a6cce43912f920170c929707687a1526dfe`
(148,200 bytes). The optional catalog cover failed its WebP bucket MIME gate;
pack-opening still fetches full artwork. Physical WhatsApp acceptance and
actual-user reaction testing remain open.

[Apple CI for iPhone 0.10.7/build 19](https://github.com/dvajjala-ui/stickerdock/actions/runs/39945178923)
passes the unsigned device build and simulator checks for sticker-first search,
WhatsApp pack handoff and persisted metadata editing. This is not a signed
iPhone installation or WhatsApp result. The earlier Android 0.11.6
[run 39946623361](https://github.com/dvajjala-ui/stickerdock/actions/runs/39946623361)
passed exact-APK emulator acceptance but failed release publication with HTTP 403.
The subsequent [run](https://github.com/dvajjala-ui/stickerdock/actions/runs/40641756805)
failed when Favorites moved outside the visible horizontal filter row at 200%
text. PR #27 repaired the layout; the published October 28 result above supersedes
these failed candidates.

## Published Android 0.11.4 and current source — October 23, 2026

[Android 0.11.4 build 19](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.4)
was published from `0a39188a597b543f23de851994455a56acee9b32` after
[signed-release CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39913314036)
passed backend tests, signed APK/AAB verification and exact-APK Android 15
emulator acceptance. An independent public APK download was 3,902,503 bytes,
SHA-256 `739cf836bb1cf78122c0eb77a70b5c08350a5b3a1f4c3e68996d749fa92be99c`;
this matches `SHA256SUMS.txt`, `release.json` and the passing `acceptance.json`.
The emulator record includes 334 packs/10,000 stickers, media preview, backup
and import flows. Physical WhatsApp acceptance remains open. Source commits
after the release add the animated catalog pack and Admin ZIP import; they are
not inside this APK.

[Apple CI for iPhone 0.10.5 build 17](https://github.com/dvajjala-ui/stickerdock/actions/runs/39913799107)
passes the unsigned device build, pinned dependencies, native integration and
simulator navigation tests at `504b6f3`. iPhone Discover now filters static and
animated packs; signed installation and WhatsApp delivery remain open.

[Apple CI for iPhone 0.10.6 build 18](https://github.com/dvajjala-ui/stickerdock/actions/runs/39940596790)
passes the unsigned device build, 20 native tests and one navigation UI test at
`f9d8faf`. The live integration test downloaded the exact published animated
pack, checked all three captions and animated media rules, confirmed preview
did not save, then explicitly saved and reloaded it. Discover now opens tapped
stickers in a full-size sheet and plays animated WebP while the sheet is active.
This is simulator/source acceptance, not Apple signing, a physical iPhone or
WhatsApp delivery.

The live public catalog now contains four packs. "Moving Animal Moods" is a
three-sticker ANIMATED pack made from independently credited CC0 animal clips.
The checked-in archive and an independently downloaded public ZIP both have
SHA-256 `c22c5c63db1bc772c79aba05a68b57e343f2c21a952fa4f86cea4f33240351fc`
(668,072 bytes). Admin's private ZIP import, exact private read-back, explicit
review and publication were verified; the public catalog reports
`kinds: ["ANIMATED"]`. See the [source and motion record](../assets/reviewed/moving-animal-moods/README.md).
The iPhone simulator test above has exercised this exact public pack; no
physical phone has been tested with it yet.
The hosted Admin at 390 px opened this published pack and decoded all three
512×512 preview images; the dialog had no horizontal overflow or JavaScript
page errors. This is browser preview evidence, not mobile-app motion playback.
[Web/API CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39916021008)
and [Android debug CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39916021017)
pass on the import commit `6d9cf69`.

### Historical 0.11.3 evidence

[Android 0.11.3 build 18](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.3)
was published from `ba3f681110659d6b62d3b39231e84068f274279c` after
[signed-release CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39787507683)
passed compilation, signing, exact-APK Android 15 emulator acceptance and
publication. An independent download of the public APK has SHA-256
`fe0f8b77e6d58838f8feb45c79b2d497574a9f0512c926206b6643c67961386c`;
that matches GitHub's asset digest, `release.json` and the passing
`acceptance.json`. ZIP integrity passes. The signed artifact is Android source
from that SHA; later `main` commits change iPhone/SQL tests and website code,
not this binary. Physical WhatsApp/Business acceptance is still open.

[Apple CI for iPhone 0.10.3 build 15](https://github.com/dvajjala-ui/stickerdock/actions/runs/39910376919)
passes an unsigned device build, package-version check, simulator tests and
artifact export at `10a2bbf`. The preceding run failed only because CI still
asserted the old 0.10.2/build 14 version after the new app had compiled; the
check now derives its expectation from `ios/project.yml`. There is no signed
iPhone installation or physical WhatsApp result.
[Web/API CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39910939503)
and [Android debug CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39910939635)
pass at `ac04f61`. The personal-caption SQL migration passed local PGlite
contract checks for grants, ownership, replay, global provider budget and
abandoned upload cleanup; it has **not** been applied to production, and the
AI flag remains off.

The two reviewed animal packs exposed a separate production bucket issue:
the public Storage bucket accepts ZIP/JSON but rejects an optional WebP cover
preview. The publisher now omits that preview if Storage rejects it, while
retaining the validated downloadable pack and exact archive checksum.
An earlier October 23 live catalog readback showed three packs; independent public ZIP
downloads of "Everyday Animal Reactions" (198,723 bytes, SHA-256
`b53b772af38d23ed4a0e7b9673f6df5fd448f28fecf3b725b24be482461ed16d`)
and "Big Mood Animals" (141,826 bytes, SHA-256
`3e4c40eb9036e4b5051c30c65c7ff6cebaa66735890124a42b802f971bdac65f`)
passed public manifest validation with the six reviewed captions. Both packs
are published; Android and iPhone Discover cards show a text cover until
the additive WebP MIME migration is applied, while explicit pack preview
loads the artwork from the ZIP. The additive migration is staged and passes a
local PGlite idempotence/preservation check; it has **not** been applied to
production. No physical phone/WhatsApp acceptance is claimed.

The Admin draft editor permits visible caption changes on static photo stickers
only when both saved provenance and current source metadata allow reuse. It
redraws and rechecksums artwork while retaining the original Factory ZIP;
reference-only/Pinterest/missing sources fail closed. Local verification:
249 Python tests pass (7 skipped), targeted Ruff passes, JavaScript syntax
passes, and a 15-check Chrome fixture smoke passes including full-size artwork
review, caption submit and narrow editor layout. Hosted headless Chrome opened
the actual private draft after the UI deployment: the cleaner caption/tag rows
fit 390 px without horizontal overflow, the 512 px artwork preview showed the
edited caption, and no page errors occurred. This is Admin browser verification,
not Android or iPhone acceptance.

The Admin pack-cover source slice builds a 240 px preview from at most
four real stickers in the checksum-validated private archive. GENERAL cards
load it lazily and cache it by pack digest; MATURE cards keep a neutral cover.
251 Python tests pass (7 skipped), targeted Ruff and JavaScript syntax pass,
and 16 local Chrome fixture checks pass. On October 23 the deployed API returned
200 for actual cover requests, and fresh headless Chrome loaded 240×240 real
artwork in four visible pack cards and two overview hero images. Desktop and
fresh 390 px mobile views had no JavaScript page errors or horizontal overflow.
The images and layout were visually inspected. This verifies the Admin display,
not the public phone catalog or physical-device experience.

Hosted write-path acceptance on October 23 used private draft
`8deca0ea-2d16-576c-8f1f-ad8670e3bab0` and a CC0 source with current
reusable metadata. The exact version-1 ZIP was downloaded and hash-verified
before the edit. One caption was changed to “Bas, ab sone do.”; version 2
read-back shows `draft` status, the new label and artwork hash. The downloaded
version-2 ZIP hash matches the saved pack row. Exactly one of five sticker
artwork entries changed; the other four bytes are identical. The new 512×512
sticker was visually inspected, and no pack was reviewed or published.

A later full-size review of all five artworks in that private draft found two
variants of the same cat-in-pan scene. One source already contains black
letterboxing and an existing text panel, so its new caption double-renders over
old text; another caption has awkward wording. The pack remains private. A
bounded dark-border filter now excludes such padded photo canvases from future
reaction-photo source selection before spending a Groq call. Its synthetic
opaque/RGBA/transparent tests pass, and local analysis of the saved draft's
exact artwork flags the bad fifth canvas while leaving the fourth. This is a
source preflight improvement, not automatic meme-quality approval.

## iPhone 0.10.2 photo ZIP acceptance — October 21, 2026

[PR #25](https://github.com/dvajjala-ui/stickerdock/pull/25) is merged at
`9940680e27e34281c52261615f82c34aa0751b12`. Its iOS/shared source matches
`53046b3dbd5d6c36aa8f6e5562bc9e9fb8ff662d`, where
[Apple CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39606440645)
passes **19 native tests and 1 navigation UI test**, zero failures/skips, plus
shared-core tests and an unsigned device build. New native tests verify PNG
transparency, EXIF-rotated JPEG geometry, normalized deduplication, animated
WebP byte retention, store/restart/portable export and whole-archive rejection
of bad later input without changing a saved library.

The downloaded verification artifact's SHA-256/CRC, IPA bundle ID, 0.10.2/build 14
metadata and absence of provisioning verify. Unsigned IPA SHA-256:
`2bf6b011e9e7b6865effc7264417b4b40f1f0723c714af84c7d018d7527902fb`.
Local artifact: `work/apple-photo-archives-verified/work/StickerDock-iOS-unsigned.ipa`.
It requires Apple signing before device installation. No App Store/TestFlight
or physical WhatsApp acceptance is claimed. See [photo ZIP behavior/bounds](IPHONE_PHOTO_ARCHIVES.md).

The new native test's actual photo ZIP export also imports explicitly on the
public Android APK: both converted photos and the animation retain exact bytes,
all existing rows stay unchanged, and restart retains the result. Public-APK
upgrade plus four licensed sample-pack imports also pass, with 12 exact media
files and static/animated segregation. Details: CREATOR_ACCEPTANCE_2026-10-21.md.

## Published Android 0.11.2 — October 21, 2026

[PR #24](https://github.com/dvajjala-ui/stickerdock/pull/24) is merged. Android
**0.11.2 (build 17)** is [published](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.2)
from `4225302a5a0f027c4c37b795c766f42684391133` after
[main CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39605443554) and
[exact signed acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39605443793)
pass. There are **135 Android / 12 shared / 207 backend tests**, no native failures
or skips, plus lint, signed APK/AAB and alignment checks. The exact released APK
passes the Android 15 10k-library journey including Favorites, large text/tablet,
photo/video creation, brush Reset/recovery, pack codes, received files and
editable backups. New cold video/warm GIF SEND checks verify captioned moving
previews and cancellation/restart without saving.

Earlier main attempts retained a Pixel Launcher system ANR and a short-GIF
screenshot phase collision. The exact failed APK shows movement locally. The
revised bounded five-capture assertion passes the real loop and rejects frozen
artwork in regression tests; no assertion is skipped. See the creator acceptance
record for retained failure evidence. The final fresh run passes independently.

Public APK SHA-256: `1e84e25b7c8cd0c7813696cc7a2e38fd46cbc246b00c19009012849f0dab2c2a`.
Public AAB SHA-256: `c17f2108edc6743c47c254871b993a054f70430e5ddf65e34064094db8522290`.
All five public assets were downloaded and checked against GitHub digests,
release.json, SHA256SUMS and acceptance.json; APK signing identity is unchanged
and both binary archives pass CRC verification. Local delivery:
`work/StickerDock-0.11.2-published/StickerDock.apk`.

A separate signed branch candidate also passes an upgrade of the existing
licensed-media library, real captioned human-reaction video/GIF previews,
explicit animated save and a fresh Apple-generated portable archive import.
Candidate and public APK hashes are deliberately separate. Current Apple CI
passes an unsigned device build, 19 native tests and 1 navigation UI test (see above).
See [the exact real-media/Apple evidence](CREATOR_ACCEPTANCE_2026-10-21.md) and
[SAF/process-stop evidence](MIGRATION_ACCEPTANCE_2026-10-21.md).

SQLite 8, portable ZIP 5, existing projects, media and signing identities remain
compatible. Favorites remain device-local preferences outside existing backups.
Physical Samsung/WhatsApp/Business, Apple signing/device, native AI, automatic
cutout quality, richer reviewed content and broader parity remain open.

## Historical Android 0.11.2 publication checkpoint — October 21

Shared GIF/video creation is merged in PR #24. Its signed branch candidate and
real licensed-media/Apple archive trials pass; see
[CREATOR_ACCEPTANCE_2026-10-21.md](CREATOR_ACCEPTANCE_2026-10-21.md). Main release
attempts retained a Pixel Launcher ANR and a short-loop screenshot sampling
failure. A bounded five-capture assertion fixes phase aliasing while preserving
frozen-artwork rejection. The prior public 0.11.1 remains current until a fresh
main run passes and its published assets are independently verified.

## Folder-import continuation — October 21, 2026

The signed local Favorites APK passes actual DocumentsUI permission, UI Pause,
Resume, process-stop/reopen and completion on a 1,215-recognized-file nested SAF
fixture. All 10,003 pre-existing stickers and project/pack rows remain unchanged;
597 imported output hashes verify and SQLite integrity is `ok`. Four animations,
EXIF grouping, 30-item splitting and invalid inputs are checked. Near-identical
static color inputs deduplicate after normalization. This is AOSP evidence,
not a physical Samsung claim. See MIGRATION_ACCEPTANCE_2026-10-21.md for artifact
binding, retained harness failures and exact counts.

## Published Android 0.11.1 — October 21, 2026

[PR #22](https://github.com/dvajjala-ui/stickerdock/pull/22) and
[follow-up #23](https://github.com/dvajjala-ui/stickerdock/pull/23) are merged.
Android **0.11.1 (build 16)** is
[published](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.1)
from `9029d136698a9361df9a0e6f61e64585a72d5fd7` after
[main CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39572989958) and
[exact signed acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39572989873)
pass. Verification includes 130 Android / 12 shared / 205 backend tests, lint,
signed APK/AAB, 16 KB alignment and a fresh Android 15 emulator journey.

Favorites search/restart/type/topic filters, move, Delete/Undo and non-destructive
removal are included. The transfer sheet keeps Copy/Move fixed below one scrolling
form/list, including 200% text. Earlier emulator transport/graphics failures remain
recorded; the successful run starts fresh using supported SwiftShader GLES.
The original acceptance error is preserved if failure-screen capture also fails.
The existing 10k-sticker, tablet, video/photo creation, brush recovery/exact Reset,
pack-code/received-file and editable backup/restore checks pass.

Public APK SHA-256:
`02929806634f5414d4bb76b8089041e7139a0720e6c7f38473444b04a9ad022f`.
APK/AAB/metadata hashes were downloaded and verified against the public assets,
release.json, SHA256SUMS and acceptance.json; signing identity is retained.
Local public artifact: `work/StickerDock-0.11.1-published/StickerDock.apk`.

A separate local APK with the same app source passed an in-place upgrade and
actual licensed human-photo Favorites journey: all 11 prior packs / 35 stickers
and project rows remained exact, as did the selected photo hash. Evidence:
`work/release-0.11.1-acceptance/fixed/real-media/report.json`. Its APK hash is
`d984ad1daf61a030eb7512e86b103f8adc2f85e38582d8682d4dcb7ea8d3997b`;
do not substitute it for the public APK evidence above. Local full acceptance
needed an emulator restart; fresh CI is the uninterrupted release evidence.

This does not close physical Samsung/WhatsApp/Business, Apple signing/device,
automatic-cutout quality, native AI, wider reviewed content or full product parity.
Favorites are device-local shortcuts and intentionally outside existing backups.
SQLite 8, portable ZIP 5, saved artwork and cloud ownership remain compatible.

## Current website change — R40

Local and [main CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39214400846)
backend suites: **205 tests pass**. Credential-free workspace read/command
fixtures, foreign-browser-origin rejection and private-phone-cloud 401 checks
pass. Browser smoke passes direct startup without session requests, outage/retry,
navigation, edit/polling protection, mobile layout and Studio source/fixture
queue/result. Production read-only/API checks pass: existing data is present,
Admin and Studio open in a fresh cookie-free browser, foreign browser origins
receive 403, phone-cloud receives 401 without a bearer and `/api/session` is 404.
No SQL/data migration, production write or Android APK change is part of this
slice. See OPEN_ADMIN.md. The owner-login
checks in the earlier publication record below predate this requested change.

## Published release — October 19, 2026

Android **0.11.0 (build 15)** is [published](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.0) from `f129430820633263316b75e44420f464f063449f`.
[Main CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39208384285) passes **124 Android / 12 shared / 208 backend tests**,
release lint/builds and 16 KB alignment. [The signed release run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39208384312)
passes signature/alignment checks and on-screen Android 15 acceptance. The
public APK/AAB and metadata were downloaded and their checksums verified.
APK: 3,853,283 bytes; SHA-256
`a84fd854da449e137f6ed34e024a80277bd5b0960bbd5b89bdf6a8f9519c40b8`.
The APK hash matches release.json, SHA256SUMS.txt and acceptance.json. The
signing certificate matches Android 0.10.2. Local delivery:
`work/StickerDock-0.11.0/StickerDock.apk`.

Signed acceptance covers 334 packs/10,000 synthetic stickers (scrolling PSS
34,571→60,108 KB), large text,
photo creation, brush/draft/restart/reset, public codes, received files and
DocumentsUI editable export/restore with retained project history. These are
emulator checks, not physical WhatsApp/Business or segmentation-quality acceptance.

[Production admin](https://stickerdock.vercel.app/admin) is deployed. The current
dashboard opens directly and its supporting routes and desktop/phone browser
checks pass. Phone-cloud authentication remains enforced. No production writes
were made in this publication check. The
existing public catalog has one four-sticker pack; its archive/media hashes
and format validate. Native AI/original artwork, Pinterest approval, catalog
breadth, physical-phone acceptance and remaining parity are still open.

## Historical pre-merge editable backup checkpoint

Earlier source `f1bcb0d` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39205131984):
**124 Android / 12 shared / 208 Python tests**, including all eight editable
backup tests. Signed native build and on-screen acceptance are tracked in
[run 39205128245](https://github.com/dvajjala-ui/stickerdock/actions/runs/39205128245)
both pass. The downloaded APK's SHA-256 is
`7aaff3b68e6177337139662acadc79edbe9f0238f4b137f0646ba4f6cefb693e` and matches
release.json, SHA256SUMS.txt and acceptance.json. Earlier artifact:
`work/editable-backup-verified/work/release/StickerDock.apk` (3,853,283 bytes).

The exact APK exports the 10k-fixture library through DocumentsUI, verifies
source/mask hashes, restores a single-project subset through DocumentsUI, and
retains its project history after restart without changing existing packs.
Format/picker cancellation and 200% text pass. Inspected screenshots 18/19/20/21:
backup body text has no curved clipping; normal viewport cropping at scroll
boundaries remains expected, with the fixed Cancel control accessible. Import
action text is readable. Scrolling PSS is **34,167→66,630 KB**. All earlier
library/editor/code/share journeys pass in this same run. Fixtures remain
synthetic/repo-owned, not proof of desirable real catalog artwork.
Physical S24/WhatsApp/Business and third-party Files/Drive providers remain
pending; a fresh ADB check finds no device.

New Android editable backup tests cover originals/masks/saved recipes/drafts,
Undo/Redo/restart/Reset, unchanged portable v5 imports, archive routing, damaged
blobs/recipes and project-storage failure rollback/retry. Source `744a13d` passes
[Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39165983862).
Updated source `cf9d8b0` passes native/shared/backend tests, lint, build and
signing/alignment; [signed emulator acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39202677418)
fails on a test-script label mismatch (`Save` versus the system picker's `SAVE`),
after prior library/editor/sharing journeys and backup format/cancel/large-text
checks. The script is corrected; original-image validation also now fully
decodes pixels, with a forged-valid-checksum malformed WebP regression.
Screenshot inspection also found pill-shaped option buttons clipping text at
200% size. They are replaced by moderately rounded option cards in the scroll
area. Rerun required for complete DocumentsUI export/restore and persisted history.
Physical Samsung Files/Drive backup picker acceptance remains pending.

## October 19 — current continuation

- **Verified candidate `019c75f`:** [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39161490697)
  and [complete signed-APK acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39161487231)
  pass: **116 Android/12 shared/208 Python tests**, lint, signing and alignment.
  APK SHA-256 `6c9856e53b2d18bbff58ca0fae5086f5eeda3c1a944a45d2460732c892a8aa24`.
  Downloaded APK hash matches both release.json and acceptance.json. Candidate
  files are under ignored `work/StickerDock-0.11.0/`; stable remains 0.10.2.
- That exact APK passes 334-pack/10k-sticker browsing, animation, tablet,
  360dp/200% text, memory trim/restart, real MP4 preview, named photo save/cancel,
  brush undo/redo/cancel, durable draft/process restart, explicit Save/exact Reset,
  public code cold/warm/share delivery, and received-file cancel/import/restart.
  Scrolling PSS is **40,919→67,443 KB**. Create, editor draft, pack-code and
  received-file screenshots were inspected. Test artwork is synthetic/repo-owned.
- The received-file fixture uses a real scoped FileProvider URI from the app's
  share directory. It does not establish another app's document-grant behavior.
  Unit round-trip checks preserve artwork hashes, labels/tags and private ratings;
  inaccessible files remain retryable. Physical S24/WhatsApp/Business, live
  segmentation quality, published-code/HTTPS sharing and original AI artwork
  quality remain separate gates. A fresh local ADB check sees no device.

- `8eac289` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39159539977),
  including the provider/content-URI fixture corrections, native regressions,
  backend checks, lint and builds. [Signed APK interaction run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39159546406)
  passes brush undo/redo/cancel, durable draft/process restart, save/exact reset
  and public code cold/warm/share handling. It then fails at received-file import
  using a shell-created DocumentsProvider URI. Overall signed acceptance fails.
  The fixture now uses a scoped FileProvider URI matching pack sharing; import
  errors also retain failed files for retry and expose a reason. A matching new
  signed run is required. Create and brush screenshots from this run were inspected.
- `4945ce0` [failed](https://github.com/dvajjala-ui/stickerdock/actions/runs/39139552573)
  two unit tests: provider metadata/resources were missing from the unit-test
  environment, and an older share fixture used file URIs. Those test fixtures
  are corrected. Network/raw file URI rejection remains enforced for incoming
  shares. This failed source is not a verified APK candidate.

## 0.11.0 — pack-focused Android candidate, October 18

- Design and public-code source `1d1d95c` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39138509715).
  The earlier design [signed run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39138121727)
  reached the brush scenario but its exact-match scroll helper skipped the sticker
  whose accessibility label includes tags. The test now selects that visible
  sticker directly. Library/large-text screenshots from that run were inspected;
  this is not a successful brush scenario. Received-file changes at `4945ce0`
  and their complete signed acceptance are pending.

- Automatic-cutout source `377b97a` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39133885444):
  build, tests, lint and native alignment. Mask tests cover soft alpha, original
  transparency, retained recipes, draft/copy/deletion recovery and damaged-mask
  refusal. This does not prove live Google Play model availability or quality.
- Brush source at `7878bbb` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39134519338):
  112 Android/12 shared/208 Python tests plus lint/build. The [signed run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39134517018)
  passed build/signature checks and existing scenarios, but stopped before brush
  interaction: its locator matched both the search field and pack title. The
  fixture now searches `001` instead of the full title. This failed run does not
  establish brush interaction acceptance. A rerun also covers the subsequent
  typography/card/Create/editor-tool layout changes.

- **Verified core checkpoint `db6c979`:** [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39070097918)
  and [signed APK emulator acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39070094081)
  both pass: 105 Android/12 shared/208 Python tests, lint and signature/alignment.
  SHA-256 `a80e6ee90f4ddd2747e6fddd1860114d3d34e2333bcae29766984ec47afd8151`.
  The corrected photo test proves picker selection/cancel do not save, explicit
  named-pack save adds one pack/sticker, and restart preserves it. The 10k search
  regression proves pack matching is not capped by the 100-thumbnail query.
  Scrolling PSS 31,626→60,579 KB. The local ADB device list is empty; physical
  S24/WhatsApp acceptance remains unavailable. This evidence does not cover
  subsequent work beyond this exact commit.

- First native slice `2c6f9e8`: [debug CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39068155580)
  and [signed APK acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39068160560)
  pass. 105 Android + 12 shared tests; lint, signatures/alignment and Android 15
  emulator checks. APK SHA-256 `2f61718879a3c9bf937faaa32a670928fa6039293dff1f7e7e3bf171dfa77319`.
- That emulator run covers 334 packs/10,000 synthetic stickers, animated
  poster/detail behavior, 360dp/200% text, tablet layout, scrollable controls,
  restart preservation and actual MP4 preview without mutation. PSS grew from
  32,146 to 63,996 KB during scrolling. Screenshots inspected for library,
  large-text pack detail and WhatsApp target sheet. Flat-color performance
  fixtures are not content-quality evidence.
- Code slice `1ac4af2` adds the original-art worker and an emulator test
  for photo selection → named destination → cancel/save → restart. Its
  [signed run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39069066380)
  passed build/native/backend checks and the existing emulator checks, then
  failed on a test locator: photo DocumentsUI says "Images in Downloads", not
  "Files in Downloads". The locator is corrected; a rerun is required. This is
  not a passing photo-flow claim. [Branch CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39069301039)
  passes at `222cebd`.
- Local full Python suite: **208 tests pass**, no skips. Original generation uses
  mocked provider responses; spending-disabled dry run makes zero submissions.
  No live image generation, background extraction or generated-art quality claim.
- The photo fixture is the repository-owned app icon, not a user collection or
  a claim of catalog quality. Physical WhatsApp/Business installations, personal
  SAF folders, real-artwork product sign-off and iPhone parity remain separate.
- Candidate source remains in [PR #21](https://github.com/dvajjala-ui/stickerdock/pull/21);
  the stable release is still 0.10.2.

## 0.10.2 — published Android reliability release, October 17

- [Signed release CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39037711698)
  passes 99 Android/12 shared/189 Python tests, release lint, signed APK/AAB and
  ZIP alignment. [Published assets](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.10.2)
  include source/version/checksum metadata and the exact-APK acceptance report.
- SHA-256 `a0988bf2def68fdb3bea0a9b3a24eb156bb0a9b8e384db5a1f4c331abea567fa`
  matches the downloaded APK, release manifest, GitHub asset digest and report.
- AOSP Android 15: 334 packs/10,000 synthetic stickers; scrolling PSS
  32,581→47,958 KB; animated poster/detail behavior; 360dp/200% text; Android
  tablet two-pane layout; filter/WhatsApp target controls; memory trim and
  restart data preservation; actual MP4 streaming preview with no saved rows.
- Low-storage rollback is covered through the save API in native unit tests;
  the emulator disk was not filled. Preview interaction time includes automation
  and screenshots, not just encoding. These are synthetic fixture bounds.
- Local Admin browser flow tests and live 390px/1280px page checks pass, with
  normal session status, protected API rejection and a stable APK redirect.
- [Live Factory main run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39037827620)
  produces 5/40 stickers after schema/recovery fixes. Fresh-runner synthetic
  40/day and interrupted-upload recovery pass; live full-day capacity remains open.
- Physical WhatsApp/Business, huge personal-phone imports, iPhone parity/signing
  and advanced AI/automatic-cutout/project-backup requirements remain separate.

## 0.10.1 — current Android artifact and archive acceptance

- 97 Android + 12 shared tests and 178 Python/no skips pass. Release lint has
  0 errors/43 warnings. APK/AAB signatures, integrity and 16 KB ZIP/ELF alignment pass.
- Exact installed APK ACBB81AA… passes the creation/navigation/large-text checks
  below, including moving MP4 preview, explicit save and restart: 10/33 to 10/34.
- A real native Swift ZIP imports through Android's picker with exact bytes,
  MATURE rating, GENERAL category and name retained. Every old row survives
  restart; library becomes 11/35. Test fixture is synthetic integrity evidence.
- Android CI: https://github.com/dvajjala-ui/stickerdock/actions/runs/38907007546.
  The archive-export subtest passes in Apple run 38907007539; that whole run fails
  a separate malformed rotation fixture and is not counted as a full Apple pass.
- Current corrected Apple tests, signed iPhone distribution, physical WhatsApp,
  populated-library performance, native AI/cloud/editor parity remain open.
  Hash-bound evidence: work/release-0.10.1-acceptance/status.json and subfolders.

## 0.10.0 — historical Android / first Apple acceptance

- LOCAL ANDROID: 94 Android + 9 shared tests, 178 Python/no skips, lint
  0 errors/43 warnings, signed APK/AAB, signature and 16 KB ZIP/ELF alignment pass.
- NATIVE ANDROID: exact installed APK 640D715B… passes Create/import return
  navigation with toolbar/system Back, 360dp/1.3x text, initial real-photo content,
  disabled missing WhatsApp/Business target buttons, moving MP4 preview with no
  library additions, explicit save and restart. Old rows retained, 9/31 → 9/32.
  Reports/screenshots: work/release-0.10-acceptance/native/.
- HOSTED FIRST CANDIDATE e2af790: [Android](https://github.com/dvajjala-ui/stickerdock/actions/runs/38899086147)
  and [Apple](https://github.com/dvajjala-ui/stickerdock/actions/runs/38899085950)
  pass. Apple builds device/simulator frameworks and unsigned iPhone app, then
  passes 6 native integration tests and 1 navigation UI test. Tests cover actual
  photo encoding, archive round trip/restart, duplicate/invalid import preservation,
  traversal rejection, generic static/animated ZIP separation and WhatsApp payload.
- NEWER CANDIDATE: native GIF/MP4/rotated-video tests, actual codec validation on
  import, public-catalog integration and pinned SPM resolution need the matching
  latest Apple result. Prior green CI is not proof of these newer changes.
- NOT ACCEPTED: physical WhatsApp/Business/iPhone, Apple distribution signing,
  huge real-phone libraries, native AI/cloud parity and production readiness.

See REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md and RECOVERY_PROGRESS.md.

## Shared core / expanded media — signed 0.9.1 local acceptance passed

5 common tests + 94 Android tests, release lint (0 errors/43 warnings), signed
APK/AAB builds, signature, 16 KB alignment and archive integrity pass. The shared
mux reproduces five real native exports exactly.
Core-only Gradle configuration passes; Apple targets are conditional on macOS
and have not been compiled. No working-tree CI or iPhone client is claimed.
Full Python suite: 176 passed/no skips, including focal-framing checks.

The existing signed 0.9 APK separately imports 12 new real-photo/animated samples
from three independent source works with credits. Four explicit imports add
exactly four packs/12 stickers; exact saved hashes, decoded sizes/kinds, old-row
preservation and force-stop persistence pass. Report:
work/broader-reactions-emulator/acceptance/report.json.

The exact signed 0.9.1 APK upgrade retains all these samples and prior rows.
Real-photo grid/detail 512px preview inspection, new moving MP4 reaction creation,
unchanged library during preview, explicit existing animated-pack save, saved
bytes/dimensions/motion and force-stop persistence pass. Library: 9 packs/30
stickers before save, 9/31 after. Accepted crash buffer is empty. The first
picker-navigation automation failure remains a failed harness attempt.
Hash-bound evidence: work/release-0.9.1-acceptance/status.json and native/report.json.
This is Android 15 emulator acceptance; physical WhatsApp/Business, Apple and
large real-phone libraries remain unverified.

## Native creator 0.9.0 — signed local acceptance passed

92 local Android tests pass, including WebP native decoder checks, exact saved
preview bytes, duplicate/wrong-target guards, failed storage cleanup preserving
existing packs and GIF resource bounds. Final signed APK/AAB build, signature,
16 KB alignment and release lint (0 errors/39 warnings) pass. The exact installed
APK passes native WebM/MP4/GIF preview/save/restart, unchanged library during
preview, stale-caption preview invalidation, saved byte/hash/alpha/motion checks
and rotated portrait pixels/margins. Reports are hash-bound in
work/release-0.9-acceptance/status.json; see RECOVERY_PROGRESS.md. These are local
emulator checks, not physical WhatsApp/Business or iPhone acceptance.
The first signed creator
candidate failed actual WebM motion because timestamp thumbnails repeated one
frame; it is superseded by sequential decoding. Earlier System UI boot ANR prevented the first
emulator attempt before creator entry; that attempt is not a passed app test.
The first rotation fixture lacked metadata; its failed portrait assertion was
a fixture problem and is not counted as passing. The corrected fixture has an
actual display matrix and passes upright reference comparison.
Real-media worker Python suite: 173 passed/no skips; no new deployment or CI.

## Recovery 0.8.0 — local signed candidate verified

Editable projects/drafts, manual cutout and additive SQLite v8 have local source
coverage. The 78-test run covers legacy migration/scan metadata, transactional
recipe/artifact rollback, draft persistence, copy/move/Undo, export privacy,
bounded outlines and native alpha rendering. Final signed build, lint (0 errors/
35 warnings) and emulator caption/keyboard, 360dp/130% text, cutout alpha and
force-stop/cold-boot checks pass. Hash-bound reports and exact APK identity are
recorded in RECOVERY_PROGRESS.md. These synthetic fixtures do not establish meme
quality. Real photo/animated product evidence is the next scope under R29.
Physical WhatsApp/Business and iPhone remain pending; no current working-tree CI.

## Recovery 0.7.0 — local candidate

Use [RECOVERY_PROGRESS.md](RECOVERY_PROGRESS.md) for current local checks and
artifact identity. Automatic catalog import has been removed in source. New
coverage checks read-only refresh, native legacy preview rendering/tamper
rejection, optional public preview metadata, persistent provider cooldown/recovery
and mobile admin status. No new CI or hosted deployment is implied. Physical
Galaxy/WhatsApp checks remain pending; user currently has no phone available.

The local candidate now passes 65 Android tests and signed-emulator editor
undo/redo, discard protection, background process reclamation and saved artwork
after cold boot. Byte/checksum/native-preview verification replaces earlier
SQL-only save acceptance, which missed a zero-byte edited file. There is still
no new CI run for this uncommitted candidate. Draft recovery does not yet cover
force-stop/reboot, and saved editable originals remain pending.

## Final 0.6.0 candidate evidence

- CODE CI SUCCESS at d9cba2b89d082482bb4e66aa57714248ee0d5ab6:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38717657942.
  Python regressions, Android unit tests, lint/build and native alignment checks
  completed successfully. Vercel deployment for this code also succeeded.
- Signed local 0.6.0 APK SHA-256:
  16AA3FCEC9795F245A651845692630E132633842B421A08F6102A9642818FEBE.
  Existing signing certificate verified; local APK and AAB built successfully.
- Final hosted root opens Admin; Admin and Studio have no manual token input.
  Both pass 390px no-overflow/no-page-error checks. Account recovery route works.
- Public-copy metadata regression preserves decoded pixels/animation while
  removing EXIF/XMP/custom outer chunks. Private draft/provenance regression
  confirms submission conversion validates and does not publish automatically.
- SMTP delivery and physical-phone acceptance remain pending as described below.

## Private cloud library / owner sessions — October 15

- LIVE: migration applied; cloud_packs, cloud_assets, community_submissions,
  community_reports and cloud_request_budgets all verified with RLS enabled.
- LIVE: cloud account isolation, private upload/metadata, checksum download,
  rights-confirmed submission/withdrawal, safe deletion retry and cleanup passed:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38716261393.
- LIVE: Vercel deploy succeeded; unauthenticated cloud returns 401, owner console
  reads and submissions list return 200, public catalog still has one real-image
  pack. No synthetic test pack was published.
- LOCAL: 52 Android unit tests, release lint and signed APK/AAB build passed.
  Backend suite: 151 tests / four video-dependency skips, plus four session tests.
- WEB: 390px admin sign-in/recovery pages, no overflow and no JavaScript page errors.
- PENDING BY USER DECISION: SMTP production mail setup skipped. Verified-account
  sessions work; do not promise confirmation/reset mail for arbitrary new users.
- DEVICE PENDING: local import → cloud → another phone restore, pause/retry,
  background scheduling, public report/hide/withdraw and WhatsApp targets.
- SAFETY: mature packs stay offline; sync does not grant publication or training
  rights. Full cloud account/media erasure is currently an operator support flow.

## Pinterest and real images — October 15

- LIVE FINAL: four-sticker `Desi photo reactions` pack published, public ZIP hash
  verified, old emoji demo unpublished but preserved privately. Fresh catalog
  read-back now passes. Phone delivery remains a device acceptance gate.
- FINAL LOCAL: 142 Python tests run, 138 passed, four local video dependency
  skips. HOSTED: owner login, four real pack previews, public catalog, 390px layout
  and absence of browser page errors passed. Code CI through c9ae6a2 passed:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38710633232.

- Final local full Python suite before the storage-read follow-up: 140 tests run,
  136 passed, four local dependency skips. Focused storage/OAuth/worker/admin
  regressions after the freshness fix: 59 tests passed. Portrait crop and source
  preservation are covered; source files are not rewritten.
- Live publication exposed an authenticated storage read-back mismatch after
  overwriting the catalog. Authenticated private reads now use distinct cache
  nonce URLs plus no-cache headers; hosted publication must be reverified after
  this follow-up deployment.

- LIVE: corrected StickerDock app 1624615 is submitted/Trial pending. Its real
  website/privacy links are verified in Details. Temporary token probe returns
  HTTP 401/code 3; app credentials/redirects remain locked pending review.
- HOSTED: real About/Privacy/Support pages and the Pinterest admin screen pass
  the 390px browser smoke. API status reports not connected, not false success.
- LIVE: licensed-photo Creator request completed on attempt 1, result retrieved
  and visually reviewed. Worker run:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38709866164.

- LOCAL: 138 Python tests run, 134 passed, four local video dependency skips.
  New checks cover signed OAuth state/cookie validation, expiration/tampering,
  token refresh, no credential leakage or Pin-response persistence, legacy Pin
  conversion rejection, and real-media requirement without template fallback.
- BROWSER LOCAL: About/privacy/support pages, chosen contact, 390px layout,
  Pinterest navigation and preview-mode connection guard pass. Existing admin
  flow and background-refresh regressions pass.
- LIVE DASHBOARD: app 1624134 Trial access denied; old fields are locked.
  Corrected request is prepared, not yet approved. No API connection claim.
- DEVICE: phone import stays local; no ADB device connected. No backend copy of
  the user's imported WhatsApp collection is implied.

## Production-flow continuation — October 15

- HOSTED: authenticated owner login, health, overview, private media listing,
  existing Factory draft migration and signed ZIP checksum read-back pass.
  One reviewed GENERAL pack with five stickers is published. Phone acceptance
  is still pending.
- LOCAL FOLLOW-UP: background refresh regression passes with a held response
  while navigating/editing settings; stale responses preserve edits. All 18
  admin API tests pass.

- LOCAL: Python suite ran 129 tests: 125 passed, 4 video tests skipped because
  local FFmpeg/DejaVu dependencies are unavailable. JavaScript syntax checks pass.
- BROWSER LOCAL: Chromium exercised unsaved-edit protection, metadata/category
  save, adding stickers to an existing draft, direct AI-result preview and
  result-to-pack selection. Missing-schema setup was exercised with mocked
  responses. Both admin and Studio fit a 390px viewport without horizontal overflow.
- BROWSER LOCAL: Studio resized an 11,148,045-byte source to a 1,677,496-byte
  upload; mocked upload/queue/result/editor flow passed. This does not prove
  hosted generation or deployment.
- LOCAL RELEASE: signed 0.5.0 APK and AAB built. APK signature verification and
  AAB JAR signature verification pass. APK SHA-256:
  `B2A0404289CD9029F2DD206130FC62A24B68D98A65734E83D689AE3B455176F4`.
  The signing key is backed up outside Git and four signing secrets are configured
  in the GitHub `android-production` environment. Phone acceptance is still pending.
- CI PASS: current Android debug build/tests and signed release build both pass:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38703559085 and
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38703589173.
- LIVE: read-only readiness confirms all eight required tables and selected
  columns are available, with zero published packs:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38703586594.
- LIVE: new Vercel Hobby deployment at https://stickerdock.vercel.app responds
  200 for Studio/admin and 401 for unauthenticated admin API. Server secret setup
  and authenticated hosted smoke remain pending.
- LIVE: updated Factory worker generated five more stickers and persisted 10/40
  for October 15, using the RSS content fallback. This is a successful batch,
  not a completed 40/40 day or proof of Groq browser-search availability:
  https://github.com/dvajjala-ui/stickerdock/actions/runs/38704185175.

Switching from a debug-signed installation to this release-signed APK requires
backing up the library before uninstall/reinstall. Future release updates must
keep the same signing key.

## Core-flow additions — October 15

- LOCAL: 52 Android unit tests pass, debug APK 0.5.0 assembled, release lint passes. Focused native-graphics coverage checks safe/stale artwork replacement, identity/metadata retention, rotation/transparency, 31-photo splitting, duplicate cleanup, corrupt shared-ZIP continuation and WhatsApp preflight rejection of damaged bytes. Existing backup compatibility remains covered; the 10k library fixture checks bounded previews.
- DEVICE PENDING: photo picker/EXIF orientation, editor controls/keyboard/layout, Add/Open in personal/Business and actual WhatsApp cache refresh after edits/deletions.
- LIVE: October 15 public catalog GET returned NoSuchKey; reviewed publication and end-to-end automatic phone delivery remain pending.

## Factory/admin delivery additions — October 14

- LOCAL: Python admin-created schema-v5 ZIP restores in the Android library with
  exact sticker hashes and metadata. Catalog tracking checks deleted packs and
  changed hashes; only Supabase's missing-index response is treated as empty.
- LOCAL: owner auth rejection, source normalization, migration idempotency,
  provenance gates, immutable edits, stale versions, catalog locking,
  review/publish/unpublish and Factory pause behavior use fixtures/fake services.
- BROWSER PREVIEW: desktop/mobile create, migration, review/publication and queue
  controls exercised with synthetic data. Hosted operation remains pending.
- DEVICE PENDING: Factory feed on open/resume, refresh, import/open, offline local
  use and WhatsApp/Business acceptance on the Galaxy S24 Ultra.
- LOCAL: content briefs validate categories/source URLs, separate browser-search
  and RSS/owner fallback modes, gate adult humor behind private MATURE output,
  export category-separated packs and auto-migrate complete output idempotently.
- LIVE PENDING: add `GROQ_TRENDS_API_KEY`, apply the admin migration, run one
  scheduled workflow and inspect real browser-search sources and usage.

**Never mark runtime behavior as proven by GitHub Actions alone.**

| Test | Expected / current status |
|---|---|
| Android test/build | GitHub Actions `:app:testDebugUnitTest :app:assembleDebug`; check current run |
| ADB ZIP smoke tests | Python unittest, duplicate collapse, bounded ZIPs; check current run |
| Pack create/rename/delete | Persists after restart, files cleaned |
| SQLite v1–v6 to v7 | Non-destructive migration retains stickers/metadata and adds the resumable import journal |
| Categories and sticker metadata | Create/edit/filter categories; backup v5 round-trip preserves labels/tags; schemas 1–4 remain readable |
| Static input | PNG/JPEG/WebP → 512px WebP <=100KB; alpha preserved |
| Animated input | Existing WebP 512px <=500KB, frame >=8ms, duration <=10s |
| Animated previews | Grid animates smoothly, releases drawable |
| Duplicate import | Folder import dedups globally; manual packs may contain shared artwork across packs |
| SAF folder import | Select WhatsApp and WhatsApp Business folders; 11k files; no root |
| EXIF grouping | Recover name/publisher from authentic WebP EXIF when present; fallback sensible |
| Failure/pause | SQLite journal and sticker row commit together; resume removes orphan files; no existing user data deleted |
| Depth bound | Report omitted deep folders and direct the user to import them separately |
| 30-sticker boundary | Multiple same-named packs, each <=30 |
| Multi-ZIP | Up to 100 archives, preserve names/kind/rating for StickerDock backups |
| ADB | Never touches private app data or credentials; works without root when accessible |
| Pack link bookmarks | Share text/plain URLs and paste many; only allow sticker.ly/s/ codes |
| ZIP traversal/bombs | Reject traversal, oversized size, entries, duplicate filenames |
| WhatsApp / Business | Official provider accepts pack; per-app intent works |
| Two accounts in one WhatsApp | Do not claim routing without real test/official support |
| Public catalog | Only review-approved GENERAL packs downloaded after integrity verification |
| Private MATURE label | No automatic public uploading |
| Offline usage | Local features remain functional without internet |
| Process death | SQLite import journal, Pause/Resume/Discard UI and persisted SAF URI are implemented; kill/restart recovery still needs Galaxy S24 Ultra validation |
| Backup round trip | Static normalized WebP bytes, SHA-256, labels/tags and category remain exact; entries stage on disk within shared export/restore bounds |

Test against Samsung Galaxy S24 Ultra and an Android emulator before publishing APK as production ready.

## Product refresh and Openverse source — October 29

- LOCAL: 328 Python tests run (7 environment/provider skips; all others pass), including
  Openverse source registration and bounded intake title/tag metadata. Admin
  JavaScript syntax checks pass.
- LOCAL LIVE: current HandNavigate source fetched and decoded 2/2 real keyless
  Openverse candidates through the fixed API thumbnail origin. HandNavigate
  37/37 core and 36/36 service tests pass. This is not hosted evidence.
- ANDROID SOURCE: root branding, light/dark color tokens, first-library flow,
  Create workflow, reviewed Discover explanation and Coming next sheet changed.
  Visible Android/Admin encoding damage was removed.
- CI PASS: Android compile, unit tests, lint and debug APK assembly passed in
  [run 40836281242](https://github.com/dvajjala-ui/stickerdock/actions/runs/40836281242).
- VISUAL PENDING: inspect light, dark and 200% text screenshots. A passing compile
  does not establish product polish or physical-device behavior.
- HOSTED PENDING: Railway deployment `e9c1cce2-a916-44e2-90ec-a94200a2db2e`
  reports `SUCCESS` at `0dd205f` after the owner applied the staged update.
  The worker has not yet connected with its new capabilities; Admin rejected
  the 2-image test without creating a job. After the next scheduled connection,
  verify `openverse`, perform Supabase delivery/read-back and only then edit the
  daily plan. No candidate is automatically approved or published.

## Android 0.12.0 signed release — October 30

- RELEASE: [android-v0.12.0](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.12.0), build 22, source `4db31807f81dd38e6a46b91b658ea1d5d723ed34`.
  [Signed workflow 40933890345](https://github.com/dvajjala-ui/stickerdock/actions/runs/40933890345)
  completed successfully, including Android 15 acceptance of the exact APK later published.
- INDEPENDENT RELEASE CHECK: the downloaded public APK's local SHA-256, GitHub asset digest, `release.json`,
  `SHA256SUMS.txt`, and [acceptance.json](https://github.com/dvajjala-ui/stickerdock/releases/download/android-v0.12.0/acceptance.json)
  all agree on `dc84d6417a3faad69e91ed95e64335e6e6b498f1b21cc2bbef1cf5623d4f8004`
  (4,001,323 bytes). The release AAB digest is
  `a9812917922e562c1052bc6314abfaa2d9934909b0c115bdd53b2c08cf99fc4f`.
- EMULATOR PASS: 334 packs/10,000 stickers; animated posters/playback, large text and
  tablet layout, scrolling/memory, MP4 preview, photo picker and explicit save,
  brush undo/redo and restart, favorites, custom/shared pack handling, and editable
  backup export plus one-project restore with source/mask/history integrity.
- DEVICE PENDING: physical Galaxy and WhatsApp/Business acceptance, live published-pack
  HTTPS sharing, low-storage rollback beyond native unit coverage, and Google Play
  subject inference/cutout quality. This emulator result does not establish those.
