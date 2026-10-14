package app.stickerdock.duo

import android.content.Context

/** Catalog imports are tracked separately from user-owned pack metadata. */
class CatalogImports(context: Context, private val store: StickerStore) {
    private val prefs = context.getSharedPreferences("catalog_imports", Context.MODE_PRIVATE)

    fun importedPackIds(id: String, hash: String): List<String> {
        if (prefs.getString("hash:$id", null) != hash) return emptyList()
        val ids = prefs.getStringSet("packs:$id", emptySet()).orEmpty().toList()
        return if (ids.isNotEmpty() && ids.all { store.getPack(it) != null }) ids else emptyList()
    }

    fun record(id: String, hash: String, packIds: Set<String>) {
        require(packIds.isNotEmpty())
        prefs.edit().putString("hash:$id", hash).putStringSet("packs:$id", packIds).apply()
    }
}
