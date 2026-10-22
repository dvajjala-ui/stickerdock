# StickerDock product execution tracker

Updated October 22, 2026. The complete 90-item, priority-ordered inventory,
product flows, research links and requirement mapping are in
[PRODUCT_AUDIT_AND_BACKLOG_2026-10-22.md](PRODUCT_AUDIT_AND_BACKLOG_2026-10-22.md).
This tracker records implementation against that inventory. A source change and
passing local test are not evidence that the deployed site, phones or WhatsApp
have accepted it.

| Item | Current state | Evidence and next acceptance |
| --- | --- | --- |
| B01 / F01: deliver partial daily batches | **Live private draft verified; publication open** | October 22 Factory run [39739493970](https://github.com/dvajjala-ui/stickerdock/actions/runs/39739493970) passed and persisted a 5/40 batch. Live Admin read-back showed a five-sticker private draft with per-sticker NASA/public-domain source metadata and five rendered WebP previews. Review found all five reused the same shuttle photo and some captions were weak; this batch remains private. No phone catalog or WhatsApp delivery is claimed. Historical media already pruned from older runs cannot be reconstructed from counters alone. |
| F02: durable lifecycle | **Partial** | State, render and batch-draft replay now exist; a stage-specific outbox, previous-day recovery and explicit dead-letter/operator retry are still needed. |
| B02: close old UTC runs | **Source implemented; hosted repair pending** | The next successful worker closes earlier `running` days with the existing database's `failed` status plus `groq_metrics.terminal_outcome=partial` when media survived. Counts and provider history remain intact, and the Admin view already labels stale rows `partial` before a worker repair. Verify production row read-back after deployment; do not fabricate packs for pruned days. |
| B03 / F09: truthful delivery health | **Source implemented; hosted acceptance pending** | Readiness now distinguishes schema availability from recent generation, private review-draft delivery and catalog publication age. Health exposes each stage and a separate `delivery_fresh` flag; schema-only `worker_ready` no longer implies content freshness in UI. Focused tests pass. Verify hosted checks against actual timestamps and add alerts/operator paging later. |
| B05/B06: usable uploaded-media browsing | **Source implemented; hosted UI unverified** | Media cards now lazy-load bounded private thumbnails and existing human titles/rights from source metadata. The full image stays behind the explicit Preview action. Verify hosted grid, failure fallback, keyboard access and narrow layouts. Studio's source picker still needs the same human-title treatment. |
| B07: media pagination | **Partial** | Admin can request and append pages beyond the first 100, with duplicate paths suppressed. Storage offset pagination can shift during concurrent uploads; implement a stable cursor/search contract and carry it through Studio before closing B07. |
| B09: upload metadata privacy | **Source implemented; hosted acceptance open** | New uploads apply EXIF orientation, normalize into bounded WebP and strip EXIF/GPS/text payloads while retaining alpha. Synthetic orientation/transparency tests pass. Verify representative real photos, wide-gamut colors and production upload/readback before calling it fully accepted. Originals are not stored by this path. |
| B10: upload rights purpose | **Source implemented; hosted acceptance open** | Admin Media and Studio now ask for reusable source versus reference-only intent. Unspecified API uploads default to `REFERENCE_ONLY`; edited results inherit source rights and carry `RESULT` purpose. Test the hosted flow, including old media, and add richer provenance/rights-review evidence before calling public publication automatic. |
| B11: duplicate Creator submissions | **Source implemented; hosted acceptance open** | Admin and Studio send a persistent request UUID through retries; the server returns the existing job if its source/prompt match, even after a primary-key race. A changed payload cannot reuse the same identity. Deliberate new submissions receive a new UUID. Verify real network-loss/reload behavior on the hosted site. |
| B12: Studio job recovery | **Partial source implemented** | Studio restores a pending source/prompt after reload and resumes status polling with the same request UUID when Generate is pressed. Durable project drafts, visible Retry/Cancel and real worker turnaround remain open. |
| B14: operational copy drift | **Partially corrected** | Factory and Creator scheduling docs now describe the actual 15-minute/10-minute checks, saved cooldowns and emergency pause. Older historical sections remain evidence snapshots; measure hosted dispatch/delivery latency before making time promises. |
| UI imagery / B03 status honesty | **Source improved; visual acceptance open** | Admin overview and pack cards no longer insert synthetic cat/emoji sticker art beside real packs. They use actual managed pack previews when available and an honest empty state otherwise. The overview labels the Factory schedule as enabled rather than implying an active worker. Browser layout review is pending while Work browser control is disabled. |
| B08 / F03–F05: native personal AI | **Open** | Operator AI remains a shared web workspace. Create authenticated account-owned jobs and native Create/Your creations on Android and iPhone; verify two-account isolation and explicit Save. |
| R16: Pinterest references | **Trial approved; connection unverified** | Developer dashboard showed app 1624615 Trial active. Live Admin status was `configured=false`, `oauth_ready=false`, `not_connected`. Save redirect, configure server-only app credentials, run read-only OAuth and verify a board response when browser access is restored. Do not archive Pins or feed them into distributable sticker output. |
| F19–F26: real meme content and quality | **Open** | Existing licensed photos/clips and conversion tools are a base, but the live public catalog was one four-sticker pack at audit. Evaluate reaction quality, transparent cutout, captions and motion with rights-cleared media and actual user testing; publish only reviewed packs. |
| F22/F25: varied real-media batches | **Source implemented; next live batch unverified** | October 22 review found five captions on one shuttle image. Photo batches now rotate up to six licensed source images, show a numbered contact sheet to vision in one request, and bind each ordered caption/render/provenance entry to its assigned source. This does not create new media or guarantee humor; test the next production batch and reject weak/duplicate output before publication. |
| F26: source-aware review | **Source implemented; hosted UI unverified** | Each sticker row in Admin now shows its source title, declared rights and original source link when available. Packs with fewer than three distinct credited images across at least three stickers get a visible variety warning. The reviewer still decides fitness and rights before publication; verify narrow layout and source-link safety on the hosted page. |
| F51–F55 / V03/V08/V10: iPhone beta | **Source/CI partial; device acceptance open** | Native iPhone source and Apple CI exist. No Mac or physical iPhone is available here for signing, installation or WhatsApp acceptance. Keep portable ZIP and API contracts aligned while building remaining parity. |

## Immediate work order

1. Finish B01 recovery hardening, full Python verification and deployment review;
   inspect an actual newly delivered batch. Do not present a synthetic fixture as
   a user-ready meme pack.
2. Verify the new upload normalization, explicit rights choice, lazy previews
   and media-page loading on the hosted site. Complete stable search/pagination
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
