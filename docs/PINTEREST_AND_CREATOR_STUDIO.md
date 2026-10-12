# Pinterest sources and Creator Studio

StickerDock has one private source inbox. Manual uploads, approved Pinterest
boards and reusable Wikimedia Commons media enter that inbox, then the scheduled
factory can turn them into photo or short-video stickers. The optional Creator
Studio preview can open inbox images, add manual captions and send the finished
image back to the inbox. AI prompts are marked Coming soon.

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

## Creator Studio preview (Coming soon)

The Android app currently shows **AI meme creator — Coming soon** and does not
open the editor. The implementation is staged in this branch for later private
testing; do not treat it as an active end-user feature yet.

For a maintainer preview, import the repository into a free Vercel project. No
framework preset or build command is required; `studio/` contains static
HTML/CSS/JavaScript and `api/` contains Python serverless functions.

Add these Vercel environment variables:

| Name | Purpose |
| --- | --- |
| `STUDIO_ACCESS_TOKEN` | A password used by the private Studio UI |
| `SUPABASE_URL` | Existing factory project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Private inbox access from serverless functions |

Deploy, open the Vercel URL, enter `STUDIO_ACCESS_TOKEN`, and use the editor.
The browser does not receive the Supabase service-role key. It stores the Studio
token only in the current browser tab (`sessionStorage`). Groq remains configured
only in the private Factory service.

Creator Studio supports:

- local PNG/JPEG/WebP selection and drag-and-drop;
- images already in the private factory inbox from Pinterest, Commons or manual
  uploads (images over 3 MB should be opened from a smaller local copy);
- fill/contain crop, caption drag positioning, font size, width, fill, outline and
  optional uppercase;
- PNG download and upload of the finished result back to Supabase.

Later activation should add an asynchronous request queue from Studio to the
private Factory, add the reviewed Studio URL to Android, and replace the disabled
Coming soon button. This keeps the Groq key in one deployment.

## Local tests

```sh
python3 -m pip install -r tools/requirements-stickers.txt -r tools/requirements-pinterest.txt
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

For a local official Pinterest scan, set the same access-token and board-ID
variables and run `tools/pinterest_worker.py` only with a configured Supabase
test project. Production state is intentionally remote so GitHub Actions jobs can
resume without committing media or databases to the repository.
