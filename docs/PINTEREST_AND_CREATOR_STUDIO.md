# Pinterest sources and Creator Studio

StickerDock has one private source inbox. Manual uploads, approved Pinterest
boards and reusable Wikimedia Commons media enter that inbox, then the scheduled
factory can turn them into photo or short-video stickers. The optional Creator
Studio preview can open inbox images, add manual captions and send the finished
image back to the inbox. A private AI request/result queue is implemented but is
not exposed by the visible Studio AI control yet.

## Source behavior

| Source | Discovery | Stored rights label | Public publish behavior |
| --- | --- | --- | --- |
| Manual Studio/Supabase upload | User-selected file | `USER_OWNED` | Eligible after review |
| Wikimedia Commons | Keyless API, CC0/public domain only | `CC0` or `PUBLIC_DOMAIN` | Eligible after review |
| Official Pinterest board | Authorized Pinterest API v5 board | `REFERENCE_ONLY` by default | Refused unless that Pin has an explicit rights declaration |
| Unofficial Pinterest topic | Optional `pinterest-dl` search | `REFERENCE_ONLY` | Refused by the public publisher |

Pinterest boards can contain work saved from other creators. Access through the
API does not establish reuse rights. The sync can still keep those files in the
private reference library, while the publish workflow checks `references.json`
and refuses any pack made with `REFERENCE_ONLY` pixels.

## Connect a Pinterest account

1. Create a Pinterest business/developer account and an app at
   <https://developers.pinterest.com/apps/>. Obtain approval and an OAuth token
   with read access for the boards you intend to monitor.
2. Find each numeric board ID. Board URLs or names are not accepted.
3. In GitHub → repository **Settings → Secrets and variables → Actions**, add:

| Type | Name | Example/purpose |
| --- | --- | --- |
| Secret | `PINTEREST_ACCESS_TOKEN` | Approved OAuth token |
| Variable | `PINTEREST_BOARD_IDS` | Comma-separated numeric IDs |
| Variable | `PINTEREST_DAILY_TARGET` | `90` (maximum `100`) |
| Variable | `PINTEREST_PER_RUN` | `30` maximum |
| Variable | `PINTEREST_SYNC_ENABLED` | `true` after the smoke test |

The Pinterest workflow also uses the existing `SUPABASE_URL` and
`SUPABASE_SERVICE_ROLE_KEY` secrets. Leave `PINTEREST_SYNC_ENABLED` unset, open
Actions → **Pinterest source sync**, and run it manually once. The job records a
rolling local API budget before requests, saves its SQLite state under the
private Supabase bucket, and uploads new media plus source metadata. After a
successful test, set the enable variable to `true`. It then runs every six hours.
Each scan permits at most 60 attempts, including failures/duplicates, with a
15-minute deadline checked between download attempts. Provider calls can finish
after that deadline; the workflow has a separate 30-minute timeout. Hosted media
staging is limited to about 54 MB and individual inputs to 19 MiB so failed
uploads remain recoverable in the bounded state archive.

The local official-API ceiling is 700 calls over a rolling 24 hours. This is a
budget guard, not a promise that Pinterest will grant that quota or return video
URLs. A 401 means the OAuth token must be refreshed; a 403 usually means the app
or selected board lacks read approval.

### Declare rights for specific Pins

Pinterest inputs stay private-reference-only unless ownership or a compatible
license is known. For Pins you own or have permission to reuse, create the
Actions secret `PINTEREST_PIN_RIGHTS_JSON`:

```json
{"123456789012345678":"USER_OWNED","234567890123456789":"CC0"}
```

Accepted values are `USER_OWNED`, `CC0`, and `PUBLIC_DOMAIN`. Declarations are
per numeric Pin ID. They affect later sync uploads; they do not rewrite older
metadata automatically.

### Optional public-topic discovery

The uploaded Pinterest AutoSync v2 topic adapter is included but disabled. To
try it, set:

- `PINTEREST_UNOFFICIAL_ENABLED=true`
- `PINTEREST_TOPICS=cats,office memes,retro reactions`

The workflow installs `pinterest-dl` only when this option is enabled. It uses
public search without login cookies, CAPTCHA handling or rate-limit bypasses.
Pinterest site changes can break it, and discovered content remains
`REFERENCE_ONLY`. Official authorized boards are the stable default.

## Creator Studio preview (Coming soon in Android)

The Android app currently shows **AI meme creator — Coming soon** and does not
open the editor. The implementation is staged in this branch for private testing;
do not treat it as an active end-user feature yet.

For a maintainer preview, import the repository into a free Vercel project. No
framework preset or build command is required; `studio/` contains static
HTML/CSS/JavaScript and `api/` contains Python serverless functions.

Add these Vercel environment variables:

| Name | Purpose |
| --- | --- |
| `STUDIO_ACCESS_TOKEN` | Password/token protecting the private Studio API |
| `SUPABASE_URL` | Existing factory project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Private inbox/queue access from serverless functions |

Deploy, open the Vercel URL, enter `STUDIO_ACCESS_TOKEN`, and use the manual
editor. The browser does not receive the Supabase service-role key. It stores the
Studio token only in the current browser tab (`sessionStorage`). Groq remains
configured only in the private Factory service.

Creator Studio supports:

- local PNG/JPEG/WebP selection and drag-and-drop;
- images already in the private factory inbox from Pinterest, Commons or manual
  uploads (images over 3 MB should be opened from a smaller local copy);
- fill/contain crop, caption drag positioning, font size, width, fill, outline and
  optional uppercase;
- PNG download and upload of the finished result back to Supabase.

Editing an inbox image preserves its original `REFERENCE_ONLY`, `CC0`,
`PUBLIC_DOMAIN`, or `USER_OWNED` label; adding a caption does not upgrade reuse
rights. A source with missing metadata stays reference-only. Local uploads are
owner-provided inputs and are validated as actual static images before storage.

## Private AI request queue

The asynchronous Studio → Factory queue is implemented in source but deliberately
not wired to the visible AI button yet.

`supabase/migrations/202610130001_creator_requests.sql` creates a
service-role-only `creator_requests` table. RLS is enabled and no public,
`anon`, or `authenticated` policy is created. The migration also defines
`claim_creator_request(worker_id)`, which uses `FOR UPDATE SKIP LOCKED` so two
workers cannot claim the same queued row; execute permission is restricted to
`service_role`.

`POST /api/captions` is protected by the same `STUDIO_ACCESS_TOKEN` as the other
private Studio functions. It supports only:

- `{"action":"queue","source_path":"inbox/<sha>.png","prompt":"..."}` — create a bounded queued request;
- `{"action":"status","id":"<uuid>"}` — read that request's status/result.

The browser never receives `GROQ_API_KEY` or the Supabase service-role key.
Source paths and request IDs are validated before provider access.

The GitHub workflow **Creator request worker** is `workflow_dispatch` only. Each
manual run atomically claims at most one request, verifies that the private
source object's SHA-256 matches its inbox filename, validates source-rights
metadata, calls the existing Groq vision caption path, renders a static WebP
locally, and stores the result plus provenance back in the private inbox. Missing
rights metadata is treated as `REFERENCE_ONLY`; invalid provenance fails before
any Groq call. Provider/internal errors are not written verbatim into the status
row.

The manual worker uses the normal GitHub secrets `GROQ_API_KEY`, `SUPABASE_URL`,
`SUPABASE_SERVICE_ROLE_KEY` and the `GROQ_VISION_MODEL` variable.

### Live activation checklist

Before enabling an AI control in Studio or scheduling the worker:

1. Apply both Supabase migrations, including
   `202610130001_creator_requests.sql`.
2. Configure the Vercel Studio token/Supabase variables and GitHub
   Groq/Supabase/vision-model settings.
3. Upload or choose one authorized private inbox image.
4. Send one authenticated `/api/captions` `queue` request.
5. Manually run Actions → **Creator request worker** once.
6. Query `/api/captions` with `action=status`; confirm `complete`, a private
   result path, correct provenance and a valid rendered sticker.
7. Inspect the real Groq usage/rate-limit behavior and Supabase storage impact.
8. Only then decide whether to schedule queue processing and replace the disabled
   Coming soon control / link it from Android.

## Local tests

```sh
python3 -m pip install -r tools/requirements-stickers.txt -r tools/requirements-pinterest.txt
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

Automated tests cover queue input validation, status parsing, atomic client/RPC
behavior, request-worker success/failure, source hash verification and
fail-closed provenance handling. They do not replace a live Supabase/Groq smoke
test.

For a local official Pinterest scan, set the same access-token and board-ID
variables and run `tools/pinterest_worker.py` only with a configured Supabase
test project. Production state is intentionally remote so GitHub Actions jobs can
resume without committing media or databases to the repository.
