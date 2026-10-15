# StickerDock requirements ledger

Updated 2026-10-15. Preserve IDs and history. Add new user requests here before implementation. Never delete a requirement; mark it Updated (with a replacement ID) or Removed with the user's decision and date. Active means still required; Partial and Pending identify unfinished work. Source implementation is not device acceptance.

| ID | Requirement | Status | Implementation and remaining work |
| --- | --- | --- | --- |
| R01 | Simple sticker import and export to WhatsApp, easier than Sticker.ly | Partial | Phone folders, images, ZIPs and pack provider exist. Add/result handling and separate target status added; phone acceptance pending. |
| R02 | Import the user's existing stickers | Partial | SAF recursive folder scans, EXIF grouping, deduplication, resume and ADB ZIP helper. Private databases and complete third-party catalogs are unavailable. Galaxy large-library QA pending. |
| R03 | AI Factory delivers new stickers automatically into the user's library | Partial | Daily private generation and reviewed public catalog exist. Android now imports up to three unseen approved pack revisions per refresh. Publishing a reviewed public pack and end-to-end phone verification remain pending. |
| R04 | Send packs to multiple WhatsApp accounts | Partial | Separate personal WhatsApp and Business targets. Selecting an account inside one installation or Samsung cloned apps is unsupported by the documented API; keep as pending research, never promise routing. |
| R05 | Friendly complete app with fast search, previews and organization | Active | Latest adaptive Compose UI retained; library, import and New tabs, search, filters, categories, selection, copy/move and undo. Ongoing phone usability review. |
| R06 | Create, rename, edit, update and delete stickers and packs | Partial | Pack CRUD, labels/tags, copy/move/remove and photo conversion exist. 0.5.0 adds local static artwork rotate/mirror, zoom/crop, position and captions, plus inline destination-pack creation. Background removal and animated artwork editing remain pending. Web admin/Studio is separate. |
| R07 | Keep add/update/delete changes synchronized with WhatsApp | Partial | Pack image_data_version increments on changes. WhatsApp reads this to refresh cached content; added status and Add/Open actions use its official API. Artwork changes get new filenames. Timing is controlled by WhatsApp and requires device verification. Removing whole installed packs and two-way private database sync cannot be forced through this API. |
| R08 | Users see stickers without needing source/factory details | Active | New tab uses user-facing wording, hides creator/source implementation details and removes disabled Studio card. Provenance remains internal for review. Required attribution must still be respected. |
| R09 | Record all new/changed/removed requirements permanently | Active | This ledger is authoritative for status/history alongside the detailed product/admin plans. |
| R10 | Ship quickly with focused necessary verification | Active | Build and relevant Android regressions required; avoid repeating unrelated Python suites for Android-only changes. Device WhatsApp acceptance and release signing remain launch gates. |
| R11 | Daily trends: funny, serious, news, cricket, movies, series, politics and Indian/Hinglish content | Partial | Daily trend brief and scheduled private generation exist; actual daily capacity and approved delivery still need operational evidence. |
| R12 | Independent web admin on Vercel with CRUD, custom topics, uploads and image-to-sticker conversion | Partial | Admin and Studio source exist. Migration/hosting verification remains pending; see ADMIN_PORTAL.md and ADMIN_CONTROL_PLANE_PLAN.md. |
| R13 | Static/animated separation, mature private packs, backups and direct sharing | Partial | Source implemented. Mature stays local/private. Animated device acceptance and backup migration QA remain pending. |
| R14 | Moderated public community eventually | Pending | Rights review, identity, scanning, report/block, moderation and age protections required before public submissions. |

## History

- 2026-10-15 follow-up: user reaffirmed R01–R10 and requested a complete Sticker.ly-like flow with advanced functions. Active work: direct photo-to-pack creation, real static artwork editing (rotate, mirror, zoom/position and captions), safer automatic delivery and faster large-library previews. Background removal, account routing inside WhatsApp and hosted production gates remain explicit pending items; no requirement removed.

- 2026-10-15: R01–R14 consolidated from the user's initial and current requests; no requirements removed. Clarified official WhatsApp constraints in R04/R07 while retaining requested account/sync outcomes.
- 2026-10-15: R03 updated from manual catalog import to bounded automatic delivery of unseen approved revisions. A user-deleted revision stays deleted until explicitly added again. New published revisions remain eligible.
- 2026-10-15: R05/R06 advanced with direct photo import, local static artwork editor and inline pack creation. R07 wording updated from Sync to Add/Open to describe the supported WhatsApp behavior. Live public catalog still returns NoSuchKey; delivery remains pending publication. No requirements removed.

## Fastest remaining production path

1. Build current candidate and install on Galaxy; add a 3–30 sticker pack to personal WhatsApp and Business, then add/remove a sticker and verify refresh/Sync.
2. Apply the provided additive db.sql if still pending, review/publish one GENERAL Factory pack and verify automatic delivery in the APK.
3. Configure real release signing, publish policy/support details and distribute a signed candidate after phone acceptance.
4. Verify hosted admin/Studio and then implement the pending Android pixel editor. Do not equate debug APK compilation with production readiness.
