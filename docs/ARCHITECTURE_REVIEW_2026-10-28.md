# Architecture and functionality review — October 28, 2026

Reviewed StickerDock `fed2e98`, PR #26 and its merge `04a687e`, plus the current
HandNavigate `service/bridge.cjs` and `service/collections.cjs` source on GitHub.
The existing stack is suitable. The immediate work is completing contracts and
real delivery acceptance; a framework or database rewrite would not address the
remaining gaps.

## Current flow

| Component | Responsibility | Acceptance boundary |
| --- | --- | --- |
| Android / iPhone | Authoritative local packs, metadata, search, media editing, explicit save and WhatsApp handoff | Android CI and Apple simulator checks do not prove physical WhatsApp or signed iPhone installation. |
| Admin / Studio + Vercel API | Topic plans, candidate review, private Creator requests, draft review and publication controls | The shared Admin is currently open by URL. Authentication and roles remain a gate before exposing a multi-user management product. |
| Supabase | Private blobs, provenance, job leases, plans, review state and public catalog | RLS/grants protect direct client access. Server APIs still need their own access controls. |
| HandNavigate | Site sessions, search, downloads, local dedup/journal and outbound leased collection work | Stored images remain candidates with `generation_eligible=false`; collection success does not grant reuse rights. |
| Factory / Creator workers | Existing planner, Groq caption/vision work, Pillow/FFmpeg rendering and private draft delivery | New candidates need explicit rights/quality admission before this path. Reviewed draft/publication remains a separate action. |

## Completed reliability increment

[PR #26](https://github.com/dvajjala-ui/stickerdock/pull/26) merged after
[Android checks](https://github.com/dvajjala-ui/stickerdock/actions/runs/40641023505)
and [Web/API plus concurrent PostgreSQL checks](https://github.com/dvajjala-ui/stickerdock/actions/runs/40641023512)
passed for `ca4071e`. It fixes filtered-search truncation, stable search semantics,
overnight draft recovery, durable acknowledgements, historical-state retention,
atomic Creator admission and private cover caching.

The [production provisioning run](https://github.com/dvajjala-ui/stickerdock/actions/runs/40641030640)
applied the additive Creator admission RPC before the API merge and read back its
service-role-only grants. Existing intake migrations were reapplied successfully.
No sticker generation or public publication was performed by that migration.

## Follow-up found during this review

Admin offered Imgflip, but the API and current HandNavigate bridge still called
the legacy claim contract. That contract intentionally claims only Pinterest and
Reddit. Consequently an Imgflip request could remain queued indefinitely.

StickerDock now accepts an optional `supported_sources` list on worker `claim`
requests and sends validated capabilities to `claim_compatible_collection_job`.
Legacy claims retain their existing contract. New Imgflip collection/retry and
daily-plan activation require one registered worker supporting all selected
sites together; separate workers supporting one site each are insufficient.
Replaying an existing request does not require the worker to be online.

The worker upgrade must advertise its actual configured sources when claiming:

```json
{
  "action": "claim",
  "worker_id": "existing-worker-uuid",
  "supported_sources": [
    {"id": "pinterest", "name": "Pinterest"},
    {"id": "reddit", "name": "Reddit"},
    {"id": "imgflip", "name": "Imgflip"}
  ]
}
```

`revision` is optional; when present it must be a real SHA-256 digest. Advertise
only adapters installed on that worker. No recipes, cookies or executable code
are uploaded by this protocol. The reviewed [HandNavigate bridge](https://github.com/dvajjala-ui/handNavigation/blob/main/service/bridge.cjs)
does not yet send this field, so the installed-worker upgrade and a real Imgflip
claim/download/storage run remain open. The API guard prevents a new unclaimable
request from being accepted in the meantime.

## Ordered remaining work

1. Finish worker capability advertisement and qualify Imgflip through the actual
   outbound bridge. Include offline, stale capability, unsupported combination,
   challenge, stop/resume and duplicate retry cases.
2. Finish an explicit candidate-to-Factory admission contract: source permission,
   quality/text-overlap/duplicate review, immutable provenance, approved inputs
   and revocation handling. Current candidate approval alone keeps generation off.
3. Observe normal scheduled collection and Factory delivery over multiple days.
   A read-only production health check during this review reported all eight
   operational tables ready and five public packs, but today's generation was
   still `0/40`, `running`; dependable automatic content is not yet established.
4. Complete shared provider/storage admission and retention limits across workers.
   Creator's new 25-outstanding transaction does not bound manual collection
   backlog, total storage or organization-wide provider spending.
5. Complete Admin authentication/roles before wider management access, followed by
   explicit source/candidate audit history and deletion/revocation handling.
6. Finish native release acceptance. The latest public Android release is still
   0.11.4. The prior 0.11.6 build passed its emulator but publication returned
   HTTP 403; the later Library redesign failed the old search locator. PR #26
   repairs that locator. [The new signed run](https://github.com/dvajjala-ui/stickerdock/actions/runs/40641756805)
   is tied to merge `04a687e`; its outcome must be recorded before claiming a
   fresh download. Physical Android/Business, signed iPhone/TestFlight and actual
   user reaction quality remain open.

Local follow-up verification: 308 Python tests pass (seven media tests skipped
for this Windows FFmpeg/font setup), including 12 focused collection-control
tests for
capable/legacy claims, malformed capabilities, incompatible worker combinations
and offline replay. Full CI and installed-worker acceptance are separate evidence.
