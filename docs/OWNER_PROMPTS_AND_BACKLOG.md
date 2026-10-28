# Owner prompts and master backlog

**This file is the single source of truth for owner intent.**
Every prompt the owner sends is logged here verbatim (minor spelling normalized).
Each prompt is decoded into concrete feature bullets.
The backlog table below is derived from *all* prompts combined.
Update this file after every session — it is the context every future
agent session should read first.

---

## All owner prompts (chronological)

### Prompt 1 — October 23, 2026 (session 1)

> i was thinking of adding like in user app. like as per the situation they can search for particular sticker very fast and actually get the result. example: add small meta info to all stickers, filter by pack vs sticker specifically. WhatsApp add should be very easy and fast. Also manage WhatsApp entire library, ask user for WhatsApp permission. Quick adding stickers/packs to WhatsApp. Improve app icon and UI/UX. Make it like a real app like Instagram UI. Look into Instagram, WhatsApp, Sticker.ly, Pinterest, LinkedIn, AI tools, payment apps, shopping apps. Consider yourself the product owner. Expand image sources beyond Wikipedia. Fix: stickers already have image text and we are placing text on top of that. Reduce duplication. Improve animated stickers / video sticker pipeline. Reddit, Pinterest, open image, open memes, uploaded reference images. Record all prompts in an md file.

**Decoded intent:**
- [x] Situation-based sticker search with fast ranked results
- [x] Per-sticker meta info (label, tags) — stored + editing UX
- [x] Filter by pack vs sticker in search (Stickers / Packs toggle)
- [x] WhatsApp add 1-tap, fast (QuickAdd from search card)
- [ ] WhatsApp manage entire library — NOT possible via public API (documented)
- [x] App icon upgrade (icon XML + iOS PNG updated)
- [ ] UI/UX overhaul: Instagram-quality layout, no UI stacking, placement with purpose
- [ ] Study: Instagram, WhatsApp, Sticker.ly, Pinterest, LinkedIn, AI tools, payments, shopping
- [ ] Expand image sources: open memes, Openverse, news-based current-affairs memes
- [x] Fix: images with baked-in text do NOT get a second caption (likely_has_meme_text guard)
- [x] Source deduplication (visual hash + title dedup)
- [ ] More and better animated stickers
- [ ] Video/GIF to animated sticker pipeline improvement
- [ ] Reddit, Pinterest, Openverse as source/inspiration feeds

### Prompt 2 — October 23, 2026 (session 2)

> can u continue work and help me complete it fast and push code in github

Done:
- Ranked search (exact > tag > pack > category > filename) on Android + iOS
- Situational suggestion chips (monday mood, thanks, lol, not today, okay, work done, send help, love this)
- Tag chips inline on search result cards
- Search placeholder changed to "monday mood, thanks, lol..."
- Embedded-meme-text detection guard in Factory (likely_has_meme_text)
- 257 tests pass, pushed to GitHub main

### Prompt 3 — October 23, 2026 (session 3)

> keep working also add the prompt that i add given in a md file for remembering and working on all the points in the prompt

Done:
- Created this file (OWNER_PROMPTS_AND_BACKLOG.md)
- Continue building from backlog

---

### Prompt 4 — October 28, 2026

> continuw eowkirng where u left off also make sure u have the ltest pull as i have added u few features and once all that is done can reisvle this PR and push to mian = https://github.com/dvajjala-ui/stickerdock/pull/26

> coontin ue where u elft off like aslo some portme execution was goin on cotinue with that too process also u can do quick review of new arciture and fucnitlitys

Result: pulled the new HandNavigate features through `fed2e98`, resolved PR #26's QA-document conflict, applied and verified its additive admission migration, and merged it as `04a687e` after passing Android and Web/API CI. The [architecture review](ARCHITECTURE_REVIEW_2026-10-28.md) records the source-capability gap and remaining acceptance work.

### Prompt 5 — October 28, 2026 (continuation)

> conntinuw from wher eu left off

Repeated continuation requests resume the active work. PR #27 delivered the
Library accessibility/search-history fix after native CI passed. Factory recovery
and the remaining source-text quality repair are recorded in the
[quality follow-up](FACTORY_RECOVERY_AND_QUALITY_2026-10-28.md).

## Master feature backlog

### Search & discovery
- [x] S1 — Ranked sticker search (exact > tag > pack > category > filename)
- [x] S2 — Situational suggestion chips
- [x] S3 — Tag chips inline on search result cards
- [x] S4 — Situational search placeholder text
- [x] S5 — Recent searches remembered + shown as tappable chips
- [x] S6 — Zero-result fallback: suggest editing tags or browsing Discover
- [ ] S7 — Global search across Library + Discover in one bar

### WhatsApp integration
- [x] W1 — 1-tap Add pack from search card
- [x] W2 — Smart WhatsApp target (skip dialog if only one installed)
- [x] W3 — Already-added state clearly shown
- [x] W4 — WhatsApp emoji tags from sticker label/tags
- [ ] W5 — Manage/delete WhatsApp built-in packs (NOT POSSIBLE via public API)
- [x] W6 — WhatsApp Business separate target with install check

### UI / UX overhaul
- [x] U1 — App icon draft (needs device review)
- [ ] U2 — Bottom nav: 3 tabs (Library / Create / Discover), move Import to Settings
- [x] U3 — Library: 2-column grid view + list view toggle
- [ ] U4 — Pack detail: sticker grid with labels below each tile
- [ ] U5 — Settings declutter: Import/Backup out of nav, into Settings gear
- [ ] U6 — Micro-animations (tab transitions, sticker preview enter/exit)
- [ ] U7 — Pull-to-refresh on Library and Discover
- [ ] U8 — Onboarding: 3-step first-run flow
- [ ] U9 — Dark mode polish
- [ ] U10 — Empty states: illustrative and action-oriented
- [x] U11 — Animated WebP preview playing in sticker sheet
- [x] U12 — Pack card animated badge if pack has motion stickers

### Content / sticker quality
- [x] C1 — Baked-in meme text detection (likely_has_meme_text)
- [x] C2 — Source deduplication (visual hash + title)
- [x] C3 — Openverse (CC/PD images) as expanded source
- [ ] C4 — Pexels trial (transformed artwork only)
- [ ] C5 — Current-affairs meme pipeline (news headline -> brief -> original art, human review gate)
- [ ] C6 — More WebP animations, better motion quality
- [ ] C7 — Video-to-WebP pipeline: trim, loop, correct palette
- [ ] C8 — Original commissioned art packs (Hinglish reactions, festivals)
- [x] C9 — User-uploaded reference images -> private sticker creation (Studio)
- [ ] C10 — Reddit topic signals (never clone post images)
- [ ] C11 — Pinterest mood board reference (browsing only, no rehosting)

### Sticker Factory (AI pipeline)
- [ ] F1 — Partial batch delivery: publish reviewed batches < 40 without waiting all day
- [ ] F2 — Factory run monitoring in Admin (live progress, warnings)
- [ ] F3 — More Groq API keys / model rotation for rate-limit headroom
- [ ] F4 — OCR-based text detection (pytesseract / easyocr) as C1 upgrade
- [ ] F5 — Admin: bulk review UI (approve/reject per row or batch)
- [ ] F6 — Draft lifecycle: close stale day-batches, deliver partial content

### Platform / mobile
- [ ] P1 — Android signed release + Galaxy S24 Ultra SAF + WhatsApp physical test
- [x] P2 — iPhone: metadata editor for sticker label/tags
- [x] P3 — iPhone: animated WebP preview in Discover
- [x] P4 — iPhone: WhatsApp handoff with emoji tags
- [ ] P5 — iPhone: App Store signing and TestFlight build
- [ ] P6 — Android: large-library thumbnail LRU cache
- [ ] P7 — Android: FTS5 virtual table for >10k sticker search

### Admin / backend
- [ ] A7 — HandNavigate topic-driven intake: production candidate intake and Admin controls delivered; finish compatible-worker rollout, multiple-day reliability and rights/quality admission into generation. See [delivered workspace](HANDNAVIGATE_ADMIN_2026-10-28.md) and [architecture review](ARCHITECTURE_REVIEW_2026-10-28.md).
- [ ] A1 — Admin: source-candidate queue with license/scene tracking
- [ ] A2 — Admin: OCR/duplicate warning flags in media review
- [ ] A3 — Admin: bulk pack publish in one flow
- [ ] A4 — Admin: Factory run health dashboard
- [ ] A5 — Admin: search by sticker label/tags not just pack name
- [ ] A6 — Community: moderation, report/block, age protection, rights review (future)

---

## Next up (session 3 active work)
1. This file created
2. UI: simplify bottom nav to Library / Create / Discover, move Import to Settings
3. Content: Openverse free image source integration in Factory
4. Factory: partial-batch delivery gate (publish >=5 reviewed stickers without waiting for 40)
5. Android: recent searches row (persist last 8 in SharedPreferences)

---

## Research references
- WhatsApp sticker limits: 3-30 stickers/pack, 512x512px, <100KB/sticker, WebP only
- Groq free tier: ~30 req/min, 6000 req/day (varies by model)
- Openverse API: https://api.openverse.org/v1/ (CC/PD image search with license filter)
- WhatsApp sticker SDK: https://github.com/WhatsApp/stickers
