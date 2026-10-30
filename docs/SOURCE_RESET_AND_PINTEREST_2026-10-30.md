# Source inbox reset and Pinterest decision — October 30, 2026

## Production state

The owner rejected the generic cat/dog/meme source set and requested a clean
start. The active **Daily animal reactions** plan (`5a1aaafc-01d9-458d-8b6b-f20fe0708c8f`)
was archived at version 7 before deleting any candidates, so the next Railway
schedule cannot repopulate the inbox with that plan. There are now **zero active
daily plans**. Railway may still wake on its existing cron and exit without a
collection job; this is not a functioning Pinterest schedule.

The [read-only reset audit](https://github.com/dvajjala-ui/stickerdock/actions/runs/41021463086)
found 174 unapproved rows, all with unique private
`handnavigate/candidates/*.webp` files. Breakdown: legacy 91, licensed 32,
Flickr 20, Pinterest 20, Imgflip 7, Reddit 4. It checked that no active daily
plan, approved source, generation-ready source, or Admin pack/creator request
reference would be affected. The exact SHA-256 fingerprint was
`a0b428cb8a15e39abadbe449eee108ad7452305c13449839d4d7ac5e01714e5e`.

The [matching apply run](https://github.com/dvajjala-ui/stickerdock/actions/runs/41021593352)
deleted **174 database rows and 174 private candidate objects**, totaling
**14,775,498 bytes**. The source-row manifest is retained privately at
`maintenance/source-reset-41021593352.json` for audit; it does not contain the
image bytes. Live Admin read-back returned zero active and zero archived source
items. Existing published packs, generated images, private phone sticker
libraries, and the older `inbox/` files were outside the reset. Previously
downloaded Pin files are gone from the HandNavigate candidate inbox; any older
independent Factory assets remain subject to their own rights and quality
review.

The [storage scan before reset](https://github.com/dvajjala-ui/stickerdock/actions/runs/41021118576)
reported 76.74 MiB total. The [scan after reset](https://github.com/dvajjala-ui/stickerdock/actions/runs/41021728479)
reported **62.73 MiB** total: 61.47 MiB private and 1.26 MiB public. These
figures are live object totals for StickerDock's two Factory buckets, not the
Supabase billing-cycle average or all projects in the organization. The GitHub
`SUPABASE_STORAGE_QUOTA_MB` variable remains unset, so the scan does not emit a
usage percentage or quota alert. [Supabase's published Free allowance](https://supabase.com/docs/guides/platform/billing-on-supabase)
is **1 GB file storage** and **500 MB database size per project**; confirm the
organization's actual plan in its dashboard before configuring a quota alert.
On a Free organization, current Factory object usage is roughly 6–7% of the
published file allowance. Database row bytes and other project storage are
separate from the two-bucket scan.

## Pinterest and Reddit

The deployed Railway worker returned `AUTH_REQUIRED` for Pinterest/Reddit
guest browsing from its network. A different cloud host, free proxy or new
login does not establish reliable access or reuse permission. HandNavigate's
earlier owner-assisted local browser run proved a bounded download path on that
computer, not unattended hosted collection. The current Pinterest OAuth
connection is read-only and shows the owner's boards/Pin links live. A fresh production check on October 30 returned `connected` and listed the `Quick Saves` board; that board had no Pins, so there are currently no live Pin ideas to browse.

[Pinterest developer guidelines](https://policy.pinterest.com/en/developer-guidelines)
prohibit automated scraping without express permission, archiving information
returned through Pinterest materials/API, and creating new distributable
content from Pins. An OAuth token for board reading or a browser sign-in does
not grant sticker reuse rights. The Factory admission API and Sources UI now
reject Pin candidates for generation, including an attempted manual rights
claim on a Pin row. This closes an older loophole where an intake row could be
marked `USER_OWNED` despite its Pinterest origin. The owner can still use live
Pin links for inspiration, and can independently upload their own original
artwork or an image with explicit reuse permission.

Reddit guest search is likewise blocked on the current host. A Reddit image
requires verification at its original source and a compatible creator license
or permission before Factory use. This is a rights review, not a Reddit login
requirement for the app owner. No hosted Reddit success is claimed.

## Workflow that works without running a local collector

1. Open **Admin → Sources** on the hosted site. Browse Pinterest under
   **Connections → Pinterest** only when choosing visual ideas.
2. In Sources, expand **Add your own image**, then upload, drop or paste an
   original PNG/JPEG/WebP under 3 MB. Select **My own image** only for an image
   the owner created and checked. Otherwise leave it as a reference and review
   permission before generation.
3. The image is private until explicitly used. From the approved source card,
   choose **Create sticker** or **Create pack**, review the result, and publish
   only a pack that meets rights, content and quality checks.
4. For open-licensed discovery, use Openverse or Flickr as candidate research;
   check the source work and license individually. The old daily animal plan is
   intentionally off until a visual-quality gate and topic list produce useful
   reaction material. This prevents another automatic generic dump.

The hosted upload path needs a browser but no daily local service or database
command. A truly unattended Pinterest-to-sticker pipeline is not currently
available under the existing access and policy. The next product milestone is
one reviewed, owner-uploaded reaction pack accepted in the Android catalog,
followed by a narrow quality-tested licensed source plan if its results are
worth collecting daily.
