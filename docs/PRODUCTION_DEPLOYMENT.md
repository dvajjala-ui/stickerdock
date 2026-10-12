# Production deployment: GitHub Actions + Supabase

This is the recommended free-first deployment. GitHub Actions runs the Python,
Pillow and FFmpeg worker; Supabase provides PostgreSQL plus private/public object
storage; the Android app reads the approved public catalog. Vercel is not used
for generation because its short-lived functions are a poor fit for FFmpeg and
persistent batch state. It can host the included Creator Studio without moving
the worker or database.

## What runs where

- **GitHub Actions:** every three hours, calls Groq directly, downloads due free
  media, processes your private inbox, renders one batch and saves state. A
  separate six-hour job can monitor authorized Pinterest boards.
- **Supabase private bucket:** factory state, your uploaded references, pending
  ZIPs and reference metadata. Nothing here is readable by the Android app.
- **Supabase public bucket:** only packs explicitly published with the reviewed
  publish workflow and its catalog JSON.
- **Android:** contains the public catalog URL. It never contains the Groq key or
  Supabase service-role key.

One production key entered into GitHub's secret UI powers the worker directly.
Putting the same key into the APK would give every installer the ability to use
the account and would make a stable shared service impossible. No `.env` file is
required on the phone or committed to Git.

## 1. Create the free Supabase project

Create one Supabase project. In its SQL editor, run
`supabase/migrations/202610120001_sticker_factory.sql`. It creates:

- `factory_runs`, readable only by the service role;
- `sticker_packs`, where public database reads see only approved GENERAL rows;
- private `sticker-factory-private` and public `sticker-factory-public` buckets;
- a public read policy only for the public bucket.

Copy the project URL and service-role key from the Supabase project settings.
The service-role key belongs only in GitHub Actions secrets.

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
| Variable | `GROQ_VISION_MODEL` | required for photo/clip batches and image-aware Studio ideas |

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
Supabase. Monitor the project's free storage allowance and choose retention
before scaling daily media ingestion; no unlimited free-storage promise is made.

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

## 5. Build the Android app against Supabase

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

Creator Studio is currently a staged Vercel preview. The Android app shows a
disabled **AI meme creator — Coming soon** button. Preview configuration and its
server-side variables are listed in the Pinterest/Studio guide.

GitHub Releases is sufficient for free APK distribution to testers. Use Play
Console for a public Android release. Vercel hosts the optional creator/editor;
Supabase remains the database and storage layer.

## Production evidence and remaining release gates

Automated tests cover persistent state archives, traversal rejection, inbox
deduplication, private pending uploads, GENERAL-only publishing, catalog URL
validation, rate-limit cooldowns, actual GIF/MP4 conversion, ZIP checksums and
Android compilation. CI builds a debug APK on every push/PR.

Before calling the app generally available, complete one live scheduled day,
inspect its Groq quota metrics, publish/import the resulting pack, and test the
signed APK on the Galaxy S24 Ultra against WhatsApp and WhatsApp Business. The
current development environment cannot perform Groq/Supabase live calls because
its outbound proxy rejects those hosts before the request reaches the provider.
