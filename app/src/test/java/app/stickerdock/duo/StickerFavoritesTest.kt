package app.stickerdock.duo

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StickerFavoritesTest {
    private lateinit var context: Context
    private lateinit var store: StickerStore
    private lateinit var favorites: StickerFavorites

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        context.getSharedPreferences(StickerFavorites.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        store = StickerStore(context)
        favorites = StickerFavorites(context, store)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase("stickerdock.db")
        context.getSharedPreferences(StickerFavorites.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun favoritesSurviveReopenWithoutChangingPackVersionMediaOrPrivateRating() {
        val pack = store.createPack("Private reactions", rating = PackRating.MATURE, category = PackCategory.REACTIONS)
        val item = add(pack, "favorite", 1, "My reaction", "happy")
        val before = store.getPack(pack.id)!!.version

        favorites.set(item.id, true)
        store.close()
        store = StickerStore(context)
        favorites = StickerFavorites(context, store)

        val result = favorites.snapshot().single()
        assertEquals(item, result.item)
        assertEquals(PackRating.MATURE, result.pack.rating)
        assertEquals(before, result.pack.version)
        assertEquals(8, store.readableDatabase.version)
        favorites.set(item.id, false)
        assertTrue(favorites.snapshot().isEmpty())
        assertEquals(item, store.stickerById(item.id))
    }

    @Test fun deletedRowsArePrunedAndOtherFavoritesStayIntact() {
        val first = store.createPack("One")
        val second = store.createPack("Two")
        val removed = add(first, "deleted", 1)
        val kept = add(second, "kept", 2)
        favorites.set(removed.id, true)
        favorites.set(kept.id, true)
        store.deletePack(first.id)

        assertEquals(listOf(kept.id), favorites.snapshot().map { it.item.id })
        assertFalse(StickerFavorites(context, store).contains(removed.id))
        assertTrue(favorites.contains(kept.id))
    }

    @Test fun favoriteMovesToNewRowButAnIndependentCopyDoesNotBecomeFavorite() {
        val origin = store.createPack("Origin")
        val destination = store.createPack("Destination")
        val original = add(origin, "original", 1)
        val copy = add(destination, "copy", 2)
        favorites.set(original.id, true)
        assertFalse(favorites.contains(copy.id))
        store.deleteSticker(origin.id, original.id)
        favorites.move(original.id, copy.id)

        assertEquals(listOf(copy.id), favorites.snapshot().map { it.item.id })
        assertFalse(favorites.contains(original.id))
    }

    @Test fun unknownIdCannotBeFavoritedAndQuotedIdsAreBoundParameters() {
        val pack = store.createPack("Known")
        val item = add(pack, "known", 1)
        assertTrue(store.stickersByIds(setOf("missing' OR 1=1 --")).isEmpty())
        try {
            favorites.set("missing", true)
            throw AssertionError("Missing rows must not become favorite shortcuts")
        } catch (_: IllegalArgumentException) { }
        assertTrue(favorites.snapshot().isEmpty())
        assertEquals(item, store.stickerById(item.id))
    }

    @Test fun batchesAboveSQLiteArgumentLimitKeepAllRowsInStableOrder() {
        val pack = store.createPack("Large library")
        val items = (0 until 1005).map { add(pack, "item-$it", it.toLong()) }
        val result = store.stickersByIds(items.mapTo(mutableSetOf()) { it.id } + "missing")
        assertEquals(items.size, result.size)
        assertEquals(items.reversed().map { it.id }, result.map { it.item.id })
        assertTrue(store.stickersByIds(emptySet()).isEmpty())
    }

    @Test fun favoritesRespectSearchTypeTopicAndSortWithoutLosingMatureItems() {
        val photo = store.createPack("Office", category = PackCategory.WORK)
        val motion = store.createPack("Private laughs", StickerKind.ANIMATED, PackRating.MATURE, PackCategory.REACTIONS)
        val first = add(photo, "first", 1, "Zulu", "Monday")
        val second = add(motion, "second", 2, "Alpha", "laugh")
        favorites.set(first.id, true)
        favorites.set(second.id, true)
        val all = favorites.snapshot()
        assertEquals(listOf(second.id, first.id), StickerFavorites.filter(all, "", "All", null, "Newest", emptyMap()).map { it.item.id })
        assertEquals(listOf(first.id), StickerFavorites.filter(all, "monday", "Static", PackCategory.WORK, "Name", emptyMap()).map { it.item.id })
        assertEquals(listOf(second.id), StickerFavorites.filter(all, "private", "Animated", null, "Name", emptyMap()).map { it.item.id })
        assertTrue(StickerFavorites.filter(all, "", "Static", PackCategory.REACTIONS, "Newest", emptyMap()).isEmpty())
        assertEquals(listOf(first.id, second.id), StickerFavorites.filter(all, "", "All", null, "Largest", mapOf(photo.id to 30, motion.id to 3)).map { it.item.id })
    }

    private fun add(pack: StickerPack, id: String, time: Long, label: String = "", tags: String = ""): StickerItem {
        val item = StickerItem(id, pack.id, "$id.webp", "hash-$id", time, label, tags)
        assertTrue(store.addSticker(item))
        return item
    }
}
