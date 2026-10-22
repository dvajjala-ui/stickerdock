# Native AI integration: private caption slice and remaining gates

The first private caption slice is now in source. It is **not yet a live mobile AI
feature**: the additive database migration and independent server/worker enable
flags have not been applied, and a real account/device/provider run is pending.
The October 19 backup continuation reviewed these exact source paths:

- `api/captions.py` queues/status-checks operator Creator Studio jobs.
- Following R40, workspace caption/upload/library routes have no account gate.
  They are shared operator operations, not a mobile-user authentication or
  ownership boundary. See OPEN_ADMIN.md. Phone cloud authentication is retained.
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

## Implemented source slice (October 22)

- `api/personal_ai.py` verifies the native account bearer, then
  `api/_personal_ai.py` accepts one explicit, general-audience image-to-caption
  request. The chosen image is bounded, oriented, pixel-normalized and stripped
  of metadata. Client-supplied owner IDs and private storage paths are ignored.
- `202610220001_personal_ai_captions.sql` reserves each user-owned request before
  storage upload. A repeated UUID with the same source/prompt resumes one job;
  a mismatched replay fails. Three jobs per rolling 24 hours, at most two pending
  per account, and four personal-provider calls per UTC day are server-side caps.
  Tables and RPCs have service-role-only grants and RLS. Cancellation is
  owner-scoped; expired worker leases are terminal, so an ambiguous call cannot
  silently run twice.
- `tools/personal_ai_worker.py` validates the private WebP hash and path, then
  asks Groq vision for one short caption. It returns text only; it does not
  publish or import artwork. The dedicated scheduled workflow is disabled until
  `PERSONAL_AI_ENABLED=true` is configured as a GitHub repository variable.
- Android's Text tool has a consent dialog, a single-preview upload (without
  caption text), persisted request identity, status recovery and an explicit
  **Use as bottom caption** action. The suggestion joins Undo/Redo and the
  existing local draft; Save remains a separate tap. The action is hidden until
  the authenticated API reports `PERSONAL_AI_ENABLED=true` in Vercel. Mature
  packs have no online action. Existing cloud sign-in supplies encrypted session
  refresh; private sync can remain disabled.

The four-call cap is for the personal lane only. Factory and shared Studio have
their own current local limits; a single cross-lane provider reservation is
still required before claiming a unified budget. iPhone does not yet have this
new account-owned action. Original image synthesis is a separate unimplemented
feature; Groq vision captioning does not create novel people or imagery.

## Next code slice

1. Apply the additive migration and enable both Vercel/GitHub flags only after
   verifying the production table/RPC grants and worker secret configuration.
   Test with two ordinary confirmed accounts, not an operator account.
2. Apply one server-enforced provider budget shared with the Factory. Two Groq
   keys from one account do not imply independent quotas. Never trust client
   counters, hide errors as success or use unbounded automatic retries.
3. Connect iPhone Create to the same private account contract and explicit
   preview/apply/save semantics. Add native end-to-end two-account tests.
4. Retain personal source media only as long as needed for recovery and add an
   owner-scoped deletion/retention job. Measure real provider quality, timeout,
   429 behavior and recovery before expanding the daily cap.
5. Keep original image generation a separate opt-in operation with provider
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

Pinterest remains a separate live-reference integration. App 1624615 now has
Trial access and a verified read-only OAuth connection; the connected board was
empty at inspection, so nonempty Pin browsing is still unverified. It must not
be used as an image-download or automatic rehosting source. The licensed
media/Commons adapters and reviewed content supply remain independent work.
