# Daily Groq sticker generator

The private StickerDock Factory generates a target of **40 stickers per UTC day**,
in **eight batches of five**, at least **three hours apart**. It supplies Groq
with recent public RSS titles or your reference titles/HTTPS links, asks for
original short captions and mood/palette choices, then draws original reaction
characters locally using Pillow. These are AI-written caption stickers with
template artwork, not diffusion-generated images or downloaded web artwork.
Groq does not supply a general image-generation API through this integration.
The example rotates news, movie-inspired jokes and everyday Hinglish topics,
alternating static illustrations and animated pulse stickers. Movie jokes use
original captions; this does not download movie frames or copy dialogue.

Android remains fully usable without a Groq key. The key never enters the APK.
The recommended production host is GitHub Actions with private secrets and
Supabase state/storage; a Linux/macOS machine with systemd/launchd is still a
supported self-hosted alternative. Public catalog publication is a separate,
review-gated action and is never implied by generation alone.

## Install and configure locally

From the repository root, with Python 3.10+:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements-stickers.txt
cp tools/daily-stickers.example.json daily-stickers.local.json
```

Set `GROQ_API_KEY` using your scheduler's private environment/secret store. Never
put the key in JSON, command-line arguments, Git, Android source, or chat. An
EnvironmentFile used by systemd should be readable only by its owner (`0600`).

The production GitHub Actions path has been live-tested with the configured key.
On October 13, 2026, Groq authentication succeeded with
`openai/gpt-oss-20b`; a controlled five-sticker batch completed, and a later run
using the real production configuration resumed persistent state from 5 to
**10/40** on a fresh runner. The remaining capacity gate is a normal-cooldown
40/40 day; do not bypass the saved cooldown just to finish that test faster.

Install DejaVu Sans Bold (Debian/Ubuntu: `fonts-dejavu-core`) or pass `--font` with
another licensed TrueType font. The current caption renderer targets printable
ASCII, including romanized Hinglish; native-script shaping is not implemented.

Customize `daily-stickers.local.json`: model, topic, batch size, interval and
request cap. Groq model availability and account limits may change; model is
configurable and unsupported models fail without silent fallback. Example custom
references:

```json
"references": [{"title": "My reference topic", "url": "https://example.org/reference"}]
```

Only configured public RSS feeds are fetched; no arbitrary search or article
scraping occurs. Links supplied under `references` are saved as provenance but
their pages are not downloaded. Feed titles need a publication date within the
last 48 hours. When all feeds fail, generation explicitly records its mode as
everyday reactions rather than claiming fresh news. Feed contents are untrusted
prompt data. Artwork is locally drawn, never downloaded or executed from a model.
GENERAL is an intended local pack label, not proof of automated moderation;
review results before sharing them.

## Photo memes, animation and larger output targets

For photos you own or may adapt, create the ignored `reference-images/` directory
and put PNG/JPEG/WebP files there. Add `"photo"` to `modes`, set `image_directory`
to that folder and set `vision_model` to an image-input model available on the
Groq account. Production currently defaults to `qwen/qwen3.8-27b` for vision,
and that model plus the real Creator queue/vision path were live-verified on
October 13, 2026.

One reference image is sent to Groq per photo batch, with five different caption
variations. Reference photos are resized and their EXIF metadata is removed before
API transmission. This mode sends the image to Groq; text/illustration modes do
not. Unsupported vision models fail instead of pretending the image was
understood. Supply authorized Pinterest/movie references yourself; automatic
copying of platform sticker libraries is not implemented.

`animated` produces four-frame, 720ms pulse animations of the original reaction
layout. It is a real animated WebP, not AI-generated video or character motion.
Static and animated assets are separated into different packs; all packs have at
most 30 assets. Set `rating` to `MATURE` only for private non-explicit adult
humor. The public publisher refuses MATURE packs.

Automatic photo masking/background removal, generative image synthesis and video
synthesis need additional implementations/providers and are not available here.
For actual clip-frame animations (`clip` mode), free downloadable reference
inputs and optional official Pinterest board access, see
[Free media factory inputs](FREE_MEDIA_FACTORY.md).

To target **100/day**, set `daily_target` to 100, `batch_size` to 5,
`interval_seconds` to 3600 and `max_requests_per_day` to at least 20. That requires
20 successful calls over at least 19 hours, plus any provider cooldown. Forty/day
needs eight successful calls. The script caps target at 100 and attempts at 100
to bound accidental runs. These are configurable safeguards, not Groq capacity
measurements: actual RPM/TPM/daily allowances depend on model and account.

After each successful live call, `state.json` records token usage and returned
`x-ratelimit-*` request/token limit, remaining and reset headers. Use those real
headers to calculate a realistic schedule instead of guessing. The 10/40 live
production continuation proves authentication, generation and restart/resume; it
does not yet prove that the account can complete 40 every day indefinitely.

## Run locally

With the secret supplied in the process environment:

```sh
.venv/bin/python tools/daily_stickers.py --config daily-stickers.local.json
```

For a local self-hosted scheduler, invoke this periodically. The command exits
quickly without an API call until the next batch is due. On Linux, edit the three
absolute paths in `tools/scheduler/stickerdock-generator.service`, install it and
the accompanying timer as systemd **user** units, then enable
`stickerdock-generator.timer`. A continuously running user manager (or existing
server scheduler) is required while logged out. On macOS use launchd with the
same command/environment. The lock uses Unix `fcntl`; Windows is not supported by
that local scheduler helper.

For the recommended production setup, use
[`PRODUCTION_DEPLOYMENT.md`](PRODUCTION_DEPLOYMENT.md). GitHub Actions restores
persistent Factory state from Supabase on a fresh runner, executes only when due,
and saves state again at the end of the run. Scheduled Factory execution is still
gated by `STICKER_FACTORY_ENABLED`; keep that gate off until the reviewed full-day
40/40 test is complete.

State lives at `generated-stickers/YYYY-MM-DD/state.json` locally and inside the
private runtime-state archive in the production Supabase flow. Use exactly one
state lineage per scheduler. OS locking stops overlapping local invocations;
GitHub Actions uses workflow concurrency. The command reserves its request budget
before calling Groq, so a timeout or process crash still counts. Validated ideas
are saved before rendering so interrupted rendering resumes without another API
request.

HTTP 429 respects `Retry-After`, including HTTP dates, and retains whichever is
longer: the normal interval or provider cooldown. Failed/invalid responses never
trigger immediate retries. Authentication failures pause at least 24 hours. There
is a maximum request-attempt bound per UTC day. There is no backlog burst or
catch-up quota after downtime. Days reset at 00:00 UTC (05:30 India time).

## Import results

Each batch produces a `batch-NN-NN.zip` with 512×512 transparent WebP images,
static under 100KB or animated under 500KB. On reaching 40 with the example's
alternating modes, `daily-complete.zip` contains **two packs of 20** in
StickerDock's schema-3 backup format, including checksums, kind and rating. Source
references stay in the adjacent `references.json`, outside the strict archive.

In production, completed private ZIPs are uploaded under the private Factory
bucket and a `sticker_packs` row stays pending until explicit review. The public
bucket/catalog remains untouched until **Publish reviewed factory pack** is run
for a valid reviewed GENERAL day. The October 13 smoke deliberately left the
public bucket empty.

For local use, transfer the final ZIP to your phone and choose **Import sticker
ZIP** or **Restore**. You can import individual batch ZIPs earlier instead, but do
not also import the final archive unless you want repeated packs: restore creates
new IDs and does not globally deduplicate across separate archives. WhatsApp
installation remains an explicit StickerDock action. Real-device provider QA is
still pending.

## Verification

```sh
.venv/bin/python -m unittest discover -s tools -p 'test_*.py' -v
```

Tests use fake Groq responses and real Pillow rendering. They check 40/day,
spacing, budget/cooldown, interrupted rendering, duplicate captions, news age,
transparent image dimensions/size, animation timing, photo inputs, pack
segregation and ZIP checksums. Live production testing additionally verified
Groq/Supabase authentication, a real generated batch, persisted resume across a
fresh runner, and the production vision model. A complete live day and Android/
WhatsApp real-device behavior remain separate gates.
