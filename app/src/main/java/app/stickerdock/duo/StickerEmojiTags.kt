package app.stickerdock.duo

import java.util.Locale

/** WhatsApp accepts one to three emoji per sticker. Only explicit metadata words drive tags. */
internal object StickerEmojiTags {
    private val categories = listOf(
        setOf("love", "heart", "romance", "romantic", "kiss") to "❤️",
        setOf("happy", "joy", "smile", "laugh", "lol", "funny", "haha") to "😂",
        setOf("sad", "cry", "tears", "heartbroken", "miss") to "😢",
        setOf("angry", "mad", "rage", "furious", "annoyed") to "😠",
        setOf("hi", "hello", "hey", "bye", "welcome", "greet") to "👋",
        setOf("party", "celebrate", "congrats", "birthday", "cheers") to "🎉"
    )

    fun from(label: String, tags: String): List<String> {
        val words = "$label $tags".lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}]+"))
            .filter { it.isNotBlank() }
            .toSet()
        val matched = categories.filter { (terms, _) -> terms.any { it in words } }
            .map { it.second }.take(3)
        return matched.ifEmpty { listOf("🙂") }
    }
}
