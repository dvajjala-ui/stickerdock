# Real-media creation and Apple archive acceptance — October 21

## Android shared GIF/video

[PR #24](https://github.com/dvajjala-ui/stickerdock/pull/24) is merged at
`4394ac163b031f70106ad10b1bd17dd8ea5cfc07`. It routes a single granted GIF or video
share into the existing animated creator, preserving explicit preview/save.
Mixed media, multiple clips and remote URLs fail before import. It does not add
an automatic download, catalog import or upload.

The branch candidate `cdc5048e41bbbf13da1f96d62abf47cd2b79c310` passes
[CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39597133389):
**135 Android / 12 shared / 205 backend tests**, release lint/build/alignment.
Its [signed acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39597159184)
passes the existing 10k library, Favorites, photo/video editing, brush/reset,
large text/tablet, public codes, shared files and editable-backup journey, plus
cold video SEND, warm GIF SEND and cancellation without library mutation.

Candidate APK SHA-256:
`af65f018344011c546ae559627408ad095d0323beba55aa60316d090bfc9fc39`.
The APK signature matches the existing release certificate. This candidate hash
is separate from the public main-build hash recorded in [QA_MATRIX.md](QA_MATRIX.md).

Main publication was initially blocked twice. Attempt 1 encountered a Pixel
Launcher system ANR before the app's first screen. Attempt 2 reached the short
GIF check but its two screenshots sampled identical loop phases. The exact
attempt-2 APK (`6f5531fdfcf9b8248bfd5c89ab79055057f3d6bcfebacddd442964fdac6e38a7`)
then played the same short GIF locally: six bounded captures showed three
different artwork frames, with no library mutation. Logs/images remain in
`work/main-motion-second-attempt/` and `work/short-gif-diagnosis/`.

The animation assertion now samples at most five images with unequal intervals,
still requiring different pixels inside the actual preview bounds. Two tests
prove that repeated loop phases can pass on a later different frame, while
frozen artwork still fails even when surrounding system chrome changes. The
release workflow also watches its imported emulator helpers, so changes to this
assertion trigger full signed acceptance. These are test-runner changes; app
media/animation source is unchanged. Passing publication requires the fresh run.

That exact candidate also upgraded the existing licensed-media emulator library
without changing any of its 11 packs, 35 stickers or artwork project rows. A real
human-reaction MP4 and GIF, derived from **Facepalm.webm by Rattyexalt, CC BY-SA
4.0**, were supplied through scoped FileProvider shares. Attribution and changes
remain with the local trial evidence. No media was committed or published.

- Video: caption “Bas kar bhai”, generated animation visibly moves, then Cancel
  leaves the library unchanged.
- GIF: caption “Group chat moment”, generated animation visibly moves; preview
  alone creates nothing. Explicit Save adds one ANIMATED pack/sticker.
- Saved output is 512×512 WebP, animated and below 500 KB. Its SHA-256 is
  `65488261c6d017e4ac9bac14f3a7054575116b7305ba55da56cdec41d41aac11`.
- All existing pack/sticker/project rows remain unchanged; restart retains the
  new item. The library becomes 12 packs / 36 stickers.

Local evidence: `work/received-motion-real-acceptance/report.json`, preview
screenshots, `shared-reaction.webp` and saved-pack screenshot. The original
trial's source attribution is retained under `work/real-media-lab/`.

This is AOSP emulator/scoped-provider evidence. Physical gallery/chat senders,
transient URI grants, real WhatsApp/Business import and phone performance remain
separate acceptance. It is not a new public catalog pack or quality-parity claim.

## Current Apple client

[Apple CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39597303190)
passes at `9029d136698a9361df9a0e6f61e64585a72d5fd7`, with unchanged iOS/shared
source through Android main `4394ac1`. It builds the unsigned native device app
and runs **15 native integration tests plus 1 navigation UI test**, zero failures
or skips, on iPhone 17 Pro / iOS 26.2 simulator. Tests cover native static and
motion codecs, rotation, archives, local-store rollback/restart and read-only
catalog behavior. Navigation screenshots were inspected. An isolated Discover
empty-state fixture does not establish that the live catalog is empty.

The iPhone client is **0.10.1 (build 13)**. The unsigned IPA SHA-256 is
`e9cfca729ea2045ff130a4f6fb6e7d24b0038985ee0182c54bdad4e620652ef6`.
Artifact digest, ZIP CRC, bundle/version and absence of provisioning were checked.
Local artifact: `work/apple-current-verified/work/StickerDock-iOS-unsigned.ipa`.
Apple signing is still required for device installation; no App Store/TestFlight
or physical WhatsApp acceptance is claimed. See [iPhone scope](../ios/README.md).

## Fresh Apple-to-Android archive

The same Apple run's actual native photo test generated and exported a MATURE,
STATIC schema 5 pack named “Reaction photos”. That exact ZIP was opened on the
signed Android candidate above. Opening the confirmation saved nothing; explicit
Import added one pack/sticker. All prior 12 packs / 36 stickers and project rows
remain exact. The new pack's name, kind, rating and original WebP bytes match the
Apple archive, including after restart. SQLite integrity is `ok`.

Archive SHA-256:
`1202adc0526fb241878bf827c6506954be8f25f4d672b42e0ec85df92e01c61a`.
Photo WebP SHA-256:
`7f9bfb27e90927896f27afc539071d1f72cd09ce819189e2035028159c4419e5`.
The resulting library is 13 packs / 37 stickers. This fixture is an iOS-generated
test photo, not a user's private photo or a real iPhone transfer.

The initial verifier incorrectly required the archive's pack UUID to remain its
Android UUID. Portable import intentionally creates new local IDs. The original
failure log is retained; verification resumed from saved before/after snapshots,
required exactly one added pack and checked that pack's metadata and exact media.
Import was not repeated and no data was repaired or removed.

Local evidence: `work/current-cross-platform-acceptance/report.json`, before/
after SQLite snapshots, exact imported WebP and restart screenshot. These local
artifacts and licensed media are intentionally outside Git.

## Importable real-reaction samples

Four existing local trial packs are bundled as
`work/StickerDock-real-reaction-samples.zip`: Group chat reactions and Monday
energy, each with separate static and animated packs. They contain 12 stickers
total, six animated, with three stickers per pack. Every media hash, 512px size,
type and applicable static/animated size bound verifies. This is a local style
and compatibility sample, not an approved public catalog publication.

Extract the outer bundle, retain its README/CREDITS and import its four inner
pack ZIPs explicitly. Credits cover Rattyexalt's Facepalm.webm (CC BY-SA 4.0) and
SecretDisc's Yawn.gif (CC BY-SA 3.0), source/license links and the transformations.
The sources and derivative licenses remain separate from StickerDock code.
Neither the app nor this bundle automatically installs a WhatsApp pack.
