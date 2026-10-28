# HandNavigate → StickerDock deployment handoff — October 28, 2026

This is the durable owner handoff for the image-collection bridge, hosting work,
Admin product changes and current acceptance boundary. It records what was asked,
what is live, what failed, what it costs and what must happen next. It contains no
worker credential, database key, browser profile or private key.

## Owner requirements captured

1. Review HandNavigate and StickerDock as one product flow, fix important defects
   and preserve the working implementation instead of rebuilding blindly.
2. Run collection away from the owner's computer, download approximately 20 new
   images daily and store them in StickerDock/Supabase. Prefer Oracle Free; use the
   existing Railway Hobby account only within the approved $5–$10 monthly range.
3. Try Pinterest and Reddit as ordinary guest-access sources. Do not require an
   API account merely because a page offers login, but do not bypass a real block,
   challenge, site restriction or rights requirement.
4. Let approved HandNavigate candidates become inputs to new sticker generation.
   Downloaded media remains ineligible until reuse rights and visual quality are
   reviewed; it is never automatically published.
5. Consolidate source images into one Admin **Sources** workspace with platform,
   topic, status, search, usage and Trash filters plus upload, drag/drop and pasted
   custom images. Never erase a healthy image because a preview transport failed.
6. Keep one dedicated **Stickers & packs** workspace with useful filters, previews,
   editing, review and publication controls.
7. Move detailed AI run history into **Runs & automation**, remove repeated daily
   clutter from Overview, and place unavailable integrations in Connections/
   roadmap states instead of presenting them as working.

## Implemented product flow

HandNavigate now polls the existing authenticated outbound queue, refreshes a
minimal cloud duplicate history, collects with a bounded headful Chromium session,
normalizes images and sends them to StickerDock. StickerDock stores candidates in
the private `sticker-factory-private` bucket and `media_intake_items`, reads the
stored bytes back, verifies SHA-256 and geometry, and records provenance. Railway
receives only the worker ingest credential—never a Supabase service key.

The scheduled process stops after 90 idle seconds or 25 active minutes. An outer
28-minute timeout and restart policy `NEVER` prevent stuck-browser billing loops.
Tini reaps Chromium children. The 5 GB `/data` volume retains browser state,
unfinished originals, journals and duplicate identities. No public domain, VNC or
browser-debug port is exposed by the Railway cron image.

StickerDock's Admin now has the requested Sources, Stickers & packs, Runs &
automation and Connections/roadmap separation. Source approval is version- and
digest-bound. Editing, revoking or archiving a source invalidates generation
admission. The legacy audit examined 123 indexed images, found zero corrupt or
broken candidates and deleted zero files.

## Oracle Free attempt

The signed-in tenancy is in Mumbai. Before VM creation, a free VCN, public subnet,
internet gateway and default route were prepared. SSH port 22 is restricted to the
deployment computer's observed public IP; no public VNC or application port was
opened. A new local Ed25519 key was created outside both repositories.

The approved VM request was Ubuntu 24.04 Minimal ARM64, `VM.Standard.A1.Flex`,
1 OCPU, 6 GB RAM and the default approximately 47 GB boot disk. Oracle returned:

> Out of capacity for shape VM.Standard.A1.Flex in availability domain AD-1.

Mumbai exposed no alternative availability domain for this request. **No VM was
created, no paid shape was selected and the account was not upgraded.** The free
network remains available for a later capacity retry. The two 1 GB AMD micro VMs
are too small for this Playwright/Chromium workload.

Useful current Always Free allowances include: 2 A1 OCPUs / 12 GB RAM total, two
1 GB AMD micro VMs, 200 GB combined boot/block storage and five backups, 20 GB
object storage, two 20 GB Autonomous databases, one 50 GB MySQL system plus 50 GB
backup, three 25 GB NoSQL tables, 10 TB monthly outbound data, 3,000 delivered
emails per month and 150 secrets. These have home-region, capacity and idle-
reclamation conditions; trial credits are separate. Object storage could hold an
optional encrypted checkpoint backup, while A1 remains the only suitable free
compute candidate for this browser worker.

## Railway deployment now live

- Project: [handnavigate](https://railway.com/project/ce45c8b7-2f41-4228-9a99-987c1c5d403f)
- Environment/service: `production` / `daily-collector`
- Region: Singapore `asia-southeast1-eqsg3a`
- Limits: 1 vCPU, 2 GB RAM, one replica
- Persistent volume: `handnavigate-state`, 5 GB at `/data`
- Source: `dvajjala-ui/handNavigation`, checked revision
  `1b2c9f92bf0e14491cc05e95f61ca4223a017634`
- First deployment: `0d242734-1c1b-4e76-b420-e41bf3e479aa`, `SUCCESS`
- Daily cron: `30 4 * * *` UTC = **10:00 Asia/Kolkata**
- Daily enqueue: enabled; restart policy: `NEVER`
- Old GitHub browser schedule: disabled after Railway qualification to avoid two
  hosts competing. SQL daily identity and quota remain a second safety layer.

The existing Railway workspace was measured over seven days before activation.
Its unrelated Backline services projected about $5.50/month. This collector is
budgeted at $1–$3 additional metered usage, for an estimated account total of
$7–$9 before tax if usage stays similar. This is a forecast, not an invoice or a
hard cap. The shared workspace compute cap was not changed because it could stop
the owner's unrelated services. Railway Hobby's $5 fee includes $5 usage; it is
not added twice.

## Exact-host acceptance evidence

Railway worker `99e899ac-7dba-464b-9f86-b9f51379065b` claimed three isolated
two-image jobs:

| Source | Job | Result |
| --- | --- | --- |
| Pinterest | `0010994b-78e2-401d-bb61-74c8d2a95f5c` | `AUTH_REQUIRED`, 0/2; guest browsing blocked on this datacenter IP |
| Reddit | `a071066a-4aad-4110-8a82-1adb03524d44` | `AUTH_REQUIRED`, 0/2; guest browsing blocked on this datacenter IP |
| Imgflip | `84a62ad7-b199-4c91-ac7a-948163cb1bfe` | `VERIFIED_STORAGE`, 2/2 |

The two Imgflip candidates were independently fetched from private storage. Their
stored SHA-256 values matched the database receipts; decoded WebP dimensions were
500×494 and 750×750. Both remain `UNREVIEWED` and `generation_eligible=false`.

Daily enqueue then correctly created no extra job because the India-day stored
image quota was already consumed. A separate idempotent 20-image acceptance job,
`4cf0729d-a629-42da-840c-1a5848e1530e`, exercised normal requested capacity. It
finished `PARTIAL`, 1/20: the current Imgflip popular-template catalog and the two
animal topics exposed only one new deduplicated candidate. That candidate was
read back as a valid WebP and its digest matched its database receipt.

Therefore the infrastructure is operational, bounded and storing real images,
but **20 fresh images every day is not yet guaranteed**. Pinterest/Reddit need a
host they permit for guest browsing or a user-authorized saved session on a
private interactive VM. Imgflip needs broader eligible topics/catalog coverage or
additional rights-compatible sources before it can sustain 20 unique daily items.
No proxy rotation, challenge bypass or hidden API-account requirement was added.

## Current production policy

The active `Daily animal reactions` plan is 20 images from Imgflip only. Pinterest
and Reddit evidence is retained as attention-required; their history was not
deleted. Railway runs at 10:00 India time and can enqueue only saved Admin plans
for the server's current India day. Candidates still require owner rights/quality
review before StickerDock Factory or Creator may use them.

## Known open issues and product gaps (as of October 28, 2026)

### Collection / sources

| Issue | Status | Action needed |
| --- | --- | --- |
| Pinterest guest browsing blocked from Railway's Singapore IP | Confirmed `AUTH_REQUIRED`, 0/2 | Use Oracle A1 (private SSH-tunneled interactive console) when capacity available, or add a VPN-tolerant guest-access alternative source |
| Reddit guest browsing blocked from Railway's Singapore IP | Confirmed `AUTH_REQUIRED`, 0/2 | Same as Pinterest. Reddit also showed human-verification prompts in earlier local tests |
| Imgflip 20-image daily yield only 1/20 after dedup | Confirmed `PARTIAL` in acceptance run | Broaden Imgflip topics beyond current animal reactions, or add additional rights-compatible free-image sources (Openverse, Pexels CC0) |
| Source-capability mismatch (pre-fix) | Fixed in PR #3 | HandNavigate now advertises installed sources; Imgflip jobs correctly claimed |
| Browser page-redirect did not check every hop | Fixed in PR #2 | Redirect guard now checks every intermediate navigation, not only final URL |
| Optional login iframe treated as redirect failure | Fixed in corrected hosted collection | Worker correctly ignores optional login overlays beside usable public images |

### Sticker quality and Factory

| Issue | Status | Action needed |
| --- | --- | --- |
| OCR-cleared source ≠ generation-eligible | Correct and intentional | Operator must explicitly approve rights + visual quality before any source feeds Factory |
| Downloaded social image ≠ reuse license | Correct and intentional | Rights/quality admission is a separate review gate; auto-publication is permanently off |
| Repeated photos in generated drafts | Confirmed — three October 28 drafts rejected | Improve source diversity; use per-topic cooldowns; broaden topic variety |
| Overlapping text (generator placed caption over baked-in meme text) | Partially fixed by OCR gate in PR #28 | Non-Latin, stylized and animated-frame text still pass OCR; visual review still required |
| `generation_eligible=false` even after approval | Correct intentional boundary | Operator admission step explicitly sets this; no path bypasses rights review |
| Factory draft quality (weak reactions, same photo used 5×) | Confirmed in review | Need more diverse source library and better topic-to-image diversity checks |

### Admin / UI

| Issue | Status | Action needed |
| --- | --- | --- |
| Admin has no login gate | Existing documented behavior | Private bucket + authenticated worker endpoints are protected; Admin workspace access control is a separate future decision |
| Overview shows repeated daily-run clutter | Fixed in PR #32 | Overview now shows summary counts + recent packs only |
| Old fragmented image pages (some with corrupted previews) | Audited and preserved | 123 legacy images checked, zero corrupt originals deleted; unified Sources page now the entry point |
| Sources page paste/upload for custom stickers | Implemented | Upload, drag-drop and clipboard paste all land in Sources with manual approval gate |
| Connections page showed unavailable integrations as working | Fixed in PR #32 | Unavailable items now in Connections/roadmap state |
| Stickers & Packs had no useful filters | Fixed | Status, origin, audience filters added to the dedicated Stickers & packs workspace |

### Infrastructure / reliability

| Issue | Status | Action needed |
| --- | --- | --- |
| Worker running on owner's local PC (sleep/shutdown risk) | Resolved via Railway | Daily Railway cron at 10:00 IST is live and independent |
| Oracle A1 capacity in Mumbai | Not available | Retry organically; no paid upgrade authorized |
| 20 unique images/day not yet guaranteed | Confirmed gap | Requires more qualified sources or broader Imgflip topic coverage |
| Pinterest/Reddit need human session from interactive host | Documented | Oracle A1 with SSH-tunneled console is the prepared route; Railway IP is blocked |
| Competing GitHub browser worker vs Railway | Fixed | Old GitHub browser schedule disabled after Railway qualification |

## Sticker.ly comparison (product owner view)

| Area | Sticker.ly behavior (public) | StickerDock current gap |
| --- | --- | --- |
| Creation | Upload photos, remove backgrounds, add captions, export | Sources → approval → Factory works; needs more diverse source library |
| Animation | Video-based animated creation, Auto Cut | Animated WebP import works; video-to-WebP pipeline needs improvement |
| Discovery | Follow creators, discover packs, share links/codes | Reviewed gallery and ZIP sharing exist; creator community distribution limited |
| Search | Mood/situation-based search | Ranked search + suggestion chips implemented on Android and iPhone |
| WhatsApp add | 1-tap add pack | 1-tap add implemented, WhatsApp Business separate target |
| Guest collection | N/A (they don't collect from Pinterest/Reddit) | Bridge working for Imgflip; Pinterest/Reddit need better host |

Note: Sticker.ly's terms distinguish personal use from redistribution. Their internal sourcing architecture is not public. StickerDock must maintain its own rights review independently.

## Next safe work — priority order

### Immediate (next 1–3 days)

1. **Observe the first natural 10:00 IST Railway run** — record deployment ID, job ID,
   unique image count, exit status and Railway metered usage. Verify Supabase
   candidate count increased. Do not claim daily operation until this passes.
2. **Review existing Imgflip candidates** for rights and visual quality. Mark
   `generation_eligible=true` only for candidates with confirmed reuse rights
   and acceptable visual quality. Do not approve bulk without review.
3. **Broaden Imgflip topics** in Admin topic plans beyond animal reactions.
   Add new plans (e.g., work reactions, festival moods, Hinglish expressions).
   Each needs a separate rights/quality review before generation admission.

### Short term (next 1–2 weeks)

4. **Add Openverse as an additional qualified source.** Openverse (CC/PD images)
   is already in the backlog (C3) and has a public API at `https://api.openverse.org/v1/`.
   It does not require guest browsing bypass or a social login. Add a HandNavigate
   adapter, qualify it on Railway with a 2-image test, then enable in the daily plan.
5. **Retry Oracle A1 Mumbai capacity** when free slots appear (check Oracle console
   periodically). If capacity becomes available, create the VM with the prepared
   free VCN/subnet, test Pinterest/Reddit from that IP, and use the SSH-tunneled
   private console for any permitted login session.
6. **Complete candidate admission into Factory.** Build the Admin review UX:
   bulk approve/reject per row, attach rights evidence field, explicit
   `generation_eligible` toggle. One source-to-sticker-to-WhatsApp result is
   the acceptance criterion.
7. **Observe 7 consecutive natural Railway runs** before calling daily operation
   dependable. Record: Railway logs, Supabase candidate count delta, Railway
   metered cost per day.

### Medium term (next 1–3 months)

8. **Pinterest/Reddit from a suitable host.** Either Oracle A1 with interactive
   console for permitted human sign-in, or identify another guest-tolerant host.
   Do not add proxy rotation or challenge bypass. Record exact-host 2-image
   qualification before enabling in daily plan.
9. **Android signed release physical test.** Galaxy S24 Ultra SAF import +
   WhatsApp physical send. Currently passing CI only, not device acceptance.
10. **iPhone TestFlight build.** App Store signing and TestFlight distribution.
    Separate from App Store publication — TestFlight is the next milestone.
11. **Video/GIF to animated sticker pipeline.** HandNavigate currently handles
    static images only. WebM/GIF intake, frame extraction, loop detection and
    WebP encoding need a separate adapter and quality gate.
12. **Per-source analytics in Admin.** Track daily yield, rejection rate, and
    rights approval rate per source. Visible failure ≠ overall failure; source
    failures should surface individually.
13. **Community / public gallery moderation.** Not part of current MVP. Requires
    identity, moderation/report/block flows, age protections, file scanning and
    privacy review before any user upload is made public.

## Session summary — what happened October 28, 2026

This is a complete record of the work session for future agent context:

1. **Architecture review** — HandNavigate and StickerDock reviewed as a unified
   product. Three critical bugs found: browser redirect gap, Imgflip source-claim
   mismatch, and missing candidate → Factory admission path.
2. **Bug fixes merged** — PR #2 (redirect guard), PR #3 (source advertisement),
   corrected iframe/redirect distinction. All 36 unit + 24 worker tests pass.
3. **Admin redesign merged** (PR #32) — Sources workspace, Stickers & packs,
   Runs & automation, Connections/roadmap pages. Upload/paste/drag-drop. Legacy
   image audit: 123 images checked, zero deleted.
4. **Hosting research** — DigitalOcean ($24/mo, exceeds budget), Hetzner
   (unavailable), Oracle Free (Mumbai capacity denied), Railway Hobby (deployed).
5. **Oracle attempt** — Free VCN/subnet/gateway created. A1 VM creation rejected:
   "Out of capacity for VM.Standard.A1.Flex in AD-1." No VM created, no paid
   resource created, account not upgraded.
6. **Railway deployment live** — daily-collector + handnavigate-state in Singapore.
   Cron: `30 4 * * *` UTC (10:00 IST). Budget: est. $1–$3/month additional.
   First deployment `0d242734` succeeded. Old GitHub browser worker disabled.
7. **Exact-host qualification** — Pinterest `AUTH_REQUIRED` 0/2, Reddit
   `AUTH_REQUIRED` 0/2, Imgflip `VERIFIED_STORAGE` 2/2. 20-image acceptance:
   `PARTIAL` 1/20 (dedup filtering reduced yield). Infrastructure is live but
   20/day not yet guaranteed.
8. **Factory recovery** (earlier in session) — PR #26 admission migration merged,
   PR #27 Library accessibility fix, PR #28 OCR text screening (Tesseract).
   74 photos screened, 38 flagged. Three October 28 drafts rejected on quality.
   Android 0.11.6 signed release published.
9. **Where we stopped** — Railway is live and running. Pinterest/Reddit need a
   better host (Oracle when capacity returns). Imgflip needs broader topics.
   First natural 10:00 IST run has not been observed yet. Candidate rights/quality
   review and Factory admission are the next required human actions.

## Source and CI references

- [Railway pricing](https://docs.railway.com/pricing/plans)
- [Railway cron lifecycle](https://docs.railway.com/cron-jobs)
- [Railway cost controls](https://docs.railway.com/pricing/cost-control)
- [Oracle Always Free resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
- [Openverse API](https://api.openverse.org/v1/)
- [HandNavigate native AMD64/ARM64 Railway checks](https://github.com/dvajjala-ui/handNavigation/actions/runs/40690215987)
- [HandNavigate worker tests](https://github.com/dvajjala-ui/handNavigation/actions/runs/40690216051)
- [HandNavigate Windows/package checks](https://github.com/dvajjala-ui/handNavigation/actions/runs/40690216084)
- [StickerDock Web/API checks](https://github.com/dvajjala-ui/stickerdock/actions/runs/40689597507)
- [StickerDock Android debug checks](https://github.com/dvajjala-ui/stickerdock/actions/runs/40689597378)
- [Encrypted credential bootstrap](https://github.com/dvajjala-ui/stickerdock/actions/runs/40690381415)
- [PR #32 — Admin redesign](https://github.com/dvajjala-ui/stickerdock/pull/32)
- [PR #28 — OCR screening](https://github.com/dvajjala-ui/stickerdock/pull/28)
- [PR #27 — Library accessibility](https://github.com/dvajjala-ui/stickerdock/pull/27)
- [PR #26 — Admission migration](https://github.com/dvajjala-ui/stickerdock/pull/26)
- [HandNavigate PR #3 — source advertisement + redirect fix](https://github.com/dvajjala-ui/handNavigation/pull/3)
- [HandNavigate PR #2 — ARM64 + redirect guard](https://github.com/dvajjala-ui/handNavigation/pull/2)
