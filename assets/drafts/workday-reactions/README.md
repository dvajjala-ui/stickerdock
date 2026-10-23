# Workday Reactions — private original-art draft

Three static, transparent, photo-style reaction stickers for everyday work chats.
This is a **private review draft**. The ZIP is not in the public catalog and has
not been tested inside WhatsApp on a physical phone. The phone app can import the
archive explicitly through its ZIP import flow.

| Scene | Chat situation | Sticker caption | Search tags |
| --- | --- | --- | --- |
| Unimpressed coffee drinker | A meeting that could have been an email | Ye meeting email ho sakti thi. | meeting, work, annoyed |
| Headphone wearer celebrating | A long call finally ends | Call chhuti, jaan bachi! | call, work, happy, relief |
| Overwhelmed woman | Returning to too many messages | Main 5 min offline kya hui? | messages, work, surprise |

The three PNG sources were generated specifically for StickerDock with the
built-in image generation tool on October 23, 2026. They depict fictional
adults, were requested without logos, existing text or other people, and were
not copied from Pinterest, Sticker.ly, Reddit or a third-party media catalog.
The third source was edited once to make its eyes look more natural. The
generated source SHA-256 values are in `references.json`. The `USER_OWNED`
reference marker describes the project's original AI-assisted asset category;
it does not prove exclusivity, identity clearance or any third-party rights.

## Reproduce and inspect

From the repository root, run
`python assets/drafts/workday-reactions/build.py`. This produces
`rendered/*.webp`, `pack.zip`, `references.json`, and `preview.png` from the
retained PNGs. The builder validates the native archive and references.
`preview.png` shows each sticker at 128 px over light and dark chat colors;
the white cutout edge keeps the subjects visible on both. All three render
without source text beneath their captions. Search labels and tags are stored
per sticker in the schema-5 archive.

The remaining publication checks are a human review of the actual ZIP artwork
and captions for visual defects and humor, private Admin ZIP import/read-back,
and physical WhatsApp/Business acceptance. The generated art must not be
described as a licensed photograph of a real person.

## Generation prompt record

Built-in image generation mode: `photorealistic-natural`, transparent
background. No external image reference was supplied.

1. `meeting.png`: “Use case: photorealistic-natural. Asset type: original transparent-background WhatsApp reaction sticker source for StickerDock. One fictional adult South Asian office worker, chest-up, holding an empty plain ceramic coffee mug, giving a sharply unimpressed sideways look with one raised eyebrow as if another pointless meeting was announced. Warm candid editorial photography, realistic facial expression and hands, crisp separation around hair and mug, centered large subject that remains legible at 128 px. Transparent background with clean alpha edges. No existing words or captions, no logos, no watermark, no emoji, no desk or scenery, no other people, no exaggerated cartoon features.”
2. `relief.png`: “Use case: photorealistic-natural. Asset type: original transparent-background WhatsApp reaction sticker source for StickerDock. One fictional adult South Asian man in simple casual work clothes and plain over-ear headphones, waist-up, eyes shut, joyful relief, both hands raised slightly as if the long call just ended; spontaneous candid reaction, not posing at the camera. Natural studio-quality photography, realistic skin and hands, strong readable silhouette, centered large subject legible at 128 px. Transparent background with clean alpha edges. No words or captions, no logos, no watermark, no emoji, no desk or scenery, no other people, no cartoon exaggeration.”
3. `messages.png`: “Use case: photorealistic-natural. Asset type: original transparent-background WhatsApp reaction sticker source for StickerDock. One fictional adult South Asian woman in a simple teal casual top, chest-up, looking straight ahead with wide eyes and a comic but believable 'I have too many messages' stunned expression, one hand lightly on her forehead, the other holding a plain closed notebook with no writing. Natural candid editorial photography, realistic face and hands, clearly different person and pose from an unimpressed coffee drinker or a celebrating man. Large centered expressive silhouette legible at 128 px; clean transparent alpha edges. No words, no captions, no logos, no watermark, no emoji, no phone or screen, no scenery or other people, no cartoon exaggeration.” A targeted edit refined the eyes without changing the scene or subject.
