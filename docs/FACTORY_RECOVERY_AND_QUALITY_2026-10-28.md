# Factory recovery and quality follow-up — October 28, 2026

## Production operations verified

PR #26 is merged as `04a687e`; the new recovery code was exercised from
`2b7031a` through bounded invocations of the existing production workflow:

| Run | Result |
| --- | --- |
| [40642570022](https://github.com/dvajjala-ui/stickerdock/actions/runs/40642570022) | Reconciled October 22's three batch identities; marked October 20–21 blocked because their stored state/media was incomplete. No new model request. |
| [40642772806](https://github.com/dvajjala-ui/stickerdock/actions/runs/40642772806) | Reconciled October 23–25 delivery, preserving existing review decisions. No new model request. |
| [40642967199](https://github.com/dvajjala-ui/stickerdock/actions/runs/40642967199) | Reconciled October 26–27 delivery. No new model request. |
| [40643505304](https://github.com/dvajjala-ui/stickerdock/actions/runs/40643505304) | Normal current-day generation resumed: five stickers saved, private draft delivered, daily target remains 40. |

Returned batch IDs include existing packs; they are not counts of newly created
drafts. Hosted read-back confirmed the already-published `Big Mood Animals` pack
retained its name, published state and three-sticker count. October 20–21 remain
blocked for explicit operator recovery; their counts were not reset and missing
artwork was not regenerated.

Independent private ZIP downloads matched the database digests and passed the
native pack validator:

| Draft | Stickers | SHA-256 | Visual review |
| --- | --- | --- | --- |
| `853354c9-b5a5-574d-824c-50cd4594ab68` | 5 | `14e120d9ec92999e3b51cd68c017d6180af7c2e4ca30dfb13a62ceef99ce7a2f` | Crowded/overlapping text and weak reaction selection. Rejected. |
| `eb33cf5b-290e-56c9-bac2-41f86ccf8f15` | 5 | `55a49435e6f130fc647afcad5d0b6f6ec772c7a4a3783920b32ab70e866e04e9` | Five captions over the same rocket photo. Rejected. |
| `ef48efb1-6b2e-5fbe-bb5d-c043e57e2493` | 5 | `c3d1148720a1ba822472daf537148db127bd63810a682cd36bcafbaa4a8902a0` | One new caption visibly overlaps pre-existing meme text. Rejected. |

Each review checked the current draft status, version and expected archive
digest before changing status. Archives remain in private storage. No pack was
published by this follow-up. Images and signed download URLs remain outside Git.

## Source-text repair

The old strip detector skipped any image carrying an alpha channel, including
fully opaque normalized PNGs. It now composites alpha over white before checking
the actual pixels. That heuristic alone cannot reliably recognize outlined
lettering, so production photo captions also require local OCR screening.

[Tesseract](https://github.com/tesseract-ocr/tesseract) is an Apache-2.0 local OCR
engine. Its documented [TSV output](https://github.com/tesseract-ocr/tessdoc/blob/main/Command-Line-Usage.md)
provides word confidence. StickerDock uses English recognition for Roman-script
captions, holds sources with confident text out of automatic captioning, and
keeps digest-keyed boolean decisions in a bounded cache. Recognized text is not
stored or sent to an external OCR service. Each subprocess has a pixel/file
bound and timeout; missing/broken OCR cannot clear a source.

The gate runs before a caption request or budget reservation. Saved pending
photo batches are screened before rendering as well; a blocked pending batch
retains its existing ideas/state for explicit operator review. CI installs the
engine and English data and runs real RGB/RGBA/outlined-caption/clean-image
acceptance. Production installs the same engine before processing a due batch.

The manual `source-text-audit.yml` workflow can check the existing private
Factory snapshot without a Groq credential, generation call or remote write.
It reports only counts and hashes of withheld sources. The
[production audit](https://github.com/dvajjala-ui/stickerdock/actions/runs/40647029975)
passed from merge `fa70eff`: 74 saved photos screened, 38 flagged and 36 without
confident text detected, zero generation calls and zero storage writes. This
snapshot audit includes saved photos independently of the later rights/purpose
and reaction-suitability filters. An OCR-clear image is not automatically eligible
for generation or publication. PR #28's [Web/API run](https://github.com/dvajjala-ui/stickerdock/actions/runs/40645187893)
passed all 315 Python tests with no skips, real OCR and database checks; its
[Android run](https://github.com/dvajjala-ui/stickerdock/actions/runs/40645188050) passed too.

This is a conservative quality screen, not complete text recognition. Tiny text,
stylized lettering, other scripts, animated frames, humor quality and repeated
scenes still need review. Detected text can be legitimate signage; those sources
are held for review rather than erased. Approved-source-to-generation admission
from HandNavigate remains separate.

## Native follow-up

[PR #27](https://github.com/dvajjala-ui/stickerdock/pull/27) merged as `5130e24`
after [native CI](https://github.com/dvajjala-ui/stickerdock/actions/runs/40643986313)
passed. It wraps Library filters so Favorites remains reachable with large text,
limits the search placeholder to one line, removes duplicate suggestion rows,
and persists only submitted searches with a Clear action. The prior signed
[run](https://github.com/dvajjala-ui/stickerdock/actions/runs/40641756805) failed
at Favorites under 200% font scaling. The new
[signed run](https://github.com/dvajjala-ui/stickerdock/actions/runs/40644511402)
passed and published Android 0.11.6/build 21 from `5130e24`. An independent public
APK download was 3,952,171 bytes with SHA-256
`c458d979c78947375ac450a632bf6fe8926ffbb48134f4d10f046dc331501e41`;
the release manifest, checksum file and passing exact-APK report agree. The
captured 200% text screen shows Favorites and all filters remain visible.
Physical WhatsApp/Business and signed iPhone acceptance remain open.
