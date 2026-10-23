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
class StickerMetadataSearchTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
        store = StickerStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun metadataIsNormalizedAndSearchesAcrossStickerAndPackFields() {
        val pack = store.createPack("Office Reactions", category = PackCategory.WORK)
        assertTrue(store.addSticker(StickerItem(
            id = "sticker-1",
            packId = pack.id,
            fileName = "generated.webp",
            sha256 = "deadbeef",
            createdAt = 1234,
            label = "  Monday   Mood  ",
            tags = "Funny; Work, funny, team chat"
        )))

        val stored = store.listStickers(pack.id).single()
        assertEquals("Monday Mood", stored.label)
        assertEquals("Funny, Work, team chat", stored.tags)
        assertEquals("Monday Mood", stored.displayName)

        assertEquals(stored.id, store.searchStickers("monday").single().item.id)
        assertEquals(stored.id, store.searchStickers("team chat").single().item.id)
        assertEquals(stored.id, store.searchStickers("office").single().item.id)
        assertEquals(stored.id, store.searchStickers("work").single().item.id)
        assertEquals(stored.id, store.searchStickers("generated.webp").single().item.id)
        assertEquals(stored.id, store.searchStickers("monday work").single().item.id)
        assertEquals(stored.id, store.searchStickers("office funny").single().item.id)
        assertEquals(setOf(pack.id), store.searchStickerPackIds("team Monday"))
        assertTrue(store.searchStickers("monday vacation").isEmpty())
        assertTrue(store.searchStickers("does-not-exist").isEmpty())
    }

    @Test
    fun updateStickerMetadataKeepsStickerAndBumpsPackVersion() {
        val pack = store.createPack("Editable")
        assertTrue(store.addSticker(StickerItem(
            "sticker-2", pack.id, "sticker.webp", "cafebabe", 2345
        )))
        val before = store.getPack(pack.id)!!.version

        store.updateStickerMetadata(pack.id, "sticker-2", "Reaction", "wow, wow; favorite")

        val sticker = store.listStickers(pack.id).single()
        assertEquals("Reaction", sticker.label)
        assertEquals("wow, favorite", sticker.tags)
        assertEquals(before + 1, store.getPack(pack.id)!!.version)
    }

    @Test
    fun exactStickerReactionRanksBeforeTagAndPackMatches() {
        val team = store.createPack("Team")
        val work = store.createPack("Work Reactions")
        assertTrue(store.addSticker(StickerItem("exact", team.id, "exact.webp", "hash-1", 1,
            label = "work")))
        assertTrue(store.addSticker(StickerItem("tag", team.id, "tag.webp", "hash-2", 3,
            label = "Relief", tags = "work")))
        assertTrue(store.addSticker(StickerItem("pack", work.id, "pack.webp", "hash-3", 5,
            label = "Hello")))

        assertEquals(listOf("exact", "tag", "pack"), store.searchStickers("work").map { it.item.id })
        assertEquals(listOf("exact", "tag"), store.searchStickers("work", limit = 2).map { it.item.id })
    }

    @Test
    fun indexedCountsAndHashesStayScopedToPack() {
        val first = store.createPack("First")
        val second = store.createPack("Second")
        assertEquals(0, store.stickerCount(first.id))
        assertTrue(store.addSticker(StickerItem("one", first.id, "one.webp", "shared", 1)))
        assertEquals(1, store.stickerCount(first.id))
        assertEquals(0, store.stickerCount(second.id))
        assertTrue(store.hasStickerHash("shared"))
        assertTrue(store.hasStickerHash("shared", first.id))
        assertTrue(!store.hasStickerHash("shared", second.id))
        store.deleteSticker(first.id, "one")
        assertEquals(0, store.stickerCount(first.id))
        assertTrue(!store.hasStickerHash("shared"))
    }

    @Test
    fun transferRecognizesDuplicateEvenWhenTargetIsFull() {
        val source = store.createPack("Source")
        val target = store.createPack("Full")
        val original = StickerItem("source", source.id, "source.webp", "hash-0", 1)
        assertTrue(store.addSticker(original))
        repeat(30) { index ->
            assertTrue(store.addSticker(StickerItem("target-$index", target.id,
                "$index.webp", "hash-$index", index.toLong())))
        }
        assertEquals(ImportResult.Duplicate,
            StickerFiles(context, store).transferSticker(original, target.id, move = true))
        assertEquals(1, store.stickerCount(source.id))
        assertEquals(30, store.stickerCount(target.id))
    }

    companion object {
        private const val DB_NAME = "stickerdock.db"
    }
}
