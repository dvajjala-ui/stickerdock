# HandNavigate hosting options — verified October 28, 2026

## Recommendation

Current account outcome: Oracle Mumbai rejected the A1 request as out of capacity.
A separate Railway collector is now deployed and scheduled at 10:00 India time.
It verified Imgflip storage, while Pinterest and Reddit returned guest-access
blocks from Railway's IP and a 20-image Imgflip run produced only one new unique
candidate. See
[the measured budget, passing checks and activation state](HANDNAVIGATE_RAILWAY_2026-10-28.md).

The owner now has an Oracle Free account and an existing Railway Hobby account.
Their approved order is **Oracle at $0**, then **Railway within $5–$10/month for
this worker**. The earlier DigitalOcean recommendation exceeds this budget and
is superseded. No paid server or account upgrade is authorized outside it.

First qualify **Oracle A1 Flex, 1 OCPU / 6 GB RAM, Ubuntu 24.04 Minimal aarch64**
in the tenancy's Mumbai home region. Confirm existing compute/storage allocation
and the final estimate before creation; expiring trial credit does not qualify
as permanent free hosting. Native ARM64 and AMD64 container checks both passed
in [HandNavigate PR #2](https://github.com/dvajjala-ui/handNavigation/pull/2),
[run 40682685422](https://github.com/dvajjala-ui/handNavigation/actions/runs/40682685422).
This proves container startup, private console and headful Chromium, not provider
capacity or Pinterest/Reddit access.

If Oracle capacity or account access prevents deployment, inspect Railway's
actual existing usage before adding a service. Prefer a bounded daily collector
that exits after work, with persistent state, instead of billing idle Chromium
all month. Railway's $5 Hobby subscription includes $5 of resource usage; RAM is
$10/GB/month, CPU $20/vCPU/month, storage $0.15/GB/month and egress $0.05/GB.
These are metered prices, not a promise of a $5 browser service. Railway's
Compute hard limit stops every workload in the workspace, so do not change a
shared workspace limit without checking the effect on existing projects.

Keep Vercel/Supabase and the current Factory. This VM only runs the persistent
browser collector and polls its existing outbound queue. It needs neither a new
database nor a paid image/model API. The existing source-rights review remains.

## Options

| Host | Official current offer | Fit and constraints |
| --- | --- | --- |
| DigitalOcean | Regular Basic 2 vCPU / 4 GiB: $24/month cap | Straightforward x86 Ubuntu/Docker target and persistent disk. Account/payment setup required. Prefer a short billed access test first. |
| Hetzner | EU CX23: 2 vCPU / 4 GB / 40 GB; €5.49/month before VAT and IPv4 | Low cost, but the official cost-optimized page currently marks CX23 unavailable. Recheck console availability and final account quote. |
| Oracle Always Free | Current docs: A1 Arm allowance equivalent to 2 OCPU / 12 GB and 200 GB block storage | First choice within the owner's $0 requirement. The 1 OCPU / 6 GB container target passes native ARM CI. Capacity can be unavailable and idle instances can be reclaimed. |
| Railway Hobby | $5 subscription includes $5 of metered usage | Budget fallback. Check existing usage and bound active runtime before deployment; persistent interactive recovery still needs host qualification. |
| Existing GitHub Actions | Scheduled runner already configured | Actual two-image Imgflip delivery passed. Both Pinterest and Reddit required attention from this network. It is scheduled, not continuously online. |

## Prepared configuration

HandNavigate `deploy/vm/` contains the Docker image, persistent Compose volume,
automatic restarts and a graphical Chromium console. HTTP and graphical ports
are bound to host loopback; the console is reached through an SSH-key tunnel.
The console supports actual human checks/optional free account sign-in where a
source permits them. It does not bypass restrictions, hide automation or use
rotating proxies. Credentials stay in a server-only ignored env file/profile.

Container build/startup, private console and headful Chromium are checked in the
new `Persistent browser VM container` workflow. A passing container check does
not prove a provider's site access, guest permissions or continuous operation.

## Activation acceptance

1. Oracle Free was tried first and failed only on Mumbai A1 capacity; the account
   was not upgraded and no paid VM was created.
2. Railway is the active fallback. Its first build, mounted state, bounded exit,
   Imgflip delivery and checksum read-back passed on the exact host.
3. Pinterest and Reddit remain attention-required after independent 0/2 tests.
   Do not bypass their blocks or present them as an unattended source.
4. The active daily plan uses Imgflip. Its full 20-image request was only 1/20
   after duplicate/topic filtering, so add qualified sources or broader topics
   before promising 20 unique items/day.
5. The old GitHub browser schedule is disabled; Railway enqueues saved plans using
   the server's India day and SQL quota/idempotency.
6. Retry Oracle A1 only when free capacity appears. If an interactive session is
   permitted, use the private SSH-tunneled console and repeat exact-host tests.

## Primary sources checked

- DigitalOcean pricing: https://www.digitalocean.com/pricing/droplets
- Creating a server: https://docs.digitalocean.com/products/droplets/how-to/create/
- Payment setup: https://docs.digitalocean.com/platform/billing/manage-payment-methods/
- Hetzner effective pricing: https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/
- Hetzner current availability: https://www.hetzner.com/cloud/cost-optimized/
- Oracle limits, capacity and reclamation: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
- Railway tiers/usage: https://railway.com/pricing
- Railway metered resource prices: https://docs.railway.com/pricing/plans
- Railway workspace cost limits: https://docs.railway.com/pricing/cost-control
- Playwright container behavior: https://playwright.dev/docs/docker
