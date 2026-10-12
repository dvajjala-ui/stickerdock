package app.stickerdock.duo

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class StickerPackMetadataTest {
    private fun u32(value: Int) = byteArrayOf(
        value.toByte(), (value ushr 8).toByte(),
        (value ushr 16).toByte(), (value ushr 24).toByte()
    )

    private fun withExif(json: String): ByteArray {
        val exif = byteArrayOf(0x49, 0x49, 0x2a, 0x00) + json.toByteArray(Charsets.UTF_8)
        val chunk = ByteArrayOutputStream().apply {
            write("EXIF".toByteArray())
            write(u32(exif.size))
            write(exif)
            if (exif.size % 2 != 0) write(0)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            write(u32(4 + chunk.size))
            write("WEBP".toByteArray())
            write(chunk)
        }.toByteArray()
    }

    @Test fun extractsOriginalPackName() {
        val raw = withExif("""{"sticker-pack-name":"Cats Forever","sticker-pack-publisher":"MySelf","sticker-pack-id":"abc"}""")
        assertEquals("Cats Forever", StickerPackMetadata.read(raw)?.packName)
        assertEquals("MySelf", StickerPackMetadata.read(raw)?.publisher)
    }

    @Test fun normalizesUnsafePackTitle() {
        val raw = withExif("""{"sticker-pack-name":"cats/..\\test","sticker-pack-id":"abc"}""")
        val name = StickerPackMetadata.read(raw)?.packName
        assertNotNull(name)
        assertFalse(name!!.contains("/"))
        assertFalse(name.contains("\\"))
    }

    @Test fun malformedExifIsIgnored() {
        assertNull(StickerPackMetadata.read(withExif("garbled")))
        assertNull(StickerPackMetadata.read("not WebP".toByteArray()))
    }
}
