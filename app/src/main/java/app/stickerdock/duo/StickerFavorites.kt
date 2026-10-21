package app.stickerdock.duo

import android.content.Context

/** Device-local shortcuts. Favorite status never changes a pack's export or cloud version. */
class StickerFavorites(context: Context, private val store: StickerStore) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun snapshot(): List<StickerSearchResult> = synchronized(StickerFavorites::class.java) {
        val saved = preferences.getStringSet(KEY, emptySet()).orEmpty().toSet()
        val found = store.stickersByIds(saved)
        val existing = found.mapTo(mutableSetOf()) { it.item.id }
        if (saved != existing) preferences.edit().putStringSet(KEY, existing).apply()
        found
    }

    fun contains(id: String): Boolean = synchronized(StickerFavorites::class.java) {
        id in preferences.getStringSet(KEY, emptySet()).orEmpty()
    }

    fun set(id: String, favorite: Boolean) = synchronized(StickerFavorites::class.java) {
        val saved = preferences.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (favorite) {
            require(store.stickerById(id) != null) { "Sticker was removed. Reopen your library." }
            saved.add(id)
        } else saved.remove(id)
        check(preferences.edit().putStringSet(KEY, saved).commit()) { "Could not save favorites. Please retry." }
    }

    /** Moving creates a new row; copies remain independent from the original shortcut. */
    fun move(originalId: String, destinationId: String) = synchronized(StickerFavorites::class.java) {
        val saved = preferences.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (originalId !in saved) return@synchronized
        require(store.stickerById(destinationId) != null) { "Moved sticker was not saved" }
        saved.remove(originalId)
        saved.add(destinationId)
        check(preferences.edit().putStringSet(KEY, saved).commit()) { "Sticker moved, but its favorite shortcut could not be saved" }
    }

    companion object {
        internal const val PREFERENCES = "sticker_favorites"
        private const val KEY = "sticker_ids"

        fun filter(items: List<StickerSearchResult>, query: String, kind: String,
            category: PackCategory?, sort: String, counts: Map<String, Int>): List<StickerSearchResult> {
            val needle = StickerMetadata.cleanLabel(query)
            val matching = items.filter { result ->
                (kind == "All" || result.pack.kind.name.equals(kind, ignoreCase = true)) &&
                    (category == null || result.pack.category == category) &&
                    (needle.isBlank() || listOf(result.item.label, result.item.tags, result.item.fileName,
                        result.pack.name, result.pack.category.label).any { it.contains(needle, ignoreCase = true) })
            }
            return when (sort) {
                "Name" -> matching.sortedWith(compareBy<StickerSearchResult> { it.item.displayName.lowercase() }.thenBy { it.item.id })
                "Largest" -> matching.sortedWith(compareByDescending<StickerSearchResult> { counts[it.pack.id] ?: 0 }.thenByDescending { it.item.createdAt }.thenBy { it.item.id })
                else -> matching.sortedWith(compareByDescending<StickerSearchResult> { it.item.createdAt }.thenBy { it.item.id })
            }
        }
    }
}
