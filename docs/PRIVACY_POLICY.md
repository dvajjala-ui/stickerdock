# StickerDock Privacy Policy — release draft

**Effective date:** October 13, 2026

This document describes the data handling implemented by the current StickerDock
Android app and, separately, the optional private Creator Studio web feature.
It is a release draft: before public store publication, the developer must publish
this policy at a stable public URL and add a private support/privacy contact to
both this document and the store listing.

## Android app

StickerDock is primarily a local sticker-library application. The current Android
source does not include an account system, advertising SDK, analytics SDK, or
background telemetry service.

### Data stored on the device

The app can store sticker images, pack names, categories, labels/tags, import
progress/checkpoints, bookmarked Sticker.ly share codes/links, and backup/restore
metadata on the device. This information is used to provide the app's local
library, search, migration, sharing, and resume features.

Android operating-system backup for the app is disabled. StickerDock does support
user-initiated export/backup files; those files are created or restored only when
the user chooses those actions.

### Files and media access

StickerDock uses Android's system document/folder picker for user-selected files
and folders. It does not request broad storage access. Persisted folder access is
used only when Android grants it and only to support the user's selected import
and resume flow.

The app does not request camera, microphone, location, contacts, account, or
advertising permissions. The current manifest requests Internet access so the app
can read the reviewed public sticker catalog.

### Network access

The Android app is configured to reject cleartext HTTP traffic. Public catalog
requests use HTTPS and fetch only the reviewed StickerDock catalog and selected
pack archives. The catalog client does not upload the user's local sticker
library, device media, or StickerDock database to the catalog service.
The home screen refreshes this public catalog when the app opens or returns to
the foreground. It automatically downloads a bounded batch of new approved pack
revisions into the local library. The New tab also allows explicit downloads.
Deleting a downloaded revision prevents that same revision from being recreated
automatically. These downloads do not upload local stickers or media.

Like ordinary HTTPS requests, the hosting provider may receive technical network
information such as the requesting IP address and standard connection metadata.
StickerDock does not add an advertising identifier or analytics identifier to
these catalog requests.

The current production catalog is hosted in a public Supabase Storage bucket.
Only review-approved GENERAL packs are intended for public publication. The app
verifies catalog structure and archive checksums before import.

### Sharing and WhatsApp installation

Sharing/exporting a pack is user initiated. When a user chooses to share a file or
install a pack into WhatsApp or WhatsApp Business, the selected sticker content is
made available to the chosen receiving application through Android's sharing or
sticker-provider mechanisms. The receiving application's own privacy practices
then apply to its handling of that content.

Sticker.ly links are stored only as local bookmarks. Opening an external link is a
user-initiated action and is handled by the chosen browser/application under that
service's privacy policy.

### Deleting Android data

Users can remove packs/stickers inside StickerDock or clear/uninstall the app to
remove its app-private local data. Files the user intentionally exported or shared
outside StickerDock must be deleted from their destination separately.

## Optional private Creator Studio web feature

Creator Studio is a separate browser-based maintainer/private feature and is not
currently exposed through the Android app.
This section applies if the Creator Studio is deployed and used.

Creator Studio is protected by a private access token. The browser stores that
token only in the current tab's `sessionStorage`. The Supabase service credential
and Groq API key remain server-side and are not sent to the browser.

When a user intentionally uploads an image or requests an AI-assisted sticker,
the selected source image and prompt can be sent to the StickerDock server-side
API. Private source/result files and provenance metadata are stored in the
StickerDock Supabase project. Creator request status is stored in the private
Creator queue. For an AI request, the configured Groq vision service receives the
content required to produce caption/reaction guidance. The resulting sticker is
rendered by StickerDock's private worker and stored back in private Supabase
storage.

Private Creator/Factory source and provenance data are retained until explicit
operator cleanup unless a narrower operational retention rule applies. Smoke-test
data used during production verification is deleted or transactionally rolled
back after the test. Users/operators should not submit content they are not
permitted to process.

Supabase and Groq process data under their own terms and privacy practices.
Creator Studio should not be treated as publicly available until its production
hosting, token configuration, and hosted privacy/security checks are complete.

## Automated private Sticker Factory

StickerDock also operates a private server-side Factory for producing candidate
sticker packs. It may use licensed/public-domain reference media, authorized
operator uploads, and configured third-party source integrations. Factory state,
source files, pending packs, and provenance are private by default. Publication
is a separate manual review step; the Android app receives only deliberately
published public catalog artifacts.

## Security

StickerDock uses measures in the current source intended to reduce unnecessary
exposure, including HTTPS-only Android catalog URLs, cleartext-traffic blocking,
private Supabase buckets for source/result data, row-level database protections,
server-side credentials, bounded uploads/queues, provenance validation, and a
manual public-publication boundary.

No software or storage system can guarantee absolute security. If a security or
privacy issue is discovered, users should avoid posting secrets or personal data
in a public issue.

## Children and public submissions

The current product does not provide public user accounts or an open community
upload service. A public submission/community system must not be enabled without
additional authentication, abuse/malware controls, rights review, moderation,
and appropriate age/safety controls.

## Changes to this policy

This policy should be updated when StickerDock's data handling materially changes,
including when Creator Studio becomes publicly accessible, analytics/telemetry is
introduced, a public submission system is enabled, or hosting/providers change.
The effective date at the top should be revised when a new version is published.

## Contact and publication requirement

Before publishing StickerDock through an app store, the developer must:

1. publish this policy at a stable public HTTPS URL;
2. add a private support/privacy email or equivalent non-public contact channel to
   this section and to the store listing; and
3. verify that the final store data-safety disclosures match the released build
   and any enabled hosted services.

For non-sensitive project questions during development, the repository issue
tracker may be used. Do **not** post passwords, tokens, private images, personal
information, or privacy-request details in a public issue.
