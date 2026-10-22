# Reaction-media source curation — 22 October 2026

The production Factory queue includes the five additional sources below. Their
Wikimedia Commons file pages and MediaWiki `imageinfo.extmetadata.LicenseShortName`
reported CC0 when checked on 22 October. Source URLs, original byte sizes and
credits were checked before the queue change. This establishes a reusable media
candidate, not sticker quality or a right to reuse unrelated Pinterest/Sticker.ly
content. Finished packs still require visual and rights review before publication.

| Expression candidate | Commons source | Reported original | Credit | File HEAD |
| --- | --- | --- | --- | --- |
| Yawning dog | [Yawning Dog.jpg](https://commons.wikimedia.org/wiki/File:Yawning_Dog.jpg) | 103,063 bytes, 600 × 590, CC0 | Teneab | HTTP 200 |
| Angry cat | [Angry cat by Juliet van Ree.jpg](https://commons.wikimedia.org/wiki/File:Angry_cat_by_Juliet_van_Ree.jpg) | 9,946,265 bytes, 3024 × 4032, CC0 | Jvanree | HTTP 200 |
| White cat | [My cat is white.jpg](https://commons.wikimedia.org/wiki/File:My_cat_is_white.jpg) | 76,221 bytes, 1200 × 1600, CC0 | Dewi Rahmawaati | HTTP 429 during this check |
| Smiling dog | [Smiling dog at Pype Hayes park.jpg](https://commons.wikimedia.org/wiki/File:Smiling_dog_at_Pype_Hayes_park.jpg) | 837,439 bytes, 1080 × 1920, CC0 | Benocalla2 | HTTP 429 during this check |
| Sleepy cat | [DomesticCatLyingOnBack.jpg](https://commons.wikimedia.org/wiki/File:DomesticCatLyingOnBack.jpg) | 2,799,604 bytes, 3547 × 3024, CC0 | Patheticandugly | HTTP 429 during this check |

The three HTTP 429 responses came after two successful HEAD requests. The
existing bounded media worker must fetch and validate them on a later cycle;
they are not counted as imported media yet. Do not bypass the provider's
cooldown or claim visual acceptance from metadata alone. Generated captions
must fit each image as a chat reaction; the October 22 astronomy-based draft
remains private despite valid rights metadata.
