# Native AI integration: audited boundary and next implementation

This is remaining work under R26/R31/R37/R38, not an implemented mobile AI
feature. The October 19 backup continuation reviewed these exact source paths:

- `api/captions.py` queues/status-checks operator Creator Studio jobs.
- `api/_shared.py:authorized` accepts an owner browser session or operational
  Studio token. It is not a general mobile-user authentication boundary.
- Android `CloudLibrary` has encrypted account sessions and refresh, but its
  transport currently targets `/api/cloud` and keeps bearer acquisition private.
- `tools/creator_request_worker.py` uses server-side Groq vision/captioning and
  supplied real media. `tools/original_artwork.py` is a separate optional original
  image worker; no live original-art provider trial has been completed.

The audited Windows host has about 8 GB of system RAM and the previously checked
4 GB GPU. The project environment has no Torch/diffusers/transformers/accelerate
installation. The evaluated FLUX model's standard memory requirement exceeds
this setup; a smaller or offloaded model would need its own quality, memory and
latency benchmark. This is not proof that every local/free model is impossible,
and is not evidence of working high-quality free generation.

Do not put Groq keys or a Studio/owner token in an APK to connect these pieces.
An existing operator caption queue does not establish native app AI support.

## Next code slice

1. Add an account-authenticated native job endpoint. Verify the existing bearer
   with Supabase, scope every upload/job/status/result to that user, and keep
   operator requests separate. Reuse authentication/session refresh through a
   shared native transport without exposing tokens to UI state or logs.
2. Add private job records with ownership/RLS, bounded input, request identity,
   status, attempt reservation, retry time and result. Repeated submissions must
   reuse a request identity. An ambiguous provider response must not silently
   become another charged/request-budget-consuming submission.
3. Apply one server-enforced provider budget shared with the Factory. Two Groq
   keys from one account do not imply independent quotas. Never trust client
   counters, hide errors as success or use unbounded automatic retries.
4. Add an explicit Text-tool suggestion action. Explain that the chosen current
   sticker is sent online, show a preview and require the user's action before
   upload. Keep mature/offline-only packs offline. Send only the chosen bounded
   rendered sticker, excluding backup originals, masks and other library media.
5. Present queued/working/retry/result states, allow cancellation, and recover a
   pending request after process death. Suggestions stay separate from the saved
   project until the user chooses one; applying a caption joins Undo/Redo and
   draft recovery. Saving/exporting remains explicit.
6. Keep original image generation a separate opt-in operation with provider
   setup, approved spending limits and a real quality trial. Groq captioning and
   image conversion are not original image synthesis. Do not publish generated
   packs or fill the user's library automatically.

## Acceptance before calling it working

Use two ordinary accounts to test cross-account isolation, expired sessions,
repeated taps, shared budgets, rate limits, timeout/ambiguous responses,
cancellation and restart. Confirm that API keys remain server-side and originals
are absent from uploads. Run the actual native flow against the deployed
endpoint with an authorized live provider trial; mocked tests and an owner web
job are insufficient. Test useful captions with varied owned/licensed reaction
photos and clips. Original-image quality and cost require their own evidence.

Pinterest remains a separate live-reference integration, currently blocked by
the invalid/expired token recorded in PINTEREST_RECONNECTION.md. It must not be
used as an image-download or automatic rehosting source. The existing licensed
media/Commons adapters and reviewed content supply remain independent work.
