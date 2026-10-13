# Live production evidence — October 13, 2026

This record captures production-facing checks performed after the October 13
Supabase migrations and the merges that enabled the production Factory and
Creator Studio queue. Temporary smoke workflows were created only on isolated
test branches and removed after use. No secret values are recorded here.

## Supabase migrations and security verification

The two production migrations were dry-run, applied and verified earlier on
October 13. The read-only verification confirmed RLS/grants for `factory_runs`,
`sticker_packs` and `creator_requests`, the service-role-only Creator claim
function, both storage buckets, absence of the old broad storage read policy, and
the expected update triggers.

## Factory: real Groq + Supabase generation

### Controlled five-sticker smoke

- Workflow run: `38395147351`
- Result: **success** after correcting a test-only invalid interval before any
  provider request was made.

Verified production result:

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

The private ZIP was downloaded again and its SHA-256 matched the `sticker_packs`
row. Nothing was published publicly.

### Real production-config continuation

- Workflow run: `38395458510`
- Result: **success**

The worker used `tools/factory-production.json` on a fresh runner and restored
persistent state instead of starting over:

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

This proves production state restore/resume through 10/40. It does not replace a
normal-cooldown full 40-sticker UTC-day capacity test.

## Creator backend: real queue → Groq vision → private result

- Workflow run: `38397706738`
- Result: **success**

The isolated smoke first refused to proceed if any unrelated queued request was
present. It then created a temporary USER_OWNED source/provenance record, queued
through the same `api._shared.queue_creator_request` helper used by the Studio
route, atomically claimed exactly that request and processed it with the real
configured Groq vision model.

Verified result:

```json
{
  "status": "complete",
  "attempts": 1,
  "dimensions": [512, 512],
  "result_bytes": 10588,
  "result_path_suffix": "webp"
}
```

The result provenance matched the request/source. Cleanup then removed the source,
result, both provenance metadata objects and the test database row:

```json
{
  "cleanup": "complete",
  "objects_deleted": 4,
  "request_deleted": true
}
```

## Creator Studio browser verification

### Manual editor Chromium smoke

- Workflow run: `38395765104`
- Result: **success**

Real headless Chromium exercised the Studio over HTTP:

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
  "browser_errors": 0
}
```

### Merged live-AI source verification

After PR #6 enabled the browser AI queue flow and the queue-aware 10-minute
Creator worker, the merged source was checked again:

- Workflow run: `38399224092`
- Result: **success**

A real Supabase queue preflight used the production service credential without
printing it and confirmed the currently deployed key type and empty queue:

```json
{
  "key_type": "sb_secret",
  "preflight": "pass",
  "queued_request_present": false
}
```

This proves the merged `sb_secret_...` authentication path used by the scheduled
Creator-worker preflight.

Real Chromium also exercised the enabled AI UI against deterministic API-boundary
responses, validating button enablement, private-source upload request, queue,
status polling and result loading:

```json
{
  "http": 200,
  "ai_button_enablement": "pass",
  "source_upload": "pass",
  "queue": "pass",
  "status_poll": "pass",
  "result_load": "pass",
  "mobile_overflow_px": 0,
  "browser_errors": 0
}
```

This browser smoke is not a hosted-Vercel proof; the HTTP API responses in this
specific UI test were deterministic test responses. The actual Supabase/Groq
backend path is independently proven by run `38397706738` above. A deployed
Vercel URL with `STUDIO_ACCESS_TOKEN` still needs a real authenticated smoke.

## Supabase storage monitor

- Workflow run: `38397881030`
- Result: **success**

The real read-only report returned:

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

`SUPABASE_STORAGE_QUOTA_MB` is still unset, so percentage warning/failure
thresholds cannot be evaluated yet. The monitor never deletes remote objects.

## Android production signing configuration

- Workflow run: `38398554181`
- Result: **blocked by missing signing configuration before Gradle/signing**

The real `android-production` environment exists and the earlier presence-only
check found these signing secrets empty:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

At the time of that probe `CATALOG_BASE_URL` was also unset. That is no longer a
release blocker: the signed-release workflow now defaults to the deployed
Supabase public catalog and retains `CATALOG_BASE_URL` only as an optional
environment override. The remaining Android configuration blocker is therefore
the four signing secrets above.

The job stopped before decoding a keystore, building or signing anything. This is
therefore a configuration blocker, not a known Android build/signature failure.

## Merged-main Android CI

The merged production source has continued through the normal Android CI build.
For PR #6's merged source, the substantive SDK setup, ADB helper validation,
debug/release test/build step and artifact upload all completed successfully in
run `38398846749`; post-job cleanup was the only work still running when inspected.

## Remaining external/release gates

- Complete one normal-cooldown Factory day to 40/40 and review real Groq quota /
  rate-limit behavior.
- Human-review a completed GENERAL production pack before deliberately publishing
  it; public storage was still empty in the live checks above.
- Deploy/configure Creator Studio on Vercel and perform one real token-authenticated
  `/api/*` browser smoke against the hosted URL.
- Configure the real Supabase storage quota before enabling meaningful daily
  percentage thresholds.
- Configure the four Android signing secrets above, then build/sign a release
  candidate and install/test it on the target Galaxy S24 Ultra.
- Configure an approved Pinterest OAuth token plus numeric board IDs and perform
  one official-board sync.
- Complete real-device WhatsApp/Business installation, animation and large-folder
  process-death acceptance testing.
