# StickerDock requirements ledger

Updated 2026-10-15. Preserve IDs and history. Add new user requests here before implementation. Never delete a requirement; mark it Updated (with a replacement ID) or Removed with the user's decision and date. Active means still required; Partial and Pending identify unfinished work. Source implementation is not device acceptance.

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R01 | Simple sticker import and export to WhatsApp, easier than Sticker.ly | Partial | Phone folders, images, ZIPs and pack provider exist. Add/result handling and separate target status added; phone acceptance pending. |
| R02 | Import the user's existing stickers | Partial | SAF recursive folder scans, EXIF grouping, deduplication, resume and ADB ZIP helper. Private databases and complete third-party catalogs are unavailable. Galaxy large-library QA pending. |
| R03 | AI Factory delivers new stickers automatically into the user's library | Partial | Android imports up to three unseen approved revisions per refresh. Four-sticker real-photo pack is published and checksum verified; live Creator/Groq request completed. Normal-cooldown 40/40 daily completion and end-to-end phone verification remain pending. |
| R04 | Send packs to multiple WhatsApp accounts | Partial | Separate personal WhatsApp and Business targets. Selecting an account inside one installation or Samsung cloned apps is unsupported by the documented API; keep as pending research, never promise routing. |
| R05 | Friendly complete app with fast search, previews and organization | Active | Latest adaptive Compose UI retained; library, import and New tabs, search, filters, categories, selection, copy/move and undo. Ongoing phone usability review. |
| R06 | Create, rename, edit, update and delete stickers and packs | Partial | Pack CRUD, labels/tags, copy/move/remove and photo conversion exist. 0.5.0 adds local static artwork rotate/mirror, zoom/crop, position and captions, plus inline destination-pack creation. Background removal and animated artwork editing remain pending. Web admin/Studio is separate. |
| R07 | Keep add/update/delete changes synchronized with WhatsApp | Partial | Pack image_data_version increments on changes. WhatsApp reads this to refresh cached content; added status and Add/Open actions use its official API. Artwork changes get new filenames. Timing is controlled by WhatsApp and requires device verification. Removing whole installed packs and two-way private database sync cannot be forced through this API. |
| R08 | Users see stickers without needing source/factory details | Active | New tab uses user-facing wording, hides creator/source implementation details and removes disabled Studio card. Provenance remains internal for review. Required attribution must still be respected. |
| R09 | Record all new/changed/removed requirements permanently | Active | This ledger is authoritative for status/history alongside the detailed product/admin plans. |
| R10 | Ship quickly with focused necessary verification | Active | Build and relevant Android regressions required; avoid repeating unrelated Python suites for Android-only changes. Release signing and local signed APK/AAB build are complete; current hosted CI and device WhatsApp acceptance remain launch gates. |
| R11 | Daily trends: funny, serious, news, cricket, movies, series, politics and Indian/Hinglish content | Partial | Daily trend brief and scheduled private generation exist; actual daily capacity and approved delivery still need operational evidence. |
| R12 | Independent web admin on Vercel with CRUD, custom topics, uploads and image-to-sticker conversion | Partial | Hosted at stickerdock.vercel.app with protected owner access; schema, reads, migration, source uploads, Creator queue/result, editing, review and publish verified. Pinterest approval and future team-account authentication remain separate pending items. |
| R13 | Static/animated separation, mature private packs, backups and direct sharing | Partial | Source implemented. Mature stays local/private. Animated device acceptance and backup migration QA remain pending. |
| R14 | Moderated public community eventually | Pending | Rights review, identity, scanning, report/block, moderation and age protections required before public submissions. |
| R15 | Review the full admin → AI → client production flow as both a normal user and experienced owner; close discovered gaps | Active | October 15 continuation implements draft source additions/category editing, direct AI-result previews, unsaved-edit guards, readiness/setup diagnostics, 3–10 batch compatibility, safe daily audience transitions and bounded Studio AI uploads. Release signing is configured with a private external backup. Hosted admin, live catalog publication and device acceptance remain separate gates. |
| R16 | Correct Pinterest application, use real website/privacy URLs, integrate connection and daily operation in Admin | Partial | New app 1624615 submitted with real URLs and Trial pending. Admin OAuth/live browse/disconnect and daily health check implemented. Temporary token blocked by Pinterest; live connection awaits platform approval and credentials. Pin content is not archived or transformed into distributed stickers. |
| R17 | Funny real-image meme/reaction stickers rather than generic face/emoji templates | Partial | Production requires independently licensed photos/clips without template fallback. Three independent licensed source photos seeded; live Groq photo result verified. Four-sticker photo pack published and emoji demo unpublished. Portrait crops fill the reaction area. Broader source variety/style acceptance remains ongoing. |
| R18 | Review imported personal phone stickers privately to learn desired style; optionally sync selected reference assets to private backend | Pending | No ADB device connected. Await backup/folder path. Local Android import does not upload a library. Preserve pack metadata and provenance; personal references do not grant public redistribution rights or model-training consent. |

## History

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
