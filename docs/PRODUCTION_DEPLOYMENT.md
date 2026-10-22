# Production deployment: GitHub Actions + Supabase

This is the recommended free-first deployment. GitHub Actions runs the Python,
Pillow and FFmpeg workers; Supabase provides PostgreSQL plus private/public object
storage; the Android app reads the approved public catalog. Vercel is not used
for generation because its short-lived functions are a poor fit for FFmpeg and
persistent batch state. It can host the included Creator Studio without moving
the worker or database.

## Current shared workspace — October 19

R40 explicitly removes the Admin/Studio password and token gate. The dashboard
loads existing data directly; workspace APIs do not require a cookie or login.
The database, storage and phone-account authentication are retained. Workspace
data and controls are accessible to anyone with the URL; provider credentials
remain server-side. See [OPEN_ADMIN.md](OPEN_ADMIN.md) for the access model.

The website checks below record the earlier release publication, before R40.

## Earlier October 19 website verification

The pack rebuild is merged in [PR #21](https://github.com/dvajjala-ui/stickerdock/pull/21)
at `f129430820633263316b75e44420f464f063449f`.
[The production Vercel deployment](https://vercel.com/dvajjala-2765s-projects/stickerdock/DdbL1xgZhTWQFau3UfYAwPZ3k1Wu)
succeeds. `/admin`, its JavaScript/CSS and `/about` match the merged source;
`/studio`, `/account`, `/privacy` and `/support` return HTTP 200. The download
route points to the latest signed GitHub release. A fresh signed-out session
returns `signed_in: false`, and the owner health endpoint returns HTTP 401 without
authentication. Public browser checks pass at 1280px and 390px, with no fixture
packs, JavaScript errors or production writes.

This check verifies the deployed public interface and authentication boundary.
It does not repeat the earlier authenticated operational checks documented below,
or establish original-art generation, Pinterest approval or phone acceptance.

## What runs where

- **GitHub Actions — Sticker Factory:** the Factory workflow has an eight-times-per-day
  schedule and normal scheduled generation is now active after the live smoke and
  resume checks passed. Set `STICKER_FACTORY_ENABLED=false` only as an explicit
  emergency pause. Manual runs remain available. Production credentials, state
  persistence, Groq text generation and resume behavior have been live-tested;
  public publication remains a separate manual/review-gated workflow.
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
- **Vercel Admin/Creator Studio:** shared dashboard/editor and Python workspace
  routes, with no password or token gate following R40. Existing workspace data
  and controls are publicly accessible. Phone cloud accounts remain separately
  authenticated. Provider credentials stay in server configuration.
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
lease check reported zero stale running Creator requests. A rollback-only live
transaction smoke then proved both recovery branches: a stale retryable request
was reclaimed at attempt 2, a stale fifth-attempt request was failed, and the
fresh reclaimed lease was not claimed twice. No smoke rows were committed.

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
| `STICKER_FACTORY_ENABLED` | Optional kill switch: set `false` to pause scheduled Factory generation; unset/`true` runs normally |
| `FACTORY_DAILY_TARGET` | Optional target override; production default is `40` |
| `GROQ_TEXT_MODEL` | Optional text-model override; production default is `openai/gpt-oss-20b` |
| `GROQ_VISION_MODEL` | Optional vision-model override where supported |

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

Scheduled private generation is enabled by default. Actions checks every 15
minutes; the saved three-hour interval, provider cooldowns and request budget
determine when a batch actually runs. Observed dispatches can be less frequent
than the cron expression, so do not promise an exact batch time. Do **not** force
extra batches or bypass the cooldown. If an operational problem appears, set
`STICKER_FACTORY_ENABLED=false` to pause schedules without changing code. The
capacity gate stays open until a full UTC day reaches 40/40 and its Groq
usage/rate-limit metrics plus private pack are reviewed.

The production default is five stickers per successful batch, 40 per UTC day.
Provider cooldowns and runner timing can lower the total. Completed batches
can enter private Admin review without reaching 40/40. Increase targets only after recorded
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

## 5. Shared Creator Studio AI queue

The Android app still shows **AI meme creator — Coming soon**. The separate
browser Studio supports the operator AI flow and opens without signing in.
The existing deployment is independent of native phone AI integration.

Configure Vercel/server-side environment variables:

- `SUPABASE_URL`;
- `SUPABASE_SERVICE_ROLE_KEY`.

`STUDIO_ACCESS_TOKEN` is no longer an API access credential. Its existing
server-only use signing Pinterest OAuth state remains; retain it for that
integration. Do not put provider or OAuth signing secrets into browser code.

The shared `/api/upload`, `/api/library`, and `/api/captions` routes keep service
credentials on the server. `/api/captions` can create a bounded request and query
status without a login; the browser never receives `GROQ_API_KEY`.

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

Run **Signed Android release candidate**. The workflow validates that all four
signing values are present before decoding the keystore or invoking Gradle. It
already defaults to the production Supabase catalog URL above, while
`CATALOG_BASE_URL` remains available as an optional environment override. The
protected job produces a signed APK and AAB retained as private workflow artifacts.
This workflow does not publish to an app store or create a GitHub release
automatically.

## Production evidence and remaining release gates

Production evidence completed October 13, 2026 includes:

- all three Supabase migration dry-run/apply operations plus
  RLS/grant/function/bucket/lease verification;
- rollback-only live Creator lease recovery proof with no committed smoke rows;
- live Supabase/Groq credential and current-model validation;
- controlled 5/5 Factory generation plus real-config resume to 10/40;
- private pack checksum verification and confirmation that public storage stayed
  empty;
- real Creator queue/vision/result/provenance/status smoke with cleanup;
- headless Chromium manual Studio and AI-interaction testing;
- live read-only storage scan (about 4.01 MB private, zero public, no 30-day
  retention candidates).

Remaining release gates are intentionally narrower:

- let the now-active normal-cooldown schedule complete one full **40/40** Factory
  day, review Groq quota metrics, then publish/import one approved GENERAL pack;
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
