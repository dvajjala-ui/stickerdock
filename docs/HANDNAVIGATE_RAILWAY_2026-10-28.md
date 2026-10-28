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

Deployment is still being qualified. Container/unit tests do not establish
Pinterest or Reddit guest access on Railway's network. Record separate source
outcomes, actual Supabase read-back and process exit before calling it active.

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
