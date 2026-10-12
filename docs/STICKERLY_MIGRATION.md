# Migrating from Sticker.ly into StickerDock

## Verified limitations
Sticker.ly does **not** document a public account-wide sticker asset export API. Its official FAQ documents exporting to WhatsApp, not a complete ZIP of an account. Its Terms of Use distinguish your own creations from others' assets: other creators' stickers are licensed for limited personal use, not general republication. Do not bypass app sandbox protections or scrape accounts.

Sources: https://sticker.ly/faq/en/ , https://sticker.ly/terms-of-use , https://sticker.ly/privacy-policy

## Working import paths in StickerDock (source implementation; device QA pending)
1. **Original images**: Open an existing static or animated pack, use Import and select images from Android's Files picker. Animated media must already be animated WebP; GIF/video conversion isn't implemented.
2. **ZIP/.wastickers image archives**: From StickerDock Home, tap **Import sticker ZIP**; a bounded ZIP scanner locates PNG/JPEG/WebP images, groups them by folder and separates animated from static. Some third-party manifest formats are not interpreted; pack names may be derived from folders.
3. **Other StickerDock collections**: Share pack ZIP or use Backup; recipient chooses Import ZIP. Original StickerDock ZIP manifests preserve pack names, static/animated kind and general/mature content rating.
4. **Share sheets**: For apps that share actual image/ZIP files to other apps, choose StickerDock as the target. Sharing a Sticker.ly *link or code* is not the same as sharing image bytes; such links aren't automatically downloaded.

## If your assets exist only on Sticker.ly
- If the stickers were designed from your own images, locate the originals or ask Sticker.ly support about an account export. Confirm you own the source material.
- Sticker.ly may provide account information upon a privacy request, but do not assume their export contains downloadable stickers.
- Do not publish copies of third-party stickers unless you hold the required rights. Personal migration and public redistribution are different use cases.

## Future development
Implement a legitimate import connector only after confirming a supported service contract. Importing authenticated libraries through unofficial hidden APIs is not part of this MVP. Separate animated WebP support is implemented; GIF/MP4 conversion needs an audited media pipeline and animated preview QA.
