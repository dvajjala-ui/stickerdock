package app.stickerdock.duo

import org.json.JSONObject

/**
 * Reads WhatsApp's optional sticker-pack metadata from an EXIF RIFF chunk.
 * It never mutates input, uses no filesystem access, and ignores malformed metadata.
 * Sticker.ly/WhatsApp do not guarantee every cached sticker has this information.
 */
object StickerPackMetadata {
    data class Details(val packName: String?, val publisher: String?, val packId: String?)

    fun read(webp: ByteArray): Details? {
        if (webp.size < 20 || ascii(webp, 0, 4) != "RIFF" || ascii(webp, 8, 4) != "WEBP") return null
        var pos = 12
        while (pos + 8 <= webp.size) {
            val size = uint32(webp, pos + 4)
            if (size < 0 || size > 65536L || size + pos + 8 > webp.size) {
                if (size > 65536 && ascii(webp, pos, 4) != "EXIF") {
                    val advance = size + 8 + (size and 1)
                    if (pos + advance > webp.size) return null
                    pos += advance.toInt()
                    continue
                }
                return null
            }
            if (ascii(webp, pos, 4) == "EXIF") {
                val raw = String(webp, pos + 8, size.toInt(), Charsets.UTF_8)
                val start = raw.indexOf('{')
                val end = raw.lastIndexOf('}')
                if (start < 0 || end <= start || end - start > 8192) return null
                return try {
                    val data = JSONObject(raw.substring(start, end + 1))
                    val name = data.optString("sticker-pack-name", "").cleanMetadata()
                    val publisher = data.optString("sticker-pack-publisher", "").cleanMetadata()
                    val id = data.optString("sticker-pack-id", "").cleanMetadata()
                    if (name.isEmpty() && id.isEmpty()) null else Details(name.ifEmpty { null }, publisher.ifEmpty { null }, id.ifEmpty { null })
                } catch (_: Exception) { null }
            }
            pos += 8 + size.toInt() + (size.toInt() and 1)
        }
        return null
    }

    private fun String.cleanMetadata() = trim().filter { it != '/' && it != '\\' && !it.isISOControl() }.take(60)
    private fun ascii(bytes: ByteArray, offset: Int, length: Int) = String(bytes, offset, length, Charsets.US_ASCII)
    private fun uint32(bytes: ByteArray, i: Int): Long = (bytes[i].toLong() and 255) or
        ((bytes[i + 1].toLong() and 255) shl 8) or
        ((bytes[i + 2].toLong() and 255) shl 16) or
        ((bytes[i + 3].toLong() and 255) shl 24)
}
