# StickerDock — private sticker creator and library

| Get StickerDock | Link |
| --- | --- |
| Latest signed Android APK | [Download StickerDock.apk](https://github.com/dvajjala-ui/stickerdock/releases/latest/download/StickerDock.apk) |
| Release notes and evidence | [Latest Android release](https://github.com/dvajjala-ui/stickerdock/releases/latest) · [SHA-256 checksums](https://github.com/dvajjala-ui/stickerdock/releases/latest/download/SHA256SUMS.txt) · [Version/source manifest](https://github.com/dvajjala-ui/stickerdock/releases/latest/download/release.json) |
| Admin website | [stickerdock.vercel.app/admin](https://stickerdock.vercel.app/admin) |
| Creator Studio | [stickerdock.vercel.app/studio](https://stickerdock.vercel.app/studio) |

Admin and Creator Studio now open directly without a password or account gate,
as explicitly requested on October 19. Their workspace data and controls are
accessible to anyone with the URL. Existing database content is retained;
phone cloud backups still require account authentication. See [the access model](docs/OPEN_ADMIN.md).

October 22: [the product audit and implementation backlog](docs/PRODUCT_AUDIT_AND_BACKLOG_2026-10-22.md)
records 90 prioritized defects, gaps/extensions, acceptance checks and decisions.
Live generation now delivers completed partial batches to private Admin review
drafts; publication remains a separate human review step. The October 22 five-
sticker draft was inspected and kept private because its images repeat. The
public catalog now has four reviewed packs, including two new three-sticker
real-photo animal packs and a three-sticker animated animal-meme pack with independently checked public ZIPs and credited
sources. Their catalog cards have no artwork cover until the
additive public-bucket WebP MIME migration is applied; opening the pack loads
the ZIP artwork on demand. The plan connects automatic content, native personal AI, website uploads,
real/animated media, iPhone completion and coherent navigation. It is an audit,
not a claim that those pending features are implemented.

Pinterest app 1624615 now has a verified read-only OAuth connection: Admin can
browse the connected account's public boards live. Pin images are never imported
into distributable stickers. Admin Media shows source previews and offers a direct
private pack draft from a selected image. See [connection limits](docs/PINTEREST_RECONNECTION.md)
and [the execution tracker](docs/PRODUCT_EXECUTION_2026-10-22.md).

These links stay current with successful releases. Android changes merged into
`main` build and verify a signed APK/AAB, then run that exact APK on an Android 15
emulator before advancing the download. Failed builds keep the previous release.
The admin website follows the existing Vercel production deployment from `main`.
See [installation, update and release verification](docs/ANDROID_RELEASES.md).

October 23: **Android 0.11.4 (build 19)** is [published](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.4)
after [signed APK and Android 15 emulator acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39913314036).
The exact public APK was downloaded again and its SHA-256 matched the release
manifest and acceptance record. The Text tool has an opt-in personal AI caption
flow, but its server-side flag is **off** pending the private database migration
and account/device checks. Admin draft review now supports redrawing a static
photo sticker's visible caption from an independently reusable source; this
later website change passed a private-draft production edit: one rendered
sticker changed, four stayed byte-identical, and the pack remained private.
The review UI now includes a full-size artwork preview. See
[release evidence](docs/QA_MATRIX.md). The latest iPhone 0.10.5 source also
passes [unsigned device and simulator CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39913799107),
but still needs Apple signing and physical iPhone/WhatsApp testing.
Admin pack cards now load lazy, checksum-bound covers composed from actual
GENERAL sticker artwork; the shared workspace keeps mature covers hidden.
The hosted desktop and mobile Admin views display them. See the
[implementation tracker](docs/PRODUCT_EXECUTION_2026-10-22.md) for the exact
verification and remaining product gaps.

October 21: **Android 0.11.2 (build 17)** was [published](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.2)
after [signed acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39605443793).
Share one video/GIF into the animated creator, trim/caption, preview and save
explicitly. Real human-reaction media and a fresh iPhone archive round trip also
pass on the signed branch candidate. See [exact artifact evidence](docs/QA_MATRIX.md)
and [real-media/Apple checks and limits](docs/CREATOR_ACCEPTANCE_2026-10-21.md).
iPhone 0.10.2 adds PNG/JPEG photo ZIPs with alpha/EXIF handling and passes 19 native
tests plus 1 UI test. It remains unsigned; see [iPhone archive scope](docs/IPHONE_PHOTO_ARCHIVES.md).

October 21: **Android 0.11.1 (build 16)** is [published](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.1)
after [signed acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39572989873).
It adds private device-local Favorites and keeps Copy/Move reachable in long
libraries and large text. Search/restart/move/Delete-Undo and the existing 10k
creation/backup journey pass. See [exact evidence and limitations](docs/QA_MATRIX.md)
and [Favorites behavior](docs/FAVORITES.md). The older release records below are history.

October 19: **Android 0.11.0 (build 15)** is [published](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.0) after
[signed-APK acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39208384312); [PR #21](https://github.com/dvajjala-ui/stickerdock/pull/21) is merged. It adds larger pack
previews, pack-grouped search, explicit photo destinations, simpler discovery and
contextual WhatsApp actions. The continued design pass adds distinct pack cards,
photo/video creation cards, focused editor tools, optional on-device cutouts,
erase/restore brushes, public pack codes and explicit opening of received pack
files, editable Android backups and readable large-text import actions. The
download above advances only after the signed release passes its tests and
on-screen acceptance. Read the [product decisions and research](docs/PRODUCT_REBUILD_2026-10-18.md),
[release verification](docs/QA_MATRIX.md) and [optional original-art worker](docs/ORIGINAL_ARTWORK_WORKER.md).
The [parity audit](docs/STICKERLY_PARITY_AUDIT_2026-10-18.md) tracks every requirement;
the app is not a complete Sticker.ly replacement yet.
See the [Android design review](docs/UI_REVIEW_2026-10-19.md) for layout decisions
and actual screenshot findings.
Original image generation is prepared but disabled and not live-validated.

Android and iPhone clients keep your sticker library independently from WhatsApp
and Sticker.ly. Android uses Kotlin/Compose; the native SwiftUI iPhone client
shares Kotlin Multiplatform media rules/containers. Both remain testing candidates;
physical WhatsApp acceptance and store distribution are separate gates.

October 17: Android **0.10.2** adds bounded grid posters for animated stickers,
detail playback that pauses in the background, streaming GIF/video compression,
low-storage save protection and wrapping large-text pack actions. The Factory
uses strict response schemas on its configured Groq models and persists bounded
retries for invalid responses. Release checks include a synthetic 10,000-sticker
library, scrolling/memory, 360dp/200% text, restart preservation and real MP4
preview on the signed APK; source-bound results accompany each published release.
Physical WhatsApp/Business acceptance remains a separate gate.
Published verification: **99 Android + 12 shared tests, 189 backend tests,
release lint and exact signed-APK emulator acceptance pass** in
[the 0.10.2 release run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39037711698).
The live Factory also [generated and persisted 5 stickers](https://github.com/dvajjala-ui/stickerdock/actions/runs/39037827620)
after the response fix; a complete normal-cooldown 40/40 day remains unverified.
The [iPhone implementation](ios/README.md) is merged and its latest
[Apple build and simulator verification passes](https://github.com/dvajjala-ui/stickerdock/actions/runs/39606440645)
with 19 native integration tests and 1 navigation UI test, zero failures or skips.
It remains unsigned and requires Apple signing for installation.
The archive path preserves private ratings and exact media across platforms;
invalid manifest backups fail explicitly instead of falling back to image imports.
It is **not an App Store/TestFlight release or complete Android feature parity**.
See [the requirements audit](docs/REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md),
[architecture](docs/CROSS_PLATFORM_AND_PRODUCT_DIRECTION.md) and
[artifact/evidence record](docs/RECOVERY_PROGRESS.md).

## Current source features
- **Local sticker organization:** create/rename/delete categorized packs, import images, move/copy/remove stickers, SHA-256 deduplication, search packs, globally search stickers, edit per-sticker labels/tags, and filter by topic or Static/Animated.
- **Make and edit stickers:** choose photos, name a new pack or select an existing destination, then explicitly save (30 per pack, static/animated separated). Static artwork supports rotate, mirror, zoom/crop, positioning, outlined captions, manual tracing, optional on-device cutout and erase/restore brushes, with live preview and bounded undo/redo. Editable projects retain the 512px starting sticker, masks and recipes; Reset restores the starting bytes. Drafts autosave locally and failed saves retain edits for retry. An explicit Android editable backup preserves these projects; portable ZIP sharing and cloud copies keep saved stickers only. Full-resolution photos, editable video projects and native AI caption suggestions remain pending. See [editable artwork](docs/LOCAL_ARTWORK_PROJECTS.md) and [backup scope and verification](docs/EDITABLE_BACKUPS.md).
- **Static and animated WebP:** WhatsApp-compliant separate pack types, 512×512 static conversion, animated frame/duration checks, animated previews (real-device QA pending).
- **Native animated creator (0.9.0):** choose a video or GIF, select a short section, framing and motion rate, add an outlined caption, preview the moving result locally and explicitly save to an animated pack. No paid provider or AI key is required for conversion. GIF decode budgets, preview invalidation and safe save guards are included. The signed local build passes real WebM/MP4/GIF and rotated-portrait emulator checks; physical WhatsApp/iPhone remain unverified. Exact artifacts and limits are tracked in [recovery progress](docs/RECOVERY_PROGRESS.md).
- **Migrate stickers already stored on your Android device:** choose a WhatsApp / WhatsApp Business sticker folder using the Android system folder picker; recursively scan images, recover optional original pack names from WebP EXIF, retain source filenames as searchable sticker labels when available, split into 30-sticker packs, and deduplicate across previously imported items. Large scans show progress and keep a SQLite resume journal with Pause/Resume/Discard controls. This is not access to other apps' private databases.
- **Desktop ADB assistant:** `python tools/adb_sticker_export.py --account both` copies accessible WhatsApp sticker media folders from your own authorized phone and creates safe ZIP batches. No root, ADB requires your consent.
- **Batch ZIP ingestion:** select up to 100 ZIP files; supports images from directories, simple generic ZIPs, Sticker Maker `.wastickers` archives and StickerDock archives. Source filenames are retained as sticker labels where possible. A separate guide covers limitations of Sticker.ly personal migration.
- **Sticker.ly public links:** save/paste bulk pack URLs or share links into the app; follow back to Sticker.ly. **Does not mass-download or rehost Sticker.ly's catalog.**
- **Sharing:** send a StickerDock pack as a portable ZIP through Android share sheet; import archives and received images.
- **Two WhatsApp installations:** supports separate consumer WhatsApp and Business integration intents; Android's public API does not route to a specified account within one WhatsApp app.
- **Discover and save:** reviewed GENERAL packs refresh on open/resume without adding anything to your library. Search packs/creators/topics, filter Static/Animated, preview artwork, then explicitly **Save to library**. New publications include small checksum-verified artwork previews; older packs can be previewed on request without saving. Add to WhatsApp/Business remains a separate action. MATURE packs stay local/private.
- **WhatsApp status and sync:** separate personal/Business added checks, Add/Open actions, preflight media checks and export-result errors. Pack edits update the provider version and notify observers; refresh timing is controlled by WhatsApp and needs phone verification.
- **Persistence:** non-destructive SQLite migrations through schema v8, app-private storage, metadata-preserving backup schema v5 with restore support for schemas 1–4. V8 adds private editing recipes/drafts without changing the portable ZIP contract. Local features require no account. Catalog metadata and small public previews use HTTPS; complete packs are saved only on request.

## Migrate your phone's stickers
[Detailed phone/ADB/Sticker.ly guide](docs/PHONE_MIGRATION.md) · [Sticker.ly legal export limitations](docs/STICKERLY_MIGRATION.md)

Android: **Import phone sticker folder** → select `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers` or your WhatsApp Business equivalent. Note that sent/received stickers in this folder are not necessarily every installed or browsed Sticker.ly sticker.

Windows/macOS/Linux with platform-tools and Python:

```sh
python tools/adb_sticker_export.py --account both --output StickerDock-Phone-Export
```

Then transfer files from `StickerDock-Phone-Export/sticker_zips/` to the phone and choose **Import sticker ZIP** (supports batch selection).

## Daily AI stickers

October 15 update: production generation now requires real independent photos or
clips and a vision model, rather than silently substituting emoji-face templates.
Legacy illustration packs remain historical samples. Pinterest integration is
being replaced with official live reference browsing, not Pin downloading or
conversion. See [current integration status](docs/PINTEREST_RECONNECTION.md).
Phone imports stay on-device by default. Optional account-based private sync needs explicit enablement; mature packs remain offline. See [cloud library](docs/CLOUD_LIBRARY.md).

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
Legacy illustration modes remain available to development fixtures. Production
real-photo/clip modes use source media with outlined captions; generating new
image pixels from a prompt would require a separate image model. Groq provides
the research/vision/caption assistance. See the [real-media creator direction](docs/REAL_MEDIA_CREATOR_PLAN_2026-10-16.md).

The [free media input guide](docs/FREE_MEDIA_FACTORY.md) adds a scheduled CC0/
public-domain Commons download queue, optional authorized Pinterest-board inputs,
local reference files and actual GIF/MP4-to-captioned-animated-WebP conversion.
Uses local FFmpeg; no paid image/video provider is configured.

The [Pinterest and Creator Studio guide](docs/PINTEREST_AND_CREATOR_STUDIO.md)
adds a persistent six-hour Pinterest source sync (official authorized boards by
default; optional public-topic adapter), Supabase inbox provenance, and a Vercel
web-editor preview. Manual meme layout, PNG download and private factory upload
are implemented. The Studio → Factory AI queue, atomic claim path and
Groq vision worker passed a real production smoke test. The Studio source now
supports queue/status/result polling and the Creator worker checks the queue every
10 minutes with a cheap idle preflight. Admin/Studio are deployed and open directly
under the requested [R40 access model](docs/OPEN_ADMIN.md); their workspace is
shared by visitors with the URL. Studio stays outside consumer Android navigation.
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

Account email setup: [Gmail Apps Script sender and Supabase integration](docs/GMAIL_ACCOUNT_EMAIL.md).
The send-only Gmail script is connected to Supabase. Live test messages reach
the owner inbox, signed delivery/retry checks pass, and unsigned mail requests
are rejected. Phone-account password entry and real account-link acceptance remain
with the account holder.

The new independent `/admin` web app manages Factory output migration, editable
pack drafts, sticker labels/tags/removal, review, publish/unpublish, inbox uploads,
AI requests, queue retry/cancel, scheduled generation settings and audit history.
It reuses the existing Vercel Python APIs and workers. It reads live data directly;
the server needs the existing Supabase configuration and additive admin schema.
The fixture browser tests intercept requests locally and never modify production.

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
- Creator Studio has historical live Supabase/Groq evidence and current deployed browser/API checks. R40 removed its owner token/login requirement; workspace controls are accessible to visitors with the URL. Native consumer AI still needs account-scoped jobs and shared provider budgets; see [the native AI handoff](docs/NATIVE_AI_HANDOFF.md).
- GIF/MP4/WebM/MOV conversion is implemented in the production worker; live phone
  import still needs device QA. Public packs require an explicit review workflow.
- WhatsApp sticker provider, large-library migration and account-specific install still require on-device QA. Build green is not release certification.

## Further documentation
[Implementation status](docs/IMPLEMENTATION_STATUS.md) · [Codex instructions](AGENTS.md) · [Codex handoff](docs/CODEX_HANDOFF.md) · [Product spec](docs/PRODUCT_SPEC.md) · [QA plan](docs/QA_MATRIX.md) · [Community moderation](docs/PUBLIC_SHARING_AND_SAFETY.md)

The project is independent and not endorsed by WhatsApp or Sticker.ly. We referenced but did **not** copy GPL-3.0 StickerVault implementation into this project's MIT code.
