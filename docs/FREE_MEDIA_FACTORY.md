# Free image and clip inputs for the sticker factory

StickerDock can automatically collect a small reusable reference library and can
also use authorized photos/clips supplied by the owner. Rendering itself uses
Pillow/FFmpeg and does not require a paid image/video-generation provider. In the
recommended production setup GitHub Actions performs the work and Supabase holds
private media/state; Groq writes captions and can read one reference frame per
photo/clip batch. Provider/model/account quotas still determine throughput.

## Free automatic image library

`tools/media_library.py` searches Wikimedia Commons using its public API without
an API key. It accepts **CC0/public-domain** files based on returned license
metadata, saves source/credit/license information, strips photo EXIF and
deduplicates files by hash. It does not infer reuse permission merely because an
image is publicly visible. Attribution-required licenses are excluded from
automatic discovery until the app can reliably preserve/display attribution.

For a local bounded sync:

```sh
python3 tools/media_library.py --config tools/media-library.example.json --output reference-images
```

The example permits at most six network-budget slots per UTC day and two media
downloads per invocation; discovery also consumes a slot. It spaces calls,
reserves attempts before network requests and respects 429 `Retry-After` across
midnight. Commons calls must remain polite; the local budget is a safety guard,
not a promise of provider capacity.

To have the Factory sync the library automatically, configure its media-library
block, for example:

```json
"image_directory": "reference-images",
"media_library": {
  "enabled": true,
  "daily_download_limit": 6,
  "max_downloads_per_run": 2,
  "interval_seconds": 3600,
  "commons_queries": ["funny cat", "dog", "space", "tea"],
  "queue": []
}
```

Use an available Groq vision model and add `photo` to `modes` to generate
captions from reference images. Production currently defaults to
`qwen/qwen3.8-27b`, which has been live-verified through the Creator vision path.
Without a valid vision model, the Factory fails rather than pretending that it
understood a photo. Illustration/animated modes can continue without reference
uploads.

## Direct downloads and your own uploads

Place authorized local PNG/JPEG/WebP/GIF/MP4/WebM/MOV files in
`reference-images/`. For a direct file URL, use an explicit queue entry such as:

```json
"queue": [{
  "title": "My reference image",
  "source_url": "https://example.org/source-page",
  "download_url": "https://example.org/photo.jpg",
  "license": "CC0"
}]
```

Supported declarations are `CC0`, `PUBLIC_DOMAIN`, and `USER_OWNED` for assets
you actually own. Source-page URLs are provenance; `download_url` must point
directly to supported media. No page parser extracts hidden images or copies
someone else's sticker pack. Downloads are bounded by size/pixel limits and
successful URLs are recorded for deduplication. A freely reachable file is not,
by itself, a reuse license.

## Pinterest: authorized boards plus reference bookmarks

The official Pinterest API is the preferred Pinterest integration. Board access
requires an approved app/OAuth token and actual authorized numeric board IDs.
The implementation uses read-only board pagination; it is not an anonymous public
meme-search/download API.

Supply `PINTEREST_ACCESS_TOKEN` only through the private runtime secret store and
configure authorized board IDs. Saved Pins do not automatically become owned
assets. Pinterest inputs default to `REFERENCE_ONLY`; only explicit compatible
rights declarations such as `USER_OWNED`, `CC0`, or `PUBLIC_DOMAIN` can make a
specific Pin eligible for later reviewed publication.

Optional public-topic discovery exists but is disabled by default. It uses public
search without login cookies, CAPTCHA handling, private endpoint access or
rate-limit bypasses, and all such results remain `REFERENCE_ONLY`.

Pinterest production is **not live-verified yet** because the required approved
app/token/board IDs have not been configured. Do not infer Pinterest approval from
other production smoke tests.

## Actual short-clip stickers

Install FFmpeg (Ubuntu/Debian: `apt-get install ffmpeg`). Add `clip` to Factory
`modes` and use a working Groq vision model. Clip batches choose local
GIF/MP4/WebM/MOV input, send only the first decoded frame for caption ideas, and
render caption variations over the **actual video frames**. Groq does not analyze
the full motion or synthesize new video. This differs from `animated` mode, which
makes a pulse animation of drawn artwork.

Converter limits include the first two seconds, eight frames/sec, at most 16
decoded frames, a 512×512 canvas, 125ms frame timing and bounded animated-WebP
size. Audio is discarded. Local decoding uses a network-disabled FFmpeg protocol
allowlist and timeout. If a clip cannot fit the sticker size bound it is rejected.

There is also an offline captioned clip command:

```sh
python3 tools/video_stickers.py --input reference-images/clip.mp4 \
  --caption 'Scene kya hai bhai?' --output generated-stickers/clip.webp
```

Factory ZIPs segregate static/animated packs and preserve media provenance in the
adjacent references/factory metadata. Android/WhatsApp real-device animation QA
is still required.

## Current verification

Automated tests use real local GIF/MP4 fixtures with FFmpeg and verify bounded
animated-WebP output. Tests also cover license filtering, image sanitization,
provenance, deduplication, daily caps, 429 cooldown and per-Pin Pinterest rights.

Production GitHub Actions networking is no longer a blocker for the core Factory:

- Groq authenticated successfully with the current text and vision models;
- a controlled live Factory batch generated 5/5 stickers;
- the real production configuration then resumed from 5 to 10/40 on a fresh
  runner;
- that real-config continuation synchronized **two reference-library assets**;
- persistent state survived the fresh runner and public storage remained empty;
- the real Creator queue/vision worker generated and verified a private WebP.

These results prove the core hosted network path and media-library integration;
they do not prove long-term Commons availability or arbitrary provider uptime.
The remaining media-provider gate is Pinterest account configuration plus one
official-board smoke sync. A normal-cooldown 40/40 Factory day and real-device
Android/WhatsApp media behavior also remain pending.

For production details see [PRODUCTION_DEPLOYMENT.md](PRODUCTION_DEPLOYMENT.md).
