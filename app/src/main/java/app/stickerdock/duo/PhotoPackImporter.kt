package app.stickerdock.duo

import android.content.Context
import android.net.Uri

/** One picker selection becomes compatible packs without a pack-setup step. */
class PhotoPackImporter(
    private val context: Context,
    private val store: StickerStore,
    private val files: StickerFiles
) {
    data class Summary(val packIds: List<String>, val added: Int, val duplicates: Int, val failed: Int, val firstError: String?, val failedUris: List<Uri> = emptyList())

    fun import(uris: List<Uri>, title: String = "My stickers", targetId: String? = null,
        deduplicateLibrary: Boolean = true): Summary {
        require(uris.size <= 100) { "Choose up to 100 images at once" }
        require(title.isNotBlank() && title.length <= 60 && title.none { it.code < 32 }) { "Choose a pack name up to 60 characters" }
        val target = targetId?.let { requireNotNull(store.getPack(it)) { "This pack was removed. Choose another pack." } }
        val current = mutableMapOf<StickerKind, StickerPack>()
        val created = mutableListOf<String>()
        val selectionHashes = mutableSetOf<String>()
        val failedUris = mutableListOf<Uri>()
        var added = 0
        var duplicates = 0
        var failed = 0
        var firstError: String? = null
        uris.forEach { uri ->
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { StickerFiles.boundedRead(it, 20_000_000) }
                    ?: error("Could not open image")
                val kind = if (WebpSticker.isAnimatedHeader(bytes)) StickerKind.ANIMATED else StickerKind.STATIC
                require(!(bytes.size >= 3 && String(bytes, 0, 3, Charsets.US_ASCII) == "GIF")) { "Use Video / GIF to keep this GIF moving" }
                require(target == null || target.kind == kind) { "Choose a ${kind.name.lowercase()} pack for this sticker" }
                // Normalize before selecting a destination so duplicates do not create extra packs.
                val normalized = if (kind == StickerKind.STATIC) files.normalize(bytes) else bytes
                val hash = StickerFiles.sha256(normalized)
                if (hash in selectionHashes || (deduplicateLibrary && store.hasStickerHash(hash))) {
                    duplicates++
                    return@forEach
                }
                var pack = target ?: current[kind]
                if (pack == null || (target == null && store.stickerCount(pack.id) >= 30)) {
                    val part = created.count { store.getPack(it)?.kind == kind } + 1
                    val base = title.trim().take(45) + if (kind == StickerKind.ANIMATED) " animated" else ""
                    pack = store.createPack(base + if (part > 1) " $part" else "", kind)
                    current[kind] = pack
                    created += pack.id
                }
                when (val result = files.restoreBytes(pack.id, normalized, hash, label = files.sourceLabel(uri))) {
                    ImportResult.Added -> { added++; selectionHashes += hash }
                    ImportResult.Duplicate -> duplicates++
                    is ImportResult.Failed -> { failed++; failedUris += uri; if (firstError == null) firstError = result.reason }
                }
            } catch (e: Exception) {
                failed++
                failedUris += uri
                if (firstError == null) firstError = e.message ?: "Could not import image"
            }
        }
        val surviving = created.filter { id ->
            if (store.stickerCount(id) == 0) { files.deletePack(id); false } else true
        }
        return Summary(if (target != null) listOf(target.id) else surviving, added, duplicates, failed, firstError, failedUris)
    }
}
