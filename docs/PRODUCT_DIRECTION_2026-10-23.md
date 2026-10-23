# StickerDock: fast reactions, reliable handoff, better media

Updated October 23, 2026. This document expands the [product execution tracker](PRODUCT_EXECUTION_2026-10-22.md) from the user's [latest request](USER_PRODUCT_INPUT_2026-10-23.md). States below distinguish shipped code from unverified product goals. StickerDock remains an opt-in local library with explicit public sharing.

## Product job and navigation

The core job is: *I need a reaction for this conversation now.* Library search should find an individual sticker by situation, caption, emotion, subject, language and pack. Search results should show the actual artwork first, then its pack and a clear Add pack action. A sticker cannot be installed into WhatsApp alone: the action offers its containing pack and explains when that pack needs at least three compatible stickers.

Keep bottom navigation to **Discover, Create, Library**. Discover is reviewed new content and inspiration; Create is a short photo/video-to-preview-to-save flow; Library is search, favorites, organization and WhatsApp handoff. Import, backups, cloud account and advanced settings belong behind Library settings, not as competing primary destinations. Admin/Studio remain separate operator tools, never mixed into consumer navigation. Review thumb reach, accessibility at 200% text, dark mode, back behavior and action hierarchy on real screenshots before declaring UI polish complete. Revisit the icon with several restrained concepts, legibility at launcher size and adaptive-icon safe zone; do not replace it based only on a desktop mockup.

## Search and WhatsApp work

| Priority | Behavior | Acceptance |
| --- | --- | --- |
| Now | Android/iPhone sticker-first results, pack result switch, static/animated filter, pack name + sticker caption/tags, multi-word situations and preview | A query such as `monday work` finds a sticker whose caption and tag separately match; a nonmatching term removes it; 10,000-item library remains responsive. |
| Now | Add the result's pack directly, choose personal/Business only when both are installed, then rely on WhatsApp's confirmation | No false success toast when the user declines; incomplete packs explain what is missing. Test on both installed WhatsApp targets. |
| Now | Send one to three accurate WhatsApp emoji tags from explicit per-sticker metadata | Official WhatsApp search can index useful reaction categories; an unlabelled sticker receives a neutral fallback, never a fabricated emotion. |
| Next | Curated metadata vocabulary: emotion, situation, visual subject, language, motion, content rating, creator/source; editable per sticker | Android and iPhone now edit label/tags; imported files retain user labels. Automatic suggestions are reviewable and never overwrite edits. Richer field filters remain open. |
| Next | Indexed local search, ranking and recent queries | Measure cold/warm search latency and memory on 10k and 100k libraries; exact caption/tag hits rank before broad pack-name hits; avoid querying on every keystroke. |
| Later | Search/download of approved public catalog stickers, pack collections and saved shortcuts | Explicit save, offline preview, rights and moderation state; no silent import on app launch. |

The [official Android sticker integration](https://github.com/WhatsApp/stickers/blob/main/Android/README.md) requires the user to add each pack and confirm in WhatsApp. It can query whether **StickerDock's own pack** was added to personal WhatsApp or Business. It cannot enumerate or remove built-in packs or packs supplied by other apps. Therefore a “manage all WhatsApp stickers” permission or one-tap deletion of default packs is not an implementable feature with the public integration. Use StickerDock as the organizer for owned/imported media, with clear links to WhatsApp's own tray controls where applicable. Android's system picker remains the supported way to choose a phone sticker folder; broad storage access is not a substitute.

## Real meme and animated content pipeline

The public catalog should be a reviewed set of *useful chat reactions*, not a volume target. Generate topic ideas from current affairs and local conversation trends, but choose an independently reusable visual or create original art before rendering. A headline alone is not a joke. Each draft needs a reaction brief (who would send it, to whom, in what situation), a short punchline, a distinct scene, and a manual review of timing and tone. Do not turn sensitive tragedies or identifiable private people into automatic jokes.

1. Ingest source candidates with URL, creator, license, permitted derivative/distribution use, attribution, capture date, media hash, and a source-family/scene identity. Treat user-uploaded images as private, reference-only by default unless the user explicitly grants reusable/public rights.
2. Detect exact and perceptual duplicates **before** Groq caption spend. The Factory now also compares conservative visual hashes across differently credited photo files; difficult crop/semantic duplicates still need review. Distinguish new frames from genuinely different scenes. For video, sample representative frames and preserve motion only if it changes the reaction.
3. Detect embedded text/OCR regions before placing captions. If the original text is part of the joke, leave it readable and skip an overlay; otherwise crop, reposition or choose another source. Automated text detection is a warning for human review, not proof the composition works.
4. Draft several situational captions, screen them for readability, overused wording, contradictory visuals, accessibility and safety; render transparent 512×512 static or animated WebP within WhatsApp limits. Preview over light/dark chat backgrounds and at actual phone size.
5. Keep drafts private until provenance, rights, visual quality, humor and file integrity pass review. Publish a small coherent pack; measure preview→save→WhatsApp handoff and user saves, not just generated count.

### Source expansion, with rights boundary

| Source | Product use | Boundary |
| --- | --- | --- |
| User photos/videos | Primary personal sticker creation; explicit upload/reference choice in Studio | Private unless explicit rights and public submission review. |
| Wikimedia Commons | Candidate pool beyond current animal/Wikipedia set | Check each file's actual license/credit and other rights; Commons [warns that requirements vary](https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia). |
| Openverse | Search/discovery for new CC/public-domain candidate sites | Openverse [does not verify licenses](https://docs.openverse.org/terms_of_service); verify at origin, respect attribution and API limits. |
| Pinterest | User's saved boards/Pins as mood-board references and trend clues | A Pin is not a redistribution license; do not download/rehost it as sticker art without independent permission. |
| Reddit, news and social posts | Topic/situation signals and links to original discussion | Do not clone post images into a sticker catalog. Reddit's [API terms](https://redditinc.com/policies/data-api-terms) limit modification and reuse of user content; copyright/publicity still apply. |
| Pexels/other stock libraries | Evaluate one provider at a time for genuinely transformed artwork | [Pexels terms](https://help.pexels.com/hc/en-us/articles/900005880463-What-are-the-Terms-and-Conditions) restrict standalone redistribution and competing collections; obtain permission or choose a clearer source before production ingestion. |
| Commissioned/original art | Reliable unique expressions, brand identity and themed animated packs | Record creator/license and review before public use. |

## Next implementation slices

1. Land and verify sticker-first native search and pack handoff on both platforms. Android emulator and Apple CI are source/simulator evidence; personal/Business WhatsApp and physical iPhone remain device gates.
2. Add metadata editing and portable archive compatibility for the richer fields. Let people tag privately; offer suggestions but confirm before saving. Instrument search latency and zero-result queries without collecting private sticker content.
3. Build a source-candidate queue with license and scene identity, plus OCR/duplicate warnings in Admin. Review a small set of current-affairs reactions manually before adding any source to automatic production.
4. Simplify consumer settings/navigation, run screenshot and accessibility review, then revise the launcher icon. Apply UI changes to Android and iPhone separately with parity checks.
5. Expand licensed static and animated packs through the same review gates. Only then test discovery/search ranking, retention and sharing with actual users.

Open gates: current Android signed release, fresh Android/Apple CI for this slice, WhatsApp personal/Business on a physical phone, App Store signing/distribution, hosted Admin migration/rights readback and a real-user usability test. None is closed by this document.
