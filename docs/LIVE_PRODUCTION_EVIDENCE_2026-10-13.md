# Live production evidence — October 13, 2026

This file records the production-facing checks run after PR #4 and PR #5 were
merged to `main`. Temporary smoke workflows were created only on an isolated test
branch and removed after each run. No secret values are recorded here.

## Merged-main baseline

- `main`: `00767afaef33802f18a638cac59295742df000a1`
- Android debug workflow run: `38394733609`
- Result: **success**

This confirms the merged PR #4/#5 state still passed the repository's normal
Android test/build workflow before the live service checks below.

## Factory: controlled live Groq + Supabase smoke

Workflow run: `38395147351`

A GENERAL illustration-only batch was deliberately limited to five stickers and
used real `GROQ_API_KEY`, Supabase service-role access and the production worker.
The first test-only attempt failed before any Groq request because its temporary
config set `interval_seconds=0`; the generator correctly rejected values below
60 seconds. The corrected run used 60 seconds and passed.

Verified result:

```json
{
  "day": "2026-10-13",
  "factory_status": "complete",
  "generated": 5,
  "target": 5,
  "pack_status": "pending",
  "pack_rating": "GENERAL",
  "sticker_count": 5,
  "private_archive_bytes": 19715,
  "public_bucket_objects": 0
}
```

The private archive was downloaded again and its SHA-256 was checked against the
`sticker_packs` row. Nothing was published to the public bucket.

## Factory: real production-config continuation

Workflow run: `38395458510`

The next run used the repository's actual `tools/factory-production.json`,
including the 40/day target and the normal feed/media-library path. It restored
persistent state from Supabase instead of starting from an empty runner.

Verified result:

```json
{
  "attempts": 2,
  "day": "2026-10-13",
  "factory_status": "running",
  "generated": 10,
  "target": 40,
  "persistent_records": 10,
  "reference_library_assets": 2,
  "pending_smoke_pack_stickers": 5,
  "public_bucket_objects": 0
}
```

This proves fresh-runner restoration and continuation from 5 to 10 generated
records. It does not replace a full 40-sticker UTC-day capacity test.

## Creator Studio: real Chromium interaction smoke

Workflow run: `38395765104`

Playwright downloaded and launched Chrome for Testing in GitHub Actions and
navigated the real static `studio/` application over HTTP. The test exercised
actual browser interactions rather than a DOM-only unit test.

Verified result:

```json
{
  "http": 200,
  "image_upload": "pass",
  "canvas_caption": "pass",
  "style_controls": "pass",
  "caption_drag": "pass",
  "png_download_bytes": 31012,
  "missing_token_guard": "pass",
  "mobile_overflow_px": 0,
  "ai_controls_disabled": true
}
```

No page errors or console errors were observed. A 390×844 viewport had no
horizontal overflow. The visible AI controls intentionally remained disabled.

The run retained a short-lived `creator-studio-chromium-smoke` artifact containing
a mobile screenshot and the downloaded PNG for manual inspection.

## Creator request backend: queue → claim → Groq vision → result

Workflow run: `38397706738`

The isolated smoke refused to start if any pre-existing queued Creator request
was present. It then generated a temporary USER_OWNED source image, stored its
rights metadata privately, enqueued a request through the same
`api._shared.queue_creator_request` helper used by the Studio route, and processed
exactly one request with the real Creator worker.

Verified worker result:

```json
{
  "status": "complete",
  "attempts": 1,
  "dimensions": [512, 512],
  "result_bytes": 10588,
  "result_path_suffix": "webp"
}
```

The test also verified the result metadata contained the matching Creator request
ID and `derived_from` source path. The source/result objects, both provenance
metadata objects and the test database row were then deleted:

```json
{
  "cleanup": "complete",
  "objects_deleted": 4,
  "request_deleted": true
}
```

This proves the live Supabase queue, service-role-only atomic claim, configured
Groq vision model, local renderer and private result path. A separate deployed
Vercel HTTP test with `STUDIO_ACCESS_TOKEN` is still required before exposing the
AI controls.

## Supabase storage monitor

Workflow run: `38397881030`

The read-only production storage reporter ran against the real project and
returned:

```json
{
  "total_bytes": 4204527,
  "quota_bytes": null,
  "usage_percent": null,
  "level": "ok",
  "buckets": [
    {"bucket": "sticker-factory-private", "objects": 7, "bytes": 4204527},
    {"bucket": "sticker-factory-public", "objects": 0, "bytes": 0}
  ],
  "private_generated_retention_days": 30,
  "private_generated_candidates": []
}
```

`SUPABASE_STORAGE_QUOTA_MB` was not configured, so this run intentionally stayed
report-only for quota percentage/threshold decisions. No remote object was
removed by the monitor.

## Android production signing environment

Workflow run: `38398554181`

A presence-only production-environment check entered the real
`android-production` environment and stopped before Gradle/signing because the
required deployment values were not configured. The missing names were:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `CATALOG_BASE_URL`

No keystore was decoded and no APK/AAB was built in this run. This turns the
signed-release gate into a concrete configuration task rather than an unknown
build problem. The temporary workflow used for this check was removed afterward.

## Gates still open after these checks

- Complete a normal 40-sticker UTC day and inspect actual Groq quota/rate-limit
  metrics before enabling recurring generation broadly.
- Human-review a completed GENERAL pack before using the publish workflow; the
  public bucket is intentionally still empty.
- Smoke-test the deployed Vercel Studio `/api/*` endpoints with the configured
  `STUDIO_ACCESS_TOKEN` before enabling visible AI controls.
- Configure the actual Supabase storage quota variable before enabling percentage
  warnings/failures on the daily monitor.
- Configure the five missing `android-production` signing/catalog values above,
  then build and install a signed release candidate on the target device.
- Complete Pinterest authorized-board setup and real-device WhatsApp/WhatsApp
  Business / large-library acceptance testing.
