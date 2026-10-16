# Android, iPhone and real-reaction product direction

Updated October 16, 2026. This follows the user's authorization to change the
architecture, research references and broaden the product. The current signed
APK is Android-only. A native SwiftUI iPhone client now builds unsigned and
passes initial simulator tests in public-repository macOS CI. Distribution
signing, physical WhatsApp and full parity remain pending; see ios/README.md
and RECOVERY_PROGRESS.md for exact source/run evidence.

## Decision: share Kotlin incrementally

Use Kotlin Multiplatform for portable rules, media containers, editing recipes,
catalog/job models and workflow state. Android consumes these modules now; iOS
consumes the same Apple framework in the native SwiftUI client. Native screens
deliver the initial iPhone client; a later shared Compose UI migration requires
a measured compatibility/usability spike. Keep platform
ports for decoding, local files, storage, permissions and WhatsApp integration.
This changes the earlier separate-native-client recommendation; it does not
discard the verified Android data/editor implementation.

This is a project-specific choice, not a claim that Kotlin produces better
designs automatically. [Kotlin and Compose support Android/iOS](https://kotlinlang.org/docs/multiplatform/supported-platforms.html),
and [common source sets share logic](https://kotlinlang.org/docs/multiplatform/multiplatform-share-on-platforms.html).

| Approach | Benefit | Cost for this repository | Decision |
| --- | --- | --- | --- |
| Kotlin Multiplatform + gradual shared Compose UI | Reuses Kotlin logic and much of the declarative UI approach | Android-specific code must be extracted behind ports; Compose UI needs a measured toolchain upgrade | Selected incrementally |
| Flutter / Dart | Mature shared Android/iOS UI | Rewrite Kotlin screens/state; still needs native media, migration and WhatsApp plugins | Viable alternative, higher migration cost here |
| React Native / TypeScript + development builds | Shared UI, potential reuse of web-editor concepts | Media/WhatsApp native modules still required; current native editor/store must be bridged or rebuilt | Viable, not selected for the first migration |
| Fully separate Kotlin and Swift clients | Maximum platform control | Duplicate screen/workflow development and parity maintenance | Native adapters remain, complete duplication avoided |

Flutter documents [native platform channels](https://docs.flutter.dev/platform-integration/platform-channels).
React Native documents [native platform APIs](https://reactnative.dev/docs/next/native-platform).
Expo can [build iOS through its hosted service](https://docs.expo.dev/develop/development-builds/introduction/),
but that does not implement the native sticker bridge or verify WhatsApp behavior.
The first Apple build uses standard free GitHub macOS runners on this verified
public repository. No signing account, paid render or deployment was started.

## Foundation implemented in source

`sticker-core/` is a real Kotlin Multiplatform module consumed by `app/`:

- Shared WebP metadata/container writer; Android facades delegate to this code.
- Shared clip recipe validation and pack/export limits.
- Five common tests, plus the existing Android suite exercising shared code.
- JVM artifact for Android; Apple device/simulator framework targets registered
  on macOS. The core-only setting excludes Android SDK requirements for that build.
- Android CI paths/test commands include the shared module. A separate manual
  Mac workflow now compiles the actual Apple framework/client and tests it.

Current local evidence: **5 common tests + 94 Android tests**, release lint
(0 errors/43 warnings) and signed 0.9.1 APK/AAB builds pass. The shared mux reproduces five previously native-generated
real-reaction animations byte-for-byte, including rotated footage. Report:
`work/shared-core-probe/report.json`. Media/Python suite: **176 passed/no skips**.
The exact installed 0.9.1 APK passes native real-photo preview, moving clip
creation, explicit save and force-stop persistence checks while retaining prior
packs and the 12 broader reaction samples. Hash-bound evidence:
`work/release-0.9.1-acceptance/status.json`. This 0.9.1 evidence is historical.
The 0.10.0 continuation passes 94 Android/9 common/178 Python local tests and
native navigation/creation acceptance. First Apple/Android CI passes at e2af790;
newer motion/live-catalog source needs its own matching result. Physical
WhatsApp/Business/iPhone acceptance remains unverified.

Real-photo inspection also found that the native large preview reused a 256px
thumbnail and enlarged it. The 0.9.1 continuation requests 512px grid/detail
images, separates cache keys by resolution/file revision and budgets cache memory
by pixel bytes (8 MB by default). New tests cover thumbnail/full-size separation,
changed-file refresh and bounded memory. Signed-build/runtime acceptance passes;
see [the artifact hashes and limits](RECOVERY_PROGRESS.md).

## Platform behavior that remains different

| Capability | Android | iPhone source and remaining work |
| --- | --- | --- |
| WhatsApp pack handoff | ContentProvider and explicit personal/Business intent | Documented pasteboard/URL source and payload tests; physical handoff pending |
| Phone migration | User-granted SAF folders and optional desktop ADB | Photos/Files, StickerDock/generic WebP ZIP source; no private WhatsApp folders; EXIF/generic-image ZIP parity pending |
| Clip/photo creation | Android codecs, native image preview | Native AVFoundation/ImageIO/WebP adapter; first photo-codec tests pass; expanded motion/rotation tests in CI |
| Private library | Existing SQLite v8/files, non-destructive migrations | Atomic private manifest/files, initial archive/restart/failure tests pass; account/cloud adapter and large-library performance pending |
| Consumer/Business targeting | Distinct installed package targets | Verify actual iOS app capabilities; do not assume Android routing or two in-app accounts |
| Sharing and backup | Native share sheet, ZIP schema 5 | Native share sheet/ZIP 1–5 reader/v5 writer, initial round-trip tests pass; cross-device acceptance pending |

The [official iOS WhatsApp sample](https://github.com/WhatsApp/stickers/tree/main/iOS)
documents the handoff and requires an app with its own useful functionality.
Physical WhatsApp/iPhone testing and Apple signing/App Store acceptance are real
release gates. Windows JVM tests do not prove those. An iOS WebP codec candidate
is [SDWebImageWebPCoder](https://github.com/SDWebImage/SDWebImageWebPCoder);
it is now installed through pinned SPM dependencies, with initial native photo
codec tests passing. ios/Package.resolved records the transitive versions.
The Apple adapter must use bulk byte transfer, avoiding per-byte Swift/Kotlin
calls for animation frames, and measure codec/memory behavior on the actual phone.

## References and concrete takeaways

All eight user-supplied repositories are cloned read-only with pinned commits.
[The complete inspection table](REAL_MEDIA_CREATOR_PLAN_2026-10-16.md) records
each hash, license finding and reuse decision. No third-party bot or APK was run.

- WhatsApp: separate pack types, per-pack export, size/frame/tray constraints,
  Android provider and different Apple handoff.
- Stickerbridge: bounded media/compression, alpha, validation and iOS handoff
  behavior informed our approach; upstream device claims are not our results.
- Stickerest: content-first discovery, reaction tags, favorites and creator flows
  inform the product backlog; its automatic-publication behavior is not adopted.
- The small sticker.ly wrapper is not Sticker.ly's source or catalog. The other
  bot/sample projects suggest workflows, not a secure complete application.
  GPL/unclear-license custom implementations have not been copied into MIT code.

## Real media: sources and integration decisions

| Source | Useful for | Current decision |
| --- | --- | --- |
| User-selected photos/videos/GIFs | Personal reactions, friend/family moments, custom packs | Primary creator input; local by default, AI upload must be chosen explicitly |
| Wikimedia Commons | Independently licensed photos and motion clips with provenance | Current bounded adapter and reviewed local trials; public allowlist remains unchanged |
| Pexels API | Higher-quality photos and short footage | Candidate requiring a key and attribution; not integrated or a blanket license for recognizable meme characters |
| GIPHY | Real reaction/GIF discovery and provider-approved sharing | Separate candidate; standard API rules prohibit unapproved storage/proxying and mixing provider grids, so it cannot silently feed our offline pack catalog |
| Tenor | Historical GIF API | Reject for new work: Google sunset the API June 30, 2026 |
| Pinterest | Moodboards and style/reference discovery | Reference links/authorized browsing; not assumed to grant rights to copy saved Pins |

Sources: [Pexels documentation](https://www.pexels.com/api/documentation/),
[GIPHY integration rules](https://developers.giphy.com/docs/api/),
[Google's Tenor notice](https://support.google.com/tenor/answer/10455265?hl=en).
Choose the integration and permitted use before asking for its credentials.

Three additional reviewed source works now produce **12 local trial stickers**:
Joe Loong's [facepalm photograph](https://commons.wikimedia.org/wiki/File:Wikip-facepalm.jpg)
(CC BY-SA 2.0), Shixart1985's [laughing reader](https://commons.wikimedia.org/wiki/File:Woman_laughing_while_reading_a_book_at_the_table.jpg)
(CC BY 2.0) and SecretDisc's [moving dog](https://commons.wikimedia.org/wiki/File:Yawn.gif)
(CC BY-SA 3.0). Original files remain unchanged; credits and changes accompany
the sample bundle under `work/real-media-breadth-2026-10-16/`. The photograph
renderer now supports explicit focal points after visual review caught a
center-crop cutting into a face. This control is currently in the media CLI,
not the native clip screen. These trials are not public catalog publications.
Persistent first-class attribution is needed before expanding the public
publisher beyond its current rights allowlist.

## Broader feature order

1. **Useful creation:** native Groq caption/trim suggestions, three-to-thirty
   variant pack builder, subject-aware framing/manual pan, reusable caption
   styling and side-by-side previews. Save only explicitly accepted results.
2. **Cutouts that work on real photos:** automatic subject masks with brush
   correction, outlines and undo. Evaluate Android/iOS models separately; handle
   unavailable models and keep manual editing usable. [ML Kit subject segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android)
   is an Android beta/downloaded-model candidate, not proven iPhone parity.
3. **Better motion:** timeline scrubbing, trim handles, loop/first-frame preview,
   poster selection, crop positioning and size/quality feedback. Keep local
   source/recipe drafts so failed or interrupted work can resume.
4. **A real daily-use library:** favorites, recents, reaction/mood tags, Hinglish
   search aliases, bulk organization and duplicate review. Preserve originals,
   static/animated separation and migration/backup compatibility.
5. **Content-led discovery:** varied expressive human/animal/contextual packs,
   creator/source credit, preview before save and explicit WhatsApp export.
   Validate populated layouts with actual media, not colored placeholders.
6. **Two-platform UI:** shared screen state/components with native media/file
   ports, then an installable iPhone app and platform-specific acceptance tests.
7. **Durable AI operations:** authenticated user jobs, one organization-wide
   provider budget, separate research/interactive priority, cached analyses,
   visible cooldowns, retries, cancellation, cleanup and spending controls.
8. **Community readiness:** source/rights records, creator identity, review queue,
   report/block/withdraw, age protections, scanned files and owner-scoped storage.

Professional presentation should center Discover, Create and Library. Use real
posters, readable captions, motion badges and small clear actions; keep import
utilities/settings secondary. Validate 360/411dp, large text, empty/loading/error
states, keyboard interactions and large libraries before visual sign-off.
The signed 0.10.0 Android pack screen removes the repeated title and moves the
large WhatsApp panel into a target sheet. Native 411/360dp and 1.3x text checks
show real artwork in the initial viewport. Create is a primary destination;
import/backup tools are secondary. Full design/performance sign-off remains open.

Performance work should measure cold library open, scroll decode work, animation
memory and conversion cancellation. Cache small thumbnails, decode visible
items, stop offscreen animations and bound concurrent media jobs. [Coil's GIF support](https://coil-kt.github.io/coil/gifs/)
is Android-only; adding a shared image-loader dependency alone does not establish
iPhone animation playback. Preserve the existing local store behind interfaces
before considering a database-library migration.

## AI and setup

The two existing Groq roles remain sufficient to implement research plus media
analysis/caption assistance. Groq is not the pixel renderer. Another key in the
same organization does not create more organization quota; measure the account
limits and reserve interactive capacity. Keys stay server-side, outside APKs.

Optional pixel synthesis can be evaluated later against quality/cost targets.
The local machine reports Intel UHD Graphics 750 and no NVIDIA runtime; there
is no basis to promise fast local large-image-model generation. No model weights
or paid image provider were added. Current photo/clip creation needs neither.

The unavailable local Mac is addressed through standard free GitHub macOS
runners on this public repository. Initial Apple app builds/native/UI tests pass.
Expanded motion/live-provider checks, distribution signing and real iPhone/
WhatsApp acceptance remain gates; local media conversion needs no new Groq key.
