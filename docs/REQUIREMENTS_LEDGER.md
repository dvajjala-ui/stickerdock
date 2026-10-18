# StickerDock requirements ledger

Updated 2026-10-18. Preserve IDs and history. Add new user requests here before implementation. Never delete a requirement; mark it Updated (with a replacement ID) or Removed with the user's decision and date. Active means still required; Partial and Pending identify unfinished work. Source implementation is not device acceptance.

Latest direction: [Product rebuild, October 18](PRODUCT_REBUILD_2026-10-18.md), with [verbatim user requests](PRODUCT_REBUILD_REQUEST_2026-10-18.md). Reconsider the full product, remove clutter and implement pack-centered creation/discovery/WhatsApp use; the complaints are examples, not the entire scope. The October 15 recovery/data guarantees and Android/iPhone requirements remain active. Automatic library population is superseded by R22.

## Product rebuild — October 18, 2026

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R36 | Rebuild the product around professional pack browsing, explicit creation, useful features and easy WhatsApp use, informed by multiple platforms and repositories | Partial | Android 0.11.0 candidate adds larger pack previews, pack-grouped metadata search, named/existing photo destinations, simplified Discover/settings and contextual WhatsApp actions. Inactive duplicate activity removed; schema, backup and signing identities retained. Research/architecture decisions recorded. Candidate verification is in QA_MATRIX.md; physical acceptance, automatic cutout, richer reviewed catalog, pack links and iPhone parity remain. |
| R37 | Prepare original AI artwork and coherent generated packs; keep working with existing Groq keys and no OpenAI key | Partial | Opt-in BFL original-image worker prepares separate reactions, durable request reservations, bounded submissions, source/artifact hashes and private review ZIPs with labels/tags. Mocked failure/recovery tests pass; no live provider call, purchase, key creation, deployment or public publication. Existing Groq path remains. Local 4 GB GPU is below the evaluated model's standard memory needs; quality/cost comparison and integration remain open. |
| R38 | Reconcile every requirement against the actual build and Sticker.ly's product/functionality; continue beyond the first redesign candidate | Active | STICKERLY_PARITY_AUDIT_2026-10-18.md reconciles all R01–R38 IDs. Core candidate has signed evidence at db6c979/run39070094081. Automatic cutout, retained masks and erase/restore brushes are now implemented; newer verification is tracked separately in QA_MATRIX.md. Pack links, editable backup, native AI, content and iOS/physical acceptance remain open. No blanket parity claim. |

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R01 | Simple sticker import and export to WhatsApp, easier than Sticker.ly | Partial | Phone folders, images, ZIPs and pack provider exist. Add/result handling and separate target status added; phone acceptance pending. |
| R02 | Import the user's existing stickers | Partial | SAF recursive folder scans, EXIF grouping, deduplication, resume and ADB ZIP helper. Private databases and complete third-party catalogs are unavailable. Galaxy large-library QA pending. |
| R03 | AI Factory delivers new stickers automatically into the user's library | Updated by R22 | User reversed automatic library addition on October 15. 0.7.0 source removes launch/resume imports; explicit preview/save is implemented and locally tested. Device acceptance remains pending. Generation/discovery remain required, with explicit user saving. |
| R04 | Send packs to multiple WhatsApp accounts | Partial | Separate personal WhatsApp and Business targets. Selecting an account inside one installation or Samsung cloned apps is unsupported by the documented API; keep as pending research, never promise routing. |
| R05 | Friendly complete app with fast search, previews and organization | Partial | Android Library/Create/Discover, secondary import tools with correct Back history, search/filters/categories/selection/copy/move/undo. Exact signed 0.10.1 passes real-artwork viewport, 360dp and 1.3x text checks. Native iPhone navigation/search/favorites implemented. Populated physical-phone design/accessibility/performance acceptance remains. |
| R06 | Create, rename, edit, update and delete stickers and packs | Partial | Pack CRUD, labels/tags, copy/move/remove and photo conversion exist. 0.5.0 adds local static artwork rotate/mirror, zoom/crop, position and captions, plus inline destination-pack creation. Background removal and animated artwork editing remain pending. Web admin/Studio is separate. |
| R07 | Keep add/update/delete changes synchronized with WhatsApp | Partial | Pack image_data_version increments on changes. WhatsApp reads this to refresh cached content; added status and Add/Open actions use its official API. Artwork changes get new filenames. Timing is controlled by WhatsApp and requires device verification. Removing whole installed packs and two-way private database sync cannot be forced through this API. |
| R08 | Users see stickers without needing source/factory details | Partial | Create/Discover use user-facing wording and explicit previews/saves; private Studio remains hidden in consumer navigation. Required source attribution remains visible where appropriate. Full visual/product acceptance remains pending. |
| R09 | Record all new/changed/removed requirements permanently | Active | This ledger is authoritative for status/history alongside the detailed product/admin plans. |
| R10 | Ship quickly with focused necessary verification | Partial | Signed 0.10.1 APK/AAB, 97 Android/12 common tests, 178 Python/no skips, lint/signature/16 KB checks and exact-APK emulator acceptance pass. Android CI 81549ab passes. Source/run binding is in RECOVERY_PROGRESS.md; device WhatsApp and operational release acceptance remain gates. |
| R11 | Daily trends: funny, serious, news, cricket, movies, series, politics and Indian/Hinglish content | Partial | Daily trend brief and scheduled private generation exist; actual daily capacity and approved delivery still need operational evidence. |
| R12 | Independent web admin on Vercel with CRUD, custom topics, uploads and image-to-sticker conversion | Partial | Hosted at stickerdock.vercel.app with protected owner access; schema, reads, migration, source uploads, Creator queue/result, editing, review and publish verified. Pinterest approval and future team-account authentication remain separate pending items. |
| R13 | Static/animated separation, mature private packs, backups and direct sharing | Partial | Both clients implement separate kinds/private ratings/ZIP sharing. Android 0.10.1 accepts an actual native Swift export with exact bytes/name/MATURE/GENERAL and preserves all old rows after restart. Broader backup migrations, physical animated WhatsApp and full original/project backup acceptance remain pending. |
| R14 | Moderated public community eventually | Pending | Rights review, identity, scanning, report/block, moderation and age protections required before public submissions. |
| R15 | Review the full admin → AI → client production flow as both a normal user and experienced owner; close discovered gaps | Active | October 15 continuation implements draft source additions/category editing, direct AI-result previews, unsaved-edit guards, readiness/setup diagnostics, 3–10 batch compatibility, safe daily audience transitions and bounded Studio AI uploads. Release signing is configured with a private external backup. Hosted admin, live catalog publication and device acceptance remain separate gates. |
| R16 | Correct Pinterest application, use real website/privacy URLs, integrate connection and daily operation in Admin | Partial | New app 1624615 submitted with real URLs and Trial pending. Admin OAuth/live browse/disconnect and daily health check implemented. Temporary token blocked by Pinterest; live connection awaits platform approval and credentials. Pin content is not archived or transformed into distributed stickers. |
| R17 | Funny real-image meme/reaction stickers rather than generic face/emoji templates | Partial | Production requires independently licensed photos/clips without template fallback. Three independent licensed source photos seeded; live Groq photo result verified. Four-sticker photo pack published and emoji demo unpublished. Portrait crops fill the reaction area. Broader source variety/style acceptance remains ongoing. |
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
| R33 | Full requirement audit, iPhone implementation and coherent optimized navigation | Partial | All 35 Markdown files inventoried and ledger requirements audited against source/evidence. Native iPhone client plus shared core build and initial native/UI tests pass in public macOS CI; expanded motion checks caught a decoder-budget defect now repaired in source and under matching verification. Signed Android 0.10.0 passes Create/import Back, artwork-first layout, large text, explicit save/restart and data preservation. Full parity, physical apps and operational/security/performance gates remain. See REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md. |

- 2026-10-15 recovery request: user rejects current UI/functionality and requests deep research and a bottom-up plan before rebuilding. Stop automatic additions at launch; preserve existing data. Verify each feature on real devices before declaring it complete. Add Android/iPhone parity, professional visual discovery/editor flows, dependable real-image/animated creation, and independent content sourcing. Investigate Sticker.ly as a product reference without assuming its source or catalog is reusable. See R22–R28 below.

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R22 | Opening/resuming must not automatically add stickers; downloads, backup and publication are explicit | Partial | 0.7.0 read-only discovery and explicit preview/save implemented; local refresh/deletion/revision tests pass. Existing packs retained. Emulator preview/save/restart checks pass; physical-device acceptance pending. |
| R23 | Research-led recovery with every core feature revalidated before advancing | Partial | Detailed recovery plan records source/live findings, phases, failure cases and completion gates. Local recovery is advancing in tested slices; physical-device/product acceptance remains pending. Historical Factory 429 and newer scheduled exits are separate from proven daily capacity. |
| R24 | Real Android and iPhone applications with platform-appropriate integration | Partial | Native iOS 17+ SwiftUI client and Kotlin shared core implemented; first Apple unsigned device/framework build, 6 native integration tests and 1 navigation UI test pass in public-repository macOS CI. Android 0.10.0 passes local signed emulator checks. Expanded iOS motion/live-catalog tests need latest matching CI. Apple distribution signing, physical WhatsApp and full parity remain pending. |
| R25 | Professional visual discovery, library, editor and account experience | Partial | 0.7.0 adds artwork-led Discover, preview/save, a compact welcome, consistent icons and neutral surfaces. Emulator checks found/fixed sheet/FAB/editor overlap. Full visual acceptance and remaining redesign are pending. |
| R26 | Dependable photo/meme/animated/AI creation with visible job status and recovery | Partial | 0.7.0 adds 429 recovery, undo/redo and verified file commits. 0.8.0 adds editable starting stickers/recipes, local draft autosave/Resume, atomic recipe/artifact saves and manual traced cutout. Signed 0.9.0 passes real native WebM/MP4/GIF trim/caption/preview/save/restart and rotated-portrait emulator checks. Current acceptance: RECOVERY_PROGRESS.md. Automatic segmentation, native AI suggestions, durable editable video projects, full-resolution/project backup and physical acceptance remain pending. |
| R27 | Content supply independent of Pinterest, with evaluated APIs/open-source tools | Partial | Commons/local media adapters, FFmpeg conversion and independently licensed human/animal trials exist; all eight reference repositories were reviewed. Broad reviewed catalog and durable attribution remain incomplete. GIPHY requires use-specific terms approval for persistent packs; no third-party catalog scraping or copying is implemented. |
| R28 | Usable secure owner admin access and production operations | Partial | Portal and email/password entry previously verified; no default password is known, supplied or reset. Current hosted refresh, recovery mail/MFA, job health, rights review, user isolation and rollout/restore remain explicit gates. |
| R29 | Broaden implementation around real WhatsApp reaction memes and animated media, with repository/product research | Partial | All eight supplied repositories and actual Pinterest/Sticker.ly previews inspected; findings/licenses in REAL_MEDIA_CREATOR_PLAN_2026-10-16.md. Signed native 0.9.1 creator passes local creation/save/upgrade checks. Three additional licensed human/animal source works produce 12 real-photo/animated trial stickers in four explicitly imported packs, retained across upgrade/restart. Focal framing and sharper previews address observed photo problems. Broad catalog and content-quality acceptance remain pending; small trials are not catalog completion. Synthetic fixtures remain data-integrity evidence only. |
| R30 | Permit architectural changes needed for a usable two-platform product | Active | October 16: user authorizes architecture changes and reference-code investigation. Prefer measured improvements and compatible migrations; approval does not imply private-endpoint access, third-party media rights or a verified iPhone build. API credentials and deployment retain their explicit setup gates. |
| R31 | Groq-first media helper: research trends separately, suggest context-aware captions and create packs from supplied photos/videos/GIFs | Partial | Signed 0.9.0 native video/GIF trim/caption/preview/save works offline and passes emulator acceptance. Local worker adds six-frame motion context and revised Groq caption instructions; these worker changes are not deployed or live-tested. Native Groq suggestions, shared provider budgets and full assistant UX remain pending. Paid image synthesis is optional; no OpenAI purchase is required for conversion and two same-organization keys do not double quota. |
| R32 | One maintainable Android/iPhone product, with shared code where useful | Partial | Shared Kotlin media/rules core is consumed by Android and the actual native SwiftUI client. First Apple/Android CI passes, including unsigned iPhone build and native/UI tests. Exact dependency lock and expanded motion/live-provider checks are undergoing matching CI. Shared screens are an optional future migration; cloud/editor/AI parity, distribution signing and physical WhatsApp acceptance remain pending. See CROSS_PLATFORM_AND_PRODUCT_DIRECTION.md. |

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
| R21 | Live admin portal with no manually pasted owner access token or key | Partial | Normal owner email/password sign-in across Admin and Creator Studio, secure HttpOnly session/refresh/logout, password recovery and live-only initial view. Site root opens Admin. Operational token fallback retained for scripts. Email setup deferred by user; verified-owner access required. |

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

## Fastest remaining production path

1. Build current candidate and install on Galaxy; add a 3–30 sticker pack to personal WhatsApp and Business, then add/remove a sticker and verify refresh/Sync.
2. Apply the provided additive db.sql if still pending, review/publish one GENERAL Factory pack and verify automatic delivery in the APK.
3. Preserve the configured release signing key, publish policy/support details and distribute the signed candidate after phone acceptance.
4. Deploy admin/Studio to the newly authorized Vercel project and verify authenticated operation. Native static artwork editing is implemented; background removal and animated editing remain pending. Do not equate compilation with production readiness.

- 2026-10-15 recovery implementation: local 0.7.0 removes automatic imports, adds preview/save and Discover artwork metadata, and exposes durable Factory cooldown/recovery. R25/R26 broader design and creation requirements remain active. Details and test boundaries: RECOVERY_PROGRESS.md. No hosted/device completion claimed.
