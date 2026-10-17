package app.stickerdock.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class StickerBackupRoundTripTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles
    private lateinit var backup: StickerBackup

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
        File(context.filesDir, "stickers").deleteRecursively()
        File(context.filesDir, "trays").deleteRecursively()
        store = StickerStore(context)
        files = StickerFiles(context, store)
        backup = StickerBackup(context, store, files)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase(DB_NAME)
        File(context.filesDir, "stickers").deleteRecursively()
        File(context.filesDir, "trays").deleteRecursively()
    }

    @Test fun staticBackupRestorePreservesExactNormalizedBytesAndMetadata() {
        val originalPack = store.createPack("Exact restore", category = PackCategory.REACTIONS)
        assertEquals(ImportResult.Added, files.importBytes(originalPack.id, png(),
            label = "Surprised", tags = "wow, reaction"))
        val originalItem = store.listStickers(originalPack.id).single()
        val originalBytes = files.stickerFile(originalPack.id, originalItem.fileName).readBytes()
        val output = ByteArrayOutputStream()
        backup.export(output)
        val archive = File(context.cacheDir, "round-trip.zip").apply { writeBytes(output.toByteArray()) }

        files.deletePack(originalPack.id)
        val summary = backup.restore(Uri.fromFile(archive))

        assertEquals(1, summary.packs)
        assertEquals(1, summary.stickers)
        assertEquals(0, summary.rejected)
        val restoredPack = store.listPacks().single()
        assertEquals(listOf(restoredPack.id), summary.packIds)
        val restored = store.listStickers(restoredPack.id).single()
        assertEquals(PackCategory.REACTIONS, restoredPack.category)
        assertEquals("Surprised", restored.label)
        assertEquals("wow, reaction", restored.tags)
        assertEquals(originalItem.sha256, restored.sha256)
        assertArrayEquals(originalBytes, files.stickerFile(restoredPack.id, restored.fileName).readBytes())
    }

    @Test fun exportPreflightRejectsMissingStickerBeforeWritingArchive() {
        val pack = store.createPack("Broken")
        assertTrue(store.addSticker(StickerItem(
            "missing", pack.id, "missing.webp", "missing-hash", 1
        )))
        val output = ByteArrayOutputStream()

        val error = runCatching { backup.export(output) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, output.size())
    }

    @Test fun resumeCleanupDeletesOnlyFilesWithoutDatabaseRows() {
        val pack = store.createPack("Resume")
        assertEquals(ImportResult.Added, files.importBytes(pack.id, png()))
        val tracked = store.listStickers(pack.id).single()
        val orphan = files.stickerFile(pack.id, "orphan.webp").apply { writeBytes("orphan".toByteArray()) }

        assertEquals(1, files.deleteOrphanedPackFiles(pack.id))

        assertTrue(!orphan.exists())
        assertTrue(files.stickerFile(pack.id, tracked.fileName).isFile)
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(700, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.MAGENTA)
        return ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
        }.toByteArray()
    }

    companion object { private const val DB_NAME = "stickerdock.db" }
}
