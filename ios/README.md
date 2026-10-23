# StickerDock for iPhone / iPad — tested candidate, not store release

Current source candidate: **0.10.4 (build 16)** adds a clear curated-pack cover
when a catalog thumbnail is unavailable, alongside the opt-in private AI caption
suggestion for a selected static photo. It requires a StickerDock phone account,
separate rights and online-processing consent, and an explicit Apply action;
preview and Save remain local, separate steps. The server switch stays off until
the private SQL migration and account isolation are verified in production.
[Apple CI for 0.10.3](https://github.com/dvajjala-ui/stickerdock/actions/runs/39910376919)
passed its unsigned device build, package-version check, simulator tests and
artifact export. The 0.10.4 UI change needs a new passing run. The version
check reads `project.yml` rather than a hard-coded version. The IPA is unsigned;
physical iPhone installation and WhatsApp acceptance remain open.

The prior tested candidate **0.10.2 (build 14)** adds PNG/JPEG photo ZIPs alongside WebP.
The native converter preserves transparency, applies EXIF orientation and bounds
source bytes/dimensions and normalized output. Static/animated packs stay separate;
duplicates collapse. Malformed/animated-disguised photos reject the whole archive
before saving. [PR #25](https://github.com/dvajjala-ui/stickerdock/pull/25) is
merged after [matching Apple CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39606440645)
passes **19 native tests and 1 navigation UI test**, no failures or skips, at
`53046b3`; its iOS/shared source is unchanged in merge `9940680`.

This native SwiftUI client uses the same Kotlin Multiplatform WebP validation/
container code as Android. iOS 17+ is the initial target. It is independent of
the official WhatsApp sample UI. The user has no Mac, so standard free macOS CI
on this verified-public repository performs Apple compilation and simulator tests.
The earlier [October 21 Apple run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39597303190)
passes an unsigned iPhone build, **15 native integration tests and 1 navigation
UI test**, with no failures or skips. It runs at Android main commit `9029d13`;
iOS and shared-core source are unchanged through `4394ac1`. Tests include native
photo/video/GIF/rotation, archive safety, store rollback/restart and read-only
catalog behavior. That older iPhone checkpoint is **0.10.1 (build 13)**. Android version
numbers do not apply to it. No signed physical-device install or App Store
acceptance is claimed.

## Implemented source

- Discover / Create / Library navigation; content-first pack grids and preview.
- Photos/Files selection with 20 MB bounded private working copies; no original
  modification, no broad Photos permission, no automatic upload.
- Static photo captions, focal crop/fit, on-device Vision foreground removal;
  GIF/video 0.5–4 second trim, 6/8/10 fps, captioned 512px WebP animation, progress
  and cancellation. Up to six caption variants; preview before explicit saving.
- Serial local store with atomic manifest commits, media-before-metadata saves,
  SHA-256 validation, per-pack dedup, 30-sticker/static/animated limits, rename,
  delete confirmation and favorites. Import/save failure preserves existing data.
- StickerDock ZIP schema 1–5 reads/schema 5 writes, checksums, bounded archive
  expansion, duplicate/path/symlink rejection, staged validation before save.
  Generic PNG/JPEG/WebP ZIPs deduplicate and split static/animated into 30-item
  packs. PNG/JPEG conversion preserves alpha/orientation and fails before save
  on invalid input. .wastickers and EXIF pack-name grouping remain parity gaps.
- Reviewed GENERAL catalog metadata/previews; full preview/download and save
  require explicit actions. Saved revisions deduplicate. Mature stays private.
- Native share sheet for pack/all-library ZIPs; share archives are private cache
  files cleaned when the share sheet closes.
- Optional account-owned AI caption suggestion for static, general-audience
  photos. A bounded caption-free preview is sent only after two explicit
  consents; the result is text until applied to the local editing recipe.
- Documented WhatsApp local-only general-pasteboard payload, 60-second expiry,
  explicit single-pack URL handoff; opening is not installed-status confirmation.
- Bounded 8 MB thumbnail/animation buffers; lazy grids/discovery, first-frame
  thumbnails, detail-only animation paused offscreen/background, chunked bounded
  network reads and redirect rejection. These are source bounds, not measured
  phone performance claims.

## Use the candidate after Apple signing

Create → choose Photos or Files → caption/crop or trim → Generate preview →
review → Save to library. Select an existing compatible pack or create a new
one. Discover previews never save automatically; choose Save explicitly.

Library settings imports/exports StickerDock ZIPs. Pack options shares one pack;
tap a sticker for moving preview, favorite or delete. Static and animated packs
stay separate. A pack needs 3–30 stickers before Add to WhatsApp. That action
opens WhatsApp for confirmation; it cannot confirm installation or choose an
account inside WhatsApp. See [cross-platform migration](../docs/PHONE_MIGRATION.md).

## Build / verification on a Mac

CI also packages an **unsigned testing IPA**. It needs the user's Apple signing
before device installation; it is not a signed download, TestFlight release or
proof of WhatsApp acceptance. No Apple credential is created or embedded.

From repository root (JDK 17 and Gradle 8.11.1):

```sh
gradle --no-daemon -PstickerCoreOnly=true :sticker-core:jvmTest :sticker-core:assembleStickerDockCoreReleaseXCFramework
brew install xcodegen
cd ios
xcodegen generate
mkdir -p StickerDock.xcodeproj/project.xcworkspace/xcshareddata/swiftpm
cp Package.resolved StickerDock.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved
xcodebuild -resolvePackageDependencies -onlyUsePackageVersionsFromResolvedFile -project StickerDock.xcodeproj -scheme StickerDock
xcodebuild -project StickerDock.xcodeproj -scheme StickerDock -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' build CODE_SIGNING_ALLOWED=NO
# Select an available simulator with xcrun simctl list devices available.
xcodebuild -project StickerDock.xcodeproj -scheme StickerDock -destination 'platform=iOS Simulator,name=<available iPhone>' test CODE_SIGNING_ALLOWED=NO
```

`.github/workflows/apple-core.yml` prepares the same unsigned device build,
native integration tests, navigation UI test and xcresult/artifacts on macos-15.
It runs manually or on relevant pull requests. Both October 21 runs above use an
iPhone 17 Pro / iOS 26.2 simulator. Later Apple/shared source changes need their
own passing result. It makes no App Store deployment.
SPM direct dependencies and the resolved transitive versions are pinned in
`Package.resolved` from the first successful Mac run. Xcode/Swift/Kotlin compatibility errors must be
fixed there before claiming source-build acceptance.

The generated Xcode project and Info.plist are ignored; `project.yml` is the
source of truth. Catalog base defaults to the same public read-only catalog as
Android and is a build setting. No Groq/Supabase service key or owner credential
belongs in this app.

## Acceptance still required

1. Apple framework/device/simulator builds and 19 native / 1 UI tests pass in
   the 0.10.2 source-bound run. Newer source needs its own result. Windows
   Swift grammar parsing is only a syntax check, not an Apple build.
2. Native codec, archive safety, restart, failed-save and navigation tests pass.
3. Import real human/animal photos/GIF/video and Android-exported archives;
   verify alpha, motion, timing, captions, rotation, crop, bytes and backup round trip.
4. On a real iPhone, confirm WhatsApp pasteboard/import/edit refresh behavior.
   No separate Business routing or in-app account targeting is promised on iOS.
5. Accessibility/large text/iPad, memory/scroll/conversion metrics, low storage,
   interrupted jobs, privacy/support/store materials and signing. The existing
   Android vector brand mark is exported as an opaque 1024px iOS app icon;
   regenerate with tools/export_apple_icon.py. The app privacy manifest declares
   file metadata access for private-container and explicitly selected files.
   Store privacy labels/provider retention still need a release review; the
   manifest is not a claim that the whole product passed a privacy audit.
6. Validate the opt-in private caption service after its production migration;
   add optional account-scoped cloud backup, durable video projects/manual mask
   correction and remaining Android editor parity.
   Current operator web APIs intentionally open under R40 are not an
   account-scoped consumer service. Do not wire private mobile jobs to them;
   use the boundary in [NATIVE_AI_HANDOFF.md](../docs/NATIVE_AI_HANDOFF.md).

## Historical 0.10.1 archive handoff — October 21

That 0.10.1 Apple run's native photo test exports a schema 5 MATURE/static pack.
That exact archive was explicitly imported on Android 0.11.2's signed candidate,
preserving its rating and WebP bytes through restart. All prior Android library
rows remained unchanged. Android creates new local IDs for a portable import;
archive IDs are not cross-device identities. This uses a generated test photo,
not a personal collection or a physical WhatsApp transfer.

Unsigned IPA SHA-256:
`e9cfca729ea2045ff130a4f6fb6e7d24b0038985ee0182c54bdad4e620652ef6`.
Its archive CRC, bundle ID, version and absence of provisioning were checked.
It cannot be installed on an iPhone until Apple signing is provided. Exact
source/artifact and real-media evidence is in
[the acceptance record](../docs/CREATOR_ACCEPTANCE_2026-10-21.md).

Current 0.10.2 unsigned IPA SHA-256:
`2bf6b011e9e7b6865effc7264417b4b40f1f0723c714af84c7d018d7527902fb`.
Its downloaded CI artifact, ZIP CRC, bundle ID, 0.10.2/build 14 metadata and
absence of provisioning verify. It still requires Apple signing for installation.
Local artifact: `work/apple-photo-archives-verified/work/StickerDock-iOS-unsigned.ipa`.
See [photo archive scope and tests](../docs/IPHONE_PHOTO_ARCHIVES.md).

No TestFlight/App Store account, Apple signing certificate, paid model/API or
cloud deployment was created. This public repository uses standard free macOS
CI runners; local Windows still cannot execute Apple builds. This is client source with explicit
unverified gates, not a claim that every requested feature is finished.
