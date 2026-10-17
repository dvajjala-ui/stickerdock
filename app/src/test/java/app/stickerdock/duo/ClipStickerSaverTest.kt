package app.stickerdock.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipStickerSaverTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles
    private lateinit var saver: ClipStickerSaver
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        listOf("stickers", "trays").forEach { File(context.filesDir, it).deleteRecursively() }
        store = StickerStore(context); files = StickerFiles(context, store); saver = ClipStickerSaver(store, files)
    }
    @After fun cleanup() {
        store.close(); context.deleteDatabase("stickerdock.db")
        listOf("stickers", "trays").forEach { File(context.filesDir, it).deleteRecursively() }
    }

    @Test fun explicitSaveCreatesAnimatedPackWithExactPreviewBytesAndCaption() {
        val bytes = animation()
        assertTrue(store.listPacks().isEmpty())
        val pack = saver.save(bytes, "Bas kar bhai", null, "Group chat")
        assertEquals(StickerKind.ANIMATED, pack.kind)
        assertEquals(PackCategory.REACTIONS, pack.category)
        val item = store.listStickers(pack.id).single()
        assertEquals("Bas kar bhai", item.label)
        assertArrayEquals(bytes, files.stickerFile(pack.id, item.fileName).readBytes())
        assertEquals(2, WebpSticker.inspect(bytes)!!.frames)
    }

    @Test fun duplicateAndWrongTargetPreserveExistingPacksAndVersions() {
        val bytes = animation()
        val saved = saver.save(bytes, "First", null, "Reactions")
        val version = store.getPack(saved.id)!!.version
        assertThrows(IllegalStateException::class.java) { saver.save(bytes, "Second", saved.id, "ignored") }
        assertEquals(version, store.getPack(saved.id)!!.version)
        assertEquals(1, store.stickerCount(saved.id))
        val static = store.createPack("Photos")
        assertThrows(IllegalArgumentException::class.java) { saver.save(bytes, "Oops", static.id, "ignored") }
        assertEquals(0, store.stickerCount(static.id))
        assertEquals(2, store.listPacks().size)
    }

    @Test fun invalidOrFailedWriteDoesNotLeaveNewEmptyPackOrDeleteExistingData() {
        assertThrows(IllegalArgumentException::class.java) { saver.save(byteArrayOf(1), "Bad", null, "Reactions") }
        val existing = store.createPack("Keep me")
        // A file blocking the output directory simulates an actual storage write
        // failure, without mocking the save/import implementation.
        File(context.filesDir, "stickers").writeText("blocked")
        assertThrows(IllegalStateException::class.java) { saver.save(animation(), "Retry", null, "New reactions") }
        assertEquals(listOf(existing.id), store.listPacks().map { it.id })
    }

    @Test fun lowStoragePreservesEveryExistingStickerAndPack() {
        val bytes = animation()
        val saved = saver.save(bytes, "Keep this", null, "Existing")
        val item = store.listStickers(saved.id).single()
        val version = store.getPack(saved.id)!!.version
        val full = StickerFiles(context, store, availableSpace = { 0L })
        val failure = assertThrows(IllegalStateException::class.java) {
            ClipStickerSaver(store, full).save(bytes, "Retry later", null, "New pack")
        }
        assertTrue(failure.message!!.contains("Not enough device storage"))
        assertEquals(listOf(saved.id), store.listPacks().map { it.id })
        assertEquals(version, store.getPack(saved.id)!!.version)
        assertEquals(listOf(item), store.listStickers(saved.id))
        assertArrayEquals(bytes, files.stickerFile(saved.id, item.fileName).readBytes())
    }

    private fun animation(): ByteArray = WebpAnimation.encode(listOf(Color.RED, Color.BLUE).map { color ->
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }.toByteArray() }
        finally { bitmap.recycle() }
    }, 125)
}
