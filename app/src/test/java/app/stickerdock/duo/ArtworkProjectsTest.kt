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
class ArtworkProjectsTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles
    private lateinit var projects: ArtworkProjects
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        listOf("stickers", "trays", "artwork-originals").forEach { File(context.filesDir, it).deleteRecursively() }
        store = StickerStore(context)
        files = StickerFiles(context, store)
        projects = ArtworkProjects(context, store, files)
    }
    @After fun cleanup() {
        store.close()
        context.deleteDatabase("stickerdock.db")
        listOf("stickers", "trays", "artwork-originals").forEach { File(context.filesDir, it).deleteRecursively() }
    }

    @Test fun reopenRestoresRecipeAndResetReturnsExactStartingBytes() {
        val item = sticker()
        val starting = files.stickerFile(item.packId, item.fileName).readBytes()
        val history = projects.open(item).history.update(ArtworkEdit(quarterTurns = 1, topText = "First caption"))
        projects.save(item, history)
        val edited = store.stickerById(item.id)!!
        val reopened = ArtworkProjects(context, store, files).open(edited)
        assertArrayEquals(starting, reopened.original)
        assertEquals("First caption", reopened.history.edit.topText)
        assertTrue(reopened.history.canUndo)
        // Editing the caption replaces it, rather than drawing over the old caption.
        val changed = reopened.history.update(reopened.history.edit.copy(topText = "Second caption"))
        projects.save(edited, changed)
        val current = store.stickerById(item.id)!!
        val reset = projects.open(current).history.update(ArtworkEdit())
        projects.save(current, reset)
        val restored = store.stickerById(item.id)!!
        assertEquals(item.id, restored.id)
        assertEquals(item.label, restored.label)
        assertEquals(item.tags, restored.tags)
        assertEquals(item.sha256, restored.sha256)
        assertArrayEquals(starting, files.stickerFile(restored.packId, restored.fileName).readBytes())
    }

    @Test fun draftSurvivesDatabaseCloseAndDoesNotChangeExportedSticker() {
        val item = sticker()
        val original = files.stickerFile(item.packId, item.fileName).readBytes()
        val history = projects.open(item).history.update(ArtworkEdit(mirror = true, bottomText = "Not saved yet"))
            .update(ArtworkEdit(mirror = true, bottomText = "Not saved yet", zoom = 2f)).undo()
        projects.saveDraft(item, history)
        val version = store.getPack(item.packId)!!.version
        store.close()
        store = StickerStore(context)
        files = StickerFiles(context, store)
        projects = ArtworkProjects(context, store, files)
        val session = projects.open(store.stickerById(item.id)!!)
        assertEquals(history.edit, session.history.edit)
        assertTrue(session.history.canUndo && session.history.canRedo)
        assertEquals(listOf(item.id), store.artworkDrafts().map { it.id })
        assertEquals(item, store.stickerById(item.id))
        assertEquals(version, store.getPack(item.packId)!!.version)
        assertArrayEquals(original, files.stickerFile(item.packId, item.fileName).readBytes())
        projects.discardDraft(item)
        assertTrue(store.artworkDrafts().isEmpty())
        assertEquals(ArtworkEdit(), projects.open(item).history.edit)
    }

    @Test fun commitFailureRollsBackArtworkAndProjectTogether() {
        val item = sticker()
        val session = projects.open(item)
        val record = store.artworkProject(item.id)
        val original = files.stickerFile(item.packId, item.fileName).readBytes()
        val bitmap = StickerArtwork.render(session.original, ArtworkEdit(topText = "Failed save"))
        val output = try { StickerArtwork.encode(bitmap) } finally { bitmap.recycle() }
        assertTrue(runCatching {
            files.replaceStaticArtwork(item, output) { hash ->
                store.putArtworkProject(record!!.copy(savedSha256 = hash))
                error("simulate project commit failure")
            }
        }.isFailure)
        assertEquals(item, store.stickerById(item.id))
        assertEquals(record, store.artworkProject(item.id))
        assertArrayEquals(original, files.stickerFile(item.packId, item.fileName).readBytes())
        assertEquals(1, files.stickerFile(item.packId, item.fileName).parentFile!!.listFiles()!!.size)
    }

    @Test fun staleEditorCannotOverwriteNewRecipeOrClearItsDraft() {
        val item = sticker()
        val history = projects.open(item).history.update(ArtworkEdit(topText = "Saved"))
        projects.save(item, history)
        val edited = store.stickerById(item.id)!!
        projects.saveDraft(edited, history.update(history.edit.copy(bottomText = "Draft")))
        val record = store.artworkProject(item.id)
        assertTrue(runCatching { projects.save(item, history) }.isFailure)
        assertTrue(runCatching { projects.saveDraft(item, ArtworkHistory()) }.isFailure)
        assertTrue(runCatching { projects.discardDraft(item) }.isFailure)
        assertEquals(record, store.artworkProject(item.id))
    }

    @Test fun copyKeepsSavedRecipeAndMoveAlsoKeepsDraft() {
        val item = sticker()
        val history = projects.open(item).history.update(ArtworkEdit(topText = "Saved caption"))
        projects.save(item, history)
        val edited = store.stickerById(item.id)!!
        projects.saveDraft(edited, history.update(history.edit.copy(mirror = true)))
        val target = store.createPack("Copy")
        assertEquals(ImportResult.Added, files.transferSticker(edited, target.id, move = false))
        val copied = store.listStickers(target.id).single()
        assertEquals(history.edit, projects.open(copied).history.edit)
        assertFalse(projects.open(copied).history.edit.mirror)
        val moveTarget = store.createPack("Moved")
        assertEquals(ImportResult.Added, files.transferSticker(edited, moveTarget.id, move = true))
        val moved = store.listStickers(moveTarget.id).single()
        assertTrue(projects.open(moved).history.edit.mirror)
        assertNull(store.artworkProject(item.id))
        assertFalse(File(context.filesDir, "artwork-originals/${item.id}").exists())
        files.deletePack(moveTarget.id)
        assertNull(store.artworkProject(moved.id))
        assertFalse(File(context.filesDir, "artwork-originals/${moved.id}").exists())
    }

    @Test fun deletionUndoSnapshotRetainsRecipeAndOriginal() {
        val item = sticker()
        val history = projects.open(item).history.update(ArtworkEdit(quarterTurns = 2, topText = "Keep"))
        projects.save(item, history)
        val edited = store.stickerById(item.id)!!
        val snapshot = projects.snapshot(edited)!!
        val bytes = files.stickerFile(edited.packId, edited.fileName).readBytes()
        files.deleteSticker(edited)
        assertEquals(ImportResult.Added, files.restoreBytes(edited.packId, bytes, edited.sha256, edited.label, edited.tags) {
            projects.restoreSnapshot(it, snapshot)
        })
        val restored = store.listStickers(item.packId).single()
        assertEquals(history.edit, projects.open(restored).history.edit)
        assertArrayEquals(snapshot.original, projects.open(restored).original)
    }

    @Test fun damagedOriginalFailsClosedWithoutLosingSavedArtifactOrDraft() {
        val item = sticker()
        val history = projects.open(item).history.update(ArtworkEdit(topText = "Draft"))
        projects.saveDraft(item, history)
        val record = store.artworkProject(item.id)!!
        projects.sourceFile(item.id, record.sourceSha256).writeBytes(byteArrayOf())
        assertTrue(runCatching { projects.open(item) }.isFailure)
        assertTrue(runCatching { projects.save(item, history) }.isFailure)
        assertEquals(item, store.stickerById(item.id))
        assertEquals(record, store.artworkProject(item.id))
        assertTrue(files.stickerFile(item.packId, item.fileName).length() > 0)
    }

    @Test fun exportsKeepSchemaFiveAndDoNotIncludeStartingArtworkOrDraft() {
        val item = sticker()
        val history = projects.open(item).history.update(ArtworkEdit(topText = "Private draft"))
        projects.saveDraft(item, history)
        val output = ByteArrayOutputStream()
        StickerBackup(context, store, files).export(output)
        val entries = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertEquals(2, entries.size)
        assertEquals(5, org.json.JSONObject(String(entries.getValue("manifest.json"))).getInt("schema"))
        assertFalse(String(entries.getValue("manifest.json")).contains("Private draft"))
        assertArrayEquals(files.stickerFile(item.packId, item.fileName).readBytes(), entries.values.last())
    }

    @Test fun automaticMaskSurvivesDraftReopenCopyAndDeletionRecoveryAndReset() {
        val item = sticker()
        val original = projects.open(item).original
        val bytes = ArtworkMasks.fromConfidence(FloatArray(512 * 512) { if (it % 512 < 120) 1f else 0f })
        val hash = projects.retainMask(item, bytes)
        val history = projects.open(item).history.update(ArtworkEdit(maskHash = hash))
        projects.saveDraft(item, history)
        assertEquals(hash, ArtworkProjects(context, store, files).open(item).history.edit.maskHash)
        assertArrayEquals(original, files.stickerFile(item.packId, item.fileName).readBytes())
        projects.save(item, history)
        val edited = store.stickerById(item.id)!!
        val target = store.createPack("Cutout copy")
        assertEquals(ImportResult.Added, files.transferSticker(edited, target.id, move = false))
        val copied = store.listStickers(target.id).single()
        assertArrayEquals(bytes, projects.mask(copied, projects.open(copied).history.edit))
        val snapshot = projects.snapshot(edited)!!
        val saved = files.stickerFile(edited.packId, edited.fileName).readBytes()
        files.deleteSticker(edited)
        assertEquals(ImportResult.Added, files.restoreBytes(edited.packId, saved, edited.sha256) { projects.restoreSnapshot(it, snapshot) })
        val restored = store.listStickers(edited.packId).single()
        assertArrayEquals(bytes, projects.mask(restored, projects.open(restored).history.edit))
        projects.save(restored, projects.open(restored).history.update(ArtworkEdit()))
        val reset = store.stickerById(restored.id)!!
        assertArrayEquals(original, files.stickerFile(reset.packId, reset.fileName).readBytes())
    }

    @Test fun damagedMaskCannotOverwriteTheSavedStickerOrDraft() {
        val item = sticker()
        projects.open(item)
        val hash = projects.retainMask(item, ArtworkMasks.fromConfidence(FloatArray(512 * 512) { 1f }))
        val history = ArtworkHistory().update(ArtworkEdit(maskHash = hash))
        projects.saveDraft(item, history)
        val record = store.artworkProject(item.id)
        val saved = files.stickerFile(item.packId, item.fileName).readBytes()
        ArtworkMaskStore(context).file(item.id, hash).writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(runCatching { projects.save(item, history) }.isFailure)
        assertEquals(record, store.artworkProject(item.id))
        assertArrayEquals(saved, files.stickerFile(item.packId, item.fileName).readBytes())
    }

    private fun sticker(): StickerItem {
        val pack = store.createPack("Editable")
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawRect(30f, 40f, 180f, 300f, android.graphics.Paint().apply { color = Color.RED })
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        assertEquals(ImportResult.Added, files.importBytes(pack.id, png, label = "Original", tags = "private"))
        return store.listStickers(pack.id).single()
    }
}
