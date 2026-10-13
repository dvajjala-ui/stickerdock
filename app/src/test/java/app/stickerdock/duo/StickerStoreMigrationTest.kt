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
    fun upgradesEveryLegacySchemaToV6WithoutLosingPackOrSticker() {
        for (legacyVersion in 1..5) {
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
                assertEquals("", stickers.single().label)
                assertEquals("", stickers.single().tags)
                assertTrue("v$legacyVersion global hash index/query", store.hasStickerHash("deadbeef"))

                store.readableDatabase.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_stickers_sha'",
                    null
                ).use { cursor ->
                    assertTrue("v$legacyVersion sha index", cursor.moveToFirst())
                }
                assertEquals(6, store.readableDatabase.version)
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
            db.execSQL(
                "CREATE TABLE stickers (" +
                    "id TEXT PRIMARY KEY, " +
                    "pack_id TEXT NOT NULL REFERENCES packs(id) ON DELETE CASCADE, " +
                    "file_name TEXT NOT NULL, sha256 TEXT NOT NULL, created_at INTEGER NOT NULL, " +
                    "UNIQUE(pack_id, sha256), UNIQUE(pack_id, file_name))"
            )
            db.execSQL("CREATE INDEX idx_stickers_pack ON stickers(pack_id)")
            if (version >= 4) db.execSQL("CREATE INDEX idx_stickers_sha ON stickers(sha256)")

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
            db.version = version
        } finally {
            db.close()
        }
    }

    companion object {
        private const val DB_NAME = "stickerdock.db"
    }
}
