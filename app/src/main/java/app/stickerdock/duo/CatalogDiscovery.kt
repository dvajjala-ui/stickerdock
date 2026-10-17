package app.stickerdock.duo

import android.content.Context

/** Refreshing discovery reads metadata and save receipts; it never downloads or imports a pack. */
internal class CatalogDiscovery(
    private val context: Context,
    private val store: StickerStore,
    private val browse: () -> List<CommunityCatalog.Listing>
) {
    data class Snapshot(val packs: List<CommunityCatalog.Listing>, val savedIds: Set<String>)

    fun refresh(): Snapshot {
        val packs = browse()
        val imports = CatalogImports(context, store)
        return Snapshot(packs, packs.filter {
            imports.importedPackIds(it.id, it.sha256).isNotEmpty()
        }.mapTo(mutableSetOf()) { it.id })
    }
}
