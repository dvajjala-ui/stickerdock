# StickerDock — Android sticker manager

A Kotlin/Jetpack Compose Android app that keeps your own sticker library independently from WhatsApp and Sticker.ly. Source is actively developed; install the latest GitHub Actions debug APK for testing only.

## Current source features
- **Local sticker organization:** create/rename/delete categorized packs, import images, move/copy/remove stickers, SHA-256 deduplication, search packs, globally search stickers, edit per-sticker labels/tags, and filter by topic or Static/Animated.
- **Make and edit stickers:** choose photos directly from Library or Import to create packs automatically (30 per pack, static/animated separated). Static artwork supports rotate, mirror, zoom/crop, positioning and outlined top/bottom captions with a live preview. Saves retain sticker identity/labels/tags and update the WhatsApp pack version. Image decoding respects EXIF orientation. Automatic background removal and animated artwork editing remain pending.
- **Static and animated WebP:** WhatsApp-compliant separate pack types, 512×512 static conversion, animated frame/duration checks, animated previews (real-device QA pending).
- **Migrate stickers already stored on your Android device:** choose a WhatsApp / WhatsApp Business sticker folder using the Android system folder picker; recursively scan images, recover optional original pack names from WebP EXIF, retain source filenames as searchable sticker labels when available, split into 30-sticker packs, and deduplicate across previously imported items. Large scans show progress and keep a SQLite resume journal with Pause/Resume/Discard controls. This is not access to other apps' private databases.
- **Desktop ADB assistant:** `python tools/adb_sticker_export.py --account both` copies accessible WhatsApp sticker media folders from your own authorized phone and creates safe ZIP batches. No root, ADB requires your consent.
- **Batch ZIP ingestion:** select up to 100 ZIP files; supports images from directories, simple generic ZIPs, Sticker Maker `.wastickers` archives and StickerDock archives. Source filenames are retained as sticker labels where possible. A separate guide covers limitations of Sticker.ly personal migration.
- **Sticker.ly public links:** save/paste bulk pack URLs or share links into the app; follow back to Sticker.ly. **Does not mass-download or rehost Sticker.ly's catalog.**
- **Sharing:** send a StickerDock pack as a portable ZIP through Android share sheet; import archives and received images.
- **Two WhatsApp installations:** supports separate consumer WhatsApp and Business integration intents; Android's public API does not route to a specified account within one WhatsApp app.
- **Automatic new stickers:** the New tab loads reviewed GENERAL packs on open/resume and adds up to three unseen pack revisions per refresh to the library. Deleted revisions stay deleted until manually added again. Open a pack and add it to WhatsApp/Business. Unreviewed output stays private; MATURE packs stay local/private.
- **WhatsApp status and sync:** separate personal/Business added checks, Add/Open actions, preflight media checks and export-result errors. Pack edits update the provider version and notify observers; refresh timing is controlled by WhatsApp and needs phone verification.
- **Persistence:** non-destructive SQLite migrations through schema v7, app-private storage, metadata-preserving backup schema v5 with restore support for schemas 1–4, no user account or paid cloud required. Approved new packs are retrieved over HTTPS automatically on open/resume.

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
Production Supabase/Groq credentials and a real Factory smoke/resume path were
validated on October 13, 2026; the recurring 40/day Factory schedule remains
enabled by default in the current workflow unless `STICKER_FACTORY_ENABLED=false`.
A successful scheduled run alone does not prove that a normal-cooldown 40/40 day
has completed and been reviewed.
This version uses template illustrations; custom AI image rendering needs a
separate image provider.

The [free media input guide](docs/FREE_MEDIA_FACTORY.md) adds a scheduled CC0/
public-domain Commons download queue, optional authorized Pinterest-board inputs,
local reference files and actual GIF/MP4-to-captioned-animated-WebP conversion.
Uses local FFmpeg; no paid image/video provider is configured.

The [Pinterest and Creator Studio guide](docs/PINTEREST_AND_CREATOR_STUDIO.md)
adds a persistent six-hour Pinterest source sync (official authorized boards by
default; optional public-topic adapter), Supabase inbox provenance, and a Vercel
web-editor preview. Manual meme layout, PNG download and private factory upload
are implemented. The private Studio → Factory AI queue, atomic claim path and
Groq vision worker passed a real production smoke test. The Studio source now
supports queue/status/result polling and the Creator worker checks the queue every
10 minutes with a cheap idle preflight. Hosted Vercel deployment/environment
configuration is still unverified, and the private Studio entry is hidden on Android.
Pinterest media is private-reference-only unless a specific reusable rights
declaration is present.

The [automated content Factory guide](docs/AUTOMATED_CONTENT_FACTORY.md) adds a
once-daily source-linked Groq browser-search brief using the separate
`GROQ_TRENDS_API_KEY`, Admin category/custom-topic controls, category-specific
pack exports and automatic completed-output migration into private review drafts.
Recent RSS titles and owner topics remain an explicit fallback.

For hosted operation, use the [production deployment guide](docs/PRODUCTION_DEPLOYMENT.md):
GitHub Actions runs the Groq/Pillow/FFmpeg worker and Supabase stores private
inputs/state plus reviewed public packs. The Android catalog URL is a release
build setting; Groq and Supabase service keys never enter the APK.

## Factory admin portal

The new independent `/admin` web app manages Factory output migration, editable
pack drafts, sticker labels/tags/removal, review, publish/unpublish, inbox uploads,
AI requests, queue retry/cancel, scheduled generation settings and audit history.
It reuses the existing Vercel Python APIs and workers. Its clearly marked local
preview uses synthetic sample data; live mode requires configured server secrets
and the additive admin database migration.

Run `python tools/admin_dev_server.py` and open `http://127.0.0.1:4173/admin`.
See [admin operation and deployment](docs/ADMIN_PORTAL.md) and the
[long-term admin architecture plan](docs/ADMIN_CONTROL_PLANE_PLAN.md).

## Build
JDK 17, Android SDK 36, Gradle 8.11.1:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
python3 -m pip install -r tools/requirements-stickers.txt -r tools/requirements-pinterest.txt
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

The repository does not currently include a full Gradle wrapper binary; CI installs Gradle. Get the newest debug artifact under [GitHub Actions](https://github.com/dvajjala-ui/stickerdock/actions).

For local Python work, install both requirements files above. Windows workers use
OS file locks, the installed Arial Bold font, and the `tzdata` package. Set
`STICKERDOCK_FONT` to a readable TrueType font to override the worker default;
the daily generator also accepts `--font`. Video tests require local FFmpeg.

## Known limitations and cautions
- **No full Sticker.ly catalog mirror or automatic account-wide export.** Sticker.ly's public pages grant limited personal use, not blanket redistribution. Its own public user content cannot be silently copied into our community.
- A modified Sticker.ly APK isn't itself a full sticker catalog. We have not inspected yours; avoid trusting unverified mod APKs with accounts or personal data.
- Folder migration now journals progress in SQLite and can resume a persisted SAF folder after process death. Android picker/provider behavior and actual kill/restart recovery still need real-device QA, especially for enormous libraries.
- The Creator Studio AI queue/worker has passed a real authenticated Supabase/Groq vision smoke test and the browser flow is covered in Chromium. Hosted Vercel deployment and its `STUDIO_ACCESS_TOKEN`/Supabase environment still need verification before treating Studio as a remotely available user feature; the private Studio entry is hidden on Android.
- GIF/MP4/WebM/MOV conversion is implemented in the production worker; live phone
  import still needs device QA. Public packs require an explicit review workflow.
- WhatsApp sticker provider, large-library migration and account-specific install still require on-device QA. Build green is not release certification.

## Further documentation
[Implementation status](docs/IMPLEMENTATION_STATUS.md) · [Codex instructions](AGENTS.md) · [Codex handoff](docs/CODEX_HANDOFF.md) · [Product spec](docs/PRODUCT_SPEC.md) · [QA plan](docs/QA_MATRIX.md) · [Community moderation](docs/PUBLIC_SHARING_AND_SAFETY.md)

The project is independent and not endorsed by WhatsApp or Sticker.ly. We referenced but did **not** copy GPL-3.0 StickerVault implementation into this project's MIT code.
