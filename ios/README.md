# StickerDock for iPhone / iPad — implementation, not Apple acceptance

This native SwiftUI client uses the same Kotlin Multiplatform WebP validation/
container code as Android. iOS 17+ is the initial target. It is independent of
the official WhatsApp sample UI. The user has no Mac; this Windows checkout has
not compiled, signed or run the app. Do not call it installable, tested on iPhone
or App Store ready until the gates below pass.

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
  Generic WebP ZIPs deduplicate and split static/animated into 30-item packs.
  Generic PNG/JPEG/.wastickers and EXIF-based grouping are not yet iOS parity.
- Reviewed GENERAL catalog metadata/previews; full preview/download and save
  require explicit actions. Saved revisions deduplicate. Mature stays private.
- Native share sheet for pack/all-library ZIPs; share archives are private cache
  files cleaned when the share sheet closes.
- Documented WhatsApp local-only general-pasteboard payload, 60-second expiry,
  explicit single-pack URL handoff; opening is not installed-status confirmation.
- Bounded 8 MB thumbnail/animation buffers; lazy grids/discovery, first-frame
  thumbnails, detail-only animation paused offscreen/background, chunked bounded
  network reads and redirect rejection. These are source bounds, not measured
  phone performance claims.

## Build / verification on a Mac

From repository root (JDK 17 and Gradle 8.11.1):

```sh
gradle --no-daemon -PstickerCoreOnly=true :sticker-core:jvmTest :sticker-core:assembleStickerDockCoreReleaseXCFramework
brew install xcodegen
cd ios
xcodegen generate
xcodebuild -resolvePackageDependencies -project StickerDock.xcodeproj -scheme StickerDock
xcodebuild -project StickerDock.xcodeproj -scheme StickerDock -sdk iphoneos -destination 'generic/platform=iOS' build CODE_SIGNING_ALLOWED=NO
# Select an available simulator with xcrun simctl list devices available.
xcodebuild -project StickerDock.xcodeproj -scheme StickerDock -destination 'platform=iOS Simulator,name=<available iPhone>' test CODE_SIGNING_ALLOWED=NO
```

`.github/workflows/apple-core.yml` prepares the same unsigned device build,
native integration tests, navigation UI test and xcresult/artifacts on macos-15.
It runs manually or on relevant pull requests. Draft PR #18 has started its
first run; results are not yet accepted. It makes no App Store deployment.
SPM direct dependencies are pinned; record the resolved transitive versions from
the first successful Mac run. Xcode/Swift/Kotlin compatibility errors must be
fixed there before claiming source-build acceptance.

The generated Xcode project and Info.plist are ignored; `project.yml` is the
source of truth. Catalog base defaults to the same public read-only catalog as
Android and is a build setting. No Groq/Supabase service key or owner credential
belongs in this app.

## Acceptance still required

1. Apple framework, Swift typecheck/device and simulator builds pass. Windows
   Swift grammar parsing is only a syntax check, not an Apple build.
2. Native codec, archive safety, restart, failed-save and navigation tests pass.
3. Import real human/animal photos/GIF/video and Android-exported archives;
   verify alpha, motion, timing, captions, rotation, crop, bytes and backup round trip.
4. On a real iPhone, confirm WhatsApp pasteboard/import/edit refresh behavior.
   No separate Business routing or in-app account targeting is promised on iOS.
5. Accessibility/large text/iPad, memory/scroll/conversion metrics, low storage,
   interrupted jobs, app icons, privacy/support/store materials and signing.
6. Add opt-in private account/cloud adapter, native Groq assistant, durable
   video projects/manual mask correction and remaining Android editor parity.
   Existing owner-only web APIs must not be exposed to consumer apps as a shortcut.

No TestFlight/App Store account, Apple signing certificate, paid model/API or
cloud deployment was created. This public repository uses standard free macOS
CI runners; local Windows still cannot execute Apple builds. This is client source with explicit
unverified gates, not a claim that every requested feature is finished.
