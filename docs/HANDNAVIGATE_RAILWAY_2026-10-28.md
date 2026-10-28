# HandNavigate hosting and cost record — October 28, 2026

The owner approved Oracle at $0, or Railway within $5–$10/month. Oracle Mumbai's
only available domain rejected the approved A1 Flex request with **Out of
capacity for shape VM.Standard.A1.Flex**. No VM was created. A dedicated free VCN,
public subnet and internet gateway are prepared; SSH ingress is limited to the
deployment computer's current IP. The account was not upgraded.

## Railway budget

The connected account already has four running Backline services and a Redis
volume. Their last seven days' average memory totals about 0.521 GB and CPU about
0.0106 vCPU. At current published rates, that projects to roughly **$5.50/month**
including a conservative volume/egress allowance. This is a forecast from sampled
metrics, not the current invoice or an account spending cap.

HandNavigate has a separate private project and one collector with a **1 vCPU,
2 GB RAM maximum**, one **5 GB volume**, and no public application domain. A
30-minute daily allowance is 15 active hours over 30 days. At full resource limits
this is about $0.42 memory + $0.42 CPU, plus at most $0.75 volume storage and egress.
Budget **$1–$3/month of additional metered usage**, giving an estimated account
total of **$7–$9 before tax** if existing usage remains similar.

Hobby's $5 subscription includes $5 resource usage across the workspace; the
subscription and usage are not added twice. Other projects and additional manual
runs can change the bill. A global Compute hard cap can stop every Backline
service, so that shared setting is not changed by this deployment.

## Collector controls

- Stop after the queue is idle for 90 seconds, or after 25 minutes of work.
- Independent container timeout: 28 minutes, then 30 seconds to force shutdown.
- Restart policy `NEVER`; one daily schedule only after exact-host qualification.
- Keep browser profiles, originals, journal and duplicate identities in `/data`.
- Refresh server duplicate history through the existing authenticated intake API.
- Daily enqueue accepts only saved Admin plans and the server's India day; the
  existing SQL quota and request identity prevent repeated daily submissions.
- Use only the existing worker ingest credential. Supabase admin credentials are
  not provisioned to Railway. Bootstrap exports only RSA-OAEP encrypted bytes to
  a one-day Actions artifact, for decryption by the local deployment operator.
- Disable the old GitHub browser worker after the new host is accepted. The
  Railway worker can enqueue daily work itself and does not wait for GitHub timing.

## Current activation state

The [private Railway project](https://railway.com/project/ce45c8b7-2f41-4228-9a99-987c1c5d403f)
contains live `daily-collector` and `handnavigate-state` resources in Singapore.
Its source is pinned to checked HandNavigate revision
`1b2c9f92bf0e14491cc05e95f61ca4223a017634`. After the connector rejected the
final apply twice, the owner applied the reviewed non-destructive patch in the
Railway dashboard. First deployment `0d242734-1c1b-4e76-b420-e41bf3e479aa`
completed `SUCCESS`. One daily cron is active at `30 4 * * *` UTC, or 10:00
Asia/Kolkata, with `HANDNAVIGATE_ENQUEUE_DAILY=1`. The old GitHub browser schedule
is disabled so it cannot compete for the same work; SQL daily identity/quota is
still the second safety layer.

[Native AMD64/ARM64 container checks](https://github.com/dvajjala-ui/handNavigation/actions/runs/40690215987)
pass, including a real headful browser and a mounted-state cron that exits.
[Worker API CI](https://github.com/dvajjala-ui/handNavigation/actions/runs/40690216051)
passes 36 unit and 24 worker tests; [Windows desktop/package CI](https://github.com/dvajjala-ui/handNavigation/actions/runs/40690216084)
also passes. The container uses Tini for child-process reaping and signal handling.
HandNavigate PR #3 merged as `038cfd1`.

StickerDock PR #32 merged as `d5108c5` after
[Web/API checks](https://github.com/dvajjala-ui/stickerdock/actions/runs/40689597507);
[Android debug checks](https://github.com/dvajjala-ui/stickerdock/actions/runs/40689597378)
also pass. The [encrypted bootstrap run](https://github.com/dvajjala-ui/stickerdock/actions/runs/40690381415)
succeeded. Actual production checks verified the existing worker credential and
151 minimal cloud duplicate identities. The bootstrap artifact contained only
encrypted bytes; no plaintext credential was committed or displayed in chat.

Exact-host qualification is complete for worker
`99e899ac-7dba-464b-9f86-b9f51379065b`. Pinterest job
`0010994b-78e2-401d-bb61-74c8d2a95f5c` and Reddit job
`a071066a-4aad-4110-8a82-1adb03524d44` both finished `AUTH_REQUIRED`, 0/2:
ordinary guest browsing is blocked from this Railway datacenter IP. Imgflip job
`84a62ad7-b199-4c91-ac7a-948163cb1bfe` reached `VERIFIED_STORAGE`, 2/2. Both
private WebPs were fetched independently and their digests matched database
receipts; they remain unreviewed and generation-ineligible.

The server correctly skipped an extra daily job after the India-day image quota
was already consumed. A separate idempotent 20-image acceptance job,
`4cf0729d-a629-42da-840c-1a5848e1530e`, then finished `PARTIAL`, 1/20: the current
Imgflip popular-template catalog and animal topics exposed only one new eligible
deduplicated image. Its private WebP read-back and SHA-256 passed. The host is
operational, but 20 new images/day is not yet a verified content outcome. See the
[complete deployment handoff](HANDNAVIGATE_DEPLOYMENT_HANDOFF_2026-10-28.md).

## Useful Oracle Always Free resources

Current published allowances include 2 A1 OCPUs / 12 GB RAM total, two tiny AMD
VMs with 1 GB RAM each, 200 GB combined VM disks and five volume backups, 20 GB
object storage, two 20 GB Autonomous databases, a 50 GB MySQL system plus 50 GB
backup storage, three 25 GB NoSQL tables, 10 TB monthly outbound transfer, 3,000
monthly delivered emails and 150 secrets. Compute/storage have home-region and
capacity conditions; idle compute can be reclaimed. Trial credits are separate.
The A1 candidate is useful for this browser worker when capacity becomes available;
object storage is useful for optional backups. No extra services were activated.

Sources checked October 28:

- [Railway metered prices](https://docs.railway.com/pricing/plans)
- [Railway cron process lifecycle](https://docs.railway.com/cron-jobs)
- [Railway workspace cost limits](https://docs.railway.com/pricing/cost-control)
- [Oracle Always Free resources, capacity and reclamation](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
