# StickerDock requirements ledger

## Product-wide audit and automatic memes — October 22, 2026

User asks for all identified bugs, missing implementation, product suggestions,
similar-product/case-study research and a complete pending-feature list. They
also request automatic meme creation visible in mobile and a website upload
destination for custom images that can become stickers or serve as references.
See [the dated audit and 90-item backlog](PRODUCT_AUDIT_AND_BACKLOG_2026-10-22.md).
This is an audit checkpoint, not completion of the proposed implementation.

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R41 | Audit all currently identified bugs, missing implementation, product gaps and improvements against source/live behavior and similar-product research | Audit complete; implementation open | October 22 read-only production/browser inspection, current source, synthetic local probes and official product/provider research produce 14 defect/operational items, 60 gaps/extensions, 12 acceptance gates and four decisions. Each has a priority and outcome. Findings are bounded by the inspected scope, not a guarantee of no other bugs. |
| R42 | Create useful memes automatically and make them accessible in the mobile product | Partial | Scheduled generation exists; October 17–21 runs report 15–25/40 while ordinary draft delivery requires a complete daily archive. The public catalog currently contains one four-sticker pack. Per-batch ready drafts, reliable publication/discovery and account-owned native AI/results remain open. Automatic creation/feed freshness does not reverse R22's explicit local Save requirement. |
| R43 | Provide a clear website custom-upload flow for sticker creation, templates and references | Partial | Media library already has Upload new meme and Studio has image upload/canvas. Add a coherent Upload / Media destination with purpose/rights/privacy selection, thumbnails/human names, pagination/search, motion/bulk inputs, pack creation and phone handoff. The R40 workspace is shared, not personal private storage. |

October 21 iPhone continuation (R13/R24/R32): tested 0.10.2/build 14 adds
bounded native PNG/JPEG photo ZIP conversion, alpha/EXIF orientation, normalized
dedup and static/animated segregation. Matching Apple CI (39606440645) passes an unsigned device build and 19 native /
1 UI tests with no failures/skips, including four new archive tests. PR #25 is
merged at 9940680. See IPHONE_PHOTO_ARCHIVES.md for artifact hashes and open gates.

October 21 received-motion continuation (R05/R06/R26): shared Android GIF/video
input now routes to the existing local animated creator for explicit trim,
caption, preview and save. Photo/pack routes stay separate; mixed or multiple
moving inputs fail before import. See RECEIVED_MOTION.md. Android 0.11.2 is published at 4225302 after matching
main CI and exact signed acceptance. Real licensed motion and a fresh Apple
archive round trip also pass on the signed branch candidate; fresh Apple CI
passes 19 native / 1 UI tests (39606440645). See QA_MATRIX.md and CREATOR_ACCEPTANCE_2026-10-21.md.
These checkpoints do not close broader native AI, content or physical-phone parity.

October 21 continuation: after pulling current main, resume the unfinished R05
Android Favorites slice on the redesigned Library. Retain the original saved
work separately; port shortcuts/actions/filtering without undoing the newer
editor, backup or WhatsApp behavior. Candidate 0.11.1 preserves SQLite 8/ZIP 5 and
passes unit/lint/build and signed on-screen acceptance and is published from
9029d13. PRs #22/#23 are merged; see QA_MATRIX.md for exact hashes and limits.
See FAVORITES.md. This is a continuation of R05/R06/R13, not closure of broader
AI, content, iPhone or physical-device requirements.

Updated 2026-10-22. Preserve IDs and history. Add new user requests here before implementation. Never delete a requirement; mark it Updated (with a replacement ID) or Removed with the user's decision and date. Active means still required; Partial and Pending identify unfinished work. Source implementation is not device acceptance.

Latest direction: [Product rebuild, October 18](PRODUCT_REBUILD_2026-10-18.md), with [verbatim user requests](PRODUCT_REBUILD_REQUEST_2026-10-18.md). Reconsider the full product, remove clutter and implement pack-centered creation/discovery/WhatsApp use; the complaints are examples, not the entire scope. The October 15 recovery/data guarantees and Android/iPhone requirements remain active. Automatic library population is superseded by R22.

## Product rebuild — October 18, 2026

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R36 | Rebuild the product around professional pack browsing, explicit creation, useful features and easy WhatsApp use, informed by multiple platforms and repositories | Partial | Android 0.11.0 adds pack artwork cards/search, named photo destinations, simpler Discover/settings, contextual WhatsApp actions, focused editor tools, retained masks/brush drafts, public codes and explicit received-pack import with retry. Editable backups and readable multiline action cards pass CI (run39205131984) and complete signed acceptance (run39205128245) at f1bcb0d; screenshots inspected. Schema/portable backup/signing identities retained. Physical segmentation/WhatsApp, richer content, HTTPS/private links, full-resolution/video projects and iPhone parity remain. |
| R37 | Prepare original AI artwork and coherent generated packs; keep working with existing Groq keys and no OpenAI key | Partial | Opt-in BFL original-image worker prepares separate reactions, durable request reservations, bounded submissions, source/artifact hashes and private review ZIPs with labels/tags. Mocked failure/recovery tests pass; no live provider call, purchase, key creation, deployment or public publication. Existing Groq path remains. Local 4 GB GPU is below the evaluated model's standard memory needs; quality/cost comparison and integration remain open. |
| R38 | Reconcile every requirement against the actual build and Sticker.ly's product/functionality; continue beyond the first redesign candidate | Active | STICKERLY_PARITY_AUDIT_2026-10-18.md reconciles all R01–R40 IDs. Current acceptance includes published Android 0.11.2, iPhone 0.10.2 native build/20 tests, SAF pause/process-stop and real reaction/portable interchange; see QA_MATRIX.md. The following f1bcb0d record is historical. Source f1bcb0d adds editable Android backups and large-text action fixes, passes CI at run39205131984 and full signed acceptance at run39205128245. Public codes/custom links exist; HTTPS/private links, native AI, content, full-resolution/video projects and iOS/physical acceptance remain open. Next native AI boundary is audited in NATIVE_AI_HANDOFF.md. No blanket parity claim. |

## Publication — October 19, 2026

User request: “push code abd upated latets apk and admin web site needed like”.

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R39 | Push the rebuilt source, update the latest signed Android APK and deploy the admin website | Published / verified | PR #21 merged at f129430; Android 0.11.0/build 15 is published after [main CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39208384285) and [signed acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39208384312) pass. Public APK/AAB/metadata checksums and prior-release signing identity match. Production admin/assets/about match source; supporting routes, signed-out session/auth boundary and desktop/mobile browser checks pass. No production writes in these hosted checks. Phone, AI, Pinterest and wider parity gates remain open. |

## Core user requirements

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R40 | Remove Admin password/account checks from frontend and APIs, open the live dashboard directly, retain all database data and push | Implemented / verified | Deployed from `a92de8c`. Shared Admin/Studio load directly with credential-free workspace APIs; owner session endpoint and login/preview code are removed. Database/schema/RLS, stored accounts/packs and private phone-cloud authentication are retained. [Main CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39214400846) passes 205 backend tests plus Android/shared tests, lint and builds. Production read-only/API and fresh-browser desktop/390 px checks pass; no production writes were used. See OPEN_ADMIN.md. |

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R01 | Simple sticker import and export to WhatsApp, easier than Sticker.ly | Partial | Phone folders, images, ZIPs and pack provider exist. Add/result handling and separate target status added; phone acceptance pending. |
| R02 | Import the user's existing stickers | Partial | SAF recursive folder scans, EXIF grouping, deduplication, resume and ADB ZIP helper. Private databases and complete third-party catalogs are unavailable. Signed AOSP SAF pause/resume/process-stop acceptance verifies 1,215 recognized inputs and preserves 10,003 existing stickers; see MIGRATION_ACCEPTANCE_2026-10-21.md. Galaxy large-library QA remains pending. |
| R03 | AI Factory delivers new stickers automatically into the user's library | Updated by R22 | User reversed automatic library addition on October 15. 0.7.0 source removes launch/resume imports; explicit preview/save is implemented and locally tested. Device acceptance remains pending. Generation/discovery remain required, with explicit user saving. |
| R04 | Send packs to multiple WhatsApp accounts | Partial | Separate personal WhatsApp and Business targets. Selecting an account inside one installation or Samsung cloned apps is unsupported by the documented API; keep as pending research, never promise routing. |
| R05 | Friendly complete app with fast search, previews and organization | Partial | Android Favorites with search/filter/move/Undo, verified in signed 0.11.1. Library/Create/Discover, secondary import tools with correct Back history, search/filters/categories/selection/copy/move/undo. Exact signed 0.10.1 passes real-artwork viewport, 360dp and 1.3x text checks. Native iPhone navigation/search/favorites implemented. Populated physical-phone design/accessibility/performance acceptance remains. |
| R06 | Create, rename, edit, update and delete stickers and packs | Partial | Pack CRUD, labels/tags, copy/move/remove and photo conversion exist. Static artwork supports transforms/captions, original-preserving history, masks and erase/restore brushes. Optional automatic cutout is implemented; physical model quality remains unverified. Animated creation exists, while editable video projects and advanced animated artwork editing remain pending. Web admin/Studio is separate. |
| R07 | Keep add/update/delete changes synchronized with WhatsApp | Partial | Pack image_data_version increments on changes. WhatsApp reads this to refresh cached content; added status and Add/Open actions use its official API. Artwork changes get new filenames. Timing is controlled by WhatsApp and requires device verification. Removing whole installed packs and two-way private database sync cannot be forced through this API. |
| R08 | Users see stickers without needing source/factory details | Partial | Create/Discover use user-facing wording and explicit previews/saves; Studio remains outside consumer navigation. Required source attribution remains visible where appropriate. Full visual/product acceptance remains pending. |
| R09 | Record all new/changed/removed requirements permanently | Active | This ledger is authoritative for status/history alongside the detailed product/admin plans. |
| R10 | Ship quickly with focused necessary verification | Partial | Published Android 0.11.2 at 4225302 passes 135 Android/12 shared/207 backend tests, lint/signature/alignment and exact signed-APK emulator acceptance (39605443793). Downloaded APK/AAB/metadata hashes match; source/run binding is in QA_MATRIX.md; device WhatsApp and operational release acceptance remain gates. |
| R11 | Daily trends: funny, serious, news, cricket, movies, series, politics and Indian/Hinglish content | Partial | Daily trend brief and scheduled private generation exist; actual daily capacity and approved delivery still need operational evidence. |
| R12 | Independent web admin on Vercel with CRUD, custom topics, uploads and image-to-sticker conversion | Partial | Hosted at stickerdock.vercel.app with open workspace access requested by R40; schema, reads, migration, source uploads, Creator queue/result, editing, review and publish verified. Pinterest approval and future team-account authentication remain separate pending items. |
| R13 | Static/animated separation, mature private packs, backups and direct sharing | Partial | Both clients implement separate kinds/private ratings/portable ZIP sharing. iPhone 0.10.2 adds native PNG/JPEG archive conversion and passes 19 native/1 UI tests; its actual photo/archive export imports byte-exactly on public Android 0.11.2. Android adds an explicit editable backup envelope for normalized originals, masks, saved recipes and drafts; eight backup tests and signed DocumentsUI export/restore/restart pass at f1bcb0d (runs39205131984/39205128245). See EDITABLE_BACKUPS.md. Existing portable v5 remains unchanged. iPhone editable restore, full-resolution/video projects, physical document providers and animated WhatsApp remain pending. |
| R14 | Moderated public community eventually | Pending | Rights review, identity, scanning, report/block, moderation and age protections required before public submissions. |
| R15 | Review the full admin → AI → client production flow as both a normal user and experienced owner; close discovered gaps | Active | Draft source additions/category editing, direct AI-result previews, unsaved-edit guards, readiness/setup diagnostics, 3–10 batch compatibility, safe audience transitions and bounded Studio AI uploads are implemented. Hosted operational checks are recorded in the October 15 entry; October 19 public deployment/auth boundary and desktop/mobile checks pass. The one published four-sticker pack passes checksum/media validation. Fresh authenticated recovery, daily content capacity and physical phone acceptance remain separate gates. |
| R16 | Correct Pinterest application, use real website/privacy URLs, integrate connection and daily operation in Admin | Partial | App 1624615 showed Trial access active on October 22. Admin OAuth/live browse/disconnect and daily health check are implemented; same-day live status remains `configured=false`, `oauth_ready=false`, `not_connected`. Redirect, server credentials, OAuth and an official board response remain to verify. Pin content is not archived or transformed into distributed stickers. |
| R17 | Funny real-image meme/reaction stickers rather than generic face/emoji templates | Partial | Production requires independently licensed photos/clips without template fallback. Three independent licensed source photos seeded; live Groq photo result verified. Four-sticker photo pack published and emoji demo unpublished. Portrait crops fill the reaction area. Four additional licensed local sample packs (12 real-image stickers, six animated) pass exact-byte explicit import/restart on the public 0.11.2 APK. They remain local trials, separate from reviewed public publication; see CREATOR_ACCEPTANCE_2026-10-21.md. Broader source variety/style acceptance remains ongoing. |
| R18 | Review imported personal phone stickers privately to learn desired style; optionally sync selected reference assets to private backend | Pending | No ADB device connected. Updated by R19: optional account cloud copy now implemented, private by default; actual user sync/style review pending. Preserve pack metadata and provenance; personal references do not grant public redistribution rights or model-training consent. |

## Android reliability and delivery — October 17, 2026

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R34 | Prioritize Android reliability, daily generation recovery, populated-library memory/scrolling, conversion, low-storage failures and large-text layouts; deliver the signed APK and working admin website | Partial | Android 0.10.2/build14 published; 99 Android/12 shared/189 Python tests, lint/signatures and exact-APK 10k-library/large-text/tablet/MP4 emulator checks pass in run39037711698. Admin deployed and live Factory resumes at5/40. Physical WhatsApp/Business and a normal full40/day remain acceptance gates. |
| R35 | Keep README APK and admin links current with every successful release | Complete | Stable latest APK, release manifest/checksums and Admin links are live in README/site. Main Android changes run verified signed publication; failed runs leave the previous release. Existing Vercel production Git integration updates the admin. User confirmed release-driven updates, not chat maintenance reminders. |

## History

- 2026-10-16 continuation: user asks to audit every Markdown requirement against
  source/evidence, implement the iPhone client and integrations, optimize the
  product and improve UI/navigation. User confirms no Mac is available. R33
  records this work; Apple compilation/signing/device acceptance cannot be
  inferred from Windows source checks.

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R33 | Full requirement audit, iPhone implementation and coherent optimized navigation | Partial | All 35 Markdown files inventoried and ledger requirements audited against source/evidence. Native iPhone/shared builds and 19 native / 1 UI tests pass in current Apple CI (39606440645), including motion/rotation, rollback and archives. A fresh native Apple photo export imports on Android with private rating and exact bytes retained. Apple signing and physical acceptance remain pending. Signed Android 0.10.0 passes Create/import Back, artwork-first layout, large text, explicit save/restart and data preservation. Full parity, physical apps and operational/security/performance gates remain. See REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md. |

- 2026-10-15 recovery request: user rejects current UI/functionality and requests deep research and a bottom-up plan before rebuilding. Stop automatic additions at launch; preserve existing data. Verify each feature on real devices before declaring it complete. Add Android/iPhone parity, professional visual discovery/editor flows, dependable real-image/animated creation, and independent content sourcing. Investigate Sticker.ly as a product reference without assuming its source or catalog is reusable. See R22–R28 below.

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R22 | Opening/resuming must not automatically add stickers; downloads, backup and publication are explicit | Partial | 0.7.0 read-only discovery and explicit preview/save implemented; local refresh/deletion/revision tests pass. Existing packs retained. Emulator preview/save/restart checks pass; physical-device acceptance pending. |
| R23 | Research-led recovery with every core feature revalidated before advancing | Partial | Detailed recovery plan records source/live findings, phases, failure cases and completion gates. Local recovery is advancing in tested slices; physical-device/product acceptance remains pending. Historical Factory 429 and newer scheduled exits are separate from proven daily capacity. |
| R24 | Real Android and iPhone applications with platform-appropriate integration | Partial | Native iOS 17+ SwiftUI client and Kotlin shared core implemented; current Apple unsigned device/framework build, 19 native integration tests and 1 navigation UI test pass in public-repository macOS CI (39606440645). Android 0.10.0 passes local signed emulator checks. Expanded iOS motion/rotation/archive/read-only catalog tests pass in that run. Apple distribution signing, physical WhatsApp and full parity remain pending. |
| R25 | Professional visual discovery, library, editor and account experience | Partial | 0.7.0 adds artwork-led Discover, preview/save, a compact welcome, consistent icons and neutral surfaces. Emulator checks found/fixed sheet/FAB/editor overlap. Full visual acceptance and remaining redesign are pending. |
| R26 | Dependable photo/meme/animated/AI creation with visible job status and recovery | Partial | 0.7.0 adds 429 recovery, undo/redo and verified file commits. 0.8.0 adds editable starting stickers/recipes, local draft autosave/Resume, atomic recipe/artifact saves and manual traced cutout. Signed 0.9.0 passes real native WebM/MP4/GIF trim/caption/preview/save/restart and rotated-portrait emulator checks. Current acceptance: RECOVERY_PROGRESS.md. Automatic segmentation, native AI suggestions, durable editable video projects, full-resolution/project backup and physical acceptance remain pending. |
| R27 | Content supply independent of Pinterest, with evaluated APIs/open-source tools | Partial | Commons/local media adapters, FFmpeg conversion and independently licensed human/animal trials exist; all eight reference repositories were reviewed. Broad reviewed catalog and durable attribution remain incomplete. GIPHY requires use-specific terms approval for persistent packs; no third-party catalog scraping or copying is implemented. |
| R28 | Usable secure owner admin access and production operations | Updated by R40 / operations partial | R40 intentionally replaced the former owner login with an open shared operator workspace. Do not restore a password gate without a new decision or describe these uploads/jobs as personal private data. Phone-cloud authentication remains separate. Accurate job health, rights review, resource controls, account-owned native AI and rollout/restore remain open; see the October 22 audit. |
| R29 | Broaden implementation around real WhatsApp reaction memes and animated media, with repository/product research | Partial | All eight supplied repositories and actual Pinterest/Sticker.ly previews inspected; findings/licenses in REAL_MEDIA_CREATOR_PLAN_2026-10-16.md. Signed native 0.9.1 creator passes local creation/save/upgrade checks. Three additional licensed human/animal source works produce 12 real-photo/animated trial stickers in four explicitly imported packs, retained across upgrade/restart. Focal framing and sharper previews address observed photo problems. Broad catalog and content-quality acceptance remain pending; small trials are not catalog completion. Synthetic fixtures remain data-integrity evidence only. |
| R30 | Permit architectural changes needed for a usable two-platform product | Active | October 16: user authorizes architecture changes and reference-code investigation. Prefer measured improvements and compatible migrations; approval does not imply private-endpoint access, third-party media rights or a verified iPhone build. API credentials and deployment retain their explicit setup gates. |
| R31 | Groq-first media helper: research trends separately, suggest context-aware captions and create packs from supplied photos/videos/GIFs | Partial | Signed 0.9.0 native video/GIF trim/caption/preview/save works offline and passes emulator acceptance. Local worker adds six-frame motion context and revised Groq caption instructions; these worker changes are not deployed or live-tested. Native Groq suggestions, shared provider budgets and full assistant UX remain pending. Paid image synthesis is optional; no OpenAI purchase is required for conversion and two same-organization keys do not double quota. |
| R32 | One maintainable Android/iPhone product, with shared code where useful | Partial | Shared Kotlin media/rules core is consumed by Android and the actual native SwiftUI client. Current Apple CI (39606440645) passes unsigned device build and 19 native / 1 UI tests, including motion/rotation and read-only catalog checks. Dependency resolution is locked; Apple-to-Android portable byte preservation passes on simulators/emulators. Shared screens are an optional future migration; cloud/editor/AI parity, distribution signing and physical WhatsApp acceptance remain pending. See CROSS_PLATFORM_AND_PRODUCT_DIRECTION.md. |

- 2026-10-16: user requests broader real-media/animated/AI work and supplies methmal66/whatsapp-sticker-bot, theabbie/sticker.ly, whatsapp/stickers, indiser/Sticker-Vault-App, colt005/whats_sticky, johnparkerg/simple_sticker_bot, LxArmitage/stickerbridge and STICKEREST/stickerest. All are cloned read-only under ignored work/reference-repos; no dependency install or third-party executable was run. Color-shape QA is data-integrity evidence only, never a content-quality sign-off. See R29/R30.

- 2026-10-15 R21: user requests a live complete Vercel admin portal without
  manually entering an owner access token or backend key. Replace token entry
  and preview mode with normal owner sign-in and server-managed sessions;
  retain authentication for destructive and publishing operations.

- 2026-10-15 new cloud-library request (R19/R20): retain local stickers and add
  opt-in authenticated private cloud copies with categories (unknown = All).
  Add per-pack public opt-in, rights confirmation and owner moderation; keep
  mature packs offline. Provide a new signed testing APK after focused checks.

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R19 | Local library plus private account cloud sync, category metadata and restore | Partial | 0.6.0 adds confirmed-account private backup, checksums/checkpoints, categories/All, periodic retry and non-destructive restore. Migration applied with RLS verified. SMTP setup explicitly deferred by user; new-account email delivery and phone acceptance remain pending. |
| R20 | User-controlled public sharing so others can use approved packs | Partial | Per-pack opt-in/withdrawal, adult/rights confirmation, immutable submissions, admin previews/review drafts, reports and hide creator/pack controls. Live two-account smoke passed (run 38716261393). Physical phone acceptance pending. |
| R21 | Live admin portal with no manually pasted owner access token or key | Updated by R40 | October 19: owner login superseded by the explicitly requested open dashboard. Earlier implementation: Normal owner email/password sign-in across Admin and Creator Studio, secure HttpOnly session/refresh/logout, password recovery and live-only initial view. Site root opens Admin. Operational token fallback retained for scripts. Email setup deferred by user; verified-owner access required. |

- 2026-10-15 Pinterest/image follow-up: new app 1624615 is submitted and Trial
  pending, replacing the old download use case with live reference browsing.
  Real public website/privacy/support links deployed. Temporary token probe is
  blocked by Pinterest (HTTP 401 consumer type); approval remains external.
  R18 clarification: phone import writes local SQLite only; user chose public web
  examples for this style review because no backend copy exists. Optional cloud
  library sync remains pending. No requirements removed.

- 2026-10-15 production continuation: Vercel admin/Studio deployed; protected
  Production credentials configured with explicit approval. Hosted owner reads,
  Factory migration, review and publication passed; one five-sticker GENERAL pack
  is available. Signed release CI passed. Background refresh form/navigation gap
  fixed with a focused browser regression. Device acceptance remains pending.

- 2026-10-15 follow-up: user reaffirmed R01–R10 and requested a complete Sticker.ly-like flow with advanced functions. Active work: direct photo-to-pack creation, real static artwork editing (rotate, mirror, zoom/position and captions), safer automatic delivery and faster large-library previews. Background removal, account routing inside WhatsApp and hosted production gates remain explicit pending items; no requirement removed.

- 2026-10-15: R01–R14 consolidated from the user's initial and current requests; no requirements removed. Clarified official WhatsApp constraints in R04/R07 while retaining requested account/sync outcomes.
- 2026-10-15: R03 updated from manual catalog import to bounded automatic delivery of unseen approved revisions. A user-deleted revision stays deleted until explicitly added again. New published revisions remain eligible.
- 2026-10-15: R05/R06 advanced with direct photo import, local static artwork editor and inline pack creation. R07 wording updated from Sync to Add/Open to describe the supported WhatsApp behavior. Live public catalog still returns NoSuchKey; delivery remains pending publication. No requirements removed.

## Remaining production acceptance

1. Install the signed release on Galaxy; test large SAF imports, pause/restart and separate WhatsApp/Business add, cancel and refresh journeys. Do not uninstall an existing library before backing it up.
2. Expand the small reviewed public catalog with coherent, licensed packs. Users explicitly preview/save; automatic library population remains superseded by R22.
3. Keep the configured release signing identity and source-bound checksums. Main publication and hosted verification are tracked in R39; physical phone acceptance remains separate.
4. Keep the R40 open Admin/Studio workspace behavior and private authenticated phone cloud separate. Direct startup, retry/navigation and desktop/mobile checks pass; optional future team-account onboarding would need its own design and acceptance. Automatic cutout quality, native AI, full-resolution/video projects and iPhone parity remain open.

- 2026-10-15 recovery implementation: local 0.7.0 removes automatic imports, adds preview/save and Discover artwork metadata, and exposes durable Factory cooldown/recovery. R25/R26 broader design and creation requirements remain active. Details and test boundaries: RECOVERY_PROGRESS.md. No hosted/device completion claimed.
