# Core product review — 2026-10-15

> Historical review preceding the user's recovery request. Automatic library
> delivery below was superseded by R22: opening/resuming never imports packs.
> Current flows/gaps: [acceptance audit](REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md),
> [product spec](PRODUCT_SPEC.md) and [recovery progress](RECOVERY_PROGRESS.md).

This is a source audit and implementation record, not a device certification. The goal is a simpler Sticker.ly-style create/import/edit/use flow with automatic approved sticker delivery. It does not promise every feature of Sticker.ly or unrestricted access to another application's accounts/catalog.

## Review against the user's ten points

| Point | Current flow | Remaining acceptance / limitation |
| --- | --- | --- |
| 1. Easy WhatsApp import/export | Photos → automatic packs → Add to WhatsApp or Business. Preflight checks count, bytes, hash, format, dimensions and tray. WhatsApp rejection reason is reported. | Actual phone export failure was not reproduced; no phone was connected. Install the candidate and confirm acceptance. |
| 2. Existing stickers | Import → phone folder or ZIP; recursive scan, dedup, metadata grouping and resume. | Android grants access only to selected accessible files. 10k+ process-death and phone-picker acceptance remain pending. |
| 3. Automatic AI stickers | Factory generates private candidates; approved published revisions automatically enter the library, up to three per refresh. | Live public catalog returned NoSuchKey on October 15. Nothing can be delivered until reviewed packs are published. Groq generates captions; current visual rendering is local templates/media, not a dedicated AI-image model. |
| 4. Multiple accounts | Explicit personal WhatsApp and Business targets, installed/added checks. | Account selection inside one WhatsApp installation and Samsung clones have no supported routing in this integration. |
| 5. Read existing requirements | README, migration guide, handoff, QA, product spec, ledger and Android implementation reviewed. | Ledger preserves active/pending/updated/removed history. |
| 6. Better Sticker.ly-style core | One-step photo selection, visual library, search/filter/sort, independent local packs, offline use and automatic approved delivery. | Background removal, animated pixel editing and wider discovery/favorite tools remain future work. |
| 7. Edit/update | Native static artwork editor: rotate, mirror, zoom/crop, reposition and top/bottom captions. Labels/tags/category/rename, copy/move/delete already exist. Create destination packs inside Copy/Move. | UI and animation acceptance on phone still pending. No automatic background removal yet. |
| 8. Sync add/update/delete | Stable pack ID, incremented image_data_version, provider notifications and immutable new artwork filenames. Already-added packs open the selected WhatsApp app to refresh through its API. | WhatsApp controls cache timing; first add requires confirmation. Removing a whole pack from WhatsApp or reading private databases is not a two-way API. |
| 9. Requirement history | REQUIREMENTS_LEDGER.md preserves IDs and requirements with dated changes. | Continue adding every user change there. |
| 10. Complete quickly | Focus Android checks on editor, import, backup compatibility, export preflight, library query and build/lint. Existing server code is retained. | Signing, phone acceptance, live catalog publication and hosted admin/Studio remain separate production tasks. |

## Gaps corrected in this continuation

- Replaced metadata-only editing with an actual local static artwork editor, while retaining metadata editing separately.
- Added photo import without requiring a user to create/name/type a pack first. It splits batches at 30, deduplicates and removes empty failed/duplicate packs.
- Added direct creation of a destination pack in Copy/Move; a one-pack library no longer disables this feature.
- Artwork replacement preserves identity and metadata, uses a new immutable filename for caches, rejects stale saves/duplicates, and keeps the original if validation/database update fails.
- Added WhatsApp preflight diagnostics and a correct Open action for installed packs instead of repeatedly sending the initial Add intent.
- Replaced one full sticker query per pack with a single bounded preview query. Refresh cancels older requests and captures the selected pack to avoid showing another pack's stickers during navigation.
- Automatic/manual catalog imports share a lock; tracking uses exactly the IDs returned by restore rather than a before/after database diff that could include concurrently created personal packs.
- Normal operations release their busy state before showing a snackbar, so a message no longer prevents the next action.
- Photo decoding uses Android ImageDecoder so EXIF rotation is respected without adding another library.
- Privacy policy wording now matches automatic pack downloads. No keys or personal sticker collections were added to Git.

## Release position

Candidate 0.5.0 preserves SQLite v7 and backup schema v5 with support for schemas 1–4. New editor code uses platform APIs and existing Compose dependencies. This review does not establish signed-release, phone, hosted-admin or daily-public-delivery readiness. See the task response and CI link for final validation results.
