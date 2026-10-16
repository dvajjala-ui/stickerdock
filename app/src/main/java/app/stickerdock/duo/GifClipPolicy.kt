package app.stickerdock.duo

/** Bound GIF canvas × frame count before Movie can allocate decoded frames. */
object GifClipPolicy {
    fun validate(bytes: ByteArray) {
        require(bytes.size in 14..20_000_000 && String(bytes, 0, 6, Charsets.US_ASCII) in listOf("GIF87a", "GIF89a")) { "Invalid GIF" }
        fun unsigned(index: Int) = bytes[index].toInt() and 255
        fun short(index: Int) = unsigned(index) or (unsigned(index + 1) shl 8)
        val width = short(6); val height = short(8)
        require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_000_000) { "Choose a GIF up to 4 megapixels" }
        var offset = 13
        if (unsigned(10) and 128 != 0) offset += 3 * (1 shl ((unsigned(10) and 7) + 1))
        var frames = 0
        var ended = false
        fun skipBlocks() {
            while (true) {
                require(offset < bytes.size) { "Incomplete GIF" }
                val size = unsigned(offset++)
                if (size == 0) return
                require(offset.toLong() + size <= bytes.size) { "Incomplete GIF data" }
                offset += size
            }
        }
        while (offset < bytes.size) {
            when (unsigned(offset++)) {
                0x3b -> { ended = true; break }
                0x21 -> { require(offset < bytes.size); offset++; skipBlocks() }
                0x2c -> {
                    require(offset + 9 <= bytes.size) { "Incomplete GIF frame" }
                    val x = short(offset); val y = short(offset + 2)
                    val frameWidth = short(offset + 4); val frameHeight = short(offset + 6)
                    require(frameWidth > 0 && frameHeight > 0 && x + frameWidth <= width && y + frameHeight <= height) { "Invalid GIF frame bounds" }
                    frames++
                    require(frames <= 300 && width.toLong() * height * frames <= 16_000_000) { "This GIF is too complex. Shorten it or choose a smaller video." }
                    val flags = unsigned(offset + 8); offset += 9
                    if (flags and 128 != 0) offset += 3 * (1 shl ((flags and 7) + 1))
                    require(offset < bytes.size && unsigned(offset) in 2..8) { "Invalid GIF compression" }
                    offset++; skipBlocks()
                }
                else -> error("Invalid GIF block")
            }
        }
        require(ended && frames >= 2) { "Choose a complete moving GIF" }
    }
}
