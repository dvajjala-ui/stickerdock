# Sources workspace and hosted collection — October 28, 2026

Owner-authorized work: fix the collection bridge, run daily away from the local
PC, prefer public guest access, use approved HandNavigate images in new stickers,
consolidate image management, clean verified damaged legacy images, and simplify
Admin navigation around product work.

## Delivered source behavior

- **Sources** is the main image library for collected, uploaded and indexed older
  images. It has title/topic search, platform and usage filters, pagination,
  checksum-verified previews, upload/drop/clipboard input and recoverable Trash.
  Collections and saved topic plans are a tab within Sources.
- **Stickers & packs** has search, status, origin and audience filters. Opening a
  pack exposes its actual sticker previews, edits and review/publication controls.
- **Runs & automation** owns AI run history. Overview shows useful counts and
  recent packs without repeating the detailed daily-run dashboard. Optional
  connections and future capabilities are separate from daily library work.
- Approval requires explicit reuse rights, evidence and a visual-quality check.
  The private receipt binds candidate ID, version and image digest. Automatic
  Factory photo selection prefers approved workspace sources while retaining
  existing OCR, diversity, cooldown and public review gates. Editing/revocation/
  Trash invalidates admission; cached and pending sources are rechecked. Merely
  downloading a social image never grants a reuse license.
- A reviewed candidate can enter the existing custom Creator queue or create a
  private pack. Phone libraries and already-created pack bytes are retained.
- Legacy maintenance indexes valid static inbox images without granting new
  permission. Verified corrupt, unreferenced images can be moved into verified
  private quarantine before their broken inbox copy is removed. A preview
  transport error alone is never evidence for deletion.

## Hosted daily operation

The existing `handnavigate-intake.yml` schedule now starts a pinned HandNavigate
worker on a GitHub-hosted Linux runner, enqueues saved/planner topics and waits
for verified Supabase delivery. It shuts down cleanly and read-back verifies a
private checkpoint of journal/dedup state and unfinished originals. Browser
profiles and credentials are excluded. No collection images go in public CI
artifacts or caches. Vercel continues serving Admin/API; Supabase keeps images
and metadata. No new hosting subscription is required by this implementation.

This is scheduled execution, not a continuously online server. Admin requests
queued between runs wait for the next hosted invocation unless another worker
is active. GitHub may delay scheduled starts. A true always-on VM remains an
optional later deployment if immediate dispatch becomes necessary.

The worker tries guest browsing. Optional login controls alongside usable public
images do not force sign-in. An actual challenge or source restriction remains
visible and is not bypassed. Guest access from a datacenter IP needs real hosted
qualification; code/tests cannot guarantee Pinterest or Reddit availability.

## Verification

Local: 319 Python tests passed, seven existing media/environment skips. New
integration coverage checks admission, provenance, Creator handoff, cached-source
revocation, stale approvals, pixel tampering and checkpoint exclusions. Real
Chromium fixture acceptance checks upload, review, consolidated navigation and
390px layout. HandNavigate passes 36 unit and 15 service tests, including redirect
non-transmission and guest galleries beside optional login prompts.

Hosted CI, actual hosted guest download/storage, legacy cleanup counts and final
deployment evidence are recorded separately when completed. No new public pack
or paid provider generation is needed to verify this release.
