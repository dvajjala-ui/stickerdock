package app.stickerdock.duo

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StickerStoreMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun upgradesEveryLegacySchemaToV8WithoutLosingPackOrSticker() {
        for (legacyVersion in 1..7) {
            context.deleteDatabase(DB_NAME)
            seedLegacyDatabase(legacyVersion)

            val store = StickerStore(context)
            try {
                val packs = store.listPacks()
                assertEquals("v$legacyVersion pack count", 1, packs.size)
                val pack = packs.single()
                assertEquals("legacy", pack.id)
                assertEquals("Legacy pack", pack.name)
                assertEquals(7L, pack.version)
                assertEquals(
                    if (legacyVersion >= 2) StickerKind.ANIMATED else StickerKind.STATIC,
                    pack.kind
                )
                assertEquals(
                    if (legacyVersion >= 3) PackRating.MATURE else PackRating.GENERAL,
                    pack.rating
                )
                assertEquals(
                    if (legacyVersion >= 5) PackCategory.LOVE else PackCategory.GENERAL,
                    pack.category
                )

                val stickers = store.listStickers(pack.id)
                assertEquals("v$legacyVersion sticker count", 1, stickers.size)
                assertEquals("legacy.webp", stickers.single().fileName)
                assertEquals("deadbeef", stickers.single().sha256)
                assertEquals(if (legacyVersion >= 6) "Kept label" else "", stickers.single().label)
                assertEquals(if (legacyVersion >= 6) "kept tag" else "", stickers.single().tags)
                assertTrue("v$legacyVersion global hash index/query", store.hasStickerHash("deadbeef"))

                store.readableDatabase.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_stickers_sha'",
                    null
                ).use { cursor ->
                    assertTrue("v$legacyVersion sha index", cursor.moveToFirst())
                }
                store.readableDatabase.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='folder_import_items'",
                    null
                ).use { cursor -> assertTrue("v$legacyVersion import journal", cursor.moveToFirst()) }
                assertEquals(8, store.readableDatabase.version)
                assertEquals(null, store.artworkProject(stickers.single().id))
                assertTrue(store.artworkDrafts().isEmpty())
                if (legacyVersion >= 7) {
                    assertEquals("scan-7", store.pendingFolderImport()!!.id)
                    assertEquals(setOf("legacy"), store.folderImportPackIds("scan-7"))
                    assertEquals(setOf("content://kept.webp"), store.folderImportProcessedSources("scan-7"))
                    assertEquals(FolderImportStats(1, 0, 0, 123), store.folderImportStats("scan-7"))
                }
            } finally {
                store.close()
            }
        }
    }

    private fun seedLegacyDatabase(version: Int) {
        val db = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        try {
            val packColumns = buildString {
                append("id TEXT PRIMARY KEY, name TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL")
                if (version >= 2) append(", kind TEXT NOT NULL DEFAULT 'STATIC'")
                if (version >= 3) append(", rating TEXT NOT NULL DEFAULT 'GENERAL'")
                if (version >= 5) append(", category TEXT NOT NULL DEFAULT 'GENERAL'")
            }
            db.execSQL("CREATE TABLE packs ($packColumns)")
            val stickerMetadataColumns = if (version >= 6)
                ", label TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT ''" else ""
            db.execSQL(
                "CREATE TABLE stickers (" +
                    "id TEXT PRIMARY KEY, " +
                    "pack_id TEXT NOT NULL REFERENCES packs(id) ON DELETE CASCADE, " +
                    "file_name TEXT NOT NULL, sha256 TEXT NOT NULL, created_at INTEGER NOT NULL" +
                    stickerMetadataColumns + ", " +
                    "UNIQUE(pack_id, sha256), UNIQUE(pack_id, file_name))"
            )
            db.execSQL("CREATE INDEX idx_stickers_pack ON stickers(pack_id)")
            if (version >= 4) db.execSQL("CREATE INDEX idx_stickers_sha ON stickers(sha256)")
            if (version >= 7) {
                db.execSQL("CREATE TABLE folder_import_sessions (id TEXT PRIMARY KEY, tree_uri TEXT NOT NULL, fallback_name TEXT NOT NULL, created_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE folder_import_packs (session_id TEXT NOT NULL REFERENCES folder_import_sessions(id) ON DELETE CASCADE, pack_id TEXT NOT NULL REFERENCES packs(id) ON DELETE CASCADE, PRIMARY KEY(session_id,pack_id))")
                db.execSQL("CREATE TABLE folder_import_items (session_id TEXT NOT NULL REFERENCES folder_import_sessions(id) ON DELETE CASCADE, source_uri TEXT NOT NULL, outcome TEXT NOT NULL, source_bytes INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(session_id,source_uri))")
            }

            val packColumnsForInsert = mutableListOf("id", "name", "version", "created_at")
            val values = mutableListOf("'legacy'", "'Legacy pack'", "7", "1234")
            if (version >= 2) {
                packColumnsForInsert += "kind"
                values += "'ANIMATED'"
            }
            if (version >= 3) {
                packColumnsForInsert += "rating"
                values += "'MATURE'"
            }
            if (version >= 5) {
                packColumnsForInsert += "category"
                values += "'LOVE'"
            }
            db.execSQL(
                "INSERT INTO packs (${packColumnsForInsert.joinToString()}) " +
                    "VALUES (${values.joinToString()})"
            )
            db.execSQL(
                "INSERT INTO stickers (id, pack_id, file_name, sha256, created_at) " +
                    "VALUES ('sticker-1', 'legacy', 'legacy.webp', 'deadbeef', 2345)"
            )
            if (version >= 6) db.execSQL("UPDATE stickers SET label='Kept label', tags='kept tag'")
            if (version >= 7) {
                db.execSQL("INSERT INTO folder_import_sessions VALUES ('scan-7','content://tree/root','Kept scan',3456)")
                db.execSQL("INSERT INTO folder_import_packs VALUES ('scan-7','legacy')")
                db.execSQL("INSERT INTO folder_import_items VALUES ('scan-7','content://kept.webp','ADDED',123)")
            }
            db.version = version
        } finally {
            db.close()
        }
    }

    @Test
    fun folderImportJournalTracksSourcesAndCascadesWithSession() {
        val store = StickerStore(context)
        try {
            val pack = store.createPack("Imported")
            val session = store.createFolderImport("content://tree/root", "Imported")
            store.addFolderImportPack(session.id, pack.id)
            store.recordFolderImportItem(session.id, "content://one.webp", "ADDED", 123)
            store.recordFolderImportItem(session.id, "content://two.webp", "REJECTED", 0)

            assertEquals(session, store.pendingFolderImport())
            assertEquals(setOf(pack.id), store.folderImportPackIds(session.id))
            assertEquals(setOf("content://one.webp", "content://two.webp"),
                store.folderImportProcessedSources(session.id))
            assertEquals(FolderImportStats(2, 0, 1, 123), store.folderImportStats(session.id))

            store.deleteFolderImport(session.id)
            assertEquals(null, store.pendingFolderImport())
            assertTrue(store.folderImportProcessedSources(session.id).isEmpty())
            assertTrue(store.folderImportPackIds(session.id).isEmpty())
            assertEquals(0, store.stickerCount(pack.id))
        } finally { store.close() }
    }

    @Test
    fun folderImportStickerAndJournalOutcomeRollbackTogether() {
        val store = StickerStore(context)
        try {
            val pack = store.createPack("Atomic")
            val session = store.createFolderImport("content://tree/atomic", "Atomic")
            store.addFolderImportPack(session.id, pack.id)

            runCatching {
                store.transaction {
                    assertTrue(store.addSticker(StickerItem(
                        "pending", pack.id, "pending.webp", "pending-hash", 1
                    )))
                    store.recordFolderImportItem(session.id, "content://pending.webp", "ADDED", 20)
                    error("simulate interruption before commit")
                }
            }

            assertEquals(0, store.stickerCount(pack.id))
            assertTrue(store.folderImportProcessedSources(session.id).isEmpty())
            assertEquals(FolderImportStats(0, 0, 0, 0), store.folderImportStats(session.id))
        } finally { store.close() }
    }

    companion object {
        private const val DB_NAME = "stickerdock.db"
    }
}
