# Production deployment: GitHub Actions + Supabase

This is the recommended free-first deployment. GitHub Actions runs the Python,
Pillow and FFmpeg workers; Supabase provides PostgreSQL plus private/public object
storage; the Android app reads the approved public catalog. Vercel is not used
for generation because its short-lived functions are a poor fit for FFmpeg and
persistent batch state. It can host the included Creator Studio without moving
the worker or database.

## What runs where

- **GitHub Actions:** every three hours, the scheduled Factory calls Groq directly,
  downloads due free media, processes the private inbox, renders one batch and
  saves state. A separate six-hour job can monitor authorized Pinterest boards.
  A third **manual-only** Creator request worker processes at most one private
  Studio AI request per dispatch while that feature remains under live testing.
  A daily/manual storage monitor measures private/public Supabase usage and
  reports retention candidates without deleting data.
- **Supabase private bucket:** factory state, uploaded references, Creator request
  inputs/results, pending ZIPs and provenance metadata. Nothing here is readable
  by the Android app.
- **Supabase PostgreSQL:** private operational state, reviewed pack metadata and a
  service-role-only `creator_requests` queue with atomic worker claiming.
- **Supabase public bucket:** only packs explicitly published with the reviewed
  publish workflow and its catalog JSON.
- **Vercel Creator Studio:** private manual editor plus token-gated queue/status
  server routes. It never sends the Supabase service-role or Groq key to browser
  JavaScript.
- **Android:** contains the public catalog URL. It never contains the Groq key or
  Supabase service-role key.

One production key entered into GitHub's secret UI powers the worker directly.
Putting the same key into the APK would give every installer the ability to use
the account and would make a stable shared service impossible. No `.env` file is
required on the phone or committed to Git.

## 1. Create the free Supabase project

Create one Supabase project. In its SQL editor, run these migrations in order:

1. `supabase/migrations/202610120001_sticker_factory.sql`
2. `supabase/migrations/202610130001_creator_requests.sql`

The first migration creates:

- `factory_runs`, readable only by the service role;
- `sticker_packs`, where public database reads see only approved GENERAL rows;
- private `sticker-factory-private` and public `sticker-factory-public` buckets;
- a public read policy only for the public bucket.

The second migration creates:

- `creator_requests`, with RLS enabled and intentionally no public/anon/authenticated
  policy;
- a bounded queued/running/complete/failed/cancelled lifecycle;
- an index for queued work;
- the service-role-only `claim_creator_request(text)` function using
  `FOR UPDATE SKIP LOCKED` to prevent duplicate claims.

Copy the project URL and service-role key from the Supabase project settings.
The service-role key belongs only in trusted server-side deployments such as
GitHub Actions and the private Vercel Studio functions.

## 2. Configure the repository once

In GitHub → repository **Settings → Secrets and variables → Actions**, add:

| Type | Name | Value |
| --- | --- | --- |
| Secret | `GROQ_API_KEY` | Groq production key |
| Secret | `SUPABASE_URL` | `https://PROJECT.supabase.co` |
| Secret | `SUPABASE_SERVICE_ROLE_KEY` | Supabase service-role key |
| Variable | `STICKER_FACTORY_ENABLED` | `true` after the smoke test |
| Variable | `FACTORY_DAILY_TARGET` | `40` initially; maximum `100` |
| Variable | `GROQ_TEXT_MODEL` | optional current Groq text model |
| Variable | `GROQ_VISION_MODEL` | required for photo/clip batches and Creator requests |

Create GitHub environments named `sticker-factory-production` and
`android-production`. Add a required reviewer to both. Copy the two Supabase
secrets into `sticker-factory-production` as well because the manual publisher
uses that protected environment.

Leave `STICKER_FACTORY_ENABLED` unset for the first run. Open Actions →
**Scheduled sticker factory** → **Run workflow**. A successful run creates or
updates the UTC day's `factory_runs` row and stores persistent state privately.
Then set the enable variable to `true`.

The schedule runs at 05:45, 08:45, 11:45, 14:45, 17:45, 20:45, 23:45 and
02:45 India time. Its production default is five stickers per run, 40 per UTC day.
Provider cooldowns can lower the total. Increase to 100 only after the state row's
recorded Groq headers show the selected model/account can sustain 20 daily calls.
The worker's cooldown is 165 minutes to tolerate small scheduler delays while
the workflow cadence remains three hours. Five stickers per scheduled run gives
a maximum of 40 at this cadence; reaching 100 requires an explicitly faster
schedule or larger batches as well as sufficient account quota.

## 3. Add your own photos and clips

In Supabase Storage, open `sticker-factory-private` and create the `inbox` folder.
Upload authorized PNG/JPEG/WebP/GIF/MP4/WebM/MOV files there. Each scheduled run
imports at most 30 new files, hashes duplicates, records provenance as owner upload
and persists the processed list. Inputs remain private. Unsupported or files over
20MB are skipped.

Commons CC0/public-domain discovery is already enabled with a daily network
budget. Photo and clip modes activate only when the inbox/library has matching
media and `GROQ_VISION_MODEL` is set. Otherwise the run continues using original
illustration and pulse-animation modes.
The local input cache is capped at 20 MB and completed older-day images are
removed from runner state after their private ZIP upload. Originals remain in
Supabase. Monitor the project's storage allowance before scaling daily media
ingestion; no unlimited free-storage promise is made.

Pinterest source ingestion is a separate, persistent workflow. Follow
[`PINTEREST_AND_CREATOR_STUDIO.md`](PINTEREST_AND_CREATOR_STUDIO.md) to add an
authorized token and numeric board IDs. New Pinterest assets are marked
`REFERENCE_ONLY` unless a specific per-Pin rights declaration is configured.

## 4. Review and publish a completed day

Generated daily ZIPs appear under
`sticker-factory-private/generated/YYYY-MM-DD/`. Download and inspect the ZIP and
`references.json`. To publish a GENERAL day, open Actions →
**Publish reviewed factory pack**, enter its UTC date, and approve the protected
environment job. The publisher revalidates the ZIP, refuses MATURE packs and any
pack using `REFERENCE_ONLY` media, copies it to public storage, updates
`catalog/packs.json`, and marks the database row approved.
Public archives must fit Android's 25 MB download bound; animations require
frames of at least 8 ms and total duration at most 10 seconds. Private archives
can be larger, but must be split or reduced before public release.

MATURE generation can use a separately configured manual worker, but this public
publisher always refuses it. The production scheduled workflow pins its rating to
GENERAL.

## 5. Private Creator Studio AI queue

Creator Studio is still a staged Vercel preview and the Android app still shows a
disabled **AI meme creator — Coming soon** button. The request/result plumbing is
implemented but intentionally manual-only until a real provider smoke test.

Configure the Vercel Studio with server-side environment variables:

- `STUDIO_ACCESS_TOKEN`
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`

The token-gated `/api/captions` route can enqueue a bounded prompt against an
existing private inbox image and query its status. It cannot call Groq directly.
The browser receives neither the service-role key nor `GROQ_API_KEY`.

To test processing, open GitHub Actions → **Creator request worker** and dispatch
it manually. It uses `GROQ_API_KEY`, `SUPABASE_URL`,
`SUPABASE_SERVICE_ROLE_KEY`, and `GROQ_VISION_MODEL`, atomically claims at most
one queued request, validates the source SHA/provenance, reuses the existing Groq
vision caption generator and local WebP renderer, and writes the result only to
the private inbox. Missing rights metadata becomes `REFERENCE_ONLY`; invalid
rights fail before the Groq call.

Do not schedule this workflow or expose the visible Studio AI control until one
live queue → worker → status/result test has verified real Supabase behavior,
Groq model compatibility/rate limits, output quality, and private storage impact.

## 6. Monitor storage and review retention candidates

The workflow **Supabase storage usage** is read-only. Run it manually before
turning on the daily schedule. It recursively totals objects and bytes in both
StickerDock buckets, writes a GitHub job summary, and never removes an object.
The scan is bounded to protect against unexpectedly huge folder/object trees.

Configure repository variables using the actual limits shown by your Supabase
project/plan rather than assuming a fixed free-tier allowance:

| Variable | Purpose |
| --- | --- |
| `SUPABASE_STORAGE_QUOTA_MB` | Current project storage quota in MiB. Leave empty for report-only mode. |
| `SUPABASE_STORAGE_WARN_PERCENT` | Actions warning threshold; default `70`. |
| `SUPABASE_STORAGE_FAIL_PERCENT` | Job-failure threshold; default `90`. Must exceed warning threshold. |
| `PRIVATE_GENERATED_RETENTION_DAYS` | Age for private generated-artifact review; default `30`. |
| `STORAGE_MONITOR_ENABLED` | Set `true` after the first successful manual report to enable daily checks. |

At the warning threshold the workflow emits an Actions warning. At the fail
threshold it exits non-zero so normal GitHub Actions failure notifications can
surface the problem.

Retention is intentionally non-destructive:

- private `inbox/` source files and `source-metadata/` provenance are retained
  until the owner explicitly cleans them up;
- public catalog packs are retained until deliberately delisted/replaced;
- private `generated/YYYY-MM-DD/` artifacts older than the configured window are
  listed by day, object count and bytes as **review candidates only**;
- no scheduled job automatically deletes source media, provenance, approved
  public packs or generated archives.

This gives a concrete cleanup policy without risking silent loss of licensed
source material or the only copy of a generated pack. Review the candidate list
and delete only intentionally through Supabase/admin tooling.

## 7. Build the Android app against Supabase

The catalog URL is:

```text
https://PROJECT.supabase.co/storage/v1/object/public/sticker-factory-public/catalog/
```

For a local release candidate:

```sh
gradle -PcatalogBaseUrl="https://PROJECT.supabase.co/storage/v1/object/public/sticker-factory-public/catalog/" \
  :app:testDebugUnitTest :app:assembleRelease
```

For signed GitHub release candidates, create `android-production` secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Set its `CATALOG_BASE_URL` environment variable to the URL above. Run
**Signed Android release candidate**. The protected job produces a signed APK and
AAB retained as private workflow artifacts for 14 days. This workflow does not
publish to an app store or create a GitHub release automatically.

GitHub Releases is sufficient for free APK distribution to testers. Use Play
Console for a public Android release. Vercel hosts the optional creator/editor;
Supabase remains the database and storage layer.

## Production evidence and remaining release gates

Automated tests cover persistent state archives, traversal rejection, inbox
deduplication, private pending uploads, GENERAL-only publishing, catalog URL
validation, rate-limit cooldowns, actual GIF/MP4 conversion, ZIP checksums,
Creator queue validation/status parsing, atomic claim client behavior, source
hash checks, fail-closed rights metadata, recursive storage accounting,
quota/threshold decisions, retention-candidate classification and Android
compilation. CI builds a debug APK and optimized release on every push/PR.

Before calling the app generally available, complete one live scheduled day,
inspect its Groq quota metrics, publish/import the resulting pack, complete one
live Creator queue smoke test, run the storage monitor once against the real
project/quota, and test the signed APK on the Galaxy S24 Ultra against WhatsApp
and WhatsApp Business. The current development environment cannot perform
Groq/Supabase live calls because its outbound proxy rejects those hosts before
the request reaches the provider.
