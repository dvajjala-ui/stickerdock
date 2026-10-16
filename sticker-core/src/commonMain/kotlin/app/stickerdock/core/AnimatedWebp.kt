package app.stickerdock.core

/** Full-canvas animated WebP muxing per Google's RIFF/ANIM/ANMF specification.
 * Frame compression is supplied by the platform's codec adapter.
 */
object AnimatedWebp {
    /** Normalize platform encoders that omit VP8X for opaque static images. */
    internal fun staticContainer(bytes: ByteArray): ByteArray {
        val image = imageChunks(bytes) // validates actual compressed dimensions
        val tag = image.decodeToString(0, 4)
        val alpha = tag == "ALPH" || (tag == "VP8L" && image.size >= 13 && (image[12].toInt() and 0x10) != 0)
        val body = ByteWriter().apply {
            write("WEBP".encodeToByteArray())
            chunk("VP8X", byteArrayOf(if (alpha) 0x10 else 0, 0, 0, 0, 0xff.toByte(), 1, 0, 0xff.toByte(), 1, 0))
            write(image)
        }.toByteArray()
        return ByteWriter().apply {
            write("RIFF".encodeToByteArray()); uint(body.size, 4); write(body)
        }.toByteArray()
    }
    fun encode(frames: List<ByteArray>, frameMs: Int, maximumBytes: Int = 500_000): ByteArray {
        require(frames.size in 2..60 && frameMs in 8..1000 && frames.size * frameMs <= 10_000) { "Choose a shorter moving clip" }
        require(maximumBytes in 1000..500_000)
        val payloads = frames.map(::imageChunks)
        require(payloads.drop(1).any { !it.contentEquals(payloads.first()) }) { "No visible motion in this section. Choose another section or a photo." }
        val body = ByteWriter()
        body.write("WEBP".encodeToByteArray())
        body.chunk("VP8X", ByteWriter().apply {
            write(byteArrayOf(0x12, 0, 0, 0)) // alpha + animation
            uint(511, 3); uint(511, 3)
        }.toByteArray())
        body.chunk("ANIM", ByteArray(6)) // transparent background, infinite loop
        payloads.forEach { image ->
            body.chunk("ANMF", ByteWriter().apply {
                uint(0, 3); uint(0, 3); uint(511, 3); uint(511, 3)
                uint(frameMs, 3); write(2) // replace full canvas, do not blend
                write(image)
            }.toByteArray())
            require(body.size() + 8 <= maximumBytes) { "Animation is too detailed. Shorten the clip or lower frame rate." }
        }
        return ByteWriter().apply {
            write("RIFF".encodeToByteArray()); uint(body.size(), 4); write(body.toByteArray())
        }.toByteArray().also { require(WebpMetadata.inspect(it)?.frames == frames.size) { "Could not validate animation" } }
    }

    private fun imageChunks(bytes: ByteArray): ByteArray {
        require(bytes.size in 20..500_000 && WebpMetadata.isWebpHeader(bytes) && read(bytes, 4) == bytes.size - 8) { "Invalid frame image" }
        var offset = 12
        var images = 0
        var alpha = false
        val result = ByteWriter()
        while (offset + 8 <= bytes.size) {
            val tag = bytes.decodeToString(offset, offset + 4)
            val length = read(bytes, offset + 4)
            require(length >= 0 && offset.toLong() + 8 + length + (length and 1) <= bytes.size) { "Invalid frame chunk" }
            when (tag) {
                "ALPH" -> { require(!alpha && images == 0); alpha = true; result.write(bytes, offset, 8 + length + (length and 1)) }
                "VP8 ", "VP8L" -> {
                    require(images++ == 0 && length >= 5)
                    val start = offset + 8
                    val dimensions = if (tag == "VP8L") {
                        require(bytes[start].toInt() and 255 == 0x2f)
                        val bits = read(bytes, start + 1)
                        Pair(1 + (bits and 0x3fff), 1 + ((bits ushr 14) and 0x3fff))
                    } else {
                        require(length >= 10 && bytes.copyOfRange(start + 3, start + 6).contentEquals(byteArrayOf(0x9d.toByte(), 1, 0x2a)))
                        fun dimension(at: Int) = ((bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)) and 0x3fff
                        Pair(dimension(start + 6), dimension(start + 8))
                    }
                    require(dimensions == Pair(512, 512)) { "Frames must be 512px square" }
                    result.write(bytes, offset, 8 + length + (length and 1))
                }
                "VP8X" -> {
                    require(length == 10 && (bytes[offset + 8].toInt() and 2) == 0) { "Frame must be static" }
                }
                "ANIM", "ANMF" -> error("Frame must be static")
                // Ignore EXIF/XMP/ICCP: output carries no source metadata.
            }
            offset += 8 + length + (length and 1)
        }
        require(offset == bytes.size && images == 1) { "Incomplete frame" }
        return result.toByteArray()
    }

    private fun read(bytes: ByteArray, offset: Int): Int = (0..3).fold(0) { result, i -> result or ((bytes[offset + i].toInt() and 255) shl (8 * i)) }
    private fun ByteWriter.uint(value: Int, count: Int) { repeat(count) { write((value ushr (8 * it)) and 255) } }
    private fun ByteWriter.chunk(tag: String, payload: ByteArray) {
        write(tag.encodeToByteArray()); uint(payload.size, 4); write(payload)
        if (payload.size and 1 != 0) write(0)
    }
}
