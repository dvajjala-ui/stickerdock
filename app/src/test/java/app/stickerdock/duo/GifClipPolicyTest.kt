package app.stickerdock.duo

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class GifClipPolicyTest {
    @Test fun boundedMovingGifRetainsItsBytes() {
        val bytes = gif(320, 200, 28)
        val before = bytes.clone()
        GifClipPolicy.validate(bytes)
        assertArrayEquals(before, bytes)
    }
    @Test fun staticTruncatedAndInvalidFrameBoundsFailBeforeDecode() {
        assertThrows(IllegalArgumentException::class.java) { GifClipPolicy.validate(gif(32, 32, 1)) }
        val truncated = gif(32, 32, 2).dropLast(4).toByteArray()
        assertThrows(IllegalArgumentException::class.java) { GifClipPolicy.validate(truncated) }
        val bad = gif(32, 32, 2).also { it[18] = 64 }
        assertThrows(IllegalArgumentException::class.java) { GifClipPolicy.validate(bad) }
    }
    @Test fun compressedCanvasTimesFramesCannotExceedDecodeBudget() {
        GifClipPolicy.validate(gif(512, 512, 60))
        assertThrows(IllegalArgumentException::class.java) { GifClipPolicy.validate(gif(512, 512, 62)) }
        assertThrows(IllegalArgumentException::class.java) { GifClipPolicy.validate(gif(4096, 4096, 2)) }
    }
    // Container structure only: tests the pre-decode resource gate, not LZW.
    private fun gif(width: Int, height: Int, frames: Int): ByteArray = ByteArrayOutputStream().apply {
        fun short(value: Int) { write(value and 255); write(value ushr 8) }
        write("GIF89a".toByteArray()); short(width); short(height); write(byteArrayOf(0, 0, 0))
        repeat(frames) {
            write(0x2c); short(0); short(0); short(width); short(height); write(0)
            write(byteArrayOf(2, 2, 0x44, 1, 0))
        }
        write(0x3b)
    }.toByteArray()
}
