# Production deployment: GitHub Actions + Supabase

This is the recommended free-first deployment. GitHub Actions runs the Python,
Pillow and FFmpeg workers; Supabase provides PostgreSQL plus private/public object
storage; the Android app reads the approved public catalog. Vercel is not used
for generation because its short-lived functions are a poor fit for FFmpeg and
persistent batch state. It can host the included Creator Studio without moving
the worker or database.

## What runs where

- **GitHub Actions — Sticker Factory:** the Factory workflow has an eight-times-per-day
  schedule, but scheduled execution is gated by `STICKER_FACTORY_ENABLED=true`.
  Manual runs are always available. Production credentials, state persistence,
  Groq text generation and resume behavior have been live-tested. Keep the
  recurring Factory gate disabled until one normal-cooldown 40-sticker day has
  been reviewed.
- **GitHub Actions — Creator worker:** checks the private Creator queue every 10
  minutes. A cheap Supabase preflight runs first; when the queue is empty it skips
  Python/package/font setup. When work exists the job can process up to five
  queued requests in one serialized run. The real queue → claim → Groq vision →
  private result/status path has passed production smoke testing. Running claims
  older than 20 minutes are recovered automatically; attempts below five are
  requeued, while stale fifth-attempt claims are failed.
- **GitHub Actions — other private jobs:** a six-hour Pinterest job can monitor
  authorized boards after Pinterest credentials are configured. A daily/manual
  storage monitor measures private/public Supabase usage and reports retention
  candidates without deleting data.
- **Supabase private bucket:** factory state, uploaded references, Creator request
  inputs/results, pending ZIPs and provenance metadata. Nothing here is readable
  by the Android app.
- **Supabase PostgreSQL:** private operational state, reviewed pack metadata and a
  service-role-only `creator_requests` queue with atomic worker claiming.
- **Supabase public bucket:** only packs explicitly published with the reviewed
  publish workflow and its catalog JSON.
- **Vercel Creator Studio:** static editor plus token-gated Python server routes.
  The source supports manual editing and the private AI queue flow, but the
  production Vercel deployment/environment has not yet been verified.
- **Android:** contains only the public catalog URL. It never contains the Groq
  key or Supabase service-role key.

No `.env` file is required on the phone or committed to Git. Provider credentials
belong only in trusted server-side deployment settings.

## 1. Supabase production project

The production project already has these migrations applied in order:

1. `supabase/migrations/202610120001_sticker_factory.sql`
2. `supabase/migrations/202610130001_creator_requests.sql`
3. `supabase/migrations/202610130002_creator_request_leases.sql`

The first migration creates:

- `factory_runs`, readable only by the service role;
- `sticker_packs`, where public database reads see only approved GENERAL rows;
- private `sticker-factory-private` and public `sticker-factory-public` buckets;
- explicit least-privilege table grants; the public Storage bucket is served by
  Supabase's public object endpoint without a broad `storage.objects` SELECT/list
  policy for anon/authenticated clients.

The second migration creates:

- `creator_requests`, with RLS enabled and explicit service-role-only table
  privileges;
- a bounded queued/running/complete/failed/cancelled lifecycle;
- an index for queued work;
- the service-role-only `claim_creator_request(text)` function using
  `SECURITY INVOKER`, an empty `search_path`, and `FOR UPDATE SKIP LOCKED` to
  prevent duplicate claims without granting elevated function privileges.

The third migration hardens worker recovery:

- adds `idx_creator_requests_running_lease` for running-request lease scans;
- recovers `running` requests older than 20 minutes;
- requeues stale requests below the five-attempt ceiling;
- marks stale fifth-attempt requests failed so they cannot remain stuck forever;
- preserves `SECURITY INVOKER`, empty `search_path`, service-role-only EXECUTE and
  the normal `FOR UPDATE SKIP LOCKED` claim path.

`supabase/verify_sticker_factory.sql` was run after deployment. Verification
confirmed the three tables/RLS flags, Data API grants and policies, Creator claim
function security/EXECUTE grants, both Storage buckets, update triggers, the
running-lease index and the 20-minute recovery logic. The old broad
`read public factory artifacts` Storage policy is absent. The live post-apply
lease check reported zero stale running Creator requests.

The repository uses the current Supabase opaque secret-key format
(`sb_secret_...`). Server clients send that value as `apikey` rather than treating
it as a JWT bearer token; legacy service-role JWTs remain supported for backwards
compatibility.

## 2. GitHub production configuration

In GitHub → repository **Settings → Secrets and variables → Actions** the two
runtime secrets that have been live-validated are:

| Type | Name | Purpose |
| --- | --- | --- |
| Secret | `GROQ_API_KEY` | Groq text/vision API access |
| Secret | `SUPABASE_SERVICE_ROLE_KEY` | Trusted server-side database/storage access |

The Supabase project URL is non-secret and is set directly in the production
workflows. Current default models are also encoded in source/workflows:

- text: `openai/gpt-oss-20b`
- vision: `qwen/qwen3.8-27b`

Optional GitHub variables can override selected runtime behavior. Important ones:

| Variable | Purpose |
| --- | --- |
| `STICKER_FACTORY_ENABLED` | Set `true` only after the reviewed full-day Factory gate |
| `FACTORY_DAILY_TARGET` | Optional target override; production default is `40` |
| `GROQ_TEXT_MODEL` | Optional text-model override |
| `GROQ_VISION_MODEL` | Optional vision-model override |

`SUPABASE_DB_PASSWORD` is needed only for database migration maintenance, not
normal Factory/Creator runtime and never by the Android app.

If protected publishing/signing is used, create GitHub environments named
`sticker-factory-production` and `android-production`, add required reviewers,
and place only the secrets required by those protected jobs into the appropriate
environment.

### Factory activation state

A controlled production smoke already generated five GENERAL stickers, persisted
`factory_runs`, stored and checksum-verified a private ZIP/pending pack, and left
the public bucket empty. A second run with the real production configuration
resumed that state on a fresh runner from 5 to **10/40** and synced two reference
library assets.

Do **not** force the remaining 30 stickers by bypassing the cooldown. Leave
`STICKER_FACTORY_ENABLED` unset/false until a normal-cooldown day reaches 40/40
and its Groq usage/rate-limit metrics plus private pack are reviewed.

The scheduled cadence is 05:45, 08:45, 11:45, 14:45, 17:45, 20:45, 23:45 and
02:45 India time. The production default is five stickers per run, 40 per UTC
day. Provider cooldowns can lower the total. Increase targets only after recorded
Groq headers demonstrate the selected account/model can sustain the needed call
volume.

## 3. Add authorized photos and clips

In Supabase Storage, use `sticker-factory-private/inbox/` for authorized
PNG/JPEG/WebP/GIF/MP4/WebM/MOV files. Each scheduled run imports a bounded number
of new files, hashes duplicates, records provenance and persists the processed
list. Inputs remain private. Unsupported or oversized files are skipped.

Commons CC0/public-domain discovery is available with a daily network budget.
Photo and clip modes activate only when the inbox/library has matching media and a
vision model is configured. Otherwise the run continues using original
illustration and pulse-animation modes.

The local input cache is bounded and completed older-day images are removed from
runner state after private ZIP upload; originals remain in Supabase. Monitor the
project's real storage allowance before scaling daily media ingestion.

Pinterest source ingestion is a separate persistent workflow. Follow
[`PINTEREST_AND_CREATOR_STUDIO.md`](PINTEREST_AND_CREATOR_STUDIO.md) to add an
approved token and numeric board IDs. New Pinterest assets are
`REFERENCE_ONLY` unless a specific per-Pin rights declaration is configured.

## 4. Review and publish a completed Factory day

Generated daily ZIPs appear under
`sticker-factory-private/generated/YYYY-MM-DD/`. Download and inspect the ZIP and
`references.json`. To publish a reviewed GENERAL day, open Actions →
**Publish reviewed factory pack**, enter its UTC date, and approve the protected
environment job. The publisher revalidates the ZIP, refuses MATURE packs and any
pack using `REFERENCE_ONLY` media, copies it to public storage, updates
`catalog/packs.json`, and marks the database row approved.

Public archives must fit Android's 25 MB download bound; animations require
frames of at least 8 ms and total duration at most 10 seconds. Private archives
can be larger, but must be split or reduced before public release.

Do not publish the five-sticker smoke pack created during production validation.
Wait for a completed reviewed production day and publish only the intended final
GENERAL pack.

## 5. Private Creator Studio AI queue

The Android app still shows **AI meme creator — Coming soon**. The separate
browser Studio source now supports the real private AI flow, but its production
Vercel deployment/environment is still a remaining gate.

Configure Vercel/server-side environment variables:

- `STUDIO_ACCESS_TOKEN` — random 32–256 character token;
- `SUPABASE_URL`;
- `SUPABASE_SERVICE_ROLE_KEY`.

Generate a strong Studio token locally, for example:

```sh
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

The server rejects configured Studio tokens shorter than 32 characters or longer
than 256 characters. Do not commit the token or place it in browser/source code;
store it only in the Vercel/server environment and enter it manually in the
private Studio when needed.

The token-gated `/api/upload`, `/api/library`, and `/api/captions` routes keep the
Supabase secret on the server. `/api/captions` can create a bounded request and
query status; the browser never receives `GROQ_API_KEY`.

For a local image, the Studio AI flow is:

1. upload the validated source into the private inbox;
2. enqueue `/api/captions` with that private source path and prompt;
3. poll the request status;
4. after `complete`, fetch the private result through `/api/library`;
5. load the result back into the editor for further editing/download.

The GitHub **Creator request worker** now checks for queued requests every 10
minutes. Its first small Supabase query is intentionally cheap. Empty queues skip
Python/package/font setup. Active runs process up to five requests, while workflow
concurrency plus the database `FOR UPDATE SKIP LOCKED` claim protect against
races/duplicate claims. A stale running request can no longer remain leased
forever because the production claim function recovers leases older than 20
minutes subject to the existing five-attempt ceiling.

The real production backend path has already been verified with an isolated test:
queue → exactly-one atomic claim → Groq vision → bounded private WebP → provenance
linkage → completed status. The smoke source/request/result/provenance objects were
cleaned afterward. Headless Chromium also verified both manual editor behavior and
the browser AI upload/queue/status/result interaction flow.

Remaining Studio activation work is deployment-specific: deploy the current
source to Vercel with the three server values above and run one authenticated
browser smoke against the hosted URL. Keep the Android entry Coming soon until
that hosted path is intentionally exposed.

## 6. Monitor storage and review retention candidates

The workflow **Supabase storage usage** is read-only. It recursively totals
objects/bytes in both StickerDock buckets, writes a GitHub job summary, and never
removes an object. The scan is bounded to protect against unexpectedly large
folder/object trees.

The first live report-only production scan already passed on October 13, 2026:

- private bucket: **7 objects / 4,204,527 bytes (~4.01 MB)**;
- public bucket: **0 objects / 0 bytes**;
- private generated artifacts older than 30 days: **none**;
- no objects were deleted.

No usage percentage was calculated because the real project quota has not yet
been configured. Configure repository variables using the actual limits shown by
the Supabase project/plan rather than assuming a fixed free-tier allowance:

| Variable | Purpose |
| --- | --- |
| `SUPABASE_STORAGE_QUOTA_MB` | Current project storage quota in MiB |
| `SUPABASE_STORAGE_WARN_PERCENT` | Actions warning threshold; default `70` |
| `SUPABASE_STORAGE_FAIL_PERCENT` | Failure threshold; default `90`, must exceed warning |
| `PRIVATE_GENERATED_RETENTION_DAYS` | Private generated-artifact review age; default `30` |
| `STORAGE_MONITOR_ENABLED` | Set `true` after quota/threshold review to enable daily checks |

At the warning threshold the workflow emits an Actions warning. At the fail
threshold it exits non-zero so normal GitHub Actions failure notifications can
surface the issue.

Retention is intentionally non-destructive:

- private `inbox/` source files and `source-metadata/` provenance are retained
  until explicit owner cleanup;
- approved public catalog packs stay until deliberately delisted/replaced;
- private `generated/YYYY-MM-DD/` artifacts older than the configured window are
  listed as review candidates only;
- no scheduled job automatically deletes source media, provenance, approved
  public packs or generated archives.

## 7. Build the Android app against Supabase

The public catalog base URL is:

```text
https://qqicsllktmcmvmzplsxl.supabase.co/storage/v1/object/public/sticker-factory-public/catalog/
```

For a local release candidate:

```sh
gradle -PcatalogBaseUrl="https://qqicsllktmcmvmzplsxl.supabase.co/storage/v1/object/public/sticker-factory-public/catalog/" \
  :app:testDebugUnitTest :app:assembleRelease
```

For signed GitHub release candidates, create `android-production` secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Run **Signed Android release candidate**. The workflow already defaults to the
production Supabase catalog URL above, while `CATALOG_BASE_URL` remains available
as an optional environment override. The protected job produces a signed APK and
AAB retained as private workflow artifacts. This workflow does not publish to an
app store or create a GitHub release automatically.

## Production evidence and remaining release gates

Production evidence completed October 13, 2026 includes:

- all three Supabase migration dry-run/apply operations plus
  RLS/grant/function/bucket/lease verification;
- live Supabase/Groq credential and current-model validation;
- controlled 5/5 Factory generation plus real-config resume to 10/40;
- private pack checksum verification and confirmation that public storage stayed
  empty;
- real Creator queue/vision/result/provenance/status smoke with cleanup;
- headless Chromium manual Studio and AI-interaction testing;
- live read-only storage scan (about 4.01 MB private, zero public, no 30-day
  retention candidates).

Remaining release gates are intentionally narrower:

- complete one normal-cooldown **40/40** Factory day, review it, publish one
  approved GENERAL pack and import it into Android;
- configure/deploy Vercel Studio and run the hosted authenticated browser/API
  smoke;
- configure the actual Supabase storage quota and enable daily storage monitoring
  only after threshold review;
- connect an approved Pinterest app/token/board IDs and run one official-board
  sync;
- configure the four Android signing secrets and perform Galaxy S24 Ultra tests
  for WhatsApp/Business installation, animation and 10k+/process-death migration
  behavior;
- finish privacy policy, app identity, store listing and production regression
  work before general availability.
