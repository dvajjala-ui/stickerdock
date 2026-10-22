# Product audit and implementation backlog — October 22, 2026

## Scope and evidence

This answers the request to list the bugs, missing implementation, product gaps,
UI/navigation improvements, automatic meme creation and website custom uploads.
It is an audit and proposed build order, **not a claim that these changes have
been implemented**. It extends [the requirements ledger](REQUIREMENTS_LEDGER.md)
and [the previous parity audit](STICKERLY_PARITY_AUDIT_2026-10-18.md).

Baseline: main aaa03dda5dbdac2b8971bb138b0045c2e337ab34, clean and synchronized
when inspected. Android 0.11.2/build 17 and unsigned iPhone 0.10.2/build 14 are
the release checkpoints recorded in [QA_MATRIX.md](QA_MATRIX.md). Their passing
build/emulator/simulator checks do not establish live AI delivery, physical
WhatsApp behavior or iPhone distribution readiness.

Evidence collected for this audit:

- **LIVE:** read-only production Admin health/overview, media inventory and
  public catalog; refreshed October 22 at 06:19 UTC (11:49 IST). Fresh browser
  inspection covered Admin overview, Media library and AI workbench.
- **CODE:** current Android, SwiftUI, Admin/Studio, APIs, Factory/Creator workers,
  workflow definitions, existing acceptance records and the eight-repository
  inspection record.
- **LOCAL REPRO:** synthetic metadata-bearing image upload, repeated identical
  job submissions and media listing, with mocked storage/network boundaries.
  These did not upload personal media or consume provider requests.
- **RESEARCH:** official product guides, engineering posts, open-source projects
  and current provider documentation linked below. Advertised behavior is not
  independent hands-on acceptance of another app.
- **UNVERIFIED:** physical Android/iPhone/WhatsApp, original-image model quality,
  new account email delivery, and newly proposed functionality. No paid calls,
  manual deployment, production content/database edits, migrations or content
  publication in this audit. Documentation can follow the existing main integration.

The ignored local evidence folder is work/product-audit-2026-10-22. It contains
redacted summaries, synthetic probes and browser evidence; it is not committed
as user media or a production database export. This is the complete currently
identified backlog from these inspections, not a guarantee that no other bug
exists. Suggested extensions are explicitly separated from verified defects.

## Main findings

1. **Generation and delivery are disconnected.** Recent daily runs report
   15–25 generated stickers against a target of 40. The production worker only
   uploads the ordinary Factory archive and creates an editable review draft
   when daily-complete.zip exists. A useful five-sticker batch is therefore
   stranded before the normal review/publish flow if the day never reaches 40.
2. **Mobile AI is not connected.** Android Discover and the iPhone catalog read
   reviewed public packs. They do not expose the operator Creator queue or a
   private account-owned AI creation inbox. A completed website AI image does
   not become a personal mobile pack automatically.
3. **There is too little public content.** The live catalog has one published
   pack, Desi photo reactions, with four stickers. Local licensed trial packs
   and run counters are not equivalent to a usable published catalog.
4. **Upload exists, but its product flow is incomplete.** Media library has
   Upload new meme; Studio has image upload/drop and a canvas. There is no
   coherent purpose-aware Upload → Create/Reference → Review → Pack → Phone
   journey. The live library shows placeholders and hash filenames.
5. **Two platforms exist, with unequal scope.** The native iPhone client builds
   and passes its recorded tests, but is unsigned and lacks Android's complete
   editing/cloud experience. No physical iPhone install or WhatsApp acceptance
   is established.

## Current live snapshot

| Area | Observation | Meaning |
| --- | --- | --- |
| Factory configuration | Enabled, target 40/day, batches of 5, automatic completed-draft migration enabled | A configured target is not a delivery guarantee |
| October 21 UTC run | 20/40, six attempts, last update 23:35 UTC, status running | Completed batches exist in run state, but the full-day gate is not reached |
| October 20 UTC run | 20/40, six attempts, invalid-provider-response recovery recorded | Saved progress/retry exists; a full ready pack is not established |
| October 19 / 18 / 17 UTC | 20/40, 25/40, 15/40; all still labelled running | Prior days need a partial/closed lifecycle and usable batch delivery |
| October 22 UTC run | 0/40, zero attempts, last update 01:55 UTC | Today's day is unfinished; this is a stale-progress signal, not proof that the entire day failed |
| Managed packs | Two: one published four-sticker pack and one older five-sticker draft | Nine managed stickers are not the same as the larger run-record totals |
| Public catalog | One pack / four stickers | Explains the near-empty mobile content experience |
| Creator queue | One completed request in returned history | A web AI result exists; it does not demonstrate native AI integration |
| Media list | API returns 100 items | The API caps the first page at 100; total stored media is unknown |
| Health | admin_ready and worker_ready both true | Current checks establish schema access, not fresh generation or delivery |

The latest five scheduled Factory Actions inspected were successful, including
[run 39708279276](https://github.com/dvajjala-ui/stickerdock/actions/runs/39708279276).
A successful scheduled invocation can simply wait for a cooldown; it does not
mean it produced or published a pack. Observed dispatches were hours apart
despite a 15-minute cron definition. The cause of that scheduling gap was not
established. There is no verified recent normal 40/40 completed day in this audit.

## Existing implementation to preserve

Do not spend the next sprint rebuilding these as if absent:

- Android local pack CRUD, labels/tags, search/filtering, Favorites,
  copy/move/remove/Undo, static/animated separation and mature private packs.
- Explicit photo, GIF and video creation; received Android clips enter the
  animated creator. Static transforms/captions, reversible drafts, cutout and
  erase/restore tools exist. Automatic cutout quality still needs acceptance.
- Android resumable SAF scans, deduplication, EXIF grouping, portable ZIPs,
  editable backups, shared-file receipt and WhatsApp ContentProvider.
- Native iPhone photo/clip creation, catalog browsing, Favorites, local storage,
  portable ZIP import/export and PNG/JPEG archive conversion. The latest Apple
  CI is [39606440645](https://github.com/dvajjala-ui/stickerdock/actions/runs/39606440645).
- Optional authenticated Android cloud backup, revision conflicts and explicit
  public submissions/review/report/hide mechanisms. These are foundations, not
  a complete operational public community.
- Browser uploads/canvas, Groq vision/caption workers, trend briefs, saved
  generation state, reviewed publication, public pack codes/custom app links,
  and Android signed-release verification.

Local phone stickers remain authoritative. Opening/resuming must never silently
populate the saved Library. Automatic creation should refresh a previewable
feed; Save, cloud upload and public publication remain separate decisions.

## Priorities and work-item types

- **P0:** required before exposing the new end-to-end consumer AI/upload flow.
- **P1:** the next product delivery work after that foundation.
- **P2:** broader improvements after the useful core journey works.
- **B:** confirmed defect or operational/UX contract mismatch, supported below.
- **F:** missing integration or suggested extension; M means documented/source
  gap, E means a proposed enhancement rather than an existing defect.
- **V:** acceptance still required for existing or new implementation.
- **D:** a product/provider/distribution decision to resolve when implementing.

There are **90 tracked work items: 14 B + 60 F + 12 V + 4 D**. Related IDs share
dependencies; they are not 90 independent projects or 90 confirmed bugs.

## Confirmed defects and operational gaps

| ID / priority | Finding and evidence | Required outcome / acceptance |
| --- | --- | --- |
| B01 / P0 | Partial daily generation has no normal draft-delivery path. LIVE recent 15–25/40 days; CODE production_worker.persist_run and auto_migrate_completed require daily-complete.zip / complete status. | Every valid completed batch becomes a durable, previewable review draft independently of the daily target. Repeated runs/recovery must not duplicate it or re-call the model. |
| B02 / P1 | Past UTC days remain running after their generation day ends. LIVE October 17–21; CODE persistence only chooses complete/running or selected failure states. | Close each day as complete/partial/failed/skipped with saved output counts, reason and next action; retain recoverable artifacts. |
| B03 / P1 | worker_ready can be green while generation/delivery is stale. CODE production_readiness checks required table columns; LIVE readiness true alongside stale progress and one public pack. | Separate database readiness, worker heartbeat, due-job age, provider status, draft readiness and catalog freshness. Never report healthy delivery from schema availability alone. |
| B04 / P1 | Schedule and wait copy imply dependable turnaround without observed delivery latency. CODE Factory 15-minute and Creator 10-minute cron; LIVE Factory dispatch gaps span hours. No Creator latency bound was verified. | Show actual queue age/last worker/next retry and measured expectations. Use a suitable worker queue for interactive jobs; do not promise an exact cron-based completion time. |
| B05 / P1 | Media cards lack thumbnails. CODE renderMedia expects preview while list_inbox never supplies it; LIVE placeholder cards. A separate Preview click does work. | Bounded thumbnails load in the grid and source picker with failure fallback, lazy loading and accessible labels. |
| B06 / P1 | Upload titles are lost in browsing. CODE source metadata stores titles, but listing returns hash-based name/path only; LIVE hashes in cards and AI source dropdown. | Preserve/display editable human titles and source/result type, retaining content hashes as internal identifiers. |
| B07 / P1 | Media browsing stops at the first 100 items. CODE list_inbox hardcodes limit 100 / offset 0; list API accepts no page cursor. LIVE returned page reaches the cap. | Stable cursor pagination/search reaches a seeded item beyond 100 without duplicates or skipped entries. Do not invent a total count from one capped page. |
| B08 / P0 | AI workbench/queue call results private despite R40's deliberately open shared workspace. LIVE UI and CODE renderGenerate/renderFactory. | Use accurate shared-workspace copy. Personal uploads/jobs must use a separate authenticated scope. Preserve the requested open Admin behavior rather than silently restoring login. |
| B09 / P0 | Source uploads retain original metadata bytes. LOCAL REPRO a synthetic JPEG EXIF marker survives upload_user_image byte-for-byte; CODE decode_image validates but does not sanitize. No real GPS leak was inspected. | Normalize orientation and remove EXIF/GPS from shared/AI derivatives. Preserve an original only in an explicitly chosen private scope; prove alpha, orientation and color remain correct. |
| B10 / P1 | Upload assigns USER_OWNED metadata without collecting ownership or reference-only intent. LOCAL REPRO and CODE upload_user_image. This does not bypass the existing publication review by itself. | Require source-purpose/rights classification; never promote a reference to distributable media automatically. Keep provenance history and review evidence. |
| B11 / P0 | Repeating the same queue submission creates independent requests. LOCAL REPRO distinct UUIDs; CODE queue_creator_request has no client request identity. Outstanding-queue cap exists but does not provide idempotency. | Retrying/reloading/repeated taps with one request identity returns the same job and reserves provider budget once. A deliberate new variant uses a new identity. |
| B12 / P1 | Studio cannot resume its canvas AI request after reload. CODE request ID lives inside the click handler; polling times out after 240 × 5 seconds and suggests trying later. | Persist a recoverable job/project identity, reconnect to status/result, and offer Retry/Cancel without silently creating a second job. Admin queue visibility is not canvas recovery. |
| B13 / P2 | Stale/minor UI copy: Android uses plural packs/stickers for count 1; iPhone empty state says StickerDock ZIP despite newly supported photo ZIPs. CODE StickerDockScreens and StickerDockApp. | Correct pluralization and supported-input guidance on both clients and Admin; verify zero/one/many and narrow layouts. |
| B14 / P1 | Operational instructions drift from behavior: DAILY_AI_STICKERS says keep Factory gate off until full-day verification; actual workflow runs unless explicitly false. Creator worker header says manual-only while a scheduled workflow exists. | Make current defaults, emergency pause, actual acceptance and historical checkpoints unambiguous. A current source/run-bound status page must override old instructions. |

Primary implementation anchors:
[production_worker.py](../tools/production_worker.py),
[daily_stickers.py](../tools/daily_stickers.py),
[shared workspace API](../api/_shared.py),
[readiness](../tools/production_readiness.py),
[Admin UI](../admin/app.js), [Studio UI](../studio/app.js),
[Android catalog refresh](../app/src/main/java/app/stickerdock/duo/StickerDockPolishedActivity.kt),
[iPhone model](../ios/Sources/LibraryModel.swift).

## Missing integration and proposed product improvements

### Reliable automatic memes and native AI

| ID / priority / type | Implementation | Acceptance |
| --- | --- | --- |
| F01 / P0 / M | Package each completed batch into immutable static/animated review drafts, with pack names, labels, tags, checksums and attribution. Fixes B01's delivery boundary. | A five-sticker batch is reviewable even if the daily target stays 5/40; 3–30 stickers per WhatsApp pack and kinds never mix. |
| F02 / P0 / M | One durable job lifecycle spanning brief, source, caption, render, validation, storage and draft delivery, with per-stage retry identities/outbox. Reuse existing saved state instead of starting again. | Render/upload/review failures recover from the last committed stage; ambiguous provider outcomes do not trigger uncontrolled duplicate calls. |
| F03 / P0 / M | Add account-owned native AI upload/job/status/result APIs using phone-cloud authentication, ownership checks, RLS and server-side keys. See NATIVE_AI_HANDOFF.md. | Two normal users cannot enumerate/fetch/cancel each other's jobs. The open operator endpoints are never the consumer ownership boundary. |
| F04 / P0 / M | Native Create → AI help: explicitly select one photo/clip, choose emotion/language, get contextual captions/variants, preview, choose and save. | Android and iPhone expose a useful online assistant; uploads are explicit and mature/offline-only packs remain offline. Suggestions join Undo/draft recovery when applied. |
| F05 / P1 / M | A recoverable Your creations inbox plus Fresh packs in Discover. Keep account-only results separate from reviewed public packs. | A browser/native personal result reopens on the same account's phone; refresh never silently adds stickers to Library. |
| F06 / P0 / M | Share server-enforced provider reservations/cooldowns across trend, Factory and Creator jobs, using actual organization/model quotas. | Two keys in one organization cannot bypass limits; 429, timeout, invalid JSON, daily exhaustion and cancellation retain truthful states. |
| F07 / P1 / E | Evaluate a persistent queue/worker with leases, priorities and backpressure for interactive generation; retain Actions for CI or scheduled fallback if useful. | Document measured queue latency and recovery under worker restart; prioritize a person's request without starving bounded daily work. |
| F08 / P2 / M | Integrate optional original artwork synthesis separately from photo/clip conversion. The existing opt-in original_artwork worker is prepared, not live accepted. | Approved provider/model/cost trial produces useful coherent reactions; show original-generation labeling and spending caps. Groq captioning is never sold as new pixel generation. |
| F09 / P1 / M | Show an end-to-end delivery dashboard: generated → validated → draft → approved → published → catalog-visible, with rejection/retry reasons. | A successful action cannot mask zero new deliverable packs; stale/missing stages are visible and actionable. |
| F10 / P1 / E | Operator controls for desired languages, topics, source style, quantity, time zone and freshness; treat quantity as a bounded target. | Scheduler behavior and UTC/local dates are clear; pauses and restart preserve work; delivery goals are based on observed capacity. |

### Website Upload / Media hub

| ID / priority / type | Implementation | Acceptance |
| --- | --- | --- |
| F11 / P1 / M | Make Upload / Media a visible navigation destination and global upload action. Extend the existing Media library instead of creating another disconnected uploader. | Desktop and phone browser users can upload and immediately choose their next action without finding a separate Studio first. |
| F12 / P0 / M | Purpose selection: Create stickers now, Save as editable template, or Reference only. Explain destination/privacy before upload. | Reference-only content is excluded from rendering/publication by default; changing purpose requires explicit rights review. |
| F13 / P1 / E | Multi-image upload with ordered previews, per-file progress/errors, bounded concurrency, resumable retry and duplicate reuse. | One invalid file does not discard valid uploads; reload/retry does not duplicate accepted assets. |
| F14 / P1 / M | Browser GIF/video ingestion and queued conversion: choose segment, crop/caption, preview animation, validate and save to a compatible pack. | Large processing runs in a bounded worker, not a long synchronous Vercel request; unsupported/oversize inputs have useful errors. |
| F15 / P1 / E | Rich asset records: title, type, dimensions/duration, created time, collection, source/result relationship, ready/failed state and usage in packs. | Cards and pickers identify the right media visually; source and generated variants cannot be confused. Depends on B05–B07. |
| F16 / P1 / M | Search/filter/sort/paginate assets by name, tags, language, static/animated, reference/source/result and ready state. | An old named asset is findable in a seeded library above 100 items; filtered selection survives navigation. |
| F17 / P0 / M | Explicit rights/credit/source fields, derivative history, reference restrictions and metadata normalization throughout upload → pack. | Reuploading identical bytes cannot silently upgrade permission or erase provenance; review can inspect required credit and usage restrictions. |
| F18 / P1 / M | Connect canvas/results to Pack builder and Send to my phone, preserving editable project identity and audience. | A chosen result becomes a previewable pack with one clear next action; personal transfer is account-scoped, public delivery uses review/publication. |

### Real content, trends and meme quality

| ID / priority / type | Implementation | Acceptance |
| --- | --- | --- |
| F19 / P1 / M | Build a broad reviewed starter catalog of real human/animal/photo reactions and genuinely moving memes. Proposed first target: 12 coherent packs of 6–12 useful stickers, including static and animated collections. | Actual reviewed assets are visible in both mobile catalogs; local trials/counters/placeholders do not count. Target is a proposal, not delivered content. |
| F20 / P1 / E | Maintain our own rights-cleared meme template layouts and editable caption boxes. Evaluate self-hosted memegen ideas where they improve our existing renderer. | Templates work with user-owned/licensed media; open-source code licensing is never treated as permission to reuse every example image. |
| F21 / P1 / M | Ground trends in dated source links and freshness checks; cover requested Indian/Hinglish, cricket, movies/series, news, serious and everyday reactions. | Admin sees why a topic is current; stale/unsupported stories are skipped. Movie/news references do not automatically grant image redistribution rights. |
| F22 / P1 / M | Generate captions from actual image/clip context and intended emotion, with varied jokes and optional user direction. | A human can match the caption to the expression/action; avoid repeating unrelated captions over the same few source photos. |
| F23 / P1 / M | Language-aware generation and rendering, including Hinglish plus properly shaped Hindi/regional scripts, font fallback and text fitting. | No missing glyphs, broken shaping or unreadable truncation at sticker size. Current ASCII-oriented caption path is not multilingual support. |
| F24 / P1 / M | Complete live acceptance of motion-context captioning and a repeatable licensed animated-content pipeline. Existing native GIF/video creation and motion worker experiments are foundations, not proof of deployed output quality. | Moving expressions, timing, loop quality, crop and caption survive mobile preview/save/export; original AI video synthesis is a separate future experiment. |
| F25 / P1 / E | Add perceptual/semantic repetition checks across days and packs, complementing existing exact file/hash deduplication. | Nearly identical crops/captions do not dominate Fresh packs; explicit personal duplicates are not destructively removed. |
| F26 / P1 / M | Use a content review rubric: recognizable reaction, funny/useful caption, coherent theme, readable text, clean crop/edges, smooth loop, rights and audience. | Evaluate at actual phone/sticker size with target users; a successful encoder or model response alone is not quality acceptance. |

### Creation and editing

| ID / priority / type | Implementation | Acceptance |
| --- | --- | --- |
| F27 / P1 / M | Retain an optional full-resolution source independently from the normalized 512px editable original. | Re-cropping does not start from an already reduced sticker; storage/backup controls are explicit and existing normalized projects still open. |
| F28 / P1 / E | Multiple independent text blocks/layers, drag/resize controls, face-safe placement, reusable font/outline styles and alignment guides. | Text stays legible, tools remain reachable at large text size, and Undo/Redo/Cancel preserve the source. Basic transforms/captions already exist. |
| F29 / P1 / M | Durable photo and video/GIF projects with recipes, source references, layer/timing state and recoverable drafts; compatible cross-platform project backups. | Reopen after process death, edit without cumulative quality loss, restore old portable/editable formats and cancel without changing saved stickers. |
| F30 / P1 / M | Benchmark current automatic cutout on hair, hands, low contrast, multiple people and non-person objects; improve selective edge repair. | Show before/after and allow manual recovery. Existing cutout/erase/restore tools are retained; model availability is not quality proof. |
| F31 / P2 / E | Animated subject segmentation/tracking with temporal consistency and manual fallback. | No flickering outlines, missing limbs or false claims that a static mask follows motion; measure latency/memory before enabling widely. |
| F32 / P2 / E | Time-bounded animated captions/overlays, frame selection and loop start/end adjustment in the motion editor. | Export timing matches preview and remains within WhatsApp limits; existing simple trim/caption flow stays easy. |
| F33 / P1 / E | Explain export tradeoffs with file-size/quality previews and suggested crop/duration changes when compression would ruin the sticker. | Preserve alpha/rotation/timing, enforce static/animated limits, and give an actionable failure rather than success with unusable media. Existing bounded encoding remains. |
| F34 / P1 / M | Turn one supplied photo/clip into a selectable contextual reaction pack using AI caption variants and appropriate crops, rather than only one result image. | Preview/reject/edit individual variations; no forced duplicate fillers to reach a pack count. Existing manual iPhone caption variants are not AI generation. |
| F35 / P2 / E | Separate full meme layouts from sticker output: multi-panel/template meme export, plus selected 512px sticker adaptation. | Users understand whether they are saving a normal image/GIF meme or a WhatsApp-compatible sticker pack. |

### Navigation, discovery and consumer experience

| ID / priority / type | Implementation | Acceptance |
| --- | --- | --- |
| F36 / P1 / E | Short optional onboarding: import existing stickers, make a reaction, or browse; choose languages/styles without blocking offline use. | New users reach a useful first pack; no mandatory account for local creation and no empty dashboard pretending to be a full catalog. |
| F37 / P1 / M | Complete a consistent Library / Create / Discover flow on Android and iPhone. Keep import/backup as clear secondary tools and operator Factory controls on the website. | Every screen has one understandable primary action and correct Back/dismiss behavior; verify with real populated packs, not empty mockups. |
| F38 / P1 / E | Add language/emotion/use-case discovery and creator/source detail, preserving existing kind/category/search and saved status. | Users find a relevant reaction quickly; filters explain an empty result and retain choices when returning from preview. |
| F39 / P2 / E | Start recommendations with explicit style/language choices and optional Save/Hide feedback; introduce embeddings only if measured retrieval quality needs them. | Clear reset/opt-out and useful cold-start choices; do not infer private WhatsApp sending history or collect a whole personal library for ranking. |
| F40 / P2 / M | Moderated creator profiles, follow/save collections and new-pack updates after a healthy catalog exists. | Profiles respect hidden/blocked creators and withdrawn content; public identity and moderation are operational before social growth. |
| F41 / P1 / M | Finish readable offline, loading, stale-catalog, queued, failed, retry and successful-save states throughout both clients and web. | Existing local stickers remain usable without network; error recovery preserves selections/projects and never silently imports or publishes. |
| F42 / P1 / M | Polish hierarchy, spacing, contrast, animation controls, accessible labels/focus and one-handed actions across populated screens. | TalkBack/VoiceOver, 200% text, small widths, keyboard/switch navigation and reduced motion have reachable controls. Existing emulator checks are retained. |
| F43 / P2 / M | Consumer-facing web pack preview/landing pages and useful install/open-app guidance, separate from the operator Admin. | Opening a public pack URL on a phone/desktop shows the right pack and compatible next action; visitors are not sent to an open operational dashboard. |

### Library, migration, backup and sharing

| ID / priority / type | Implementation | Acceptance |
| --- | --- | --- |
| F44 / P2 / E | Optional user collections/folders, pinning and better bulk organization alongside existing packs/tags/Favorites. | Large personal collections become easier to navigate without changing portable pack identity or hiding unknown categories from All. |
| F45 / P2 / M | Explicit optional backup/restore for organization preferences such as Favorites, sort choices and collections. | Document device-only versus portable metadata; merge preferences safely. Android Favorites are currently intentionally outside portable/cloud sticker backups. |
| F46 / P1 / E | Improve import planning/results: estimated size, original grouping, duplicates/skips/failures, retry selection and storage recovery. | Preserve current resumable SAF/EXIF/dedup behavior; an invalid file or interrupted import never deletes existing stickers. |
| F47 / P1 / M | Finish user-facing cloud onboarding/backup/conflict/restore status and selective storage controls, while retaining current revision/fingerprint protection. | Account switching, expired sessions and 409 revision_conflict are understandable; local data survives offline/auth failure and cloud deletion is explicit. |
| F48 / P1 / M | HTTPS public pack links/app links/universal links/QRs and a separately designed private expiring/revocable sharing flow. | Correct pack previews before Save; private credentials never enter public catalog/logs. Revocation stops future access, not copies already downloaded. Existing ZIPs/codes remain valid. |
| F49 / P2 / M | Evaluate dedicated Telegram/Signal pack exporters after the core WhatsApp flow; use each platform's actual API/format and permissions. | A generic share-sheet file is not labelled successful pack installation. No use of bot sessions to read personal chats. |
| F50 / P1 / M | Refine WhatsApp add/update/help feedback, minimum pack-size guidance, missing-app and cancel handling, and separate consumer/Business status. | Explain what WhatsApp controls; never promise routing two accounts inside one installation or forced deletion of an already installed pack. |

### iPhone completion

| ID / priority / type | Implementation | Acceptance |
| --- | --- | --- |
| F51 / P1 / M | Native iPhone authenticated private cloud adapter and onboarding/restore parity. | Account isolation, local-first behavior, conflict handling and cross-platform selected-pack restore match the shared contract. |
| F52 / P1 / M | iPhone AI assistant and private creations inbox using F03/F04's shared server contract. | Actual native queued/result/restart/save flow works; an unsigned simulator build or website job is not proof of installed-phone AI. |
| F53 / P1 / M | iPhone editor/project/backup/metadata parity with accepted Android behavior; explicit gap matrix per tool. | Reversible editing, manual repair and editable-project recovery work without regressing current Photos/Files/ZIP/motion/Favorites functionality. |
| F54 / P1 / M | iOS share extension for incoming selected photos/clips/pack files and broader authorized archive metadata interoperability. | Scope security-accessed files correctly, reject invalid/mixed input before mutation, and recover cancelled/interrupted receipt. No access to WhatsApp private folders is promised. |
| F55 / P1 / M | Apple signing/provisioning, installable beta/TestFlight, privacy/store metadata and release automation. | Install the exact signed build and record device results. The current unsigned IPA is not a consumer-installable release; a local Mac is not required if an authorized hosted Mac pipeline is used. |

### Operations, community and resource control

| ID / priority / type | Implementation | Acceptance |
| --- | --- | --- |
| F56 / P1 / M | Delivery/queue/error telemetry and support diagnostics tied to version/job IDs, excluding private media/captions/tokens. | Diagnose a missing result from stage events; alerts concern meaningful failure/staleness and include an actionable next step. |
| F57 / P0 before public community / M | Complete operational identity, moderation/report/block/takedown/appeal flows, age protections and scanning around existing submission/review/hide foundations. | Test both accounts and moderators, including withdrawn/blocked content. Public uploads remain gated until actual operational readiness, not just database fields. |
| F58 / P0 / M | Enforce bounded upload/job/storage/provider resources, per-user native quotas and workspace-wide abuse/budget controls. | One caller cannot exhaust the shared worker budget or storage unchecked; shared Admin access remains the R40 decision, not an assumed private operator identity. |
| F59 / P1 / M | Complete release operations: Google Play test/distribution preparation, store listing/Data safety, database/media restore, release rollback, emergency pause and schema-compatible recovery. Existing signed APK publication stays intact. | Store readiness is checked separately from a GitHub APK release; exact source/artifact evidence and compatible backups survive failed rollout. Rehearse restores in isolation, never destructively against production. |
| F60 / P1 / E | Reconcile privacy/retention/export/delete UX for personal uploads, AI derivatives, references, public copies and project originals. | Users can understand each scope and remove selected cloud material without losing local stickers. Audit existing paths before adding redundant delete APIs or claiming end-to-end encryption. |

## Acceptance still needed

These are verification gaps, not evidence that the implemented feature fails.
Unavailable devices/accounts are recorded rather than requested again.

| ID / priority | Gate | Required evidence |
| --- | --- | --- |
| V01 / P1 | Physical Android consumer WhatsApp and Business | Signed build: add/cancel/send/update/cache refresh/invalid pack/missing app; distinct installation results. In-app account routing remains unsupported. |
| V02 / P1 | Real Samsung phone migration | Personal/Business SAF roots, thousands of actual WebP, duplicates/EXIF/invalid input, cancel/retry, revoked grants, low storage and process death. Existing AOSP 1,215-input / 10k-library acceptance is retained. |
| V03 / P1 | Physical iPhone and WhatsApp | Exact signed beta: Photos/Files/share receipt, static/animated handoff, cancellation, restart, memory and iOS version coverage. Simulator tests do not close this gate. |
| V04 / P0 | New live AI journey | One authorized owned/licensed input through deployed native/browser job → real provider → durable result → pack → phone preview/save. Record source/version, latency and outcome; mocks alone are insufficient. |
| V05 / P0 | Two-account AI ownership/recovery | Cross-user reads/writes/cancel denied; expired sessions, retry identities, process restart, budgets and unavailable provider preserve results and correct ownership. |
| V06 / P1 | Content and cutout quality | Target-user review of varied real photos/clips, clean edges, readable captions, contextual humor and smooth motion; compare outputs at actual sticker size. |
| V07 / P1 | Physical performance | Populated 10k collection, thumbnail cache eviction, cold launch/search/scroll, long clip jobs, battery/thermals, low-memory/storage and interrupted background work. Existing emulator checks remain valid but narrower. |
| V08 / P1 | Accessibility and visual acceptance | Android TalkBack/iOS VoiceOver, large text, small phones/tablets, dark mode/contrast, reduced motion and keyboard/focus paths using real content. |
| V09 / P1 | Automatic service reliability | Observe at least seven normal days: due jobs, unique valid outputs, draft readiness, review/publication, delivery freshness, rate-limit recovery and actual provider usage. Define a measured target; do not advertise 40/day from settings. |
| V10 / P1 | Device backup/restore and offline durability | Current/old portable formats, Android editable backup, iPhone archives, selected cloud restore, conflict/account switching and crash/low-storage failures without changing existing data. |
| V11 / P0 | Consumer security boundary | Release-bound checks for server-only secrets, scoped derivatives/EXIF, upload/archive abuse limits, shared-workspace copy and private account APIs. Open Admin is a recorded design decision, not proof of user isolation. |
| V12 / P1 | Complete user journeys | New user and large-library user: find/create → edit → save → WhatsApp; web upload/reference → create/review → phone. Observe confusion, task time and recovery without operator coaching. |

## Decisions to resolve during implementation

| ID | Recommendation and boundary |
| --- | --- |
| D01 | Preserve R40's open operator workspace for now and label it accurately. Build authenticated personal AI separately. Optional team roles/login would be a new product decision, not an unannounced reversal. |
| D02 | Use existing Groq caption/vision plus local rendering first. Trial original-image synthesis only with approved setup/spending and quality criteria; no extra key is needed merely to write this plan. |
| D03 | Start catalog curation with Indian/Hinglish everyday/human reactions and a balanced static/animated selection, then add languages/topics using observed demand. Validate the proposed 12-pack target with quality and rights evidence. |
| D04 | Keep native Android + SwiftUI + shared Kotlin media contracts. Evaluate a full UI rewrite only against measured maintenance/product benefits; no rewrite is required to support both platforms. Ship a signed Apple beta before claiming iPhone availability. |

## Reference products, case studies and reuse decisions

Research checked October 22. These are workflow/engineering references, not
permission to copy branding, private endpoints, proprietary source or catalogs.
The adaptations in the third column are recommendations for StickerDock.

| Primary reference | Relevant documented behavior | Application here |
| --- | --- | --- |
| [Sticker.ly creation guide](https://sticker.ly/) | Photo/meme upload, cutout/text editing, named packs, WhatsApp export and unique sharing links/codes. | Complete the whole creation-to-sharing journey; larger artwork and useful real packs matter more than adding isolated tool buttons. Basic creation/codes already exist here. |
| [Telegram sticker editor, April 2024](https://telegram.org/blog/sticker-maker) | Cutout with erase/restore/outline, visual/text composition, emotion emoji suggestions, sets and share links. | Improve contextual tags, composition, edge quality and reusable collections. Existing Android cutout/brush tools are not a missing feature. |
| [Signal sticker design, December 2019](https://signal.org/blog/make-privacy-stick/) | Private encrypted packs, frequently used stickers and pack links carrying decryption capability. | Study private pack sharing without making all creations public. StickerDock currently does not implement Signal's encryption design; server-private storage is not end-to-end encryption. |
| [Imgflip API](https://imgflip.com/api) | Template/caption workflows and optional advanced AI/GIF endpoints, with distinct free/premium capabilities. | Reference good template/caption UX. Evaluate cost/terms before adding a provider; paid AI/GIF endpoints are not a free dependency. |
| [Spotify recommendation engineering case study, September 2025](https://research.atspotify.com/2025/9/generalized-user-representations-for-large-scale-recommendations) | Cold-start onboarding preferences and behavior-informed recommendations; freshness and model/version consistency matter. | Start with optional language/style preferences and Save/Hide feedback. A small sticker catalog does not justify copying Spotify's large embedding infrastructure or predicting its results here. |
| [Official WhatsApp sticker sample](https://github.com/WhatsApp/stickers/blob/main/Android/README.md) | 3–30 stickers per pack, 512px WebP, at most 100KB static / 500KB animated, bounded animation duration and tray image constraints. | Keep shared validators and official platform-specific handoff; add actual device acceptance rather than inferring success from a ZIP or provider unit test. |

### The eight repositories supplied by the user

All eight were inspected at pinned commits, recorded in
[REAL_MEDIA_CREATOR_PLAN_2026-10-16.md](REAL_MEDIA_CREATOR_PLAN_2026-10-16.md).
That is a historical inspected-source record, not a new claim about today's
upstream branch. No third-party bot/session/modified APK was run for this audit.

| Repository | Useful reference / restriction |
| --- | --- |
| [methmal66/whatsapp-sticker-bot](https://github.com/methmal66/whatsapp-sticker-bot) | Text rendering workflow; GPL-3.0 code is not copied into our MIT source. It is not a complete AI photo/motion app. |
| [theabbie/sticker.ly](https://github.com/theabbie/sticker.ly) | Small old endpoint wrapper, not Sticker.ly's app codebase. Do not depend on its undocumented catalog endpoint. |
| [WhatsApp/stickers](https://github.com/WhatsApp/stickers) | Official Android provider and different iOS handoff; primary interoperability reference. |
| [indiser/Sticker-Vault-App](https://github.com/indiser/Sticker-Vault-App) | Pack browser/provider behavior; inspected tree's missing root license requires caution. No custom code/art copied; repository GPL guardrails remain. |
| [colt005/whats_sticky](https://github.com/colt005/whats_sticky) | Background removal/video conversion workflow ideas; no root license found in inspected tree. Research only. |
| [johnparkerg/simple_sticker_bot](https://github.com/johnparkerg/simple_sticker_bot) | Incoming-image conversion shortcut; account sessions, full-message logging and reconnect behavior are not a consumer app architecture. No root license found in inspected tree. |
| [LxArmitage/stickerbridge](https://github.com/LxArmitage/stickerbridge) | Bounded encoding/alpha/validation and iOS export concepts. Upstream phone results are not our physical-phone acceptance. |
| [STICKEREST/stickerest](https://github.com/STICKEREST/stickerest) | Content-led cards, tags, Favorites and creator navigation. Its public-upload approach is not adopted without our privacy/rights/moderation boundary. |

### Integration choices

| Tool/provider | Decision | Conditions |
| --- | --- | --- |
| [Groq vision](https://console.groq.com/docs/vision) | Reuse existing server-side caption/visual-understanding integration. | Documented image interpretation/captioning returns text; this is not original image synthesis. Do not ship keys in apps. |
| [Groq quotas](https://console.groq.com/docs/rate-limits) | One coordinated budget using actual account headers/limits. | Limits apply at organization level; two same-organization keys do not guarantee double free capacity. No new key is needed for this audit. |
| Existing Pillow/FFmpeg/native codecs | Keep validated conversion foundations and improve composition/quality. | Preserve output budgets, alpha, EXIF orientation and bounded processing. Check complex text support with [Pillow RAQM](https://pillow.readthedocs.io/en/stable/reference/ImageFont.html), appropriate fonts and actual language tests. |
| [memegen](https://github.com/jacebrowning/memegen) | Evaluate MIT template/overlay/self-hosting patterns; reuse only where it improves our renderer. | Code license does not clear template artwork. Never put private photos/captions into a public image URL merely to render a meme. |
| Owned/commissioned/licensed media and Commons/local adapters | Primary source of persistent sticker-pack media. | Record author, original URL, license, attribution, derivative permissions and restrictions per source. Verify each asset; do not treat an entire provider as rights-cleared. |
| [KLIPY API](https://docs.klipy.com/) | Candidate for a separately scoped GIF/sticker discovery trial. | [API terms](https://klipy.com/support/api-terms) require attribution and govern licensed use. Confirm transformation, persistent download and WhatsApp redistribution permission before building a pack factory around it. No unrestricted-rehosting claim. |
| [GIPHY](https://developers.giphy.com/docs/) | Conditional discovery integration, not an assumed downloadable source warehouse. | Current guidelines require attribution/direct client loading and restrict caching/proxying/reordering/mixing provider grids unless specifically approved. Obtain approval for the intended use before persistent conversion/export. |
| [Tenor developer API](https://support.google.com/tenor/answer/10455265?hl=en) | Exclude as a new dependency. | Official notice: new keys/integrations stopped January 13, 2026; API decommissioned June 30, 2026. Its consumer website remaining available does not restore API access. |
| Pinterest | Optional approved reference browsing/bookmarks, independent of content supply. | [Current reconnection record](PINTEREST_RECONNECTION.md) now confirms app 1624615 Trial access active. Redirect/credentials/OAuth and live board acceptance remain open. Do not scrape or treat Pins as redistribution licenses. |
| Original-image provider / smaller local model | Optional measured experiment after the caption/conversion flow is useful. | Existing worker preparation is not live quality proof. Compare output quality, memory, latency and per-usable-pack cost with approved trial spending; no monthly purchase assumed. |

No extra provider is necessary to fix the five-sticker delivery gate, improve
upload browsing or connect native account-owned caption jobs. Adding a provider
cannot substitute for those missing product integrations.

## Intended user flows

### Automatic general-audience content

Trend/evergreen brief → rights-cleared source → contextual Groq captions → local
render/validation → **ready batch draft immediately** → operator review → public
publication → mobile Discover → preview → explicit Save → Add to WhatsApp.

The daily target is a scheduling/budget target, not the transaction boundary for
content becoming useful. Keep each completed batch recoverable across midnight.
Review and public publication remain explicit. A future preapproved-source policy
would need its own reviewed definition; it is not permission to auto-publish
unreviewed generated content now.

### Personal AI creation

Create → choose one photo/clip → choose language/emotion → explicitly allow online
AI for that input → queued/result state → preview and select variants → choose
static/animated pack → Save locally → optional authenticated backup/share.

Account-owned output may appear automatically in Your creations when the job
finishes. This is different from automatically downloading it into the saved
Library. Uploading a whole private collection for recommendations is not needed.

### Website upload and reference use

Upload / Media → choose files → preview → choose purpose and destination → set
rights/title/tags → save source or reference → choose Create/Caption/Template →
review results → choose pack → personal phone transfer or separate public review.

The current open shared workspace must say that uploaded sources are shared.
Private phone creation requires account-scoped APIs. Reference-only assets can
inform a style/topic request without automatically becoming a rendered sticker;
the app must not suggest that choosing Reference gives redistribution rights.

### Suggested navigation

| Surface | Primary destinations | Supporting actions |
| --- | --- | --- |
| Android / iPhone | Library, Create, Discover | Import, backups and settings remain secondary; Your creations lives under Create; Fresh packs lives under Discover |
| Operator website | Overview, Upload / Media, Packs, AI workbench | Content briefs, queue/activity, publication, health and settings remain accessible without crowding the first task |
| Consumer web | Pack preview/open app, later selected creation tools | Never expose operator actions as normal consumer navigation |

## Build order and completion criteria

| Stage | Scope / dependencies | Must be demonstrated before calling it done |
| --- | --- | --- |
| 1. Make existing generation useful | B01–B04, F01/F02/F09; preserve budgets/provenance. | An incomplete daily target still yields a valid previewable five-sticker draft; recovery is idempotent and no new model call is needed just to recover storage/delivery. Then review/publish one authorized pack and read it back in both catalogs. |
| 2. Make uploads understandable | B05–B10, B12/B13, F11–F18; normalize derivatives and retain purpose/rights. | A normal user finds a named image beyond item 100, distinguishes reference/source/result, creates a pack and follows a working phone handoff. No false private labels or silent rights upgrade. |
| 3. Connect native personal AI | B11, F03–F07, F34, F52/F58 and V04/V05/V11. | Both actual native clients use account-owned jobs with real provider evidence, shared quotas, cancellation/restart and preview/save. Do not route user jobs through open operator APIs. |
| 4. Build the content product | F19–F26, F36–F42 alongside the stages above. | A reviewed diverse starter catalog is usable on phone; caption/crop/motion quality is accepted by target users; seven-day service evidence supports any daily-output promise. |
| 5. Finish editing, backup and sharing | F27–F33, F44–F50; compatible project/schema changes. | Reversible high-quality editing, durable photo/video projects, understandable backup/conflicts and correct private/public links; old archives/data retained. |
| 6. Ship a real iPhone beta | F51–F55, V03/V08/V10; share server/media contracts from earlier stages. | Signed installable artifact, source-bound Apple CI, real-device creation/import/AI/WhatsApp evidence and distribution metadata. No Mac availability is assumed. |
| 7. Broader community/platforms | F35/F39/F40/F43/F49/F57 plus operational maturity. | Moderation, privacy, abuse limits, rights and platform-specific acceptance precede public growth. Optional social/export features do not block making the core app useful. |

Stages are dependencies rather than calendar estimates. Content curation and
account-independent iPhone work can continue while device/distribution access is
unavailable. A large architecture rewrite is not the first step.

The first coherent implementation should span **ready automatic batch → visual
web review → reviewed mobile pack**, with a real photo/animated source and exact
save behavior. In parallel scope, complete the Upload / Media source-purpose
flow. Native personal AI is the next connected journey. This addresses missing
memes and uploads directly instead of spending the entire iteration on small
editor buttons.

## Measures of success

- **Useful content:** unique validated stickers and ready/reviewed packs, not raw
  model calls or target counters. Track rejection reasons and source diversity.
- **Delivery:** time from completed render to reviewable draft, publication to
  catalog visibility, and user save success; alert on stale/missing stages.
- **Reliability:** seven-day ready-batch rate, overdue job age, recovery without
  duplicate provider calls, actual rate-limit usage and per-usable-output cost.
- **Usability:** first pack creation/save time, source-find success, abandoned
  jobs, clear error recovery and target-user reaction quality feedback.
- **Data safety:** unchanged pre-existing library across failure/retry/upgrade,
  no implicit saves/uploads, scoped personal data, accurate audience labels and
  compatible old backups. Never log whole private captions/media as telemetry.
- **Platform truth:** signed install/device evidence is separate from CI,
  simulator checks, provider mocks, live catalog reads and source audits.

## Requirement traceability

R41–R43 record this October 22 request without discarding earlier decisions.
Every earlier ID is retained; some rows below group related requirements.

| Requirements | Backlog / preserved boundary |
| --- | --- |
| R01, R02, R04, R07 | F46/F50 and V01–V03; actual migration/WhatsApp acceptance, no unsupported in-app account routing |
| R03, R22 | F01/F05/F41: automatic creation/feed freshness with explicit local Save; R03's former auto-import request stays superseded |
| R05, R08, R25, R36 | F36–F44, V07/V08/V12; professional populated consumer navigation |
| R06, R26 | F27–F35, F53, V06/V10; creation/editor/project completeness |
| R09, R23, R33, R38, R41 | This dated evidence-backed audit and the unchanged requirement identities; no blanket completion claim |
| R10, R34, R35, R39 | B01–B04, F09/F55/F59, V09; keep verified release links/checksums and distinguish successful CI from product delivery |
| R11, R17, R27, R29 | F19–F26 and integration research; real licensed reactions/motion, not emoji filler or a copied catalog |
| R12, R15, R42, R43 | B01–B12, F01–F18, V04/V12; automatic generation, useful website upload and complete phone journey |
| R13 | F29/F45/F48 and V10; local private/mature packs, separate kinds, compatible backups and sharing |
| R14, R20 | F40/F57, V05/V11; operational moderated public sharing after its safety gates |
| R16 | Pinterest integration decision above; approved references remain independent of licensed supply |
| R18 | D03/V06; personal style/media review only when explicitly available and authorized, not a claim to have inspected the user's phone |
| R19 | F47/F51, V05/V10; optional private cloud and local-first recovery |
| R21, R28, R40 | B08, F03/F58, D01; R40 supersedes owner login, while personal APIs need separate account isolation |
| R24, R32 | F51–F55, V03; two native clients plus shared rules, signed physical iPhone acceptance still open |
| R30 | D04; compatible architecture changes permitted, unnecessary rewrite avoided |
| R31 | F03/F04/F06/F22/F24/F34; Groq-first research/caption/media helper with native UX |
| R37 | F08/D02; original artwork is optional prepared work requiring its own live trial |

This audit intentionally leaves identified work open. Documentation, mock probes
and a passing prior release are not completion of the new backlog.
