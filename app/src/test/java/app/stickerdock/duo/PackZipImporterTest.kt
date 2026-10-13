package app.stickerdock.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PackZipImporterTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
        File(context.filesDir, "stickers").deleteRecursively()
        File(context.filesDir, "trays").deleteRecursively()
        store = StickerStore(context)
        files = StickerFiles(context, store)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DB_NAME)
        File(context.filesDir, "stickers").deleteRecursively()
        File(context.filesDir, "trays").deleteRecursively()
    }

    @Test
    fun importsStickerMakerWastickersTitleAndIgnoresTrayPng() {
        val archive = File(context.cacheDir, "sample.wastickers")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.add("title.txt", "Vacation Reactions\n".toByteArray())
            zip.add("author.txt", "StickerDock Tester\n".toByteArray())
            zip.add("1700000000.png", png(96, 96, Color.YELLOW))
            zip.add("1700000001.png", png(512, 512, Color.RED))
            zip.add("1700000002.png", png(512, 512, Color.GREEN))
            zip.add("1700000003.png", png(512, 512, Color.BLUE))
        }

        val summary = PackZipImporter(context, store, files).import(Uri.fromFile(archive), "Fallback")

        assertEquals(1, summary.packs)
        assertEquals(3, summary.imported)
        assertEquals(0, summary.duplicates)
        assertEquals(0, summary.skipped)
        val pack = store.listPacks().single()
        assertEquals("Vacation Reactions", pack.name)
        assertEquals(StickerKind.STATIC, pack.kind)
        assertEquals(3, store.listStickers(pack.id).size)
        assertTrue(files.trayFile(pack.id).isFile)
    }

    @Test
    fun rejectsTraversalEntryBeforeCreatingPack() {
        val archive = File(context.cacheDir, "traversal.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.add("../escape.png", png(32, 32, Color.RED))
        }

        assertRejected(archive, "Unsafe archive path")
        assertTrue(store.listPacks().isEmpty())
    }

    @Test
    fun rejectsHighlyCompressedOversizedImageBeforeCreatingPack() {
        val archive = File(context.cacheDir, "oversized.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.add("oversized.png", ByteArray(1_000_001))
        }

        assertRejected(archive, "File too large")
        assertTrue(store.listPacks().isEmpty())
    }

    @Test
    fun rejectsArchiveWithTooManyEntriesBeforeCreatingPack() {
        val archive = File(context.cacheDir, "too-many.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            repeat(1_201) { index ->
                zip.add("metadata/$index.txt", ByteArray(0))
            }
        }

        assertRejected(archive, "Too many ZIP entries")
        assertTrue(store.listPacks().isEmpty())
    }

    private fun assertRejected(archive: File, expectedMessage: String) {
        try {
            PackZipImporter(context, store, files).import(Uri.fromFile(archive), "Fallback")
            fail("Expected archive import to be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(
                "Expected '$expectedMessage' but was '${error.message}'",
                error.message?.contains(expectedMessage) == true
            )
        }
    }

    private fun png(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { out ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun ZipOutputStream.add(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    companion object {
        private const val DB_NAME = "stickerdock.db"
    }
}
