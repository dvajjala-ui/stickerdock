# StickerDock public sharing design

## Implemented now
- Offline pack creation and editing on device.
- Each pack contains exclusively static or animated stickers.
- Content rating label: GENERAL or MATURE, stored locally and carried in StickerDock ZIP backup/share archives.
- User-initiated Share pack ZIP (Android FileProvider; grant limited read access) so friends can import each other's pack.
- Read-only curated GitHub JSON catalog, available via the app's Community packs button. Empty until submissions are reviewed; app downloads only from pinned repository paths, verifies SHA-256, and imports validated archives.
- Public catalog reads GENERAL content only. MATURE packs are private/offline exchange, **not publicly indexed**.

## Publishing workflow without a paid backend
Users propose a pack by GitHub pull request to `catalog/`. Maintainer reviews creator ownership and redistribution permission, age/content category, malware/ZIP parsing safety, checksums and basic uniqueness; only then merges. No automated uploads or automatic listing of user-submitted content.

## Requirements before open publishing
Identity and account security; upload scanning; moderation queues; clear upload terms, license confirmation and attribution; content/user reporting; blocking; takedown handling; repeat-offender policy; moderation audit log; privacy/data deletion; age screening; default-safe view and multi-action opt-in for incidental sexual content; geo-law compliance. Do not put UGC online before this exists.

## Adult content
Users may label their **local** packs MATURE; this does not override WhatsApp rules or law. Public pornographic/sexually gratifying content is not supported by the catalog, because Google Play has strong restrictions, and a public UGC app must not principally promote objectionable content. Consensual, legal, incidental mature content would require proper user-age checks, default-hidden filters and moderation; this has **not** been implemented.

References:
- Google Play UGC: https://support.google.com/googleplay/android-developer/answer/9876937
- Google Play sexual content: https://support.google.com/googleplay/android-developer/answer/18258653
- WhatsApp sticker requirements: https://github.com/WhatsApp/stickers/blob/main/Android/README.md
