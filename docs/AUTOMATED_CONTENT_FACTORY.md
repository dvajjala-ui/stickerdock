# Automated content Factory

StickerDock now has a daily content-intelligence stage in front of the existing
three-hour sticker generator. The first enabled Factory invocation on each UTC
day creates a private, source-linked brief. Later invocations reuse that brief,
rotate its categories, and continue the saved daily sticker state.

## GitHub names to configure

Add these in **GitHub → repository Settings → Secrets and variables → Actions**.
Values remain server-side and never enter the APK or Admin browser JavaScript.

| Type | Exact name | Purpose |
| --- | --- | --- |
| Secret | `GROQ_TRENDS_API_KEY` | Separate Groq account/key for the once-daily browser-search and structuring calls |
| Secret | `GROQ_API_KEY` | Existing caption and vision generation key |
| Secret | `SUPABASE_SERVICE_ROLE_KEY` | Existing private Factory state/storage access |
| Variable | `GROQ_TRENDS_MODEL` | Optional; defaults to `openai/gpt-oss-20b` |
| Variable | `GROQ_TEXT_MODEL` | Optional caption-model override |
| Variable | `FACTORY_DAILY_TARGET` | Optional workflow default; Admin settings override it after migration |

Use the exact secret name `GROQ_TRENDS_API_KEY` for the new higher-limit key.
The current Groq browser-search implementation uses `openai/gpt-oss-20b`.
Do not use the retired `groq/compound` or `groq/compound-mini` model IDs.

Pinterest is a separate read-only reference connection. Its app secret is a
Vercel Production variable, and OAuth credentials are stored in private
Supabase storage. The Pinterest Actions job checks connection health only; it
does not download, archive, or pass Pin media to the Factory. See
[PINTEREST_RECONNECTION.md](PINTEREST_RECONNECTION.md). The old
`PINTEREST_PIN_RIGHTS_JSON` and downloader settings are retired.

## Daily flow

1. GitHub Actions starts the Factory every three hours. Existing cooldown and
   request-budget state prevents extra generation calls.
2. The first run of the UTC day reads Admin settings and checks `content_briefs`.
3. If discovery is enabled and `GROQ_TRENDS_API_KEY` exists, Groq browser search
   researches the enabled categories. A separate JSON pass validates a bounded
   plan. Only HTTPS source URLs present in the research result are accepted.
4. If browser search is unavailable, the brief records an explicit fallback and
   uses recent configured RSS titles plus owner topics.
5. The brief is stored in the service-role-only table and private Storage at
   `content-briefs/YYYY-MM-DD.json`.
6. The caption generator rotates the brief's topics. It renders original local
   illustration/animation stickers or transforms authorized private inbox media.
7. The final ZIP groups packs by static/animated kind, audience and Android pack
   category. Completed output can automatically enter Admin as a private draft.
8. The owner reviews wording, appearance and source rights. Only a reviewed
   `GENERAL` pack can be published to the APK catalog.
9. The APK refreshes the Factory feed and shows the categories contained in each
   reviewed archive. Users choose **Import**, organize the resulting category
   packs, then choose **Add to WhatsApp** or Business.

## Admin controls

The Content planner shows the latest brief, source mode, category, source link,
date and warnings. Settings control:

- generation pause/resume, target and batch size;
- daily discovery on/off;
- automatic completed-output migration into private Admin drafts;
- general versus private mature output;
- Trending, Politics, Creative, Movies, Series, India, Sports, Technology,
  Everyday and Adult humor topic selection;
- up to 20 custom topics.

Custom topics may use `CATEGORY | topic`, for example:

```text
INDIA | Cricket group-chat reactions
MOVIES | Opening-night excitement without quotes or film frames
CREATIVE | Monday meeting reactions
```

`ADULT_HUMOR` requires a `MATURE` run. All packs from that run remain private;
the public publisher rejects MATURE content. News and politics are converted to
short reactions rather than factual claims, endorsements or copied headlines.

## Image behavior

The automated Factory does not need a web image for every sticker. It can draw
original illustrated and animated reactions locally. It also consumes:

- owner uploads from Admin or Creator Studio;
- CC0/public-domain images discovered through Wikimedia Commons;
- live Pinterest board source links for inspiration only, with no Pin bytes in
  the media queue;
- licensed short clips supported by the existing FFmpeg path.

Groq provides research, caption writing and source-image analysis here; it is
not used as an image-synthesis provider. Search results, movie stills, celebrity
photos and arbitrary memes are not downloaded merely because they are popular.

## Activation and proof boundary

The new admin migration must be applied before this source is deployed. The code
has local unit/browser/build coverage, but a real browser-search generation run
requires the new GitHub secret and the revision on the default branch. After
adding the secret, manually run **Scheduled sticker factory** once. Verify the
Actions log summary, Admin Content planner, private draft, and then review one
GENERAL pack before publishing it. Real WhatsApp and WhatsApp Business install
acceptance remains a Galaxy S24 Ultra device check.
