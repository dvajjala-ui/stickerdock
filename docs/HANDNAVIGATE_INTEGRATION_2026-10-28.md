# HandNavigate daily source intake — implementation and deployment plan

Owner request, 28 October 2026: StickerDock's own planner should request changing topics from HandNavigate daily. HandNavigate must search Pinterest, Reddit and later configured platforms, save about 20 potential source images total, and deliver them to storage for topic-based sticker creation. Downloading must be application automation, not actions performed by ChatGPT's browser. News can inform topics; Commons/news/blog pictures must not substitute for these requested sources.

## Existing product flow inspected live

StickerDock already has Vercel Admin/Studio, Supabase PostgreSQL/private Storage, Groq topic/vision/caption work, local Pillow/FFmpeg rendering, scheduled GitHub Actions and private review drafts. The live read-only health check showed all eight required operational tables ready and five published packs. The current UTC day's existing generation had saved 20/40 stickers at inspection. No generation was requested by this integration test.

The current Pinterest OAuth connection checked `connected`, in `live_reference_only` mode. Its archived downloader command deliberately fails; its daily job checks the OAuth connection rather than downloading media. Those existing OAuth behavior and Factory media-rights checks are preserved.

Baseline `6f9a2f1` has [passing Web/API CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39968621830) and [Android debug CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39968621780). Its [signed Android release failed](https://github.com/dvajjala-ui/stickerdock/actions/runs/39968621774); this intake work changes no Android source or signing. These links do not qualify the new local changes.

## Implemented integration pieces

| Component | Behavior |
| --- | --- |
| HandNavigate `service/server.cjs` | Authenticated collection API with configured sources, dynamic topics, one shared count, durable request identity and status/resume/stop |
| HandNavigate `ServiceBrowser` + existing `Runner` | Its own persistent Chromium profiles, real site search, pagination, bounded image reads, image decoding, durable library deduplication and local originals |
| `tools/handnavigate_client.py` | Server-side caller; accepts explicit topics or today's saved planner `topic_plans`, with a stable daily request ID and optional completion polling |
| `api/ingest.py` + `_media_intake.py` | Dedicated bearer-authenticated worker endpoint. Rejects browser Origins; validates/normalizes pixels, preserves topic/platform/source URLs and both original/stored digests, verifies Storage and database read-back |
| `202610280001_media_intake.sql` | Additive service-role-only `media_intake_items` table; no public RLS access |
| `.github/workflows/handnavigate-intake.yml` | About 09:53 Asia/Kolkata daily/manual caller; disabled unless `HANDNAVIGATE_INTAKE_ENABLED=true`. Waits for `VERIFIED_STORAGE`; incomplete results fail the job |

Binary media goes in the existing **private Storage bucket** at `handnavigate/candidates/<digest>.webp`. PostgreSQL holds searchable provenance/status, not base64 image blobs. The original downloaded file remains in HandNavigate until a deliberate retention policy removes it. Retry identities cannot overwrite conflicting provenance.

Candidate storage is outside `inbox/`. Every entry starts `UNREVIEWED`, `generation_eligible=false`. This avoids falsely labeling arbitrary social images `USER_OWNED`, preserves existing Factory behavior and leaves current published packs unchanged. Admin intake browsing/admission and a reviewed permissions policy are still required before these candidates can feed the current generator. A technical download is not permission to redistribute or transform somebody else's work into public packs. Current primary platform references: [Pinterest guidelines](https://policy.pinterest.com/en/developer-guidelines), [Reddit Data API terms](https://redditinc.com/policies/data-api-terms).

## Actual verification

HandNavigate's own API/browser—not Codex's browser—saved **20 Pinterest images** for `funny cat reaction`, then **four Reddit images** across `cute cat` and `cute dog`. All 24 were independently decoded and their distinct hashes checked from disk. Source cookies came from the worker's own profile. Reddit required human verification; its media-search markup and navigation timing needed adapter fixes. This proves today's local source/download flow, not seven-day uptime or server-IP access.

The cross-repo check executed this actual path:

```text
StickerDock Python caller
  -> HandNavigate HTTP API
  -> real Chromium + production image runner
  -> StickerDock production intake handler
  -> disposable SQLite metadata + local blob fixture
  -> independent read-back: 20 images / 20 database records
```

It repeats the caller's request and returns the original collection. It makes zero AI calls and zero publications. The fixture stands in for Supabase only at its storage/database client boundary. **The new migration and endpoint have not been deployed, and these candidate images have not been written to production Supabase.**

HandNavigate evidence and the detailed runtime/API guide are in `D:\5. GitUploaded\hand\SERVICE.md` and `service/evidence/`. Local backend test logs are in ignored `work/handnavigate-backend-checks.txt`; no new hosted CI success is claimed until the change has a passing run link.

Final local backend regression: **274 tests passed**. The SQL checks also execute the new migration twice in PGlite and verify idempotency, private RLS/grants, default review state and the constraint preventing unreviewed generation admission. This validates migration behavior locally, not its application to the hosted database.

## Deployment sequence

1. **Keep Vercel and Supabase.** No new AI provider is required. Apply the additive migration, set a 32+ character `HANDNAVIGATE_INGEST_TOKEN` in Vercel, and deploy the intake endpoint. Existing Supabase service credentials stay server-side.
2. **Run HandNavigate on one persistent host.** The current Windows computer is suitable for further qualification; an always-on Linux VM with an initial estimated 2 vCPU/4 GB RAM and persistent disk is a reasonable first server. HandNavigate includes Docker/Compose files, but container/Linux behavior has not been run here. Qualify the selected server before depending on it. Keep a remote desktop/console for occasional human sign-in/checks.
3. **Connect the two server APIs.** HandNavigate gets StickerDock's HTTPS URL and matching `STICKERDOCK_INGEST_TOKEN`. StickerDock gets the worker's HTTPS endpoint in `HANDNAVIGATE_URL` plus `HANDNAVIGATE_API_TOKEN`. Loopback HTTP is supported through a local tunnel; remote plaintext HTTP is rejected by the client. Do not expose Chrome's debugger or place tokens in Admin JavaScript.
4. **Run one cloud smoke.** Two images, exact selected host, real new database table and private objects. Read back pixels, provenance and duplicate retries. Then test 20 total across topics and sources; retain incomplete outcomes explicitly.
5. **Finish candidate admission.** Add Admin review/filter/search, source permissions/rights, visual suitability checks and explicit admission into existing Factory media inputs. Continue using existing Groq caption/render/draft generation. Download/Storage success must not stand in for this separate result.
6. **Enable the prepared daily workflow.** It reads the existing brief, sends one collection with total limit 20, polls the result, and fails on partial/auth/storage failures. GitHub may delay scheduled jobs; it is not an exact-time promise. Start a seven-day acceptance run before raising the shared daily budget.

Run the worker on a server only after the host is available; no server was purchased or deployed by this task. For future platforms, add reviewed source recipes/allowed hosts and platform acceptance cases rather than passing arbitrary URLs or scripts from the model.

## Commands

From StickerDock, with server-side environment settings configured:

```powershell
python -m tools.handnavigate_client --topic "funny cat reaction" --topic "cute dog reaction" --limit 20 --wait
python -m tools.handnavigate_client --latest-brief --limit 20 --wait
python -m tools.handnavigate_client --status COLLECTION_UUID
```

From HandNavigate:

```powershell
npm run service:login -- pinterest
npm run service:login -- reddit
npm run service
node service/cross-check.cjs "D:\5. GitUploaded\sticker"
```

Stop the worker before the login helper opens the same profiles. The worker does not solve CAPTCHA, bypass gated content, call Groq or generate stickers itself. Its current automated media format is static JPEG/PNG/WebP; video/GIF intake, deletion/revocation propagation, cache retention and multi-worker scaling remain separate work.
