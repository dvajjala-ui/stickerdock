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

## Android 0.10.2 scope and limits

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
WhatsApp/Business checks, advanced Android AI/automatic cutout/project backups
and iPhone parity remain separately tracked in the requirements audit.
