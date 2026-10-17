# Real-media creator continuation — October 16, 2026

Follow-up: [cross-platform/product decision](CROSS_PLATFORM_AND_PRODUCT_DIRECTION.md)
now selects incremental Kotlin Multiplatform sharing, superseding the separate
native-client direction in step 6 below. Android consumes the shared media core;
Apple adapters/client/build acceptance remain pending. Three further source
works now produce 12 human/animal trial stickers, with native import/restart
evidence and focal-point framing. See RECOVERY_PROGRESS.md for current evidence.

The user rejects an implementation confined to emoji/shape fixtures. Those
fixtures establish data integrity only. The product needs recognizable reaction
photos, human expressions, contextual Hinglish captions, moving reaction clips,
useful search and a clear creation flow. R17/R26/R29/R30 remain active.

## References actually inspected

All eight supplied repositories were cloned without checkout into ignored
`work/reference-repos`. No third-party application, install script or bot was run.
Commit IDs below make this a reproducible source review, not a claim that these
projects are maintained, secure or production-ready.

| Repository / inspected commit | Finding and decision |
| --- | --- |
| [methmal66/whatsapp-sticker-bot](https://github.com/methmal66/whatsapp-sticker-bot) `448d2d7` | Its server renders text/color to PNG; it is not an AI photo or animated creator. GPL-3.0: no source copied into this MIT app. |
| [theabbie/sticker.ly](https://github.com/theabbie/sticker.ly) `aaa7a95` | MIT, eight files; `index.js` wraps an old HTTP v3.1 endpoint. It is not Sticker.ly's app source. Do not make this undocumented endpoint a catalog dependency. |
| [whatsapp/stickers](https://github.com/whatsapp/stickers) `06144a1` | BSD official Android ContentProvider and iOS pasteboard/export contracts. Primary reference for pack/frame/size limits and platform differences. |
| [indiser/Sticker-Vault-App](https://github.com/indiser/Sticker-Vault-App) `f612d75` | Java sample-derived pack browser/provider/validator. README claims BSD but its referenced root LICENSE is absent in this tree. No custom source or art reused; the repository instruction's GPL warning remains in force for other StickerVault work. |
| [colt005/whats_sticky](https://github.com/colt005/whats_sticky) `88bc159` | Go webhook/bot with background-removal/video ideas. README references FFmpeg and U-2-Net, but no root license was found. Treat as behavioral research; no copying or dependency on its deployment. |
| [johnparkerg/simple_sticker_bot](https://github.com/johnparkerg/simple_sticker_bot) `c9eb8df` | Baileys session bot, image trigger, Sharp resize and sticker reply. Its 200px intermediate, whole-message logging and reconnect recursion are not our release architecture. No root license found. No account/session connected. |
| [LxArmitage/stickerbridge](https://github.com/LxArmitage/stickerbridge) `3c864e2` | MIT; inspect `stickers.py`, `whatsapp.py` and Swift export module. Useful references: VP9 alpha handling, bounded compression ladder, validation before export, progress and iOS local/expiring pasteboard. Its reported real-iPhone results are upstream claims, not StickerDock acceptance. |
| [STICKEREST/stickerest](https://github.com/STICKEREST/stickerest) `5afe2ce` | MIT React Native social/Telegram product. Useful discovery cards, tags, favorites and creator navigation. Its automatic public upload behavior conflicts with our explicit-publication/moderation model; hosting warnings also rule out treating it as a ready backend. |

Visual inspection: [real reaction pack](https://sticker.ly/s/X0CWS2) displays human
expressions and short reaction clips; [Pinterest board](https://www.pinterest.com/lotdzack/whatsapp-sticker/)
shows face/TV/cartoon crops, contextual captions and collections by reaction.
These are style references, not a source license or permission to rehost pixels.
Sticker.ly's [animated creator guide](https://sticker.ly/faq/en/FAQs%20aaee0686e0434de48956d7ba663edc14/Learn%20how%20to%20create%20animated%20stickers%20on%20Sticker%20l%204ca5a909ea8347ee9e0b27133ad59696.html)
confirms video/GIF selection and trimming as a core creation flow.

## Groq-first assistant, optional paid synthesis

The user confirms the two existing Groq roles: daily research and sticker/media
assistance. Workflows already reference `GROQ_TRENDS_API_KEY` and `GROQ_API_KEY`;
their values were not retrieved. Absence from local env files does not mean the
deployed secrets are absent. No new OpenAI key or paid request is authorized here.

Groq vision understands images and returns captions/structured edit plans; our
renderer produces pixels. It is not an image-synthesis endpoint. Use Groq for
topic research, caption alternatives, mood/emoji tags, source selection and clip
suggestions; use local codecs for crop, trim, caption, alpha and pack export.
The existing worker already handles real-photo/clip modes, but the tiny cat
library and fixed caption-card composition do not establish the user's quality
target. Native upload/assistant UX and realistic variety remain incomplete.

[Groq vision](https://console.groq.com/docs/vision) currently documents Qwen 3.8
27B, up to three images/request, 2,048 input tokens/image. [Limits](https://console.groq.com/docs/rate-limits)
are organization-wide; two keys in one organization do not double capacity. The
published table lists 1,000 requests/day and 200,000 tokens/day for this model;
the account's actual limits and request-header measurements are authoritative.
Batch caption choices, cache analyses and reserve interactive capacity. Measure
finished, accepted stickers/day, not successful requests or captions alone.

Latest-image discovery needs real search/feed adapters; a text model's memory
is not a current-events feed. The current research module uses browser search
with GPT OSS plus RSS/owner topics. Do not switch to retired Groq Compound:
[its shutdown was September 21, 2026](https://console.groq.com/docs/deprecations).

Paid image synthesis is an optional adapter, not a prerequisite for this app.
OpenAI's [official output estimates](https://developers.openai.com/api/docs/guides/image-generation#calculating-costs)
for GPT Image 2 at 1024px are $0.006/$0.053/$0.211 for low/medium/high.
At 30 attempts/day over 30 days that is $5.40/$47.70/$189.90 in output charges,
before inputs, retries, hosting or taxes. These are attempts, not approved
stickers. [FLUX](https://bfl.ai/pricing) is another paid API; no model comparison
or account access has been tested. Compare actual accepted outputs and total
cost per accepted sticker before committing to a provider or spend.

## Implementation order and wider architecture

1. Preserve signed 0.8.0 as the verified local editing baseline. Its 78-test and
   emulator evidence remains separate from real-media quality acceptance.
2. Build a reusable real-media renderer: local photo/video/GIF inputs, explicit
   trim/crop/contain, compact outlined captions, bounded decoding, actual motion,
   readable 512px output and separate static/animated exports. Produce examples
   from licensed moving footage and import them through the native app.
3. Bring creation into the native client: source picker → trim/framing/caption
   choices → animated preview → explicit save to a suitable pack. Show progress,
   cancellation and actionable errors; no silent import or upload. Test a real
   MP4/WebM/GIF, portrait/rotation, no-motion, corrupt and oversized cases.
4. Add the Groq assistant to that creator through authenticated, bounded jobs.
   The user chooses media upload; keys stay server-side. Analyze a bounded frame
   contact sheet; return validated caption/trim choices with manual override.
   Share provider budgets across research/editorial/interactive work and show
   waiting state. Preserve source and recipe so regeneration is an edit.
5. Broaden discovery with rights-cleared content plus reference-only links:
   topical human/animal reactions, cinema-style reaction moments, cricket and
   everyday Hinglish. Separate provenance, public rights and personal-use data.
   Add actual expressive thumbnails, motion badges, favorites and reaction tags;
   test populated screens with these assets before visual sign-off.
6. Keep native Android and add a native iPhone client around shared media/job/
   pack contracts. Use the official iOS export mechanism, informed by the Swift
   reference. Mac/Xcode/iPhone acceptance is still a real platform gate; changing
   to Expo alone cannot establish export, performance or store acceptance.
7. Harden operations: source approval, per-user budgets, storage cleanup, durable
   retries, rollback/migration tests, moderation/report/block and public deletion.
   Do not merge private reference collections with public publication.

First real-media trial: [Facepalm.webm](https://commons.wikimedia.org/wiki/File:Facepalm.webm),
Rattyexalt, CC BY-SA 4.0, verified original SHA-1
`3181ffab9944cf87683b9cea971e884dd0e926e4`. Derivative media must carry that
attribution/license and changes notice. It is not CC0 and is not eligible for
the current CC0/public-domain public-publisher allowlist. Keep the trial local;
do not weaken that gate. One clip and several caption variants demonstrate the
pipeline only, not a complete or culturally representative catalog.

The app must look and work well with actual media before calling this phase
complete. A broad roadmap, a green test fixture or an upstream screenshot is
not a substitute for that evidence.

## Implementation evidence after research

- `reaction_media.py` and `build_reaction_pack.py` now build local schema-5
  packs from actual photos/clips with explicit captions, trim, cover/contain,
  rounded photo edge, no-motion rejection and bounded output. Original inputs
  stay unchanged. Pack title is editable. No provider request or upload occurs.
- Worker photo/clip rendering uses the shared reaction composition. Clip vision
  input is a six-frame chronological contact sheet, replacing first-frame-only
  analysis; caption instructions request expression/action-aware alternatives.
  This source change is local and not deployed. It does not prove live caption
  quality or finished editorial capacity.
- Latest full local Python suite: **173 passed, no skips**, with FFmpeg on PATH.
- Local licensed Facepalm trial: three static and three genuinely moving WebP
  caption variants, final rounded animated outputs 160–202 KB, 33 frames,
  3.3 seconds. The source is 72,576 bytes with verified original checksum.
- Signed 0.8.0 imported static and animated trial archives through the Android
  system picker; its native animation preview visibly changes across captures.
  Library grew deliberately from 2/7 to 4 packs/13 stickers. Static import used
  the first layout iteration; animated import used the final rounded layout.
  Report: `work/real-media-lab/native-import/report.json`.
- Native 0.9.0 creator passes 92 local Android tests and signed APK/AAB, lint,
  signature and alignment checks. Actual WebM testing exposed repeated first
  frames from Android's thumbnail API on this emulator; a bounded sequential
  MediaExtractor/MediaCodec decoder replaces that path. It handles YUV strides,
  crop, BT.601/BT.709 ranges and explicit rotation without another binary.
- The exact replacement APK passes native WebM, MP4 and GIF selection,
  captioned moving previews, stale-preview invalidation, explicit new/existing
  pack save and force-stop persistence. Exports are 512px, 20 frames/2.5 seconds,
  205,186/178,798/495,472 bytes, with verified alpha, durations and checksums.
  Previews leave the library unchanged; explicit saves add exactly one pack and
  three stickers (4/13 to 5/16). Report:
  `work/creator-0.9-emulator/sequential-native/report.json` records the APK hash.
  A corrected MP4 with actual 90-degree display metadata also passes upright
  reference pixel comparison, transparent portrait margins, moving preview,
  explicit save and force-stop persistence; `rotation-report.json` records this.
  Verified APK copy: `work/StickerDock-0.9.0-recovery.apk`; complete local record
  and source hashes: `work/release-0.9-acceptance/status.json`. Physical WhatsApp,
  iPhone, live Groq assistance and broad catalog quality remain unverified.

The trial media/credits are ignored local artifacts, not bundled app content or
a public catalog change. The clip remains CC BY-SA 4.0; this does not change the
MIT license of independently written code.
