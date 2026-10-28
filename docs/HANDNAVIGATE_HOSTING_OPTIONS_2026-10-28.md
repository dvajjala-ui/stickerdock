# HandNavigate hosting options — verified October 28, 2026

## Recommendation

Use one **DigitalOcean Basic Regular x86-64 Droplet, 2 vCPU / 4 GiB / 80 GiB,
Ubuntu 24.04**, for a short access qualification before committing to continuous
operation. Official published price: **$24/month cap, $0.03571/hour**, excluding
applicable tax/optional extras. Account creation and a valid payment method are
required. A different datacenter IP is not evidence of Pinterest/Reddit access.

The cheaper 1-vCPU/2-GiB regular Droplet is $12/month; it is a possible later
right-sizing target, not the initial unmeasured browser-memory assumption.

Keep Vercel/Supabase and the current Factory. This VM only runs the persistent
browser collector and polls its existing outbound queue. It needs neither a new
database nor a paid image/model API. The existing source-rights review remains.

## Options

| Host | Official current offer | Fit and constraints |
| --- | --- | --- |
| DigitalOcean | Regular Basic 2 vCPU / 4 GiB: $24/month cap | Straightforward x86 Ubuntu/Docker target and persistent disk. Account/payment setup required. Prefer a short billed access test first. |
| Hetzner | EU CX23: 2 vCPU / 4 GB / 40 GB; €5.49/month before VAT and IPv4 | Low cost, but the official cost-optimized page currently marks CX23 unavailable. Recheck console availability and final account quote. |
| Oracle Always Free | Current docs: A1 Arm allowance equivalent to 2 OCPU / 12 GB | Potential free route; capacity can be unavailable and idle instances can be reclaimed. This new VM deployment targets x86-64; Arm/container qualification is additional work. |
| Railway Hobby | $5 minimum usage, includes $5 credit; actual resource usage adds cost | Convenient containers/volumes. The minimum is not a guaranteed monthly Chromium cost. Interactive browser recovery needs extra setup. Free tier's 0.5-GB limit is below the proposed browser sizing. |
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

1. Owner chooses/creates the hosting account and completes billing/verification.
2. Create the Ubuntu VM, attach SSH public key, configure its firewall and deploy
   the reviewed container with existing ingest credentials kept server-side.
3. Test Pinterest, Reddit and Imgflip independently on that exact host. Record
   guest/challenge behavior, actual image counts, provenance and Supabase digests.
4. If a source permits an interactive check or free signed-in session, complete
   it through the private console and rerun. Otherwise leave the source disabled
   or attention-required. Stop the short pilot if required sources cannot work.
5. Prove restart/resume and 20 total images before moving the active worker.
   Switch the GitHub daily job to enqueue-only for the persistent VM. Preserve
   old worker data until the new host is accepted.
6. Observe normal daily runs. No host can guarantee a third-party site will never
   change, block an IP or require a later human check.

## Primary sources checked

- DigitalOcean pricing: https://www.digitalocean.com/pricing/droplets
- Creating a server: https://docs.digitalocean.com/products/droplets/how-to/create/
- Payment setup: https://docs.digitalocean.com/platform/billing/manage-payment-methods/
- Hetzner effective pricing: https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/
- Hetzner current availability: https://www.hetzner.com/cloud/cost-optimized/
- Oracle limits, capacity and reclamation: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
- Railway tiers/usage: https://railway.com/pricing
- Playwright container behavior: https://playwright.dev/docs/docker
