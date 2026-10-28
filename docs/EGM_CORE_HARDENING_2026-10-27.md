# Core hardening after Elephant Goldfish review — October 27, 2026

Baseline: main `6f9a2f10b64ea6f7b2783ddba58dfa3e9c1e05dc`. This is the next
implementation increment, not a claim of deployment or complete product parity.
Requirement R51 authorizes implementation and a branch push.

## Review and scope

[Google's Elephant Goldfish Model](https://github.com/google/elephant-goldfish-model)
was installed as `egm@egm` 0.1.0. Five review stages and an independent source
audit used ten fresh-context reviewers. The existing stack fits the product:
local authoritative native libraries, shared media rules, separate workers,
Supabase storage/queues and explicit review/save/WhatsApp confirmation. No
database, app-framework or provider replacement is needed for these repairs.

The first critic's missing-catalog-lock finding was withdrawn after checking
Admin dispatch and the separate publisher: both already use the database lease.
Historical October 22 findings about missing basic upload normalization,
idempotency, partial-batch drafts and Openverse are superseded by later source.

## Current changes and contracts

| Review item | Implemented in this increment | Acceptance / limits |
| --- | --- | --- |
| A2: filtered sticker search | Kind/category predicates run in SQLite before ranking and LIMIT. Changing filters restarts the debounced query. Existing unbounded pack discovery stays separate. | Native regression seeds 110 newer static matches and an older animated WORK result; filtered queries must find it. Pagination and 100k-library measurement remain open. |
| A1: release acceptance | Library search has stable `Search library` accessibility semantics independent of its visible example placeholder. | Existing emulator assertions remain intact. Local compilation/unit/lint are separate from rerunning exact production-signed APK acceptance and publication. |
| A3: overnight delivery | Per-batch draft IDs are unchanged. `delivered_batches` is written only after both immutable ZIP upload and draft upsert succeed, or an existing reviewer-owned draft is found. | Fault injection covers interrupted draft write, fresh-runner next-day replay, preserved edits, repeated replay and acknowledgement/status repair. |
| A3: retention/replay | Replay at most three prior unacknowledged days per invocation before pruning. Recovery invocations return without trend/caption/generation calls. Preflight checks prior delivery even when today's target is complete. | Never prune undelivered/pending/unreadable state, including beyond the eight-day history window. Acknowledged old media can be pruned; durable remote drafts remain. |
| A3: missing historical media | Mark delivery blocked with a reason, preserve surviving files and counters, exclude blocked rows from automatic recovery-only wakeups. | No fabricated draft, implicit deletion or paid/model regeneration. Operator repair remains required for already-lost media or unrendered old batches. |
| A4: queue cap | New jobs and retries share service-role `admit_creator_request`, a PostgreSQL transaction/advisory lock, payload-checked UUID replay and 25 queued/running slots. | Real concurrent PostgreSQL sessions test distinct admission, same-ID replay and retry/new-submit races. Existing claiming, leases, cancellation and API response shapes remain. |
| A7: cover cost | Persist bounded 240px covers in the private bucket under `admin/covers/v1/<archive-sha>.webp`. Repeated sessions fetch the small derivative instead of the ZIP. | Check current GENERAL rating before cache access; digest changes invalidate it. Invalid cache is rebuilt; cache-write failure does not block a valid rendered cover. |

SQLite remains schema 8 and portable ZIP remains schema 5. No personal media,
existing packs, publication approvals or signing identities are reset. Factory
acknowledgement fields are additive JSON state, requiring no database migration.

An older snapshot containing fewer records than the database is blocked even
when its surviving subset was acknowledged. Unreadable prior state is preserved
byte-for-byte as `state.unreadable-<digest>.json`, outside active history readers.
`delivery-blocked.json` records missing/quarantined state durably even if the
original database row never committed. Interrupted remote status writes are
reconciled on the next fresh runner. Current-day corruption fails closed; it
never resets the day's request budget. Operator recovery must preserve these
files and explicitly clear the block only after repairing the relevant state.

## Deployment order and rollback

1. Apply `supabase/migrations/202610270001_creator_atomic_admission.sql` before
   deploying the updated web APIs. It adds/replaces one service-role-only RPC;
   it does not rewrite rows or replace worker claim functions. Reapplication is
   supported. Old APIs remain compatible during this additive migration.
2. Deploy the API/worker changes together after checks pass. New admission has
   no non-atomic fallback: missing migration fails closed with setup guidance.
   Smoke-test new admission, same-ID replay, a retry and a queued cancellation.
3. Run one storage-only recovery and inspect historical counters, private draft
   ZIP hashes, provenance and reviewer edits. No new public publication is
   implied by delivery acknowledgement. Then inspect a normal scheduled batch.
4. Run the full exact signed-APK gate before updating the public Android download.
   Current-main acceptance failed on the old search locator; the earlier 0.11.6
   candidate also encountered HTTP 403 while publishing. The workflow already
   declares `contents: write`; actual publication credential/repository policy
   needs verification. Do not change the production signing identity.

Roll back application code without dropping the additive RPC. A rollback to old
queue writers loses the atomic admission guarantee, so pause shared admission
while reverting instead of silently relying on the old count/insert cap. Do not
roll back to the old Factory pruning implementation while undelivered history
exists. Preserve runtime snapshots and use the existing Factory pause control
for operator recovery. Private AI remains disabled throughout this increment.

## Verification

Local result for this source increment: **283 Python tests, 140 Android tests and
12 shared-core tests pass with no skips**. Debug assembly and release lint pass
(zero lint errors; 44 warnings and six informational findings). PostgreSQL 17
concurrent-session checks, existing PGlite migration checks, JavaScript syntax,
Python undefined-name checks and whitespace checks pass. These are local results;
the pushed PR's CI and a production-signed emulator run remain distinct evidence.

An independent EGM Codebase Details reviewer read the diff and contracts. Its
initial blocked-status and optional-cache transport findings were fixed with
fault-injection coverage. A later orphan-history starvation case was fixed with
a durable marker and fresh-runner regression. The reviewer withdrew a stale
pruning concern after checking the existing blocked acknowledgement guard, and
reported no residual findings in the final narrow review. This is not a claim
that every product requirement or production deployment has been accepted.

- Python worker/API tests exercise recovery failure/retry, retention, admission
  response validation and cover caching. The full suite must pass without skips.
- Android/shared tests, debug APK and release lint run with SDK 36 / JDK 17 /
  Gradle 8.11.1. Hardware/physical WhatsApp acceptance is a separate gate.
- `python tools/check_creator_admission.py --docker-container <disposable-pg>`
  applies real Creator migrations to a uniquely named disposable database and
  runs concurrent sessions. Web CI supplies an isolated PostgreSQL service.
  It never connects to the production database or makes generation calls.
- JavaScript syntax, Python undefined-name checks and whitespace checks remain
  required. CI evidence must name the pushed source; old successful runs are
  not acceptance for this increment.

## Remaining priorities

1. **Production acceptance:** apply the admission migration, verify hosted APIs,
   recovery, cache use and a normal scheduled day; rerun signed Android release
   and resolve publication permissions. Physical WhatsApp/Business is open.
2. **A5 resource envelope:** shared organization/model reservations across Factory,
   trends, Creator and personal requests; storage admission/headroom, revision
   retention and explicit rejection/deferral. Preserving undelivered files can
   consume the existing bounded runtime snapshot; this is not unlimited storage.
3. **A6 private AI:** upload-generation fencing and terminal cleanup for delayed
   same-ID uploads; two-account/provider checks and safe activation/rollback.
   Current source exists, but activation remains off.
4. **Media/product:** stable searchable media cursors and ownership-aware phone
   transfer; richer sources, OCR/text-overlap warnings and user-reviewed Hinglish
   photo/animated reactions. Forty drafts is not forty publishable stickers.
5. **Platforms:** physical Android migration/export, signed iPhone install and
   parity, full-resolution/video editing projects and store distribution.

Implementation and CI maintenance belong to the engineering work; production
migration and operational readback need the deployed service context. The owner
approves content rights/quality and supplies physical-device acceptance. These
external gates do not invalidate locally passing source checks.
