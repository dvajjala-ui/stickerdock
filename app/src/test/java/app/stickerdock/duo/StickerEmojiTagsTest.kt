package app.stickerdock.duo

import org.junit.Assert.assertEquals
import org.junit.Test

class StickerEmojiTagsTest {
    @Test fun explicitMetadataChoosesUpToThreeRelevantWhatsAppEmojis() {
        assertEquals(listOf("😂", "👋"), StickerEmojiTags.from("Funny hello", "work"))
        assertEquals(listOf("😢", "😠", "🎉"), StickerEmojiTags.from("Sad angry party", ""))
        assertEquals(listOf("🙂"), StickerEmojiTags.from("Monday Mood", "office"))
        assertEquals(listOf("🙂"), StickerEmojiTags.from("unhappy", ""))
    }
}
