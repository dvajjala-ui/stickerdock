package app.stickerdock.duo

import android.content.Context
import android.net.Uri

/** One picker selection becomes compatible packs without a pack-setup step. */
class PhotoPackImporter(
    private val context: Context,
    private val store: StickerStore,
    private val files: StickerFiles
) {
    data class Summary(val packIds: List<String>, val added: Int, val duplicates: Int, val failed: Int, val firstError: String?)

    fun import(uris: List<Uri>, title: String = "My stickers"): Summary {
        require(uris.size <= 100) { "Choose up to 100 images at once" }
        val current = mutableMapOf<StickerKind, StickerPack>()
        val created = mutableListOf<String>()
        var added = 0
        var duplicates = 0
        var failed = 0
        var firstError: String? = null
        uris.forEach { uri ->
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { StickerFiles.boundedRead(it, 20_000_000) }
                    ?: error("Could not open image")
                val kind = if (WebpSticker.isAnimatedHeader(bytes)) StickerKind.ANIMATED else StickerKind.STATIC
                var pack = current[kind]
                if (pack == null || store.stickerCount(pack.id) >= 30) {
                    pack = store.createPack(if (kind == StickerKind.ANIMATED) "$title animated" else title, kind)
                    current[kind] = pack
                    created += pack.id
                }
                when (val result = files.importBytes(pack.id, bytes, globalDedup = true, label = files.sourceLabel(uri))) {
                    ImportResult.Added -> added++
                    ImportResult.Duplicate -> duplicates++
                    is ImportResult.Failed -> { failed++; if (firstError == null) firstError = result.reason }
                }
            } catch (e: Exception) {
                failed++
                if (firstError == null) firstError = e.message ?: "Could not import image"
            }
        }
        val surviving = created.filter { id ->
            if (store.stickerCount(id) == 0) { files.deletePack(id); false } else true
        }
        return Summary(surviving, added, duplicates, failed, firstError)
    }
}
