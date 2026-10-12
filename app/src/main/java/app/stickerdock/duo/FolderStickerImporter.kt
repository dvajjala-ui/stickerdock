package app.stickerdock.duo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque
import java.util.Locale

/**
 * On-device collection migration. Users select the directory with Android's
 * system folder picker; no broad storage permission, root, or hidden app APIs.
 *
 * Useful sources:
 *   Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers
 *   Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers
 *   any personal sticker image export directory.
 */
class FolderStickerImporter(
    private val context: Context,
    private val store: StickerStore,
    private val files: StickerFiles
) {
    data class Result(val filesScanned: Int, val packsCreated: Int, val added: Int, val duplicates: Int, val rejected: Int)
    private data class Folder(val node: DocumentFile, val depth: Int)
    private data class Group(val title: String, val kind: StickerKind)

    fun import(treeUri: Uri, fallbackName: String = "Imported"): Result {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Cannot access the selected folder")
        require(root.isDirectory && root.canRead()) { "Selected folder is not readable" }

        // Groups may span 30-sticker packs; never create a pack until an item is accepted.
        val groups = mutableMapOf<Group, MutableList<StickerPack>>()
        val visited = mutableSetOf<String>()
        val existingHashes = store.allStickerHashes().toMutableSet()
        val queue = ArrayDeque<Folder>().apply { add(Folder(root, 0)) }
        var visitedNodes = 0
        var scanned = 0
        var added = 0
        var duplicate = 0
        var rejected = 0
        var created = 0
        var totalBytes = 0L

        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            if (!visited.add(folder.node.uri.toString())) continue
            require(folder.depth <= 6) { "Folder tree too deep" }
            for (child in folder.node.listFiles()) {
                if (++visitedNodes > 40_000) error("Folder contains too many entries")
                if (child.isDirectory) {
                    if (folder.depth < 6) queue.add(Folder(child, folder.depth + 1))
                    continue
                }
                if (!child.isFile) continue
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (ext !in setOf("webp", "png", "jpg", "jpeg")) continue
                if (++scanned > 20_000) error("Import limit of 20,000 images exceeded")
                if (child.length() > 1_000_000) { rejected++; continue }
                val image = try {
                    context.contentResolver.openInputStream(child.uri)?.use { StickerFiles.boundedRead(it, 1_000_000) }
                } catch (_: Exception) { null }
                if (image == null) { rejected++; continue }
                totalBytes += image.size
                require(totalBytes <= 1_500_000_000L) { "Selected folder contains over 1.5 GB of image data; import in smaller batches" }
                val kind = if (WebpSticker.isAnimatedHeader(image)) StickerKind.ANIMATED else StickerKind.STATIC
                val metadata = if (ext == "webp") StickerPackMetadata.read(image) else null
                val title = (metadata?.packName ?: folder.node.name ?: fallbackName)
                    .trim().take(60).ifBlank { fallbackName }
                val key = Group(title, kind)
                val belongingPacks = groups.getOrPut(key) { mutableListOf() }

                // Optimize repeat imports: hashes are checked after normalize/import,
                // and the in-memory index is updated from the resulting DB entry.
                val current = belongingPacks.lastOrNull()
                val candidate = if (current == null || store.listStickers(current.id).size >= 30) {
                    store.createPack(title, kind).also { belongingPacks += it; created++ }
                } else current
                when (files.importBytes(candidate.id, image)) {
                    ImportResult.Added -> {
                        val imported = store.listStickers(candidate.id).last()
                        if (!existingHashes.add(imported.sha256)) {
                            files.deleteSticker(imported)
                            duplicate++
                        } else added++
                    }
                    ImportResult.Duplicate -> duplicate++
                    is ImportResult.Failed -> rejected++
                }
            }
        }
        // Remove empty packs caused by invalid/duplicate entries, including
        // trailing packs for which all files were skipped.
        groups.values.flatten().filter { store.listStickers(it.id).isEmpty() }.forEach {
            files.deletePack(it.id)
            created--
        }
        return Result(scanned, created, added, duplicate, rejected)
    }
}
