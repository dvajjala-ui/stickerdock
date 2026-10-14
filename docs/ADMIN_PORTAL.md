# Factory admin portal and APK delivery

## Implemented source

The independently deployable `/admin` browser app uses the existing Vercel Python
backend, Supabase and Factory workers. It keeps the existing Creator Studio
available at `/studio/index.html`. It does not require a new Node build or a new
Android account system.

- Overview dashboard with live pack, sticker, queue and catalog counts.
- Pack library with search/status filters, draft creation from inbox images,
  rename, sticker labels/tags/removal, archive/restore and reject/review.
- Existing Factory output migration. Migration is idempotent for a source ID and
  checksum and preserves its original archive. Missing provenance stays private.
- Static draft creation normalizes uploaded images to 512×512 WebP, deduplicates
  normalized content and retains source rights. Animated/mixed Factory ZIPs can
  be migrated with their existing pack segregation preserved.
- Per-sticker previews, paged to respect web function response limits. Private
  ZIP download uses short-lived signed URLs.
- Public catalog preview, reviewed GENERAL-only publish/unpublish, immutable ZIP
  paths, serialized catalog writes and authenticated read-back verification.
- Private source uploads/library browsing, prompt presets, AI request enqueue,
  request progress, failed/cancelled request retry and queued-request cancellation.
- Daily target, batch size, topic rotation and pause/resume settings. The worker
  reads settings before its next invocation; existing cooldowns and request caps
  still apply. Running jobs are not interrupted mid-render.
- Daily discovery, content categories, GENERAL/private-MATURE audience and
  completed-pack auto-migration controls. Content planner shows the latest
  source-linked brief and explicit fallback warnings.
- Append-only owner audit events and server-only privileged credentials.
- Responsive desktop/mobile layout and a clearly marked synthetic local preview.

## User flow on the phone

1. Factory generates a complete private daily ZIP.
2. Owner opens Admin → Sticker packs → Existing Factory output → Migrate.
3. Owner previews the stickers and source rights, edits the draft, and approves it.
4. Owner selects Publish. Only reviewed GENERAL content enters the public catalog.
5. The newly built APK reads that catalog automatically on open/resume; Refresh
   fetches it again while the app stays open. CDN propagation may add a short delay.
6. User selects Import. The pack enters the offline local library.
7. User selects Add to WhatsApp or WhatsApp Business and confirms in that app.

Generation does not automatically approve content or silently install stickers
into WhatsApp. A published pack appears without rebuilding the APK as long as
both sides use the same catalog URL. Older GitHub-catalog debug APKs must be
replaced with the updated build or rebuilt with the production catalog setting.

Existing phone stickers remain migratable through the Android folder/ZIP tools;
the admin portal manages Factory/server assets and does not read phone storage.

## Local preview

```powershell
.\.venv\Scripts\python.exe tools/admin_dev_server.py --port 4173
```

Open `http://127.0.0.1:4173/admin`. Synthetic preview data and simulated actions
are labeled throughout; preview mode never calls Supabase or an AI provider.
Connect switches to the real APIs only after the owner token is authenticated.
The token stays in tab memory and is cleared on reload/disconnect.

## Vercel and database setup

This change has not been deployed and its migration has not been applied to
production. Apply `supabase/migrations/202610140001_admin_control_plane.sql` after
review and a database backup, before running this revision's admin API or manual
CLI publisher. The new migration adds `admin_packs`, `factory_settings`,
`content_briefs`, `admin_events` and a shared catalog publication lease. All are service-role-only;
the existing Factory run/output/request data remains unchanged.

The scheduled generator retains previous configuration when the admin table is
absent. Once present, owner settings override workflow default targets/batches.
The CLI and admin publication paths both require the migration's lease RPCs,
preventing simultaneous read/modify/write catalog updates.

Deploy the repository to Vercel with the existing `vercel.json` and Python
requirements. Set these only as protected server environment values:

- `STUDIO_ACCESS_TOKEN`: random 32–256-character owner access token.
- `SUPABASE_URL`: the Factory's Supabase project URL.
- `SUPABASE_SERVICE_ROLE_KEY`: service-role/secret API key.

Keep the GitHub worker's Groq/Supabase credentials in Actions secrets. Add
`GROQ_TRENDS_API_KEY` as the separate daily browser-search key; keep
`GROQ_API_KEY` for caption/vision generation. See
`AUTOMATED_CONTENT_FACTORY.md` for the complete scheduled flow and Pinterest
credential boundary.
The web API only enqueues AI requests; it does not need Groq credentials. Verify
the 10-minute Creator worker and three-hour Factory schedules are running on the
same project. The GitHub emergency pause variable still overrides scheduled
workflow invocation.

This MVP uses one owner token. Team roles, Supabase Auth/MFA, per-person auditing,
malware scanning, larger uploads, richer prompt-template versions, image synthesis
providers and community submission/moderation remain future work in the plan.

## Validation and release gates

- Python tests exercise auth rejection, idempotent migration, hash validation,
  rights gates, immutable revisions, stale versions, catalog locking,
  review/publish/unpublish and pause behavior using an isolated fake backend.
- Browser preview QA exercises the UI with synthetic records. It is not evidence
  of hosted Supabase/Vercel operation.
- A synthetic ZIP produced by the Python admin API restores through Android's
  native image decoder with all three stickers, exact hashes and label/tag metadata.
- Local validation on October 14 passed 118 Python tests and 45 Android unit tests,
  `lintRelease` and the updated debug APK build. Desktop/mobile preview flows at
  1440px and 390px had no console errors or horizontal overflow.
- Android tests/build/lint validate source and pack-format compatibility. Real
  Galaxy S24 Ultra folder access, phone UI and WhatsApp/Business acceptance remain
  device gates.
- Before live use: apply the migration, deploy/configure Vercel, authenticate,
  migrate one real output, review it, publish it and verify the updated APK's feed
  and real WhatsApp installation flow. Do not label the chain complete before that
  hosted/device check passes.

Web payload bounds follow [Vercel Function limits](https://vercel.com/docs/functions/limitations).
Database access and RPC grants follow [Supabase function security](https://supabase.com/docs/guides/database/functions).
