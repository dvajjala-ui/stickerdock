# HandNavigate Admin delivery — 28 October 2026

## Production result

**24 real source images are stored in production Supabase: 20 Pinterest + 4 Reddit.** HandNavigate's own browser, runner and outbound bridge performed the downloads. Its sink verified the stored blob and metadata by fetching each image back and comparing digests, geometry, decoded pixels and original source provenance. These were real hosted queue/API/storage calls, not Codex browser downloads or the older SQLite contract fixture.

The 20-image mixed-source request initially encountered an unconfigured Reddit image host and filled its count using Pinterest. HandNavigate was fixed to allow Reddit's advertised `external-preview.redd.it`; a separate four-image Reddit collection then passed. Source failures are visible even when another platform fills a request.

Safe evidence is in [production intake evidence](evidence/handnavigate-production-20261028.json). Downloaded image bytes, browser profiles and credentials remain outside Git.

## Admin flow

[Open Admin](https://stickerdock.vercel.app/admin) → **Collect images**:

- Enter topics, choose Pinterest/Reddit and a single total count; collect now or save a reusable named plan.
- Create, read, edit, archive and restore topic plans. The existing planner's topic plans or sticker angles can fill the topic field without another model call.
- Enable a plan's daily switch. GitHub Actions enqueues active daily plans around 09:53 Asia/Kolkata; the repository activation variable is enabled. The worker polls outbound, so no inbound browser/worker port is needed.
- See live worker connectivity, queued/running/final states, per-source errors and local/downloaded vs database/stored counts. Stop or resume incomplete collections.
- Browse actual database candidates with site, exact-topic and archive filters, 25-image pagination and lazy previews. Download, edit titles/topics/tags/review status, archive and restore images.

The **Daily animal reactions** plan is enabled at **20 images total**, across two topics and both platforms. Every plan shares HandNavigate's normal 20/day local budget. Scheduled intake skips a day already fulfilled by stored manual/test images. Its [manual workflow verification passed](https://github.com/dvajjala-ui/stickerdock/actions/runs/40580811653) with zero new requests after the 24-image live test. Archive preserves the data and its provenance. Permanent purge is not part of these controls.

## Deployment and verification

Vercel production follows `main`. Both additive intake migrations have been applied to the existing Supabase project; RLS denies direct anon/authenticated access to intake/jobs/plans/worker tables. Only server service-role access is granted. The worker endpoint rejects browser Origins and requires its server-only bearer credential. A SHA-256 credential hash is stored in the private integration configuration; the plaintext is held by GitHub Actions and the configured worker only.

Provisioning passed at [initial migration](https://github.com/dvajjala-ui/stickerdock/actions/runs/40578536889) and [heartbeat extension](https://github.com/dvajjala-ui/stickerdock/actions/runs/40579777213). The final functional Web/API revision [77f64c7 passed CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/40581384063). The local backend suite passed 280 tests before the additional quota-skip regression test; the seven focused collection-control tests pass, plus all SQL migration/grant/lease checks. The final UI regression also verifies decoded library previews, not only the full-size image dialog.

The real hosted Admin browser test saved a daily plan, edited a candidate, downloaded a file whose SHA-256 equaled the stored digest, archived/restored it, and checked desktop/mobile layout without page errors. The existing open shared Admin model is unchanged: possession of its URL permits workspace management. Private phone backups remain separately authenticated.

HandNavigate's final functional revision `1028004` also passed [worker CI](https://github.com/dvajjala-ui/handNavigation/actions/runs/40581869552) and [Windows desktop, build and portable acceptance](https://github.com/dvajjala-ui/handNavigation/actions/runs/40581869494). The parameterized collection service remains a separate worker entry point; these checks do not imply that it was embedded into the portable EXE.

## What is still pending

1. Move the worker to an always-on host if this PC cannot remain awake. A desktop and Windows Startup shortcut were installed on the current PC. No new server was bought or deployed. Docker/Linux and a new server's sign-in/session behavior still need live acceptance.
2. Observe multiple days of daily source completion, duplicate avoidance, challenge recovery and storage delivery before calling the quota dependable. Expired logins and human checks can still pause automation.
3. Review candidates for quality, source permission and reuse rights before admitting anything into the Factory inbox. Candidate review **does not** enable generation or publication. The Pinterest OAuth integration remains reference-only; this patch does not weaken that existing boundary. Caption/render quality and an approved source-to-sticker result remain separate work.
4. Qualify future platform recipes and Admin source registry support, GIF/video intake, retention/purge and source deletion/revocation handling.

The old [implementation plan](HANDNAVIGATE_INTEGRATION_2026-10-28.md) and [local fixture evidence](evidence/handnavigate-local-20261028.json) remain historical records. Their pre-deployment pending items are superseded where explicitly completed above.
