package app.stickerdock.duo

import android.content.Context
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderStickerImporterCheckpointTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore
    private lateinit var files: StickerFiles

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
        context.getSharedPreferences("folder_import_checkpoint", Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(context.filesDir, "folder-import").deleteRecursively()
        store = StickerStore(context)
        files = StickerFiles(context, store)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase(DB_NAME)
        context.getSharedPreferences("folder_import_checkpoint", Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(context.filesDir, "folder-import").deleteRecursively()
    }

    @Test fun legacyCheckpointMigratesBeforeDiscardAndRemovesOnlyEmptySessionPack() {
        val empty = store.createPack("Legacy empty")
        val unrelated = store.createPack("Keep me")
        context.getSharedPreferences("folder_import_checkpoint", Context.MODE_PRIVATE).edit()
            .putString("tree_uri", "content://legacy/tree")
            .putString("fallback_name", "Legacy")
            .putInt("added", 1)
            .putInt("duplicates", 1)
            .putLong("total_bytes", 200)
            .putStringSet("pack_ids", setOf(empty.id))
            .commit()
        File(context.filesDir, "folder-import").mkdirs()
        File(context.filesDir, "folder-import/processed-uris.txt")
            .writeText("content://one.webp\ncontent://two.webp\n")

        assertEquals(1, FolderStickerImporter(context, store, files).discardPendingImport())

        assertNull(store.getPack(empty.id))
        assertTrue(store.getPack(unrelated.id) != null)
        assertNull(store.pendingFolderImport())
        assertNull(FolderStickerImporter.pendingImportUri(context))
    }

    companion object { private const val DB_NAME = "stickerdock.db" }
}
