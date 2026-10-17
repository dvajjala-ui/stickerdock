package app.stickerdock.duo

import android.graphics.BitmapFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatalogPreviewTest {
    @Test fun legacyPreviewShowsArtworkWithoutSavingPacks() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        val archive = File(context.cacheDir, "preview-contract.zip")
        archive.writeBytes(javaClass.getResourceAsStream("/admin-contract-pack.zip")!!.use { it.readBytes() })
        val before = archive.readBytes()
        val raw = CatalogPreview.fromArchive(archive)
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
        assertEquals(576, bitmap.width)
        assertEquals(384, bitmap.height)
        assertTrue("First sticker must contain visible artwork", bitmap.getPixel(96, 96) != 0)
        assertEquals("Unused cells stay transparent", 0, bitmap.getPixel(480, 288))
        assertArrayEquals(before, archive.readBytes())
        StickerStore(context).use { assertTrue(it.listPacks().isEmpty()) }
    }

    @Test fun rejectsTamperedStickersBeforeDisplayingThem() {
        val directory = RuntimeEnvironment.getApplication().cacheDir
        val original = File(directory, "preview-source.zip")
        original.writeBytes(javaClass.getResourceAsStream("/admin-contract-pack.zip")!!.use { it.readBytes() })
        val changed = File(directory, "preview-changed.zip")
        ZipFile(original).use { source -> ZipOutputStream(changed.outputStream()).use { target ->
            source.entries().toList().forEach { entry ->
                target.putNextEntry(ZipEntry(entry.name))
                val raw = source.getInputStream(entry).use { it.readBytes() }
                if (entry.name.endsWith(".webp")) raw[raw.lastIndex] = (raw.last().toInt() xor 1).toByte()
                target.write(raw)
                target.closeEntry()
            }
        } }
        try { CatalogPreview.fromArchive(changed); fail("Tampered preview accepted") }
        catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("checksum")) }
    }
}
