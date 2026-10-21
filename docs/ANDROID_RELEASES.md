# Android installation and release updates

[Download the latest signed APK](https://github.com/dvajjala-ui/stickerdock/releases/latest/download/StickerDock.apk)
· [Admin website](https://stickerdock.vercel.app/admin)
· [Release notes](https://github.com/dvajjala-ui/stickerdock/releases/latest)

Android 11 or newer is required. Open the downloaded APK on your Android phone
and allow installation from the browser/file manager when Android asks. Install
over an existing release to retain its library; the signing identity must match.
If Android reports an incompatible signature, export a library backup before
removing any previous installation. Never uninstall first without a backup.

The APK link always selects the latest successfully published signed release.
The site also exposes it at `https://stickerdock.vercel.app/download` and in the
admin sidebar. APK installation remains an explicit user action.

## Verification and publishing

`.github/workflows/android-release.yml` runs for Android source changes on
`main`, or manually on a selected branch. Branch runs produce reviewable artifacts;
only a successful `main` run publishes a GitHub release. The production signing
key stays in the protected `android-production` environment.

The workflow runs backend regressions, shared/Android unit tests, release lint,
signed APK/AAB builds, signature/ZIP alignment checks, then signed-APK emulator
acceptance with 334 packs/10,000 synthetic stickers, scrolling/memory checks,
360dp/200% font controls, restart preservation and real MP4 preview. Synthetic
fixtures are isolated to a disposable rooted emulator, never a user's phone.

Release assets include `StickerDock.apk`, `StickerDock.aab`, `SHA256SUMS.txt`,
`release.json` and `acceptance.json`. The manifest binds version, versionCode,
source commit, SHA-256 and the Actions run. On Windows, verify the download with:

```powershell
Get-FileHash .\StickerDock.apk -Algorithm SHA256
```

Before publishing changed Android code, increment `versionCode` and `versionName`
in `app/build.gradle.kts`. An existing version cannot be silently republished from
a different commit. Failed checks leave the previous latest release intact.
The stable README/site links need no editing for each new version.

## Android 0.11.2 — published October 21

Share one GIF or video to StickerDock, trim/caption and generate a moving preview,
then save explicitly. The release also includes private Favorites and fixed
Copy/Move actions for long libraries and large text. Shared packs/photos retain
separate confirmation paths. Opening or cancelling does not fill the library.

[Main CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39605443554) and
[signed acceptance](https://github.com/dvajjala-ui/stickerdock/actions/runs/39605443793)
pass at `4225302`; the exact APK passes the 10k-sticker Android 15 journey and
cold video/warm GIF sharing. Public binaries, metadata and signing identity were
verified after download. APK SHA-256:
`1e84e25b7c8cd0c7813696cc7a2e38fd46cbc246b00c19009012849f0dab2c2a`.
See [full checks and limits](QA_MATRIX.md) and
[real-media/iPhone evidence](CREATOR_ACCEPTANCE_2026-10-21.md).

## Historical Android 0.11.0 — published October 19

Android **0.11.0 (build 15)** is [published](https://github.com/dvajjala-ui/stickerdock/releases/tag/android-v0.11.0) from `f129430820633263316b75e44420f464f063449f`.
[Main CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/39208384285) passes **124 Android / 12 shared / 208 backend tests**,
release lint/builds and 16 KB alignment. [The signed release run](https://github.com/dvajjala-ui/stickerdock/actions/runs/39208384312)
passes signature/alignment checks and on-screen Android 15 acceptance. The
public APK/AAB and metadata were downloaded and their checksums verified.
APK: 3,853,283 bytes; SHA-256
`a84fd854da449e137f6ed34e024a80277bd5b0960bbd5b89bdf6a8f9519c40b8`.
The APK hash matches release.json, SHA256SUMS.txt and acceptance.json. The
signing certificate matches Android 0.10.2. Local delivery:
`work/StickerDock-0.11.0/StickerDock.apk`.

Signed acceptance covers 334 packs/10,000 synthetic stickers (scrolling PSS
34,571→60,108 KB), large text,
photo creation, brush/draft/restart/reset, public codes, received files and
DocumentsUI editable export/restore with retained project history. These are
emulator checks, not physical WhatsApp/Business or segmentation-quality acceptance.

[Production admin](https://stickerdock.vercel.app/admin) is deployed. Its
HTML/JS/CSS and About page match source; supporting routes, signed-out session,
owner authentication boundary and desktop/phone browser checks pass. No
authenticated production writes were made in this publication check. The
existing public catalog has one four-sticker pack; its archive/media hashes
and format validate. Native AI/original artwork, Pinterest approval, catalog
breadth, physical-phone acceptance and remaining parity are still open.

The release adds pack-centered browsing/search, named photo destinations,
focused editing tools, mask brushes/drafts, public codes, received-pack import
and editable Android backups. Portable backup compatibility is preserved.

## Earlier Android 0.10.2 scope and limits

- Library grids use cached first-frame posters; tap an animated sticker to play.
  Only open previews animate, and playback pauses when the activity is paused.
- GIF/video composition compresses and releases each bitmap before the next
  frame. Recompression may decode the bounded clip again for detailed footage.
- Saves reserve space for SQLite and the previous artwork. Low-storage failures
  retain existing pack/sticker rows and media, allowing retry.
- Pack actions wrap at narrow widths and large text. Filter tools scroll.
- Factory JSON schema checks and app validation reject bad batches. One request
  is made per invocation; invalid output/network errors persist a retry within
  15 minutes. Daily request caps and provider cooldowns remain enforced. Cheap
  preflight skips paused, complete, budget-exhausted or not-yet-due work.

A synthetic 40/day recovery test proves scheduling/state logic, not live provider
capacity. Actual full-day generation, varied reviewed content, physical phone
WhatsApp/Business checks, native AI, live automatic-cutout quality,
full-resolution/video projects and iPhone parity remain separately tracked in the requirements audit.
