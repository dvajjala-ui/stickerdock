# StickerDock product execution tracker

Updated October 22, 2026. The complete 90-item, priority-ordered inventory,
product flows, research links and requirement mapping are in
[PRODUCT_AUDIT_AND_BACKLOG_2026-10-22.md](PRODUCT_AUDIT_AND_BACKLOG_2026-10-22.md).
This tracker records implementation against that inventory. A source change and
passing local test are not evidence that the deployed site, phones or WhatsApp
have accepted it.

| Item | Current state | Evidence and next acceptance |
| --- | --- | --- |
| B01 / F01: deliver partial daily batches | **Source implemented; production unverified** | Each committed batch has a saved range and can become an idempotent, private Admin review draft before 40/40. A replay preserves owner edits. Focused Python tests pass. After deployment, generate or recover a real licensed-media batch, inspect its draft and provenance, review it, publish one valid GENERAL pack and read it back in the phone catalog. Historical media already pruned from older runs cannot be reconstructed from counters alone. |
| F02: durable lifecycle | **Partial** | State, render and batch-draft replay now exist; a stage-specific outbox, previous-day recovery and explicit dead-letter/operator retry are still needed. |
| B02: close old UTC runs | **Open** | Old partial rows still say running. Add terminal partial/failed states without discarding surviving media; do not fabricate packs for pruned days. |
| B03 / F09: truthful delivery health | **Open** | Schema readiness is separate from recent generation, draft and catalog freshness. Add timestamps and alerts that expose missing stages. |
| B05/B06: usable uploaded-media browsing | **Source implemented; hosted UI unverified** | Media cards now lazy-load bounded private thumbnails and existing human titles/rights from source metadata. The full image stays behind the explicit Preview action. Verify hosted grid, failure fallback, keyboard access and narrow layouts. Studio's source picker still needs the same human-title treatment. |
| B07: media pagination | **Partial** | Admin can request and append pages beyond the first 100, with duplicate paths suppressed. Storage offset pagination can shift during concurrent uploads; implement a stable cursor/search contract and carry it through Studio before closing B07. |
| B09: upload metadata privacy | **Source implemented; hosted acceptance open** | New uploads apply EXIF orientation, normalize into bounded WebP and strip EXIF/GPS/text payloads while retaining alpha. Synthetic orientation/transparency tests pass. Verify representative real photos, wide-gamut colors and production upload/readback before calling it fully accepted. Originals are not stored by this path. |
| B10: upload rights purpose | **Open** | The current upload API still labels new sources `USER_OWNED` without an explicit rights/purpose choice. Add an explicit source/reference classification before consumerizing the flow. |
| B08 / B11 / F03–F05: native personal AI | **Open** | Operator AI remains a shared web workspace. Create authenticated account-owned jobs and native Create/Your creations on Android and iPhone; verify two-account isolation and explicit Save. |
| R16: Pinterest references | **Trial approved; connection unverified** | Developer dashboard showed app 1624615 Trial active. Live Admin status was `configured=false`, `oauth_ready=false`, `not_connected`. Save redirect, configure server-only app credentials, run read-only OAuth and verify a board response when browser access is restored. Do not archive Pins or feed them into distributable sticker output. |
| F19–F26: real meme content and quality | **Open** | Existing licensed photos/clips and conversion tools are a base, but the live public catalog was one four-sticker pack at audit. Evaluate reaction quality, transparent cutout, captions and motion with rights-cleared media and actual user testing; publish only reviewed packs. |
| F51–F55 / V03/V08/V10: iPhone beta | **Source/CI partial; device acceptance open** | Native iPhone source and Apple CI exist. No Mac or physical iPhone is available here for signing, installation or WhatsApp acceptance. Keep portable ZIP and API contracts aligned while building remaining parity. |

## Immediate work order

1. Finish B01 recovery hardening, full Python verification and deployment review;
   inspect an actual newly delivered batch. Do not present a synthetic fixture as
   a user-ready meme pack.
2. Verify the new upload normalization, lazy previews and media-page loading on
   the hosted site. Complete explicit rights purpose, stable search/pagination
   and the Studio source picker, then verify Upload → Create → Pack.
3. Add account-owned AI job APIs with strict ownership and provider reservation;
   connect both native creation flows without auto-saving generated results.
4. Build a reviewed real-media starter catalog and measure draft-to-catalog
   latency, sticker quality and save success on available devices.
5. Continue iPhone feature parity and cloud/archive compatibility; record Apple
   build, signing, device and WhatsApp gates separately.

Pinterest is a live inspiration/reference feature only. Sticker media must come
from user uploads or independently reusable sources. The full backlog remains
open where this tracker does not show concrete acceptance evidence.
