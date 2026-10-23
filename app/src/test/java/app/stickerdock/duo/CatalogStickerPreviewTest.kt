package app.stickerdock.duo

import android.net.Uri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    @Test fun reviewedMotionPackSavesExactMediaAndCaptionsOnlyOnExplicitRestore() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        val archive = File(context.cacheDir, "moving-animal-moods-import.zip")
        requireNotNull(javaClass.getResourceAsStream("/moving-animal-moods.zip")).use {
            archive.writeBytes(it.readBytes())
        }
        val preview = CatalogStickerPreview.fromArchive(archive)
        StickerStore(context).use { store ->
            val existing = store.createPack("My private pack")
            assertEquals(listOf(existing), store.listPacks())

            val files = StickerFiles(context, store)
            val summary = StickerBackup(context, store, files).restore(Uri.fromFile(archive))
            assertEquals(1, summary.packs)
            assertEquals(3, summary.stickers)
            assertEquals(2, store.listPacks().size)
            assertTrue(store.getPack(existing.id) != null)
            val imported = store.getPack(summary.packIds.single())!!
            assertEquals(StickerKind.ANIMATED, imported.kind)
            val saved = store.listStickers(imported.id)
            assertEquals(preview.map { it.label }.toSet(), saved.map { it.label }.toSet())
            assertEquals(preview.map { it.sha256 }.toSet(), saved.map { it.sha256 }.toSet())
            val originals = preview.associateBy { it.sha256 }
            saved.forEach { item ->
                assertArrayEquals(originals[item.sha256]!!.bytes,
                    files.stickerFile(imported.id, item.fileName).readBytes())
            }
        }
    }
}
