# StickerDock — Android sticker manager

A Kotlin/Jetpack Compose Android app that keeps your own sticker library independently from WhatsApp and Sticker.ly. Source is actively developed; install the latest GitHub Actions debug APK for testing only.

## Current source features
- **Local sticker organization:** create/rename/delete packs, import images, move/copy/remove stickers, SHA-256 deduplication, search packs and filter Static/Animated.
- **Static and animated WebP:** WhatsApp-compliant separate pack types, 512×512 static conversion, animated frame/duration checks, animated previews (real-device QA pending).
- **Migrate stickers already stored on your Android device:** choose a WhatsApp / WhatsApp Business sticker folder using the Android system folder picker; recursively scan images, recover optional original pack names from WebP EXIF, split into 30-sticker packs, deduplicate across previously imported items. This is not access to other apps' private databases.
- **Desktop ADB assistant:** `python tools/adb_sticker_export.py --account both` copies accessible WhatsApp sticker media folders from your own authorized phone and creates safe ZIP batches. No root, ADB requires your consent.
- **Batch ZIP ingestion:** select up to 100 ZIP files; supports images from directories, simple generic ZIPs and StickerDock archives. A separate guide covers limitations of Sticker.ly personal migration.
- **Sticker.ly public links:** save/paste bulk pack URLs or share links into the app; follow back to Sticker.ly. **Does not mass-download or rehost Sticker.ly's catalog.**
- **Sharing:** send a StickerDock pack as a portable ZIP through Android share sheet; import archives and received images.
- **Two WhatsApp installations:** supports separate consumer WhatsApp and Business integration intents; Android's public API does not route to a specified account within one WhatsApp app.
- **Community:** read-only, reviewed GENERAL-content catalog from `catalog/packs.json`. No public packs are included yet, and no unmoderated uploads; MATURE labels stay local/private.
- **Persistence:** SQLite migrations, app-private storage, local backup/restore, no user account or paid cloud required. Public gallery is networked and opt-in.

## Migrate your phone's stickers
[Detailed phone/ADB/Sticker.ly guide](docs/PHONE_MIGRATION.md) · [Sticker.ly legal export limitations](docs/STICKERLY_MIGRATION.md)

Android: **Import phone sticker folder** → select `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers` or your WhatsApp Business equivalent. Note that sent/received stickers in this folder are not necessarily every installed or browsed Sticker.ly sticker.

Windows/macOS/Linux with platform-tools and Python:

```sh
python tools/adb_sticker_export.py --account both --output StickerDock-Phone-Export
```

Then transfer files from `StickerDock-Phone-Export/sticker_zips/` to the phone and choose **Import sticker ZIP** (supports batch selection).

## Daily AI stickers

The optional [Groq daily generator](docs/DAILY_AI_STICKERS.md) targets **40 stickers
per day in batches of five**, spaced three hours apart. It turns recent public
RSS titles or supplied references into original Hinglish caption stickers, draws
reaction artwork locally, and exports StickerDock ZIPs. Rotates topics, supports
supplied-photo memes with a configured vision model and animated pulse stickers,
and separates static/animated packs (40 split into two packs of 20).
Includes saved progress, duplicate checks, request caps and rate-limit cooldowns.
Requires a privately configured `GROQ_API_KEY` and a computer/server scheduler;
live generation is not activated yet. This version uses template illustrations;
custom AI image rendering needs a separate image provider.

The [free media input guide](docs/FREE_MEDIA_FACTORY.md) adds a scheduled CC0/
public-domain Commons download queue, optional authorized Pinterest-board inputs,
local reference files and actual GIF/MP4-to-captioned-animated-WebP conversion.
Uses local FFmpeg; no paid image/video provider is configured.

For hosted operation, use the [production deployment guide](docs/PRODUCTION_DEPLOYMENT.md):
GitHub Actions runs the scheduled Groq/Pillow/FFmpeg worker and Supabase stores
private inputs/state plus reviewed public packs. The Android catalog URL is a
release build setting; Groq and Supabase service keys never enter the APK.

## Build
JDK 17, Android SDK 35, Gradle 8.9:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
python3 -m pip install -r tools/requirements-stickers.txt
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

The repository does not currently include a full Gradle wrapper binary; CI installs Gradle. Get the newest debug artifact under [GitHub Actions](https://github.com/dvajjala-ui/stickerdock/actions).

## Known limitations and cautions
- **No full Sticker.ly catalog mirror or automatic account-wide export.** Sticker.ly's public pages grant limited personal use, not blanket redistribution. Its own public user content cannot be silently copied into our community.
- A modified Sticker.ly APK isn't itself a full sticker catalog. We have not inspected yours; avoid trusting unverified mod APKs with accounts or personal data.
- Folder migration is best-effort and isn't yet process-death/resume-safe for enormous libraries. Android system picker visibility varies with OS/version.
- GIF/MP4/WebM/MOV conversion is implemented in the production worker; live phone
  import still needs device QA. Public packs require an explicit review workflow.
- WhatsApp sticker provider, large-library migration and account-specific install still require on-device QA. Build green is not release certification.

## Further documentation
[Codex instructions](AGENTS.md) · [Codex handoff](docs/CODEX_HANDOFF.md) · [Product spec](docs/PRODUCT_SPEC.md) · [QA plan](docs/QA_MATRIX.md) · [Community moderation](docs/PUBLIC_SHARING_AND_SAFETY.md)

The project is independent and not endorsed by WhatsApp or Sticker.ly. We referenced but did **not** copy GPL-3.0 StickerVault implementation into this project's MIT code.
