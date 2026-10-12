package app.stickerdock.duo

import android.content.Context
import android.net.Uri
import java.util.zip.ZipInputStream

/**
 * Import user-supplied .zip/.wastickers archives containing image files.
 * This does NOT access Sticker.ly's private storage or bypass its APIs.
 * Unknown manifests are ignored: images are grouped by directory and media kind.
 */
class PackZipImporter(
    private val context: Context,
    private val store: StickerStore,
    private val files: StickerFiles
) {
    data class Summary(val packs: Int, val imported: Int, val duplicates: Int, val skipped: Int)
    private data class Asset(val group: String, val kind: StickerKind, val bytes: ByteArray)

    fun import(uri: Uri, title: String = "Imported"): Summary {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read archive")
        val assets = mutableListOf<Asset>()
        val seen = mutableSetOf<String>()
        var entries = 0
        var uncompressed = 0L
        var skipped = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 1200) { "Too many ZIP entries" }
                val path = entry.name.replace('\\', '/').trimEnd('/')
                require(path.isNotEmpty() && !path.startsWith("/") && !path.contains("..") &&
                    !path.split('/').any { it.isEmpty() || it == "." } && path.length <= 250) { "Unsafe archive path" }
                require(seen.add(path)) { "Duplicate ZIP entry" }
                if (entry.isDirectory) { zip.closeEntry(); continue }
                val bytes = StickerFiles.boundedRead(zip, 1_000_000)
                uncompressed += bytes.size
                require(uncompressed <= 75_000_000) { "Archive exceeds 75 MB" }
                val extension = path.substringAfterLast('.', "").lowercase()
                if (extension !in setOf("png", "jpg", "jpeg", "webp")) {
                    skipped++
                    zip.closeEntry()
                    continue
                }
                val animated = WebpSticker.isAnimatedHeader(bytes)
                val group = path.substringBeforeLast('/', title).takeIf { it != path && it.isNotBlank() } ?: title
                assets += Asset(group.take(60), if (animated) StickerKind.ANIMATED else StickerKind.STATIC, bytes)
                zip.closeEntry()
            }
        }
        require(assets.isNotEmpty()) { "Archive has no PNG, JPEG or WebP images" }
        var created = 0
        var imported = 0
        var duplicates = 0
        assets.groupBy { it.group to it.kind }.forEach { (identity, group) ->
            group.chunked(30).forEachIndexed { index, chunk ->
                val suffix = if (index == 0) "" else " ${index + 1}"
                val packName = (identity.first.take(60 - suffix.length) + suffix).ifBlank { title }
                val pack = store.createPack(packName, identity.second)
                created++
                chunk.forEach { asset ->
                    when (files.importBytes(pack.id, asset.bytes)) {
                        ImportResult.Added -> imported++
                        ImportResult.Duplicate -> duplicates++
                        is ImportResult.Failed -> skipped++
                    }
                }
                if (store.listStickers(pack.id).isEmpty()) { files.deletePack(pack.id); created-- }
            }
        }
        return Summary(created, imported, duplicates, skipped)
    }
}
