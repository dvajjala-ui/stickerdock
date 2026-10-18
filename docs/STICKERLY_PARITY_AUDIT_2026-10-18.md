# Product parity and requirement reconciliation

This follows the user's October 18 instruction to compare the actual product,
functionality and requirements with Sticker.ly. It is a working acceptance map,
not a claim that every requirement is closed. Preserve the original IDs in
[REQUIREMENTS_LEDGER.md](REQUIREMENTS_LEDGER.md).

## Reference scope

Primary sources inspected: [Sticker.ly's product and creation flow](https://sticker.ly/),
[its current Android listing](https://play.google.com/store/apps/details?id=com.snowcorp.stickerly.android),
and [support FAQs](https://sticker.ly/faq/en/). The documented journey includes
named packs, photo cutouts, captions/transforms, animated creation, WhatsApp and
Telegram export, discovery, creator following and pack links/codes. These pages
are evidence of advertised behavior, not proof of every private app screen or
implementation. Their catalog size is not a reusable asset inventory.

Comparison target: equivalent useful journeys with our own UI/assets and better
private ownership, migration, recovery and optional AI supply. Advertising gates,
forced accounts and social mechanics do not become requirements simply because
another app has them. No user requirement is silently removed by that decision.

| Journey | Current StickerDock | Gap / acceptance |
| --- | --- | --- |
| Browse packs → inspect → save | Rebuilt Android pack rows and read-only Discover; explicit offline save | Small reviewed catalog; populated real-content design acceptance |
| Name pack → photos → edit | Explicit new/existing destination; independent original and draft | Automatic removal now under implementation/test; selective edge correction next |
| Text and transforms | Caption placement, rotation, mirror, scale, undo/redo | Rich text styling/free placement and iOS editor parity |
| Video/GIF → animated stickers | Trim, caption, moving preview, explicit save, type-separated packs | Editable video projects and moving-subject segmentation |
| Add pack to WhatsApp | Official provider, app-specific personal/Business actions, add-again | Actual S24/WhatsApp refresh/cancel/Business acceptance; no intra-app account routing claim |
| Send a pack link/code | Portable file sharing and reviewed catalog | Native public-pack links/codes and private revocable links |
| Telegram destination | No dedicated pack exporter | Research/implementation/testing required; do not label a generic file share Telegram pack installation |
| Follow creators / public profiles | Reviewed catalog and hide/report mechanisms | Moderated community product, identity, safety and operational review |
| Private durable collection | Local database/media, backups, optional private cloud | Full editable-project backup, phone migration and iOS cloud parity |
| AI-supplied initial content | Groq caption/media Factory; optional original-art worker | Live original-model trial, style consistency/cutout quality, reviewed pack breadth, native assistant |

## Every ledger requirement

Status vocabulary: **verified checkpoint** identifies actual evidence, **partial**
means work remains, **external gate** needs a device/account/platform capability,
**superseded** retains the old request and its replacement. A code file alone does
not close a requirement.

| ID | Reconciliation | Next acceptance or implementation |
| --- | --- | --- |
| R01 | Partial: import/provider/actions exist | Physical import → add → send journey |
| R02 | Partial + external gate: SAF journal/EXIF/dedup/resume | S24 personal/Business folders, thousands of files, interrupted process |
| R03 | Superseded by R22 | Preserve explicit saving; never restore launch-time imports |
| R04 | Partial + external gate | Consumer/Business app checks; intra-app accounts not independently targetable |
| R05 | Partial; db6c979 core verified | Real-artwork library, edge editor, remaining parity |
| R06 | Partial; CRUD and reversible static editor | Automatic cutout verification, selective repair and advanced animation editing |
| R07 | Partial + external gate | Device cache refresh/update/delete behavior controlled by WhatsApp |
| R08 | Partial; operator tools moved out of primary navigation | Review all empty/error/progress states in a populated collection |
| R09 | Active | Keep this map, verbatim request and ledger current |
| R10 | Core checkpoint verified | New features require new matching CI/APK acceptance |
| R11 | Partial: trend/source/caption jobs exist | Consistent reviewed daily variety and useful pack coherence |
| R12 | Partial: Admin/Studio deployed | Current owner onboarding and operational CRUD/recovery acceptance |
| R13 | Partial: private ratings/kinds and portable format | Full editable-project backups and link-based pack sharing |
| R14 | Pending product gate | Public submissions require rights, moderation, identity, scanning and age protections |
| R15 | Active: end-to-end owner/consumer audit | Fresh hosted operational checks after relevant deployment |
| R16 | External gate: Pinterest approval | Keep approved reference browsing; no scraping workaround |
| R17 | Partial: real licensed photo/clip rendering exists | Broad human-reviewed real reaction packs; no filler templates |
| R18 | External gate: private phone collection unavailable | Authorized personal-style review when device/media is accessible |
| R19 | Partial: opt-in Android cloud exists | Device onboarding/restore and native iOS cloud adapter |
| R20 | Partial: submission/review/report/hide code exists | Production safety/isolation and rights-review acceptance |
| R21 | Partial: normal owner sign-in exists | Owner must complete their own account credentials; no agent-created password |
| R22 | Core checkpoint verified; keep active | Preview/selection/cancel must never silently save |
| R23 | Active | Match every claim to current source and evidence |
| R24 | Partial: native Android/iOS + shared core | Apple distribution signing and physical-device WhatsApp acceptance |
| R25 | Partial: core Android redesign verified | Editor, actual content and iOS visual parity |
| R26 | Partial | Automatic/repair cutout, durable animated projects, native AI assistant |
| R27 | Partial: licensed-source adapters exist | Reliable independently reviewed catalog supply and attribution |
| R28 | Partial | Auth recovery/restore/rollout/security operations; never infer owner account existence |
| R29 | Partial: repository/reference research and real-media trials | Wider content/style acceptance and native assistant |
| R30 | Active authorization | Measured compatible architecture changes, not data-destructive rewrites |
| R31 | Partial: Groq media/caption worker | Native authenticated assistant with shared quotas; two keys do not double an organization's quota |
| R32 | Partial: shared media core and two native clients | Cloud/editor/AI feature parity and both distribution paths |
| R33 | Active audit | This complete ID map supersedes the older acceptance summary; implementation remains open |
| R34 | Reliability checkpoint verified | Phone acceptance and normal daily Factory capacity still open |
| R35 | Existing stable-release links complete | New candidate is not silently substituted for a published release |
| R36 | Partial; core redesign verified | Continue creation, sharing, content and infrastructure acceptance |
| R37 | Prepared, not live | Original worker has mocks/dry-run only; provider credentials, approved trial and output review absent |
| R38 | Active | Continue reconciling and implementing; no blanket parity/completion claim |

## Evidence and next work

`db6c979` passes [Android CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39070097918)
and [signed emulator acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39070094081):
105 Android, 12 shared, 208 Python tests; 10k-sticker browsing, large text, tablet,
animation, picker/cancel/save/restart. See QA_MATRIX.md for hash and limits.
Source after that checkpoint is not covered by it. Local ADB currently sees no
device. Image-generation provider keys are absent; no paid render was made.

Implementation sequence: automatic cutout and edge correction → useful pack
links/codes → durable editable-project backup → native AI assistance and broader
reviewed content → iOS parity. Device, identity, moderation and provider acceptance
remain explicit gates alongside implementation. The project is not yet a complete
Sticker.ly-equivalent product.
