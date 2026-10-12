package app.stickerdock.duo

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class WebpStickerTest {
    private fun u24(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 255).toByte()
        target[offset + 1] = (value shr 8 and 255).toByte()
        target[offset + 2] = (value shr 16 and 255).toByte()
    }
    private fun u32(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { i -> target[offset + i] = (value shr (8 * i) and 255).toByte() }
    }
    private fun chunk(name: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(name.toByteArray(Charsets.US_ASCII))
        val size = ByteArray(4)
        u32(size, 0, data.size)
        out.write(size)
        out.write(data)
        if (data.size % 2 != 0) out.write(0)
        return out.toByteArray()
    }
    private fun animation(durationMs: Int = 200, width: Int = 512): ByteArray {
        val vp8x = ByteArray(10).also {
            it[0] = 0x02
            u24(it, 4, width - 1)
            u24(it, 7, 511)
        }
        val frame = ByteArray(16).also { u24(it, 12, durationMs) }
        val body = ByteArrayOutputStream().apply {
            write("WEBP".toByteArray())
            write(chunk("VP8X", vp8x))
            write(chunk("ANIM", ByteArray(6)))
            write(chunk("ANMF", frame))
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            val size = ByteArray(4)
            u32(size, 0, body.size)
            write(size)
            write(body)
        }.toByteArray()
    }

    @Test fun validatesAnimationHeader() {
        val details = WebpSticker.inspect(animation())!!
        assertTrue(details.animated)
        assertEquals(512, details.width)
        assertEquals(512, details.height)
        assertEquals(200, details.durationMs)
        assertEquals(1, details.frames)
    }
    @Test fun rejectsTooShortFrames() {
        assertNull(WebpSticker.inspect(animation(durationMs = 7)))
    }
    @Test fun rejectsAnimationsLongerThanTenSeconds() {
        assertNull(WebpSticker.inspect(animation(durationMs = 10_001)))
    }
    @Test fun rejectsWrongDimensions() {
        assertNull(WebpSticker.inspect(animation(width = 320)))
    }
    @Test fun rejectsInvalidRiffLength() {
        assertNull(WebpSticker.inspect(animation().dropLast(1).toByteArray()))
    }
    @Test fun rejectsNonWebp() {
        assertNull(WebpSticker.inspect("not a WebP".toByteArray()))
    }
}
