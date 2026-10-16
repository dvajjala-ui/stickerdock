package app.stickerdock.core

/** WebP RIFF validation; GIF or video conversion is not performed here. */
object WebpMetadata {
    data class Details(val animated: Boolean, val width: Int, val height: Int, val durationMs: Int, val frames: Int)
    fun inspect(bytes: ByteArray): Details? {
        if (bytes.size < 30 || ascii(bytes, 0, 4) != "RIFF" || ascii(bytes, 8, 4) != "WEBP") return null
        if (uint32(bytes, 4) != bytes.size.toLong() - 8) return null
        var offset = 12
        var width = 0
        var height = 0
        var animated = false
        var animationHeader = false
        var frames = 0
        var duration = 0L
        while (offset + 8 <= bytes.size) {
            val length = uint32(bytes, offset + 4)
            if (length > Int.MAX_VALUE || offset.toLong() + 8 + length > bytes.size) return null
            val start = offset + 8
            when (ascii(bytes, offset, 4)) {
                "VP8X" -> {
                    if (length < 10) return null
                    animated = (bytes[start].toInt() and 0x02) != 0
                    width = 1 + uint24(bytes, start + 4)
                    height = 1 + uint24(bytes, start + 7)
                }
                "ANIM" -> animationHeader = length >= 6
                "ANMF" -> {
                    if (length < 16) return null
                    frames++
                    val frameMs = uint24(bytes, start + 12)
                    if (frameMs < 8) return null
                    duration += frameMs
                    if (duration > 10_000) return null
                }
            }
            offset += 8 + length.toInt() + (length.toInt() and 1)
        }
        if (offset != bytes.size || width != 512 || height != 512) return null
        if (animated && (!animationHeader || frames == 0)) return null
        if (!animated && frames != 0) return null
        return Details(animated, width, height, duration.toInt(), frames)
    }
    fun isAnimatedHeader(bytes: ByteArray): Boolean =
        isWebpHeader(bytes) && bytes.size >= 21 && ascii(bytes, 12, 4) == "VP8X" &&
            (bytes[20].toInt() and 0x02) != 0

    fun isWebpHeader(bytes: ByteArray): Boolean =
        bytes.size >= 12 && ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WEBP"

    private fun ascii(bytes: ByteArray, start: Int, length: Int) = bytes.decodeToString(start, start + length)
    private fun uint24(bytes: ByteArray, start: Int) =
        (bytes[start].toInt() and 255) or ((bytes[start + 1].toInt() and 255) shl 8) or
        ((bytes[start + 2].toInt() and 255) shl 16)
    private fun uint32(bytes: ByteArray, start: Int): Long =
        (bytes[start].toLong() and 255) or ((bytes[start + 1].toLong() and 255) shl 8) or
        ((bytes[start + 2].toLong() and 255) shl 16) or ((bytes[start + 3].toLong() and 255) shl 24)
}
