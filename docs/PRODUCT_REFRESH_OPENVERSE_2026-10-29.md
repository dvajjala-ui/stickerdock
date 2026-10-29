# StickerDock product refresh and Openverse source — 2026-10-29

## Product-owner decision

StickerDock should feel like a focused personal reaction library, not an admin
dashboard or a collection of generic AI feature cards. The primary consumer flow
stays **Library → Create → Discover**:

1. Library answers “find the reaction I need now” and keeps WhatsApp handoff close.
2. Create explains a short media → edit → pack → send workflow.
3. Discover contains reviewed packs only. Raw daily-source images never appear in
   the mobile app.
4. Import, backups, cloud and planned features remain secondary tools.
5. Unfinished ideas are listed in one Coming next sheet instead of becoming dead
   buttons across everyday screens.

## Android refresh in this change

- Reused the launcher mark as the in-app brand: stacked sticker sheets, ink and
  volt-lime chat bolt.
- Replaced the muted generic palette with ink, volt lime, warm paper and one
  restrained coral accent, with matching dark-mode tokens.
- Added a compact StickerDock lockup to root app bars and a larger lockup to the
  first-library experience.
- Rewrote the empty library and Create screens around user outcomes, privacy and
  three visible steps rather than marketing-style cards.
- Added a Discover masthead that explains the reviewed-publication boundary.
- Added a Coming next sheet for suggestions, community identity/moderation,
  reviewed-source expansion and large-library search.
- Fixed visible encoding damage in Android and Admin (`â€¢`, `Â·`, broken ellipses
  and arrows).

The navigation labels and default Library destination are intentionally
preserved so existing users and automated acceptance flows do not lose their
orientation. The current launcher vector already has a distinctive readable
mark, so this change applies it consistently rather than replacing it without a
device-size review.

## Openverse product boundary

Openverse is the next primary free source because it supports keyless search and
license/mature/use filters. HandNavigate source now requests CC0/public-domain,
commercial/modification-compatible, non-mature candidates and downloads pixels
only through Openverse's fixed API thumbnail endpoint. It retains the original
work URL, title, creator and source-reported rights as review hints.

Openverse does not verify each license. StickerDock therefore stores every result
as `UNREVIEWED` and `generation_eligible=false`. Admin shows the hints beside the
original-work link, but the owner must still verify the origin, choose reusable
rights and record evidence before private generation. Publication remains a
separate pack review.

## Evidence and remaining gates

- StickerDock Python suite: 328 tests passed, 7 skipped.
- Admin JavaScript syntax checks passed.
- HandNavigate core: 37 tests passed.
- HandNavigate service: 36 tests passed.
- Real local Openverse check: 2 requested, 2 decoded and verified; one reported
  CC0 and one public-domain mark.
- Android source changed but local Java/Gradle is not installed on this computer;
  Android compile, screenshots, 200% text, dark mode and signed release must be
  established by GitHub Actions and then physical-device review.
- Openverse is not yet enabled on Railway. Deploy the compatible worker, run a
  2-image hosted storage/read-back check, then change the daily plan.
- Pinterest and Reddit remain Coming Soon/interactive-host work because Railway
  datacenter guest access is blocked. No login bypass is attempted.

## Next acceptance order

1. Merge green HandNavigate and StickerDock CI.
2. Deploy the new worker to Railway; verify it advertises `openverse`.
3. Run a 2-image Openverse job into Supabase and read both images back.
4. Replace or extend the daily plan only after that hosted check.
5. Review one candidate into a private Factory draft; do not auto-publish.
6. Inspect Android library, create, Discover and Coming next screens in light,
   dark and 200% text; then test Galaxy S24 Ultra SAF and WhatsApp handoff.
