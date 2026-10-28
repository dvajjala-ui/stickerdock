# StickerDock × HandNavigate — Future Implementation Roadmap

**Last updated: October 28, 2026**
**Author role: Product owner perspective**

This document is the continuation plan from where we stopped on October 28, 2026.
Read `HANDNAVIGATE_DEPLOYMENT_HANDOFF_2026-10-28.md` for what is live today.
Read `OWNER_PROMPTS_AND_BACKLOG.md` for the master feature backlog.
Every future agent session should read this file first.

---

## Where we stopped (October 28 end-of-session state)

| Component | State |
| --- | --- |
| Railway daily collector | **Live** — 10:00 IST cron, Singapore, first deployment succeeded |
| Imgflip source | **Working** — 2/2 exact-host qualification passed |
| Pinterest source | **Blocked** — `AUTH_REQUIRED` from Railway IP, needs interactive host |
| Reddit source | **Blocked** — `AUTH_REQUIRED` from Railway IP, needs interactive host |
| Imgflip daily yield | **Partial** — 1/20 in acceptance run due to dedup; needs broader topics |
| Oracle A1 VM | **No capacity** — Mumbai rejected; free network prepared; retry organically |
| Candidate review | **Pending** — Imgflip candidates stored, unreviewed, `generation_eligible=false` |
| Factory admission | **Not connected** — no approved candidate has fed Factory yet |
| Source → Sticker → WhatsApp | **Not yet proven** — end-to-end not yet completed |
| Android physical test | **Open** — Galaxy S24 Ultra SAF + WhatsApp device test not done |
| iPhone TestFlight | **Open** — App Store signing and TestFlight not done |

---

## Phase 1 — Immediate (next 1–3 days)

These are the minimum actions needed to verify the deployed infrastructure works.

### 1.1 Observe first natural Railway run
- [ ] Wait for 10:00 IST next day
- [ ] Check Railway dashboard: deployment ID, exit code, runtime
- [ ] Verify Supabase `media_intake_items` count increased
- [ ] Record Railway metered usage for the day
- [ ] **Pass criterion:** Job exits `SUCCESS`, at least 1 new unique candidate stored

### 1.2 Review Imgflip candidates
- [ ] Open Admin → Sources → filter by platform: Imgflip
- [ ] For each stored candidate:
  - Check image visually: reaction quality, no obvious text overlay, no watermark
  - Check source: Imgflip popular meme templates are CC-licensed for meme creation
  - If acceptable: approve + set `generation_eligible=true`
  - If not: archive/trash
- [ ] **Do not bulk-approve** without reviewing each image
- [ ] **Pass criterion:** At least 5 candidates approved with confirmed reuse rights

### 1.3 Broaden Imgflip topic plans
- [ ] Add new Admin topic plans beyond "Daily animal reactions":
  - "Work and office reactions" (relatable workplace humor)
  - "Desi/Hinglish expressions" (Indian-context reactions)
  - "Festival and celebration moods" (Diwali, weekend, Friday vibes)
  - "Friendship and relationship reactions" (sending to friends on WhatsApp)
- [ ] Each plan: 20 images, Imgflip source, separate topic slots
- [ ] **Pass criterion:** At least 3 new plans saved and enabled

---

## Phase 2 — Short term (next 1–2 weeks)

### 2.1 Openverse integration (new rights-compatible source)

Openverse (https://api.openverse.org/v1/) provides CC and public domain images.
It does not require guest browsing, login bypass, or social account access.
This directly addresses the 20/day yield gap without needing a better hosting IP.

**HandNavigate changes needed:**
- [ ] Add `openverse.cjs` adapter in `service/` — query Openverse v1 API by topic
- [ ] Filter by license: CC0, CC BY, CC BY-SA only (no NC, no ND unless explicitly allowed)
- [ ] Normalize to WebP, check dimensions (min 512×512 for sticker use)
- [ ] Add `openverse` to the source-capability advertisement
- [ ] Register `openverse` host in the allowed-redirect list

**StickerDock changes needed:**
- [ ] Add `openverse` as a valid platform in `media_intake_items`
- [ ] Add Openverse filter option in Sources workspace
- [ ] Add rights auto-annotation: CC license type from Openverse API response

**Qualification:**
- [ ] 2-image exact-host Railway qualification test
- [ ] Verify license type stored in provenance
- [ ] Enable in daily plan only after qualification passes

### 2.2 Complete candidate → Factory admission

This is the most critical missing link. Currently no collected image has ever
entered the Factory pipeline. One end-to-end result is the acceptance criterion.

**Admin UX needed:**
- [ ] Sources page: "Admit to Factory" button (requires `generation_eligible=false` → `true`)
- [ ] Rights evidence field: URL or note confirming reuse is permitted
- [ ] Visual quality checklist before admission:
  - [ ] No existing text/watermark detected (OCR gate already exists)
  - [ ] Reaction is expressive and WhatsApp-appropriate
  - [ ] No repeated scenes vs existing packs
  - [ ] Dimensions suitable (≥512px, correct aspect ratio)
- [ ] Bulk approve/reject per row in Sources table view

**Factory changes needed:**
- [ ] Factory photo-source selection: prefer `generation_eligible=true` candidates
  from `media_intake_items` over existing inbox (already partially implemented — verify it works)
- [ ] Track which source images fed which sticker generation runs
- [ ] Generated stickers retain source identity (already implemented — verify)
- [ ] Results marked RESULT so they don't become caption inputs again (already implemented)

**Acceptance criterion:**
- [ ] One Imgflip candidate: approved → Factory → draft sticker reviewed → published to WhatsApp

### 2.3 Factory quality improvements

The three October 28 draft batches were rejected for:
- Same photo used for all 5 captions in one batch
- Overlapping text (caption on top of existing meme text)
- Weak reaction selection (not funny/expressive enough for WhatsApp)

**Fixes needed:**
- [ ] Per-photo usage cooldown in Factory (don't use same source twice in one batch)
- [ ] Diversity enforcement: max 1 sticker per source image per batch
- [ ] Better reaction-quality prompting in Groq caption call
- [ ] Image-text detection improvement: stylized/outlined text still passes OCR gate
- [ ] Non-Latin script detection (Devanagari, Arabic, etc. — not handled by current English OCR)

### 2.4 Observe 7 consecutive Railway runs

Before calling daily operation dependable:
- [ ] Record results for 7 consecutive natural 10:00 IST runs
- [ ] Log per-run: job ID, source breakdown, unique count, Railway usage cost
- [ ] Identify any pattern failures (expired state, duplicate saturation, quota limits)
- [ ] **Pass criterion:** 5 of 7 runs complete with ≥1 unique new candidate stored

---

## Phase 3 — Medium term (next 1–3 months)

### 3.1 Pinterest / Reddit from suitable host

**Oracle A1 retry (preferred — free):**
- [ ] Check Oracle Mumbai console periodically for A1 Flex capacity
- [ ] When capacity available: create VM with the prepared free VCN/subnet
- [ ] Deploy the prepared Docker container from `deploy/vm/`
- [ ] Use SSH-tunneled private console (port forwarded to localhost only)
- [ ] Perform 2-image Pinterest qualification: must see actual image results
- [ ] If any site shows a challenge/login requirement: complete it through console
- [ ] Only enable in daily plan after passing exact-host 2-image test

**If Oracle remains unavailable — evaluate alternatives:**
- [ ] Hetzner CX23 (€5.49/month) — recheck availability quarterly
- [ ] DigitalOcean Basic ($24/month) — exceeds budget; only if owner explicitly approves
- [ ] Do NOT add proxy rotation, VPN or challenge bypass — these violate terms

### 3.2 Android physical device testing

Current state: CI passes, device not tested.

- [ ] Install signed APK from latest release on Galaxy S24 Ultra
- [ ] Test SAF folder import: personal WhatsApp and Business roots
- [ ] Test with thousands of WebP, duplicates, EXIF grouping, invalid files
- [ ] Test cancel/retry and process death during import
- [ ] Verify add-to-WhatsApp flow works on physical device
- [ ] Test WhatsApp Business as separate target
- [ ] Record results in QA_MATRIX.md

### 3.3 iPhone TestFlight build

- [ ] Set up App Store Connect for StickerDock
- [ ] Configure iOS signing: distribution certificate + provisioning profile
- [ ] Create TestFlight build from current main
- [ ] Internal test: verify animated WebP preview, WhatsApp handoff, search
- [ ] **Not App Store publication** — TestFlight is the milestone

### 3.4 Video / GIF to animated sticker pipeline

HandNavigate currently handles static JPEG/PNG/WebP only.

**HandNavigate changes needed:**
- [ ] GIF adapter: download GIF sources, convert to animated WebP
- [ ] Video adapter: download MP4/WebM, trim to ≤30 frames, loop detect, encode WebP
- [ ] Frame deduplication: skip nearly-identical frames
- [ ] Quality gate: minimum 256×256, maximum 512×512, ≤100KB output

**StickerDock changes needed:**
- [ ] Intake handler: accept `animated=true` WebP from HandNavigate bridge
- [ ] Factory: support animated source → animated sticker pipeline
- [ ] Admin Sources: animated badge on animated candidates

### 3.5 Per-source analytics in Admin

- [ ] Sources overview: daily yield chart per source (Imgflip, Pinterest, Reddit, Openverse)
- [ ] Per-source: approval rate, rejection reasons, Factory usage count
- [ ] Failure visibility: `AUTH_REQUIRED` / `PARTIAL` / `VERIFIED_STORAGE` per source per day
- [ ] "Source health" indicator in the daily plan card

---

## Phase 4 — Long term (3+ months)

### 4.1 Admin access control

Currently Admin has no login gate. This is a documented deliberate decision for the
MVP. Before any wider sharing or team access:

- [ ] Define access model: owner-only? invite-only? shared team?
- [ ] Implement login: Supabase Auth (email/magic link) or simple admin token
- [ ] Audit log: who approved which candidate, who published which pack
- [ ] **Do not implement before explicit owner approval of the access model**

### 4.2 UI / UX overhaul (from October 23 prompt)

Owner requested Instagram-quality UI. Remaining items:

- [ ] U4 — Pack detail: sticker grid with labels below each tile
- [ ] U6 — Micro-animations (tab transitions, sticker preview enter/exit)
- [ ] U7 — Pull-to-refresh on Library and Discover
- [ ] U8 — Onboarding: 3-step first-run flow
- [ ] U9 — Dark mode polish
- [ ] U10 — Empty states: illustrative and action-oriented
- [ ] P7 — Android FTS5 virtual table for >10k sticker search

### 4.3 Community and public gallery

**Not for current MVP.** Requires separate decisions on:

- [ ] Identity verification for uploaders
- [ ] Content moderation pipeline (report, block, remove)
- [ ] Age protection for mature content
- [ ] Rights review for community submissions
- [ ] File scanning (malware, CSAM detection)
- [ ] Privacy review for collected metadata

These are not technical blockers — they are policy and legal decisions that the
owner must make before any community feature is built.

### 4.4 Additional image sources (backlog C4–C11)

| Source | Backlog ID | Notes |
| --- | --- | --- |
| Pexels (CC0 transformed artwork) | C4 | Requires API key; no rights to redistribute original photos as stickers without transformation |
| Current-affairs meme pipeline | C5 | News headline → Groq brief → original art; human review gate required |
| Video-to-WebP pipeline | C7 | Covered in Phase 3.4 above |
| Original commissioned art | C8 | Hinglish reactions, festivals; requires artist contracts |
| Reddit topic signals | C10 | Signal only, never clone post images |
| Pinterest mood board reference | C11 | Browsing reference only; no rehosting |

---

## Product owner priorities summary

As product owner, the order of value delivery is:

1. **Prove the loop works once** — one Imgflip image → approved → sticker → WhatsApp send
2. **Make daily operation reliable** — 7-day Railway observation, broader topics
3. **Get Pinterest/Reddit working** — Oracle A1 retry or alternative host
4. **Physical device acceptance** — Galaxy S24 Ultra real test
5. **Quality before quantity** — better sticker reactions before more volume
6. **iPhone TestFlight** — expand to iOS users
7. **Admin access control** — when team grows beyond solo owner
8. **Community features** — only after all of the above

---

## What NOT to do next

These are explicit guardrails based on session decisions:

- **Do not add proxy rotation or VPN** to bypass Pinterest/Reddit blocks
- **Do not bulk-approve candidates** without individual rights/quality review
- **Do not auto-publish** any collected image without explicit generation → review → publish flow
- **Do not change Railway workspace compute cap** — it would stop the owner's other Backline services
- **Do not deploy to DigitalOcean** at $24/mo without explicit owner budget approval
- **Do not scrape or mirror** Sticker.ly's catalog or any locked/private endpoint
- **Do not delete legacy images** without verified corrupt/unreferenced evidence
- **Do not claim 20/day guarantee** until 7-day Railway observation passes

---

## Key credentials and infrastructure summary

| Resource | Location | Notes |
| --- | --- | --- |
| StickerDock Admin | https://stickerdock.vercel.app/admin | No login gate currently |
| Railway project | https://railway.com/project/ce45c8b7-2f41-4228-9a99-987c1c5d403f | Daily cron at 10:00 IST |
| HandNavigate repo | https://github.com/dvajjala-ui/handNavigation | Current: rev `1b2c9f92` |
| StickerDock repo | https://github.com/dvajjala-ui/stickerdock | Current: main `a2807f4` |
| Oracle Free tenancy | Mumbai region | Free VCN prepared; A1 no capacity |
| Supabase | Private project | `media_intake_items` + `sticker-factory-private` bucket |
| Worker ingest token | Railway env var + GitHub Actions secret | Never committed to Git |
| Oracle SSH key | Local machine only | Never committed to Git |

---

## References

- [HANDNAVIGATE_DEPLOYMENT_HANDOFF_2026-10-28.md](HANDNAVIGATE_DEPLOYMENT_HANDOFF_2026-10-28.md) — full session record
- [OWNER_PROMPTS_AND_BACKLOG.md](OWNER_PROMPTS_AND_BACKLOG.md) — master feature backlog
- [HANDNAVIGATE_RAILWAY_2026-10-28.md](HANDNAVIGATE_RAILWAY_2026-10-28.md) — Railway budget and controls
- [HANDNAVIGATE_HOSTING_OPTIONS_2026-10-28.md](HANDNAVIGATE_HOSTING_OPTIONS_2026-10-28.md) — hosting comparison
- [ADMIN_SOURCES_AND_HOSTED_COLLECTION_2026-10-28.md](ADMIN_SOURCES_AND_HOSTED_COLLECTION_2026-10-28.md) — Sources workspace delivery
- [FACTORY_RECOVERY_AND_QUALITY_2026-10-28.md](FACTORY_RECOVERY_AND_QUALITY_2026-10-28.md) — Factory quality issues
- [ARCHITECTURE_REVIEW_2026-10-28.md](ARCHITECTURE_REVIEW_2026-10-28.md) — full bug and gap analysis
- [HANDNAVIGATE_ADMIN_2026-10-28.md](HANDNAVIGATE_ADMIN_2026-10-28.md) — production delivery evidence
- [QA_MATRIX.md](QA_MATRIX.md) — full acceptance test matrix
