# Free image and clip inputs for the sticker factory

Use both paths: automatically collect a small reusable reference library and add
your own downloaded photos/clips. Rendering uses local Pillow/FFmpeg, with no paid
image/video-generation provider or cloud deployment. Groq writes captions and
optionally reads one reference frame per batch. Its model/account quota still
determines throughput; free-tier availability is not established by this code.

## Free automatic image library

`tools/media_library.py` searches Wikimedia Commons using its public API without
an API key. It accepts **CC0/public-domain** files based on returned license
metadata, saves their source/credit/license information, strips photo EXIF and
deduplicates files by hash. It does not infer a reuse license from search results
or from an image being publicly visible. Attribution-required licenses are
excluded from automatic discovery until the app can preserve display attribution.

Run an hourly bounded sync:

```sh
python3 tools/media_library.py --config tools/media-library.example.json --output reference-images
```

The example permits at most six network-budget slots per UTC day and two media
downloads per invocation; discovery also consumes a slot, so successful media
downloads may be fewer than six. It spaces calls hourly, reserves attempts before
network requests and respects 429 `Retry-After` across midnight. Commons calls
must be polite and fit its actual server limits; no unlimited download guarantee.

To have the existing factory scheduler sync the library automatically, add this
block to `daily-stickers.local.json`:

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

Use a working Groq `vision_model` and add `photo` to `modes` to generate captions
from the downloaded images. Without a valid vision model, the factory will not
pretend that it understood a photo. Existing illustration/animated modes continue
without reference uploads.

## Direct downloads and your own uploads

Place authorized local PNG/JPEG/WebP/GIF/MP4/WebM/MOV files in `reference-images/`.
For a direct file URL, add an explicit queue entry:

```json
"queue": [{
  "title": "My reference image",
  "source_url": "https://example.org/source-page",
  "download_url": "https://example.org/photo.jpg",
  "license": "CC0"
}]
```

Supported declarations are `CC0`, `PUBLIC_DOMAIN`, and `USER_OWNED` (for assets you
actually own). Source-page URLs are provenance; `download_url` must point directly
to a supported media format. No page parser extracts hidden images or copies
someone else's sticker pack. Downloads are limited to 20MB, images to 20MP, and
successful library URLs are recorded to avoid repeated downloads. The library
and personal files are ignored by Git. Pinterest photos/clips supplied by you
can use this same local-input path; a free download alone is not a reuse license.

## Pinterest: authorized boards plus reference bookmarks

The [official Pinterest API specification](https://github.com/pinterest/api-description/blob/main/v5/openapi.yaml)
was checked during implementation. `/search/partner/pins` is documented as beta
and **not available to all apps**. `/pins/{pin_id}` reads pins owned by the
authorized account or on a shared group board. OAuth authorization is required.
This is not an anonymous public meme-search/download API.

Optional board ingestion uses the official read-only
`GET /v5/boards/{board_id}/pins` endpoint, one page (at most 25 pins) per sync.
Supply your authorized `PINTEREST_ACCESS_TOKEN` in the scheduler environment and
configure these fields inside `media_library`:

```json
"pinterest_board_id": "123456789",
"pinterest_pin_licenses": {"987654321": "USER_OWNED"}
```

Board access still depends on the app/account permissions. Saved pins do not
automatically become owned assets: only pin IDs with a supported explicit reuse
declaration enter the download queue. All returned pins can be retained as
reference bookmarks in `reference-images/library.json`. No posting, login
scraping, private endpoint access, partner-search assumption or paid fallback.
The adapter does not imply that Pinterest grants rights to adapt the content.

## Actual short-clip stickers

Install FFmpeg (Ubuntu/Debian: `apt-get install ffmpeg`). Add `clip` to factory
`modes` and set a working Groq vision model. Clip batches choose local GIF/MP4/WebM/
MOV input, send only the first decoded frame for caption ideas, and render five
caption variations over the **actual video frames**. Groq does not analyze the
full video's motion or synthesize new video. This differs from `animated` mode,
which makes a pulse animation of a drawn character.

Converter limits: first two seconds, eight frames/sec, at most 16 decoded frames,
512×512 canvas, 125ms per frame and animated WebP <=500KB. Audio is discarded.
Images keep their visual background; background removal is not implemented.
Local decoding uses a network-disabled FFmpeg protocol allowlist and timeout.
If a clip cannot fit the size limit it is rejected rather than exported as valid.

There is also a completely offline captioned clip command:

```sh
python3 tools/video_stickers.py --input reference-images/clip.mp4 \
  --caption 'Scene kya hai bhai?' --output generated-stickers/clip.webp
```

Import the resulting WebP as an animated sticker into StickerDock. Factory ZIPs
segregate static/animated packs, each with at most 30 stickers. Media provenance
travels into daily `references.json` and factory records, outside the strict ZIP
manifest. Phone/WhatsApp runtime QA is still required.

## Current verification and remaining blocker

Real local GIF/MP4 fixtures were decoded with FFmpeg and exported as bounded
animated WebP in automated tests. Tests also check license filtering, image
sanitization, provenance, deduplication, daily caps, 429 cooldown, and per-pin
Pinterest rights declarations. Provider responses are fixtures, not live claims.

This environment still blocks `api.groq.com`, `commons.wikimedia.org` and Pinterest
with proxy 403. Live provider/vision tests and account-limit measurements therefore
remain pending. For online image use the runtime needs `api.groq.com`,
`feeds.bbci.co.uk`, `commons.wikimedia.org` and `upload.wikimedia.org` allowed.
Pinterest additionally needs `api.pinterest.com` and the actual returned media
hosts (commonly `i.pinimg.com`). No daily job has been activated in this temporary
development environment, and no paid service has been provisioned.
