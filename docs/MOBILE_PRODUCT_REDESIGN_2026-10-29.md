# Mobile product redesign — October 29, 2026

## Owner direction

The owner wants a complete product-quality rethink of Android and iPhone: clear
purpose, intentional page hierarchy, real icons and motion, focused settings,
easy WhatsApp use, discoverable generated stickers, useful search and filters,
and a coherent brand. Treat the existing UI as a starting point, not a finished
design. Preserve personal libraries, private content and existing backups.

## Core job and product principles

StickerDock should help someone find or make the right reaction, organize it,
and add a pack to WhatsApp without losing their collection. Main destinations:

- **Library**: search actual stickers and packs, favorites, organization and a
  visible WhatsApp entry point.
- **Create**: choose media, edit, preview, then save to a compatible pack.
- **Discover**: browse published, reviewed packs with real artwork, filters,
  preview and explicit save. Private Factory drafts never appear here.
- **Settings**: import, backup, account, privacy and advanced tools.

Motion should communicate navigation and feedback, respect reduced-motion
settings, and never delay a frequent action. Platform-native controls, clear
labels, 200% text and light/dark layouts are acceptance criteria.

## Evidence and constraints

- The official [WhatsApp sticker protocol](https://github.com/WhatsApp/stickers/blob/main/Android/README.md)
  requires a user action for each pack and 3–30 stickers, with static and animated
  media in separate packs. Android can target installed consumer and Business
  apps; it cannot select an account inside one app. iOS handoff is a request,
  not proof of acceptance.
- The public catalog fetched on October 29 contains five GENERAL packs. One is
  credited to StickerDock AI Art. Other Factory outputs remain private drafts or
  were rejected, so they cannot appear in Discover until rights, quality and
  publication review pass. Both native apps read the same public catalog; this
  evidence does not prove a particular installed app successfully loaded it.
- [Apple tab-bar guidance](https://developer.apple.com/design/human-interface-guidelines/tab-bars)
  supports labeled stable destinations and native icons. [Apple motion guidance](https://developer.apple.com/design/human-interface-guidelines/motion)
  calls for purposeful motion and reduced-motion support.
- User discussions about [sticker organization](https://www.reddit.com/r/whatsapp/comments/gzydlt/we_need_a_way_to_organize_our_stickers/)
  and [backup friction](https://www.reddit.com/r/whatsapp/comments/fxv222/is_there_any_good_whatsapp_sticker_creating_app/)
  are qualitative signals, not representative market research: quick finding,
  migration and nonintrusive backups merit real-user tests.

## First implementation slice

- Added a visible WhatsApp entry in Library on Android and iOS. It lists packs,
  explains the three-sticker minimum, opens incomplete packs and invokes the
  existing explicit handoff for ready packs.
- Android retains installed-app selection and existing verification; iPhone
  explains that WhatsApp must confirm the handoff.
- iPhone Create starts with a branded, focused photo/file choice instead of
  presenting editing controls before media exists. Existing editing and private
  AI consent remain intact; choosing different media remains available.
- Android creation choices are now above the explanatory steps so the first
  action is reachable sooner. iPhone stage transition respects Reduce Motion.

## Remaining work and acceptance

This is the first slice, not a complete redesign. Next: screenshot review and
iteration of all core screens at phone sizes, dark mode and large text; a
sticker-first Discover with better preview and save-to-WhatsApp continuity;
intentional artwork-led cards and responsive navigation; clearer Android and
iOS creator steps; realistic search, migration and two-app WhatsApp tests; and
reviewed original content that people want to send. Verify real catalog loading,
save and handoff on devices; do not publish the existing rejected Factory drafts.
Use Android emulator and iPhone simulator as early checks, then real WhatsApp,
Business and physical iPhone/Samsung checks before a release claim.
