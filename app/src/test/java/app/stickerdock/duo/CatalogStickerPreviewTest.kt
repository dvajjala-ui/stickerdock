package app.stickerdock.duo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogStickerPreviewTest {
    @Test fun reviewedMotionPackPreviewsWithoutSavingOrChangingMedia() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        val bytes = requireNotNull(javaClass.getResourceAsStream("/moving-animal-moods.zip")).use { it.readBytes() }
        val archive = File(context.cacheDir, "moving-animal-moods.zip").apply { writeBytes(bytes) }

        val items = CatalogStickerPreview.fromArchive(archive)

        assertEquals(3, items.size)
        assertEquals(listOf("Meri bhi sun lo!", "Busy hoon, baad mein.", "Khaana kidhar hai?"), items.map { it.label })
        assertTrue(items.all { it.kind == StickerKind.ANIMATED })
        assertTrue(items.all { (WebpSticker.inspect(it.bytes)?.frames ?: 0) >= 2 })
        assertTrue(items.all { it.bytes.size <= 500_000 })
        assertArrayEquals(bytes, archive.readBytes())
        StickerStore(context).use { assertTrue(it.listPacks().isEmpty()) }
    }
}
