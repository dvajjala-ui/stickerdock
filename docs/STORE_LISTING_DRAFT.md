# StickerDock store listing draft

This is a release-preparation draft for Android version `0.5.0` (`versionCode 5`,
application ID `app.stickerdock.duo`). Re-check every statement against the final
signed build before submitting it to an app store.

## App name

**StickerDock**

## Suggested category

**Personalization** is the closest fit for a sticker-pack organizer/creator.
Confirm the final store category when the listing is created.

## Short description

Organize, import, create and send your sticker packs to WhatsApp.

## Full description

StickerDock is a private-first sticker library for Android. Build your own packs,
keep them organized, import stickers from files you choose, and send compatible
packs to WhatsApp or WhatsApp Business.

### Organize your collection

- Group packs by categories such as Reactions, Hinglish, Movies, News, Animals,
  Love, Work and Festivals.
- Search packs and individual stickers.
- Add your own sticker labels and tags.
- Copy or move stickers between packs without losing their metadata.

### Bring in stickers you already have

- Import PNG, JPEG and WebP images.
- Import ZIP packs and multiple ZIP files.
- Pick a phone folder through Android's system file picker.
- Resume large folder imports after interruption.
- Import compatible user-provided sticker exports and keep Sticker.ly links as
  local bookmarks.

StickerDock does not silently scan your whole phone. Folder/file access starts
from Android's system picker and the locations you choose.

### Create, back up and share

- Create static and animated packs.
- Rename, copy, move or remove stickers.
- Export/share pack ZIPs.
- Create and restore local StickerDock backups.
- Install compatible packs into WhatsApp or WhatsApp Business.

### Reviewed community catalog

StickerDock can read a small curated public catalog. Only deliberately published
GENERAL packs are shown. A missing catalog simply means there are no approved
public packs yet. Downloaded packs are checked for expected structure and SHA-256
integrity before import.

### Private by default

The Android app has no account system, ads or analytics SDK in the current build.
Your local sticker library is not uploaded to the public catalog. StickerDock
requests only Internet permission; it does not request camera, microphone,
location, contacts, account or broad-storage permissions. Android backup is
disabled and cleartext HTTP traffic is blocked.

The private Creator Studio entry is hidden on Android. Private Creator
Studio/Factory infrastructure is separate from the Android app and is not needed
to use StickerDock's local library features.

## Version 0.5.0 release notes

- Make packs directly from selected photos and create destination packs while copying/moving.
- Edit static artwork with rotation, mirror, zoom/crop, positioning and outlined captions.
- Faster large-library previews and clearer WhatsApp export diagnostics.
- Approved new public packs enter the library automatically; availability depends on publication.

- Added pack categories plus sticker-level labels/tags and search.
- Improved folder/ZIP imports, deduplication and resumable large-folder migration.
- Added WhatsApp/WhatsApp Business compatibility hardening and migration helpers.
- Added reviewed public-catalog support with GENERAL-only filtering and integrity
  checks.
- Added an app launcher identity, HTTPS-only networking, optimized release builds,
  and additional production safety checks.

## Privacy policy

Repository draft:

`docs/PRIVACY_POLICY.md`

Before store submission:

1. publish the policy at a stable public HTTPS URL;
2. add the developer's chosen private support/privacy contact to the policy and
   store listing;
3. remove the “release draft” wording only after that final review; and
4. make the store Data Safety answers match the exact signed build and any hosted
   services that are actually enabled.

Do not use a personal/private email from repository metadata without the owner's
explicit choice to publish it.

## Data Safety preparation notes

These notes are not a completed Google Play Data Safety form. They summarize the
current Android source for the person filling out the final questionnaire.

- No advertising SDK.
- No analytics SDK.
- No Android account/login system.
- No camera, microphone, location, contacts, account or broad-storage permission.
- User-selected files/folders are processed locally through Android file APIs.
- Public catalog access is outbound HTTPS retrieval; the app does not upload the
  local sticker database/library to that catalog.
- Sharing and WhatsApp installation are explicit user actions that hand selected
  content to another app.
- The private AI/Creator Studio web service is not currently exposed in Android;
  reassess disclosures if that changes.

## Store graphics still required

The APK now has a native launcher icon, but store artwork is a separate release
asset. Prepare and review:

- 512×512 high-resolution store icon based on the StickerDock launcher identity;
- 1024×500 feature graphic if required by the target store;
- current-phone screenshots of the library, category/search flow, pack editor,
  imports, backup/restore and WhatsApp install flow;
- screenshots captured from the actual release candidate, not mock-only screens;
- no Creator AI screenshot until the hosted feature is intentionally released.

## Screenshot plan

Recommended first screenshot set:

1. Pack library with categories and search.
2. Pack detail with static/animated stickers.
3. Sticker label/tag editing.
4. Folder/ZIP import and resumable progress.
5. Backup/restore tools.
6. WhatsApp/WhatsApp Business install action.
7. Approved community catalog only after a real reviewed public pack exists.

## Release blockers before store submission

- Configure the four Android signing secrets and build the signed APK/AAB.
- Install/test that signed candidate on the target Galaxy S24 Ultra.
- Test current WhatsApp and WhatsApp Business pack acceptance.
- Test animated previews and 10k+/process-death folder migration behavior.
- Publish/finalize the privacy policy and support contact.
- Produce store icon/feature graphic/screenshots.
- Complete the store content-rating and Data Safety questionnaires against the
  final build.

Vercel Creator Studio deployment, Pinterest source authorization, Factory 40/40
capacity proof and Supabase quota monitoring are separate production-service gates;
keep them distinct from the core Android store listing unless those services are
actually exposed to Android users at release time.
