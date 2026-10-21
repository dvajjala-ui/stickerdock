# Requirements acceptance audit — October 16, 2026

This is an evidence audit, not a declaration that every requirement is complete.
This October 16 snapshot is superseded by the current
[QA matrix](QA_MATRIX.md), [parity audit](STICKERLY_PARITY_AUDIT_2026-10-18.md)
and [October 21 real-media/Apple evidence](CREATOR_ACCEPTANCE_2026-10-21.md).
Pending-build/test wording below is historical; physical acceptance remains open.
Read REQUIREMENTS_LEDGER.md for original IDs/history. Historical Markdown is not
current acceptance; RECOVERY_PROGRESS.md binds accepted local Android behavior
to exact APKs. The current user authorizes implementation and UI/integration
improvements and confirms there is no Mac available.

| Requirement | Source / local evidence | Remaining acceptance or implementation |
| --- | --- | --- |
| R01, R04, R07 WhatsApp import/export and accounts | Android provider, separate consumer/Business intents, media preflight, version updates | Actual WhatsApp/Business device installation/refresh. An account inside one app cannot be targeted independently. iOS handoff/device acceptance separate. |
| R02, R18 personal migration | SAF journal/resume, EXIF grouping, ADB ZIP tool | Galaxy huge-library/process-death QA; private collection/style review requires user's media. iOS cannot read another app's private media. |
| R03 superseded by R22 explicit save | Signed 0.7–0.9.1 read-only discovery, accepted preview/save checks | Maintain on both platforms; never restore launch-time imports. |
| R05, R08, R25 visual flows | Android Create tab, secondary import tools with correct Back history, content-first pack/target sheet; exact 0.10.1 APK passes 360dp/1.3x text and real-artwork viewport checks. Native iPhone navigation implemented. | Full populated-phone accessibility, design and performance acceptance; more varied actual content. |
| R06, R13 organization and backups | Android CRUD/tags/static-animated separation/SQLite 8/ZIP 5; iOS atomic library/favorites/rename/delete and ZIP interchange; actual native Swift export retains bytes/name/MATURE/category after Android picker import/restart. | Durable editable video projects, original-resolution project backup, Android automatic subject removal and full iOS editor/import parity. |
| R09, R23 evidence and requirements | Ledger + recovery/QA docs | Correct stale spec/status conflicts. Source tests are not release certification. |
| R10 release | Signed 0.10.1 APK/AAB, 97 Android + 12 common tests, 178 Python, lint/signature/16 KB checks and exact-APK native acceptance; Android CI b5b33c6 passes. | Latest expanded Apple CI, signing, real phones and complete operational release checks. Older artifacts do not include newer fixes. |
| R11, R17, R27, R29 real content supply | Real-media worker, Groq vision/trends roles, licensed trials: 12 additional stickers/4 packs/3 works | Reviewed varied catalog, source/attribution persistence, daily operational capacity; trial variants do not constitute a finished catalog. |
| R12, R15, R21, R28 admin/operations | Admin/Studio routes, owner session/auth, review/publish worker code and historical live evidence | Refresh hosted acceptance after deployment; email/SMTP deferred; MFA/restore/rollout review. No known default password. Never print secrets. |
| R14, R20 community | Rights/consent/moderation/report/block source and prior smoke evidence | Production isolation/scanning/age/moderation acceptance; public uploads not an unrestricted MVP feature. |
| R16 Pinterest | OAuth/reference browsing code, application pending in historical record | Platform approval/current credentials. No scraping/rehosting workaround. |
| R19 optional cloud | Android authenticated opt-in private backup/restore/checkpoints | Physical phone + email onboarding; native iOS account adapter still pending. Cloud never replaces authoritative local media. |
| R24, R30, R32 Android/iPhone architecture | Real shared Kotlin core consumed by Android and native SwiftUI iOS17 client; unsigned device/framework builds and initial simulator tests pass through public-repository macOS CI. | Expanded codec/rotation acceptance is recorded below; Apple distribution signing, real-device WhatsApp, full cloud/editor/AI parity remain gates. No local Mac is required for the CI builds. |
| R26, R31 creation and AI | Native Android offline photos + GIF/video trim/caption/save, worker Groq assistance | Native authenticated assistant, shared budget/cooldown, durable video drafts, batch suggestions and automatic cutout. Do not expose owner tokens to consumer apps. |
| R33 complete audit and product improvement | This audit, corrected spec, new implementation/evidence recorded below | Remains active until platform and operational gates actually pass. |

## Implementation order

1. Correct stale behavior descriptions and preserve exact 0.9.1 artifact identity.
2. Build iPhone local library, real-media creator, portable ZIP integration,
   explicit reviewed-catalog save and documented WhatsApp pasteboard handoff.
3. Test archive safety, media contracts and non-destructive persistence; provide
   unsigned Mac build/unit/UI-test workflow without pretending it ran on Windows.
4. Make Android pack content visible immediately; retain creation/import/share
   and consumer/Business actions with accessible labels.
5. Close native AI/account and advanced editor gaps; then measure populated
   phone performance and cross-platform parity. No new provider key is needed
   just to convert user-selected media locally.

Initial baseline inspection: fetched origin/main; commit remained c5871d7. GitHub
connector returned no PR-triggered runs for that commit; that filtered response
does not establish absence of scheduled/push runs. Dirty local source has not
run through hosted CI at that point; later source-bound results are recorded below. No deployment, paid API call or account change is made
by this audit.

## Implementation and acceptance

The iOS client in `ios/` implements native Discover/Create/Library, bounded local
photo/GIF/video creation, caption variants, photo subject removal, favorites,
atomic private storage, Android-compatible ZIP interchange, explicit catalog
preview/save and the official WhatsApp pasteboard handoff. Apple compilation and
initial native/UI tests pass; expanded checks and physical acceptance are separate,
not full feature parity. Android 0.10.1
adds Create navigation and a content-first pack screen with a compact export
sheet. Its local build, 97 Android + 12 common tests and exact signed-artifact
UI/real-MP4/save/restart acceptance pass.

All 35 repository Markdown files, including catalog and fixture notes, are inventoried by
`tools/audit_markdown_requirements.py`; hashes, requirement IDs and review cues
are recorded in `work/requirements-audit/md-inventory.json`. Historical spec
conflicts have dated warnings; the product spec now requires explicit saving.
This inventory is not itself evidence that all listed features work.

No local Mac exists, but live repository metadata confirms this repository is
public. Standard GitHub macOS runners are free for public repositories according
to [GitHub's runner documentation](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).
[Draft PR #18](https://github.com/dvajjala-ui/stickerdock/pull/18) starts unsigned
Apple device/simulator builds and native integration/navigation tests. Initial
run: [Apple verification](https://github.com/dvajjala-ui/stickerdock/actions/runs/38899085950).
Do not infer a pass from its existence. Vercel auto-deployment is disabled for
this branch using its documented `git.deploymentEnabled` setting.

The expanded native motion test then found a real false-rejection bug in the
new decoder validation: SDWebImageWebPCoder applies its memory estimate to all
animation frames even with first-frame-only decoding, shrinking valid 512px
posters under a fixed limit. Source now accounts for the declared frame count,
still decodes one poster and retains the exact 512px check. A defensive Objective-C
boundary rejects codec exceptions from malformed imports; a corrupt-pixel test
exercises the error path. The failed run is retained, not counted as passing.
Latest matching source/test results are on PR #18 and in
`work/release-0.10.1-acceptance/status.json` after acceptance.

Release preparation adds the existing Android vector brand mark as an opaque
1024px iOS app icon, exact transitive dependency pins, bundled notices and a
required-reason privacy manifest. These assets do not constitute App Store or
privacy approval. The app remains a candidate with the outstanding gates above.

An actual Swift-exported archive imported through Android's system picker exposed
two additional interoperability defects: Foundation used upper-case UUIDs, while
Android accepted lower-case only; the generic ZIP tool ignored a manifest and
recompressed content with default GENERAL metadata. This could discard a private
rating. The 0.10.1 source shares archive identity rules, writes canonical UUIDs
while reading both spellings, and routes manifest archives through validated
restore with no generic fallback. Regression tests require exact bytes, names,
tags and private ratings and preserve existing data on invalid manifests. The
failed synthetic QA import is retained as failure evidence, not a passed test.

Further checking distinguished UI labels from wire values: Android displays All
using the existing `GENERAL` archive key; neither `ALL` nor the test's private
`FUNNY` alias is a valid v5 category. Shared writer normalization now emits
`GENERAL` for unknown private aliases and retains known categories. Android's
strict backup reader remains strict. ZIP failures now show the first reason
instead of only a failed count. Interoperability tests must use a native export
from this corrected writer, not reinterpret a malformed old fixture as success.

The corrected native export from b5b33c6 now passes Android's actual file picker:
one pack/sticker, exact bytes/name/MATURE/GENERAL, all prior rows retained after
force-stop/restart. Its photo/archive subtest passes even though the whole Apple
run fails a separate rotation fixture assertion. GIF and MP4 encoding pass; the
rotation fixture was found to have no matrix because the legacy FFmpeg rotate
metadata option was ignored. It is replaced with verified display-matrix bytes,
and XCTest now checks the source transform before checking portrait output.
No failed run is counted as a full Apple pass. Final corrected CI is tracked on
PR #18. The half-second clip UI also no longer offers trim positions beyond the
source duration. Signing, physical WhatsApp and all remaining table gates stand.
