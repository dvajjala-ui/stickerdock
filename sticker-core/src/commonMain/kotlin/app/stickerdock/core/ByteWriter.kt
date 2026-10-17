package app.stickerdock.core

/** Small bounded byte writer with no JVM I/O dependency. An oversized final
 * frame may be inspected before the mux rejects it, so allow up to 1 MB here. */
internal class ByteWriter {
    private var bytes = ByteArray(1024)
    private var used = 0

    fun size() = used
    fun toByteArray() = bytes.copyOf(used)

    fun write(value: Int) {
        reserve(1)
        bytes[used++] = value.toByte()
    }

    fun write(source: ByteArray, offset: Int = 0, length: Int = source.size - offset) {
        require(offset >= 0 && length >= 0 && offset.toLong() + length <= source.size)
        reserve(length)
        source.copyInto(bytes, used, offset, offset + length)
        used += length
    }

    private fun reserve(length: Int) {
        val needed = used.toLong() + length
        require(needed <= 1_000_064) { "Animation is too detailed. Shorten the clip or lower frame rate." }
        if (needed > bytes.size) bytes = bytes.copyOf(maxOf(needed.toInt(), minOf(bytes.size * 2, 1_000_064)))
    }
}
