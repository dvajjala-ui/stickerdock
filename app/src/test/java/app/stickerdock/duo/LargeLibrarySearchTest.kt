package app.stickerdock.duo

import android.content.ContentValues
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
class LargeLibrarySearchTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
        store = StickerStore(context)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test fun countsAndBoundedSearchStayCorrectWithTenThousandStickers() {
        val database = store.writableDatabase
        database.beginTransaction()
        try {
            repeat(PACK_COUNT) { pack ->
                database.insertOrThrow("packs", null, ContentValues().apply {
                    put("id", "pack-$pack")
                    put("name", "Pack $pack")
                    put("version", 1)
                    put("created_at", pack.toLong())
                    put("kind", "STATIC")
                    put("rating", "GENERAL")
                    put("category", "REACTIONS")
                })
            }
            repeat(STICKER_COUNT) { index ->
                database.insertOrThrow("stickers", null, ContentValues().apply {
                    put("id", "sticker-$index")
                    put("pack_id", "pack-${index / 30}")
                    put("file_name", "$index.webp")
                    put("sha256", "hash-$index")
                    put("created_at", index.toLong())
                    put("label", "needle-$index")
                    put("tags", "common, reaction")
                })
            }
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }

        val counts = store.stickerCounts()
        assertEquals(PACK_COUNT, counts.size)
        assertEquals(30, counts["pack-0"])
        assertEquals(10, counts["pack-${PACK_COUNT - 1}"])
        val previews = store.stickerPreviews()
        assertEquals(PACK_COUNT, previews.size)
        assertTrue(previews.values.all { it.size == 4 })
        assertEquals("sticker-0", previews.getValue("pack-0").first().id)
        assertEquals("sticker-9999", store.searchStickers("needle-9999").single().item.id)
        val bounded = store.searchStickers("common")
        assertEquals(100, bounded.size)
        assertEquals((0 until PACK_COUNT).map { "pack-$it" }.toSet(), store.searchStickerPackIds("common"))
        assertEquals(setOf("pack-333"), store.searchStickerPackIds("needle-9999"))
        assertEquals(setOf("pack-0"), store.searchStickerPackIds("0.webp").filter { it == "pack-0" }.toSet())
        assertTrue(store.searchStickerPackIds("%_").isEmpty())
        assertTrue(store.searchStickerPackIds("  ").isEmpty())
        assertTrue(bounded.zipWithNext().all { (left, right) ->
            left.item.createdAt >= right.item.createdAt
        })
    }

    companion object {
        private const val DB_NAME = "stickerdock.db"
        private const val STICKER_COUNT = 10_000
        private const val PACK_COUNT = 334
    }
}
