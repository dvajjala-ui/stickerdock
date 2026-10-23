package app.stickerdock.duo

import java.util.Locale

internal object StickerMetadata {
    fun cleanLabel(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)

    fun cleanTags(value: String): String = value
        .split(Regex("[,;\\n]"))
        .map { cleanLabel(it).take(24) }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(12)
        .joinToString(", ")

    /** A situation can span a caption, tags and a pack name without matching punctuation. */
    fun searchTerms(value: String): List<String> = cleanLabel(value)
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(8)
}
