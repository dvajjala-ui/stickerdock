# StickerDock shared core

Kotlin Multiplatform code used by the Android app and intended for the iPhone
client. This module is an incremental migration, not an implemented iOS app.

- `commonMain`: media constraints, edit recipes and WebP container logic.
- `jvm`: consumed directly by the existing Android app.
- `iosArm64` / `iosSimulatorArm64`: registered on macOS for Apple framework builds.

Run `gradle :sticker-core:jvmTest :app:testDebugUnitTest` on Windows/Linux/macOS.
On a Mac with Xcode, `gradle -PstickerCoreOnly=true :sticker-core:assembleStickerDockCoreReleaseXCFramework`
builds the Apple framework. Apple compilation has not been verified on Windows.
The conditional target registration avoids downloading an unusable Apple
toolchain on non-Apple hosts; it does not simulate an iOS build.
The core-only setting excludes the Android app so this Apple build does not
need an Android SDK. `.github/workflows/apple-core.yml` prepares a manual Mac
framework check; it has not been run and produces no installable iPhone app.

Keep Android media codecs, SAF, SQLite and ContentProvider in Android adapters.
iOS needs its own picker, media decoder/encoder, local store and explicit
WhatsApp pasteboard handoff. Shared container checks are not a pixel decoder.
Do not place credentials, app context, user files or cloud side effects here.
