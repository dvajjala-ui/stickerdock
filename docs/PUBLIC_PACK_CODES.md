# Public pack codes

Reviewed Discover packs can share a plain-text message with `SD:<catalog-id>`,
a `stickerdock://pack/<catalog-id>` app link and the existing app-download URL.
Recipients can paste the code in Discover → options → Open pack code, share the
message into StickerDock, or follow the custom app link where supported.

Every request resolves against a fresh, filtered public catalog. Removed packs,
hidden creators, network errors and malformed codes do not import anything.
Opening a valid code previews the current published revision; Save remains
explicit. Catalog checksums/download restrictions continue to apply. The ID is
public metadata, not an authentication or private-sharing token.

The parser accepts only one bounded canonical ID. It rejects arbitrary HTTP
hosts, credentials, query strings, fragments, percent encoding and path traversal.
Cold starts and `onNewIntent` are handled. Existing incoming file shares still
reach their file-import flow. Opening a link does not upload the local library.

This is native pack-code/custom-link support. It is **not** a deployed HTTPS pack
landing page, verified Android App Link, universal iOS link, or private revocable
link. Some messengers do not make custom schemes clickable; the copied code and
explicit instructions remain usable. A subsequent web rollout needs a reviewable
public page and association with the actual production signing certificate.

References: [Android deep links](https://developer.android.com/training/app-links/create-deeplinks)
and [website associations](https://developer.android.com/training/app-links/configure-assetlinks).
The custom scheme has no authentication role; other apps can register the same
scheme. No credentials are placed in the message or URL.
