package app.stickerdock.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
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
class EditableBackupTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles
    private lateinit var projects: ArtworkProjects
    private val folders = listOf("stickers", "trays", "artwork-originals")

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        folders.forEach { File(context.filesDir, it).deleteRecursively() }
        reopen()
    }
    @After fun cleanup() {
        store.close()
        context.deleteDatabase("stickerdock.db")
        folders.forEach { File(context.filesDir, it).deleteRecursively() }
    }
    private fun reopen() {
        store = StickerStore(context)
        files = StickerFiles(context, store)
        projects = ArtworkProjects(context, store, files)
    }

    @Test fun roundTripRetainsMasksSavedRecipeDraftRedoAndExactResetAfterRestart() {
        val item = editableSticker()
        val original = projects.open(item).original
        val snapshot = projects.snapshot(item)!!
        val archive = export()
        val result = EditableBackup(context, store, files).restoreOrSaved(uri(archive))
        assertEquals(1, result.packs)
        assertEquals(1, result.stickers)
        val restored = store.listStickers(result.packIds.single()).single()
        assertNotEquals(item.id, restored.id)
        assertEquals(restored.id, result.stickerIdsByArchivePath["packs/${item.packId}/${item.fileName}"])
        assertEquals(item.sha256, restored.sha256)
        assertEquals(item.label, restored.label)
        assertEquals(item.tags, restored.tags)
        val restoredPack = store.getPack(restored.packId)!!
        assertEquals(PackRating.MATURE, restoredPack.rating)
        assertEquals(store.getPack(item.packId)!!.category, restoredPack.category)
        store.close()
        reopen()
        val session = projects.open(store.stickerById(restored.id)!!)
        assertArrayEquals(original, session.original)
        assertEquals(snapshot.record.savedHistory, session.savedHistory.encode())
        assertEquals(snapshot.record.draftHistory, session.history.encode())
        assertTrue(session.history.canUndo && session.history.canRedo)
        assertArrayEquals(snapshot.masks.getValue(session.history.edit.maskHash), projects.mask(restored, session.history.edit))
        assertTrue(store.artworkDrafts().any { it.id == restored.id })
        projects.save(restored, session.history.update(ArtworkEdit()))
        val reset = store.stickerById(restored.id)!!
        assertArrayEquals(original, files.stickerFile(reset.packId, reset.fileName).readBytes())
        // Restoring makes a separate pack and never rewrites the source.
        assertEquals(snapshot.record, store.artworkProject(item.id))
    }

    @Test fun portableV5RemainsSavedArtworkOnlyAndRestoresThroughEitherImporter() {
        val item = editableSticker()
        val archive = ByteArrayOutputStream().also { StickerBackup(context, store, files).export(it) }.toByteArray()
        val content = unzip(archive)
        assertEquals(2, content.size)
        assertEquals(5, JSONObject(content.getValue("manifest.json").toString(Charsets.UTF_8)).getInt("schema"))
        assertFalse(content.keys.any { it.startsWith("blobs/") })
        val result = EditableBackup(context, store, files).restoreOrSaved(uri(archive))
        val restored = store.listStickers(result.packIds.single()).single()
        assertNull(store.artworkProject(restored.id))
        assertArrayEquals(files.stickerFile(item.packId, item.fileName).readBytes(), files.stickerFile(restored.packId, restored.fileName).readBytes())
        assertEquals(1, PackZipImporter(context, store, files).import(uri(archive)).imported)
    }

    @Test fun genericZipEntryPointRecognizesEditableBackupAndNeverImportsItsMasksAsStickers() {
        editableSticker()
        val before = store.listPacks().map { it.id }.toSet()
        val result = PackZipImporter(context, store, files).import(uri(export()))
        assertEquals(PackZipImporter.Summary(1, 1, 0, 0), result)
        val restored = store.listPacks().single { it.id !in before }
        assertNotNull(store.artworkProject(store.listStickers(restored.id).single().id))
    }

    @Test fun damagedMissingAndUnreferencedBlobsRejectBeforeChangingExistingLibrary() {
        val item = editableSticker()
        val original = projects.snapshot(item)!!
        val archive = unzip(export())
        val mask = archive.keys.first { it.endsWith(".png") }
        val source = archive.keys.first { it.endsWith(".webp") }
        val variants = listOf(
            archive.toMutableMap().apply { remove(mask) },
            archive.toMutableMap().apply { this[mask] = byteArrayOf(1, 2, 3) },
            archive.toMutableMap().apply { this[source] = byteArrayOf(1, 2, 3) },
            archive.toMutableMap().apply { this["blobs/${"a".repeat(64)}.png"] = archive.getValue(mask) },
            archive.toMutableMap().apply { this["../outside.webp"] = archive.getValue(source) },
            archive.toMutableMap().apply { this["library.zip"] = archive.getValue("library.zip") + byteArrayOf(0) }
        )
        for (variant in variants) {
            assertTrue(runCatching { EditableBackup(context, store, files).restore(uri(zip(variant))) }.isFailure)
            assertEquals(listOf(item.packId), store.listPacks().map { it.id })
            assertEquals(original.record, store.artworkProject(item.id))
            assertArrayEquals(original.original, projects.open(item).original)
            assertFalse(context.cacheDir.listFiles().orEmpty().any { it.name.startsWith("editable-backup-") })
        }
    }

    @Test fun invalidRecipeMismatchAndDuplicateProjectRejectWithoutImportingPacks() {
        val item = editableSticker()
        val base = unzip(export())
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONArray("projects").getJSONObject(0).put("saved_history", "broken") },
            { it.getJSONArray("projects").getJSONObject(0).put("saved_sha256", "0".repeat(64)) },
            { it.getJSONArray("projects").getJSONObject(0).getJSONArray("masks").put("0".repeat(64)) },
            { it.getJSONArray("projects").put(it.getJSONArray("projects").getJSONObject(0)) }
        )
        for (mutate in mutations) {
            val manifest = JSONObject(base.getValue(EditableBackup.MANIFEST).toString(Charsets.UTF_8))
            mutate(manifest)
            val content = base.toMutableMap().apply { this[EditableBackup.MANIFEST] = manifest.toString().toByteArray() }
            assertTrue(runCatching { EditableBackup(context, store, files).restore(uri(zip(content))) }.isFailure)
            assertEquals(listOf(item.packId), store.listPacks().map { it.id })
        }
    }

    @Test fun projectStorageFailureRollsBackOnlyNewPacksAndRetrySucceeds() {
        val item = editableSticker()
        val snapshot = projects.snapshot(item)!!
        val archive = export()
        val sources = File(context.filesDir, "artwork-originals")
        val unavailable = File(context.filesDir, "temporarily-unavailable-originals")
        assertTrue(sources.renameTo(unavailable))
        sources.writeText("Simulated unavailable storage")
        try {
            assertTrue(runCatching { EditableBackup(context, store, files).restore(uri(archive)) }.isFailure)
            assertEquals(listOf(item.packId), store.listPacks().map { it.id })
            assertEquals(snapshot.record, store.artworkProject(item.id))
        } finally {
            assertTrue(sources.delete())
            assertTrue(unavailable.renameTo(sources))
        }
        assertArrayEquals(snapshot.original, projects.open(item).original)
        assertEquals(1, EditableBackup(context, store, files).restore(uri(archive)).stickers)
    }

    @Test fun packsWithoutEditableProjectsStillRoundTripInEditableEnvelope() {
        val item = editableSticker()
        val plain = store.createPack("Unedited")
        assertEquals(ImportResult.Added, files.restoreBytes(plain.id, projects.open(item).original, StickerFiles.sha256(projects.open(item).original)))
        val restored = EditableBackup(context, store, files).restore(uri(export()))
        assertEquals(2, restored.packs)
        assertEquals(2, restored.stickers)
        val unedited = restored.packIds.mapNotNull(store::getPack).single { it.name == "Unedited" }
        assertNull(store.artworkProject(store.listStickers(unedited.id).single().id))
    }

    private fun editableSticker(): StickerItem {
        val pack = store.createPack("Private project", rating = PackRating.MATURE)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(40f, 30f, 270f, 400f, Paint().apply { color = Color.BLUE })
        val image = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        assertEquals(ImportResult.Added, files.importBytes(pack.id, image, label = "My photo", tags = "personal, reaction"))
        val initial = store.listStickers(pack.id).single()
        projects.open(initial)
        val hash = projects.retainMask(initial, ArtworkMasks.fromConfidence(FloatArray(512 * 512) { if (it % 512 < 300) 1f else 0f }))
        val saved = ArtworkHistory().update(ArtworkEdit(maskHash = hash, topText = "Saved caption"))
        projects.save(initial, saved)
        val item = store.stickerById(initial.id)!!
        val draft = saved.update(saved.edit.copy(bottomText = "Private unfinished draft"))
            .update(saved.edit.copy(bottomText = "Redo me", mirror = true)).undo()
        projects.saveDraft(item, draft)
        return item
    }
    private fun export() = ByteArrayOutputStream().also { EditableBackup(context, store, files).export(it) }.toByteArray()
    private fun uri(bytes: ByteArray): Uri = Uri.fromFile(File.createTempFile("editable-test-", ".zip", context.cacheDir).apply { writeBytes(bytes) })
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().apply {
        ZipInputStream(bytes.inputStream()).use { input ->
            while (true) { val entry = input.nextEntry ?: break; this[entry.name] = input.readBytes() }
        }
    }
    private fun zip(content: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> content.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
        } }
    }.toByteArray()
}
