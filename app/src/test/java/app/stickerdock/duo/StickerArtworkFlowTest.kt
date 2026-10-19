package app.stickerdock.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
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
class StickerArtworkFlowTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        File(context.filesDir, "stickers").deleteRecursively()
        store = StickerStore(context)
        files = StickerFiles(context, store)
    }
    @After fun cleanup() {
        store.close()
        context.deleteDatabase("stickerdock.db")
        File(context.filesDir, "stickers").deleteRecursively()
        File(context.filesDir, "trays").deleteRecursively()
    }

    @Test fun editPreservesIdentityAndMetadataAndRejectsStaleSave() {
        val pack = store.createPack("Editable")
        assertEquals(ImportResult.Added, files.importBytes(pack.id, png(Color.RED), label = "Hello", tags = "reaction"))
        val original = store.listStickers(pack.id).single()
        val originalBytes = files.stickerFile(pack.id, original.fileName).readBytes()
        val bitmap = StickerArtwork.render(originalBytes, ArtworkEdit(quarterTurns = 1, topText = "Hello"))
        val editedBytes = try { StickerArtwork.encode(bitmap) } finally { bitmap.recycle() }
        val version = store.getPack(pack.id)!!.version
        files.replaceStaticArtwork(original, editedBytes)
        val edited = store.listStickers(pack.id).single()
        assertEquals(original.id, edited.id)
        assertEquals(original.label, edited.label)
        assertEquals(original.tags, edited.tags)
        assertNotEquals(original.fileName, edited.fileName)
        assertEquals(version + 1, store.getPack(pack.id)!!.version)
        assertArrayEquals(editedBytes, files.stickerFile(pack.id, edited.fileName).readBytes())
        assertFalse(files.stickerFile(pack.id, original.fileName).exists())
        assertTrue(runCatching { files.replaceStaticArtwork(original, editedBytes) }.isFailure)
        assertArrayEquals(editedBytes, files.stickerFile(pack.id, edited.fileName).readBytes())
        assertEquals(1, files.stickerFile(pack.id, edited.fileName).parentFile!!.listFiles()!!.size)
    }

    @Test fun invalidEditDoesNotDestroyOriginal() {
        val pack = store.createPack("Keep me")
        files.importBytes(pack.id, png(Color.BLUE))
        val item = store.listStickers(pack.id).single()
        val original = files.stickerFile(pack.id, item.fileName).readBytes()
        assertTrue(runCatching { files.replaceStaticArtwork(item, byteArrayOf(1, 2, 3)) }.isFailure)
        assertArrayEquals(original, files.stickerFile(pack.id, item.fileName).readBytes())
        assertEquals(item, store.listStickers(pack.id).single())
    }

    @Test fun damagedSourceCannotBeMovedAndLost() {
        val source = store.createPack("Source")
        val target = store.createPack("Target")
        files.importBytes(source.id, png(Color.GREEN))
        val item = store.listStickers(source.id).single()
        files.stickerFile(source.id, item.fileName).writeBytes(byteArrayOf())
        val result = files.transferSticker(item, target.id, move = true)
        assertTrue(result is ImportResult.Failed)
        assertEquals(item, store.listStickers(source.id).single())
        assertEquals(0, store.stickerCount(target.id))
    }

    @Test fun writeFailureKeepsOriginalArtworkAndDatabaseRow() {
        val pack = store.createPack("Keep original")
        files.importBytes(pack.id, png(Color.CYAN))
        val item = store.listStickers(pack.id).single()
        val original = files.stickerFile(pack.id, item.fileName)
        val directory = original.parentFile!!
        val blocked = File(directory.parentFile, "write-failure-${pack.id}")
        assertTrue(directory.renameTo(blocked))
        directory.writeText("not a directory")
        try {
            val bitmap = StickerArtwork.render(File(blocked, item.fileName).readBytes(), ArtworkEdit(topText = "Keep"))
            val edited = try { StickerArtwork.encode(bitmap) } finally { bitmap.recycle() }
            assertTrue(runCatching { files.replaceStaticArtwork(item, edited) }.isFailure)
            assertEquals(item, store.listStickers(pack.id).single())
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(File(blocked, item.fileName).readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(item.sha256, digest)
        } finally { directory.delete(); assertTrue(blocked.renameTo(directory)) }
    }

    @Test fun rotationMovesArtworkAndPreservesTransparency() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawRect(0f, 0f, 256f, 256f, android.graphics.Paint().apply { color = Color.RED })
        val input = try { StickerArtwork.encode(bitmap) } finally { bitmap.recycle() }
        val result = StickerArtwork.render(input, ArtworkEdit(quarterTurns = 1))
        try {
            assertTrue(Color.red(result.getPixel(400, 100)) > 200)
            assertEquals(0, Color.alpha(result.getPixel(100, 100)))
            assertEquals(512, result.width)
            assertEquals(512, result.height)
        } finally { result.recycle() }
    }

    @Test fun cutoutRemovesOutsideAndMovesWithMirroredSubject() {
        val original = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val bytes = try { StickerArtwork.encode(original) } finally { original.recycle() }
        val outline = listOf(CutoutPoint(0.1f, 0.1f), CutoutPoint(0.4f, 0.1f), CutoutPoint(0.4f, 0.9f), CutoutPoint(0.1f, 0.9f))
        val result = StickerArtwork.render(bytes, ArtworkEdit(mirror = true, cutout = outline))
        try {
            assertEquals(0, Color.alpha(result.getPixel(100, 200)))
            assertEquals(255, Color.alpha(result.getPixel(400, 200)))
            assertTrue(Color.blue(result.getPixel(400, 200)) > 200)
            val exported = StickerArtwork.encode(result)
            val decoded = android.graphics.BitmapFactory.decodeByteArray(exported, 0, exported.size)
            try {
                assertEquals(0, Color.alpha(decoded.getPixel(100, 200)))
                assertEquals(512, decoded.width)
            }
            finally { decoded.recycle() }
        } finally { result.recycle() }
    }

    @Test fun photoImportSplitsAtThirtyAndDoesNotCreateEmptyDuplicatePacks() {
        val uris = (0..30).map { index ->
            Uri.fromFile(File(context.cacheDir, "photo-$index.png").apply { writeBytes(png(Color.rgb(index * 7, 40, 120))) })
        }
        val importer = PhotoPackImporter(context, store, files)
        val imported = importer.import(uris)
        assertEquals(31, imported.added)
        assertEquals(listOf(30, 1), imported.packIds.map(store::stickerCount))
        val repeated = importer.import(uris)
        assertEquals(31, repeated.duplicates)
        assertTrue(repeated.packIds.isEmpty())
        assertEquals(2, store.listPacks().size)
    }

    @Test fun explicitPhotoCreationCanReuseAPhotoWithoutDuplicatingWithinTheSelection() {
        val uri = Uri.fromFile(File(context.cacheDir, "owned-photo.png").apply { writeBytes(png(Color.MAGENTA)) })
        val importer = PhotoPackImporter(context, store, files)
        importer.import(listOf(uri), "Old pack")
        val result = importer.import(listOf(uri, uri), "Friends", deduplicateLibrary = false)
        assertEquals(1, result.added)
        assertEquals(1, result.duplicates)
        assertEquals(2, store.listPacks().size)
        assertEquals("Friends", store.getPack(result.packIds.single())!!.name)
    }

    @Test fun photosSaveIntoChosenPackAndRetryRetainsOnlyFailedInputs() {
        val target = store.createPack("Chosen")
        val good = Uri.fromFile(File(context.cacheDir, "valid-photo.png").apply { writeBytes(png(Color.CYAN)) })
        val bad = Uri.fromFile(File(context.cacheDir, "broken-photo.png").apply { writeBytes(byteArrayOf(1, 2)) })
        val importer = PhotoPackImporter(context, store, files)
        val result = importer.import(listOf(good, bad), "Ignored name", target.id, deduplicateLibrary = false)
        assertEquals(1, result.added)
        assertEquals(listOf(target.id), result.packIds)
        assertEquals(listOf(bad), result.failedUris)
        assertEquals(1, store.listPacks().size)
        assertEquals(1, store.stickerCount(target.id))
        assertEquals(1, importer.import(listOf(good), "Chosen", target.id, deduplicateLibrary = false).duplicates)
        assertEquals(1, store.stickerCount(target.id))
    }

    @Test fun deletedPhotoDestinationDoesNotCreateAReplacementPack() {
        val target = store.createPack("Removed")
        files.deletePack(target.id)
        assertTrue(runCatching { PhotoPackImporter(context, store, files).import(emptyList(), "New", target.id) }.isFailure)
        assertTrue(store.listPacks().isEmpty())
    }

    @Test fun exportPreflightAcceptsRealPackAndIdentifiesDamagedArtwork() {
        val pack = store.createPack("WhatsApp")
        listOf(Color.RED, Color.BLUE, Color.GREEN).forEach { files.importBytes(pack.id, png(it)) }
        WhatsAppIntegration.validatePack(pack, store, files)
        val item = store.listStickers(pack.id).first()
        files.stickerFile(pack.id, item.fileName).writeBytes(ByteArray(32))
        val error = runCatching { WhatsAppIntegration.validatePack(pack, store, files) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("damaged"))
    }

    @Test fun corruptSharedZipDoesNotStopFollowingPhotos() {
        val badArchive = Uri.parse("content://shared.test/broken.zip")
        val photo = Uri.parse("content://shared.test/shared-photo.png")
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(badArchive,
            java.io.ByteArrayInputStream("broken archive".toByteArray()))
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(photo,
            java.io.ByteArrayInputStream(png(Color.CYAN)))
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, arrayListOf(badArchive, photo))
        }
        val result = IncomingStickers(context, store, files).import(intent)
        assertTrue(result.contains("1 added, 1 skipped"))
        assertEquals(1, store.stickerCounts().values.sum())
    }

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(180, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }
}
