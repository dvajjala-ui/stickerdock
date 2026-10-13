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

    companion object {
        private const val DB_NAME = "stickerdock.db"
    }
}
