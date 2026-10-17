# StickerDock product recovery and expansion plan

Date: October 15, 2026. Status: research baseline; implementation is now tracked in [RECOVERY_PROGRESS.md](RECOVERY_PROGRESS.md). The full plan is not implemented or release-certified.

The objective is a professional sticker and meme application for Android and iPhone: discover useful content, make genuinely good stickers, organize a private library, and reliably use/share packs. Reassess every user journey. Preserve personal data and proven infrastructure while replacing behavior and components that fail acceptance.

This request changes the previous automatic-delivery requirement. Opening/resuming the app must not add packs to the personal library. Browsing, saving a pack, private backup, public publication, and adding to WhatsApp are five distinct actions.

## 1. Findings verified in this investigation

Evidence labels: LIVE means read directly during this investigation; CODE means inspected source, not proven runtime behavior; HISTORICAL means repository documentation describes an earlier check; PROPOSED means future work; UNVERIFIED means no suitable current evidence.

| Finding | Evidence | Consequence |
| --- | --- | --- |
| Local HEAD and fetched origin/main are c5871d77f0c7d99eda7f79300a73d8c7e095a36e | Git fetch and rev-parse | A known baseline exists. Eight modified tracked files and two new cloud-sync files were already present; this audit leaves them intact. |
| Opening/resuming adds up to three unseen public pack revisions | CODE: `StickerDockPolishedActivity.kt`, `refreshFactory()`, launch and lifecycle callbacks | The user's complaint has a direct code explanation. Remove this automatic import path; retain explicit download. |
| Individual automatic import failures are swallowed by `runCatching` without per-pack error reporting | CODE: same function | Catalog refresh can appear successful even if a download failed. Separate catalog and import states. |
| Discover/New is text cards, categories and buttons, without sticker artwork previews | CODE: `StickerDockScreens.kt`, `FactoryScreen`; `CommunityCatalog.Listing` has no preview manifest | This is a substantial product gap, not just colors or spacing. |
| Current Android launcher has no connected AI creator journey | CODE: manifest points to `StickerDockPolishedActivity`; its routes cover Library, Import and catalog | The older `StickerDockActivity` contains “Coming soon,” but is not the manifest launcher. Do not confuse legacy UI with the shipped route. |
| Latest scheduled Factory run failed with `Groq HTTP 429; cooldown saved` | LIVE: [Factory run 38727851141](https://github.com/dvajjala-ui/stickerdock/actions/runs/38727851141/job/116398029235#step:6:32) | A confirmed provider rate-limit failure, not proof all generation code is missing. The precise exhausted quota was not established from the redacted log. |
| Code already persists cooldown and pending work | CODE: `tools/daily_stickers.py` | Improve accounting, retry scheduling and visibility rather than claim backoff is wholly absent. |
| Interactive Creator worker runs every ten minutes; Factory every three hours | CODE: workflow YAML files | Neither schedule provides a responsive consumer creation experience. |
| Latest inspected Creator workflow is green | LIVE: [run 38737727690](https://github.com/dvajjala-ui/stickerdock/actions/runs/38737727690) | This alone does not prove a new sticker was generated; an empty-queue preflight can succeed. |
| Last inspected Android debug CI succeeded at d9cba2b | LIVE: [run 38717657942](https://github.com/dvajjala-ui/stickerdock/actions/runs/38717657942) | Build/test evidence for that SHA, not current dirty files or physical-phone acceptance. No new test suite was run for this planning pass. |
| Public production `catalog/packs.json` contains one listing, “Desi photo reactions” | LIVE: public HTTPS read; its SHA is a2d163ee125c22bd94c8fa6bee28aeeadfd7a204873c430833aa3c2975657d54 | A one-pack catalog cannot deliver the requested discovery experience. Earlier docs record four stickers; this pass did not download/count the ZIP. |
| Phone cloud upload is a different path and defaults off | CODE: `CloudLibrary.enabled` requires sign-in and an enabled preference, default false | The launch complaint most closely matches automatic catalog downloading. An already enabled account can sync; audit its consent and scope separately. Do not claim that no user data can upload. |
| Admin is reachable and opens normal email/password sign-in | LIVE: browser inspection of `/admin`; CODE: `api/session.py` | Authentication exists; the owner password is not a built-in shared password. |
| No ADB phone or emulator connected | LIVE: `adb devices -l` empty | Neither StickerDock nor Sticker.ly was installed/exercised on a phone in this pass. Native visual and WhatsApp findings remain unverified. |
| No iPhone application project found in inspected repository files | CODE: Android Gradle/Compose project plus web admin/Studio | iPhone requires a real implementation and device testing, not a responsive website label. |

The handoff/README/QA files contain superseded statements about deployed versus pending features, old token login, catalog publication and cloud availability. Maintain the history, but create one current acceptance table. A completed code item must never silently become a completed device item.

### Admin access

- Portal: https://stickerdock.vercel.app/admin
- Configured owner default in `api/session.py`: `dvjjala@gmail.com` (server environment can override it).
- Password: unknown to this audit. No default password exists in the inspected authentication flow. The latest handoff says the agent did not create/change the owner's password.
- Recovery: https://stickerdock.vercel.app/account. The latest handoff says production SMTP was deferred; do not promise email arrival until tested.
- Make verified owner login, recovery delivery, refresh, logout/revocation and MFA an early operational milestone. Never replace the password with a privileged service key or expose the operational token in UI/chat.

## 2. What “like Sticker.ly” should mean

Match the useful workflows and content quality with an independent brand and implementation. Sticker.ly's official site and store listing describe photo cutout, captions, animated creation, discovery, creator following and shareable packs. These are a valid feature benchmark. This investigation reviewed the public website visually and read official listings/FAQs; it did not complete an authenticated mobile walkthrough. [Sticker.ly website](https://sticker.ly/), [official App Store listing](https://apps.apple.com/us/app/sticker-ly-wasticker/id1458740001), [animated creation guide](https://sticker.ly/faq/en/FAQs%20aaee0686e0434de48956d7ba663edc14/Learn%20how%20to%20create%20animated%20stickers%20on%20Sticker%20l%204ca5a909ea8347ee9e0b27133ad59696.html).

I did not find a verified official, reusable Sticker.ly application codebase. The public projects found include an explicitly unofficial API wrapper and an archived downloader; neither is the app's source or a sound production dependency. A supplied APK could support bounded static inspection of permissions, dependencies and bundled assets, but would not supply server code or the online catalog. Use the official store app for behavioral comparison on a test device; do not route production through undocumented/private endpoints. [Unofficial wrapper](https://github.com/theabbie/sticker.ly), [downloader](https://github.com/ciwga/Stickerly).

Public availability is not permission to republish creators' packs. Sticker.ly's terms distinguish personal use/export from redistribution. Build our own commissioned, licensed, original and explicitly submitted catalog. [Sticker.ly terms](https://sticker.ly/terms-of-use).

| User journey | Target behavior | Completion evidence |
| --- | --- | --- |
| First launch | Useful empty state: Create, Import, or Explore; no surprise library changes; guest local use | Fresh-install screen recording and network/file-write checks |
| Find a reaction | Visual packs/stickers, language/emotion/category search, static/animated filters, favorites | Relevant results for a fixed English/Hindi/Hinglish query set |
| Make from photo | Pick → automatic cutout or manual erase → caption/border → preview → save | Actual output readable at chat size and usable in WhatsApp |
| Make a meme | Real photo/clip, purposeful crop, editable Hinglish caption, optional cutout | Owner-reviewed examples that meet a defined visual rubric |
| Make animation | Select clip/GIF/WebP → trim/crop/speed/text → loop preview → validate → save | Accepted animated pack on both supported mobile platforms |
| Use a pack | Preview → explicitly save → explicitly add to WhatsApp → confirm target result | Real consumer/Business device evidence, cancellations included |
| Manage a large library | Fast grid, tags/emoji, folders/collections, multiselect, sort, dedup and undo | 10,000-sticker fixture plus real authorized phone library |
| Change phone | Export/import or optional private backup → verified restore | Checksums and metadata preserved across two devices |
| Share | Portable ZIP locally; controlled HTTPS pack link/QR for approved hosted sharing | Recipient on Android and iPhone opens/imports correct revision |
| Create with AI | Visible queued/running/waiting/retry/result states; editable output | Request-to-download trace with real result and controlled failure tests |
| Publish | Separate submission/review/publication; report/block and takedown | Isolation and moderation tests; no private content leaks |

## 3. Product and visual design

Proposed navigation: **Discover / Create / Library / You**. Search belongs in Discover and Library. Import lives inside Create and Library; Factory operations remain in Admin.

| Screen | Layout and interactions |
| --- | --- |
| Discover | Compact search header, language/category chips, artwork-led pack cards with 4–6 real previews, creator, sticker count and animated badge. Preview opens before saving. Separate New, Popular and editorial collections; never fabricate popularity. |
| Create | Three clear entry points: Photo, Video/GIF, Meme. AI becomes a real mode only after its end-to-end gate passes. Permission and network needs appear at the action that needs them. |
| Editor | Large checkerboard canvas; bottom tools for cutout, erase/restore, crop, text, outline and effects; undo/redo; accessible controls; draft autosave; visible save/export progress. Preserve editable originals separately from exported WebP. |
| Library | Artwork grid with Packs/Stickers views, favorites, recent, downloads, collections and private-local labels. Persistent filters, multiselect and progress for lengthy operations. Avoid full-width explanatory cards consuming the first screen. |
| Pack detail | Name/creator and grid first; one clear add-to-WhatsApp action with available target chooser, save state, overflow for edits/share/delete. Explain invalid pack size before launching WhatsApp. |
| You/settings | Optional account, selected backup scope, transfer, storage usage, privacy, accessibility and help. Account creation must not block offline creation. |
| Admin | Sign in → actionable health and failed jobs → visual media/drafts → edit/review/publish. Separate generated, reviewed, published, downloaded and exported counts. |

Create tokens for spacing, typography, colors, corners and icon style. Use one main accent on neutral surfaces, consistent real icons, generous sticker space, dark-mode/chat-background previews, accessible contrast and native minimum touch targets. Support large font settings, screen readers, reduced motion, keyboard avoidance and edge-to-edge insets.

First design deliverable: fully populated and empty-state prototypes for Discover, Library, Pack detail and Editor, plus loading/offline/failure states. Use rights-cleared real sample stickers. The user must be able to judge the actual visual direction before it spreads across the app. Screenshot comparisons are a design gate, not proof buttons work.

## 4. Content and API research: what to use

The production foundation should be our own curated content library. External APIs are optional adapters. Losing Pinterest or one GIF supplier must not break creation, search over downloaded packs or WhatsApp export.

| Source | Suitable role | Decision and limitation |
| --- | --- | --- |
| User-selected photos/videos | Private creation; public only with a separate rights/submission flow | Primary input. Local first. Cloud processing needs clear per-action consent. |
| Commissioned creators/original media | Recognizable reaction packs and culturally relevant memes | Primary catalog strategy. Obtain rights covering edits, distribution, attribution and revocation handling. |
| Wikimedia Commons | Licensed photographs and clips | First discovery adapter. Verify each file's actual license/author/source; favor CC0/public-domain for the first collection. Keep attribution when required. API metadata is useful, not a guarantee of all underlying rights. [Imageinfo API](https://www.mediawiki.org/wiki/API:Imageinfo) |
| Openverse | Search across openly licensed media | Useful second adapter with explicit license filters and source-page verification. Not a ready-made meme catalog. [API documentation](https://docs.openverse.org/api/) |
| GIPHY | Optional branded GIF/sticker search experience | Production access/pricing and specific use approval required. Standard integration prohibits caching media and mixing provider content in one grid; persistent editable WhatsApp packs are not automatically authorized. [API documentation](https://developers.giphy.com/docs/api/) |
| KLIPY | Candidate GIF/sticker search partner | Its terms also restrict content storage. Seek explicit permission for export/rehosting/editing before adoption. Do not present it as an unrestricted free catalog. [API terms](https://klipy.com/support/api-terms), [documentation](https://docs.klipy.com/) |
| Pinterest | Optional reference browsing after approval | Not a production sticker-material source. Guidelines restrict retention, scraping and distributable derivatives of Pins. Current approval status is historical pending, not freshly checked in its dashboard. [Developer guidelines](https://policy.pinterest.com/en/developer-guidelines) |
| Tenor | None for a new external integration | Reject. Official notice says the API was decommissioned June 30, 2026. [Google notice](https://support.google.com/tenor/answer/10455265?hl=en_SG) |
| Pexels/other stock providers | Potential individual creative inputs | Not interchangeable with CC0. Pexels restricts standalone redistribution and large-scale copying. Review the exact transformation/distribution use before integrating. [Pexels guidance](https://help.pexels.com/hc/en-us/articles/900005880463-What-are-the-Terms-and-Conditions) |
| Unofficial Pinterest/Sticker.ly download APIs | None in the release path | Availability, authorization and redistribution rights are unproven. Avoid dependency on scraping or bypass services. |

Initial editorial proposal: 12 coherent packs of 12–20 stickers, covering everyday Hinglish reactions, work/college, friends, family, celebrations, animals, cricket reactions and original entertainment humor. This is a pilot target, not content already available. Famous movie stills or celebrity reactions require appropriate rights; API access alone does not solve that.

Each publishable asset should have source ID/URL, author, license version/evidence, permitted transformations, redistribution/attribution requirements, content rating, review status and removal history. Unknown provenance stays private. Do not send imported personal collections into training or trend ingestion.

Quality rubric: readable at actual chat size; subject clearly visible; good cutout edges; no clipped captions; natural language; useful emotion; distinct from neighboring outputs; clean loop; appropriate context; source rights satisfied. Reject poor outputs rather than fill a “40 per day” counter.

## 5. Architecture decisions and reusable tools

### Mobile strategy

Recommended baseline: repair Android in Kotlin/Compose and build an iPhone client with native Apple interfaces against the same versioned backend and pack contract. Keep the Android application ID, signing identity, schema migrations and backups. Extract business logic from the large activity into repositories/use cases and screen state holders; Android's architecture guidance supports this separation. [Android architecture](https://developer.android.com/topic/architecture).

| Option | Benefits | Costs | Decision |
| --- | --- | --- | --- |
| Kotlin Android + native iPhone client | Reuses existing Android integrations; strongest control over each platform's picker, media and messaging flow | Two presentation implementations and device suites | Recommended starting point |
| Kotlin Multiplatform shared domain logic | Could share validation, metadata, API models and sync rules while keeping native UI | Existing Android Context/SQLite/Bitmap code is not portable automatically | Time-box a small validation/API-model spike after contracts stabilize; adopt only if it reduces real duplication. [JetBrains guidance](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/) |
| Flutter rewrite | Shared presentation code across platforms | Requires migration and native bridges for media/WhatsApp; does not prove reliability | Viable alternative, not the default recovery choice. Compare only with a working export/editor prototype. [Flutter platform integration](https://docs.flutter.dev/platform-integration) |
| Website/PWA as mobile app | Useful public pack landing pages and admin access | Does not replace native WhatsApp integration and media workflows | Companion only |

iPhone feasibility must be checked early with the official WhatsApp sample and a real device, before committing to full UI parity. A Mac/Xcode signing environment and iPhone are necessary project resources. Android SAF migration cannot be promised on iPhone; use Files/Photos imports, ZIPs and optional account transfer. WhatsApp Business targeting on iPhone remains a separate verification item. Consider iMessage extension after the main iPhone creation/export flow works.

### Media components

| Component | Reuse/build choice | Gate |
| --- | --- | --- |
| WhatsApp integration | Official Android/iOS samples and documented protocols | [Official repository](https://github.com/WhatsApp/stickers); actual install/send on supported versions |
| Android automatic cutout | Evaluate ML Kit subject segmentation first; manual erase/restore always available | API is documented as beta; measure animals, hair, objects, model availability and offline behavior. [ML Kit](https://developers.google.com/ml-kit/vision/subject-segmentation/android) |
| iPhone cutout | Apple Vision foreground-instance masks | Validate supported OS/device range and fallback. [Apple sample](https://developer.apple.com/documentation/vision/applying-visual-effects-to-foreground-subjects) |
| Server cutout fallback | Evaluate rembg/ONNX on licensed models | rembg's MIT license does not license every model weight; audit selected model separately. [rembg](https://github.com/danielgatis/rembg) |
| Mobile animation editing | Native trim/crop/decode; explicit animated-WebP encode pipeline | Media3 Transformer is useful but does not by itself complete animated sticker export. Benchmark codec path, memory and loop correctness. [Media3](https://developer.android.com/media/media3/transformer), [Android engineering blog](https://android-developers.googleblog.com/2025/03/media-processing-performance-jetpack-media3-transformer.html) |
| Worker rendering | Retain Pillow/FFmpeg approach behind a versioned renderer with pinned dependencies | Decode sandbox, resource bounds, codec/license review, deterministic artifact validation |
| FFmpeg mobile wrapper | Do not add the original FFmpegKit as a new dependency | Upstream retired; audit a maintained replacement or own minimal bindings before choosing. [Upstream](https://github.com/arthenica/ffmpeg-kit) |
| Migration research | Study StickerVault's folder/archive approach | GPL-3.0; no copying into MIT code without an explicit compliant licensing decision. [StickerVault](https://github.com/mintnchocochip/stickervault) |
| AI caption generation | Retain Groq behind a provider interface | Benchmark actual captions, current account limits, failure behavior and per-success cost; no silent model downgrade |
| AI image synthesis | Optional later capability, distinct from captioning a photo | No provider selected in this plan. Compare licensed hosted/self-hosted candidates on the same reference set before committing spend |

Do not rewrite reliable codecs, authentication or a moderation console from scratch merely to appear comprehensive. Build the parts unique to StickerDock: the editor document model, pack/library behavior, reliable job orchestration, rights-aware catalog and good discovery UX.

### Data/backend boundaries

- **Local library:** source assets, editable project recipe, exported sticker versions, local packs/collections and migration journals. Originals survive edits. Large files stream/stage to disk.
- **Private account storage:** explicitly selected packs only, account-scoped identifiers, checksums, sync checkpoints and conflict versions. Default off. No cloud-read result silently deletes a local sticker.
- **Generation:** durable job record, provider/renderer version, input authorization, attempt/lease, next retry time, result checksum and bounded error code. User jobs have priority over editorial batches.
- **Public catalog:** immutable reviewed revisions and preview renditions, paginated/searchable metadata, publisher identity, language/emotion/category, attribution, report/removal state. Browsing metadata/previews must not download/import full ZIPs.
- **Publication:** draft → review → approval → publication, independently tracked from generation completion. Editing an approved artifact returns the new revision to review.
- **Compatibility:** add fields through explicit schema/API versions. Continue reading backup schemas 1–5 and migrating existing SQLite without data loss. Preserve the old public catalog while rolling out a richer version.

Retain Supabase/Postgres/storage unless measured limitations justify replacement. Keep Vercel for web/API coordination; move interactive processing to a durable worker process rather than treating GitHub cron as a low-latency job runner. Start by strengthening the existing atomic claim path. Evaluate Supabase Queues if it simplifies durable delivery; visibility/lease guarantees do not remove the need for idempotent external calls and artifact commits. [Supabase Queues](https://supabase.com/docs/guides/queues).

GitHub explicitly warns scheduled jobs can be delayed or dropped. Keep Actions for CI and suitable batch maintenance, not an interactive latency promise. Provisioning a new hosted worker and paid providers remains a later approved deployment action. [GitHub scheduling](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).

## 6. Repairing generation properly

1. Reproduce the observed 429 in a controlled adapter test; inspect safe rate-limit headers and actual account quotas. Do not log request images, keys or raw private prompts. Groq documents request/token limits and `retry-after`. [Groq rate limits](https://console.groq.com/docs/rate-limits).
2. Unify budgets for Creator, Factory and trend jobs sharing the same provider account/model. A separate key does not by itself prove a separate quota.
3. Persist retry state outside temporary worker files before returning. Confirm current production exception paths upload the updated checkpoint; local file persistence alone does not establish cross-run durability.
4. Show queued, processing, waiting-for-provider, failed, cancelled and ready states. Return a retry time and understandable error. Do not label queue acceptance as a completed sticker.
5. Use bounded exponential backoff/jitter, provider reset times, attempt caps, lease recovery, cancellation checkpoints and a dead-letter/manual-review path. Avoid retry storms and duplicate renders.
6. Keep input validation, caption planning, segmentation/layout, rendering, technical checks and human review separate. A failed caption API must not destroy manual editing or previously completed outputs.
7. Reserve headroom. Eight scheduled batches of five per day leave no slack for a failed batch when targeting 40. Target is configurable; deliver fewer good stickers rather than pretend every day completed.
8. Prove 20 representative interactive jobs and a seven-day editorial trial, including forced 429, worker kill, malformed output and missing input. Measure latency and actual successful artifacts; don't count idle green workflows.

## 7. Security, privacy and operational foundation

These are implementation gates, not claims that all protections are missing:

- Revalidate every endpoint/object operation with two accounts: cross-account list/read/write/delete, guessed IDs, expired sessions, signed URLs and admin role checks. Service-role APIs must enforce ownership themselves; “RLS enabled” alone does not prove isolation when privileged server credentials bypass it.
- Keep guest local use. Finish confirmation/reset mail delivery before public account onboarding. Verify owner MFA, session rotation/revocation, CSRF, rate limiting, and meaningful per-actor audit records.
- Sandbox image/video decoding. Bound bytes, pixels, frames, duration, ZIP expansion, CPU and time. Verify MIME from content; strip public metadata; quarantine untrusted uploads before publication. Include malicious/oversized media tests.
- Protect source-fetch adapters against SSRF, unsafe redirects, internal addresses and unexpected types; independent provider failure must not stall the whole queue.
- Separate private backups from public copies. Keep mature packs local/offline as required. “Private” does not mean end-to-end encrypted; do not claim E2EE without a separate design and key-recovery plan.
- Implement account/media export and deletion with retention handling, backup expiry and completion status. Cloud deletion must explain its effect on downloaded copies and never silently erase local collections.
- Add request/job correlation IDs, redacted errors, crash/ANR monitoring, queue-age alerts, publish failure alerts, quotas and spend caps. Do not include personal sticker pixels/names in telemetry by default.
- Version/pin dependencies, scan secrets and vulnerable packages, produce license inventory/SBOM, protect signing backups and test release rollback with compatible data migrations.
- Drill restore from a staging backup. Define proposed recovery objectives before launch: database RPO <=24 hours and RTO <=4 hours for the initial pilot, then verify the chosen hosting/backup plan supports them.
- Public UGC needs filtering, reports, blocking, contact/takedown routes, age protections and accountable moderation before unrestricted submissions. Apple explicitly requires these controls; store acceptance is not guaranteed by local/offline labels. [Apple review guidelines](https://developer.apple.com/app-store/review/guidelines/).

## 8. Build order and stop/go gates

Work one complete feature slice at a time. Each slice includes UI, persistence/API, failure handling, tests and device evidence. A phase may be designed while an earlier phase is underway, but it does not receive a completion label while its dependencies fail.

| Phase | Deliverable | Must pass before advancing |
| --- | --- | --- |
| 0 — Baseline and regression capture | Record installed version/signature and existing data; preserve dirty work; verify backups; screen-by-screen defect inventory; official Sticker.ly mobile comparison on test device | Reproducible signed baseline, restore verified, each reported defect has steps/evidence; no destructive reset |
| 1 — Stop surprises and expose failures | Remove launch/resume imports; explicit Add to library; upload consent audit; correct empty/loading/errors; Factory 429 visibility; owner access/recovery | Reopen/resume/refresh never adds library entries or uploads when disabled; explicit save works once; failed generation has recoverable status |
| 2 — Foundation and platform feasibility | Separate screen state/use cases/storage; versioned contracts; migration fixtures; staging; early iPhone WhatsApp export experiment | Old backups/databases survive; Android official-provider contract passes; real iPhone export proven or precise blocker recorded before full iPhone commitment |
| 3 — Visual shell and local static creator | Approved design, photo editor, automatic/manual cutout, captions/outlines, undo/drafts, save/edit | User accepts real populated screens; portrait/landscape/EXIF/transparent photos work; save→kill→reopen retains exact output; static pack works in WhatsApp |
| 4 — Library and migration | Favorites, collections, tags/emoji/filenames, search/sort, large-library caching, multiselect, import/backup/share | 10k+ library responsive; duplicate/corrupt ZIP/low storage/permission revocation/process death recover without losing originals |
| 5 — Animated creator | Clip/GIF/WebP import, trim/crop/speed/loop/text, bounded encode, separate packs | Real static/animated exports pass; loops, transparency and size limits correct; cancellation/thermal/memory behavior acceptable |
| 6 — Real generation and catalog | Durable responsive worker, provider budgets, real-media pipeline, preview catalog, explicit downloads, initial curated packs | 20 interactive jobs plus failure injection; seven-day editorial trial; generation→review→publish→phone preview→save→WhatsApp trace completed |
| 7 — iPhone parity and private transfer | Native client, creator/library/export, selected private backup, Android↔iPhone transfer, hosted sharing | Real-device parity matrix and cross-device restore; personal/Business capabilities stated per platform; no reliance on iPhone access to another app's storage |
| 8 — Moderated community | Creator profiles/following, submissions, reports/blocking, moderator tools, takedowns/appeals, rights/age controls | Abuse and isolation tests; moderator response ownership; no unreviewed public media; user deletion/export works |
| 9 — Production release | Accessibility, load/performance, reliability trial, store policies/listings, signed builds, staged rollout/rollback | All release gates below pass with SHA/build/device evidence; no open critical/high data-loss/security/core-flow defects |

Phases 3–7 can each ship a private test candidate, but that is not a claim of full product completion. The broader feature list is retained; it must not dilute the first working creation/export loop.

### First implementation changes after this plan

1. Update the requirement history so manual saving supersedes automatic library population.
2. Split `refreshFactory()` into browse/status-only and an explicit user-triggered download/import action. Preserve existing imports; do not silently delete previously added packs. Offer a reviewed bulk cleanup later.
3. Add a meaningful regression that launch/resume/refresh cannot call the import path, plus explicit save/error/retry coverage. Remove misleading auto-add copy.
4. Add safe generation failure/retry information to admin and test persistent cooldown upload across worker failure. Fix the demonstrated 429 handling/visibility path before increasing the daily quota.
5. Create the visual pack-preview contract and one fully working Discover → preview → save → WhatsApp slice, alongside the local photo creator design.

## 9. Acceptance matrix

All timings below are proposed targets to measure, not existing results. Record reference hardware, OS/app versions, release build SHA and fixture sizes so results are reproducible.

| Area | Acceptance target |
| --- | --- |
| Data safety | Zero unintended loss in migration/restore/cancel/low-storage tests; original bytes retained; backup schema 1–5 fixtures readable |
| Privacy | Fresh install: zero private media uploads; sync off remains off through restart; publication cannot follow from backup alone |
| Launch | No automatic pack import; usable local library within 2 seconds warm / 3 seconds cold on the agreed reference device, measured at p95 |
| Large library | 10,000 stickers with bounded cache and pagination; search p95 <=300 ms after index warm-up; record frames/memory, no OOM/ANR through a 15-minute scroll/search session |
| Editor | No clipped controls at 360dp width or large fonts; undo/redo and crash recovery; readable output against light/dark/patterned chat backgrounds |
| Media validation | 3–30 stickers per pack; 512×512; static <=100 KB; animated <=500 KB; homogeneous static/animated packs; animated duration <=10s and frame duration >=8ms; tray icon validation |
| WhatsApp | Personal and Business Android installs, absent app, cancel, update, reopen and actual send; iPhone tested independently; no guarantee of account selection within a single installation |
| AI | Queue acknowledgement p95 <=2s; target first completed photo-caption result <=60s under defined load and provider availability; visible waiting state when quota prevents this; zero duplicate final artifacts in retry tests |
| Content | Every published pack has rights/review evidence; owner-approved quality examples and an agreed reject rubric; no template substitution hidden as real photos |
| Cloud | Two-account isolation, token expiry, retries, duplicate commits, simultaneous edits, interrupted restore and deletion tested; conflicts preserve recoverable originals |
| Reliability | Initial closed-beta goal >=99.5% crash-free sessions over at least 1,000 sessions/14 days; all critical journeys repeatedly pass; extend sampling if volume too low |
| Operations | Alerts detect worker outage, stuck jobs and publication failure; staging restore and release rollback drills succeed |

The media numbers and one-pack-at-a-time user confirmation follow WhatsApp's official Android/iOS documentation. They do not guarantee that an installed pack refreshes instantly or that cloned/multiple accounts can be addressed separately. [Android reference](https://github.com/WhatsApp/stickers/blob/main/Android/README.md), [iOS reference](https://github.com/WhatsApp/stickers/blob/main/iOS/README.md).

## 10. Broader features after the core gates

Prioritized expansion: editable non-destructive projects; batch cutout/caption styles; multilingual caption alternatives; private smart collections; near-duplicate suggestions with manual confirmation; emoji keywords for WhatsApp search; pack revision/update notices; share links/QR codes; creator subscriptions with opt-in notifications; seasonal editorial collections; optional iMessage and Telegram adapters; collaborative private packs after permission design; optional paid high-quality generation after measured economics.

Avoid launching comments, direct messages, anonymous uploads, ad tracking and paid subscriptions simultaneously with the recovery. They add operational and moderation obligations without fixing creation, content or export. These can remain explicitly deferred roadmap items.

## 11. Time, costs and remaining decisions

This is a multi-month product program. A rough planning envelope for one experienced engineer with part-time design, content and QA help is 20–36 engineering weeks for the two-platform scope, plus store/provider approvals and ongoing moderation. The first usable Android recovery candidate is a much smaller milestone. Re-estimate after Phase 0; this range is not a delivery commitment or measured velocity.

Track actual cost per successful usable sticker: model input/output + image/clip generation if enabled + worker CPU/GPU + storage/egress + moderation. Record retries and rejected outputs in cost accounting. Start with local editing and a bounded licensed catalog; set explicit pilot caps before adding hosted rendering or paid APIs. No provider purchase, new deployment, production migration, password reset or content publication was performed in this planning pass.

Resources needed during execution: authorized Galaxy S24 Ultra testing, at least one mid-range Android device, Mac/Xcode and iPhone testing, working owner recovery email, an agreed visual reference set and curated-content owner, provider quota visibility, and a bounded operating budget. Until supplied/verified, those gates stay pending while independent local work continues.

Every completion report must distinguish SOURCE, LOCAL TEST, LIVE BACKEND, DEVICE and USER ACCEPTANCE. “All broken” is the starting verification assumption, not a reason to erase existing data or disregard demonstrated components. “Perfect” is not a testable promise; the gates above are.
