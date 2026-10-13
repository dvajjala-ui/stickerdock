package app.stickerdock.duo

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Import user-supplied .zip/.wastickers archives containing image files.
 * This does NOT access Sticker.ly's private storage or bypass its APIs.
 *
 * Generic ZIPs are grouped by image metadata/directory. Sticker Maker style
 * .wastickers archives are also recognized by root title.txt/author.txt files;
 * their explicit title is used for flat packs and their 96x96 PNG tray image is
 * not imported as a sticker.
 */
class PackZipImporter(
    private val context: Context,
    private val store: StickerStore,
    private val files: StickerFiles
) {
    data class Summary(val packs: Int, val imported: Int, val duplicates: Int, val skipped: Int)

    private data class Asset(
        val path: String,
        val recoveredName: String?,
        val kind: StickerKind,
        val bytes: ByteArray,
        val width: Int,
        val height: Int
    )

    fun import(uri: Uri, title: String = "Imported"): Summary {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read archive")
        val assets = mutableListOf<Asset>()
        val seen = mutableSetOf<String>()
        var archiveTitle: String? = null
        var wastickersMetadataPresent = false
        var entries = 0
        var uncompressed = 0L
        var skipped = 0

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= MAX_ENTRIES) { "Too many ZIP entries" }
                val path = entry.name.replace('\\', '/').trimEnd('/')
                require(
                    path.isNotEmpty() && !path.startsWith("/") && !path.contains("..") &&
                        !path.split('/').any { it.isEmpty() || it == "." } && path.length <= MAX_PATH_LENGTH
                ) { "Unsafe archive path" }
                require(seen.add(path)) { "Duplicate ZIP entry" }
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                val fileName = path.substringAfterLast('/')
                val rootMetadata = !path.contains('/') &&
                    (fileName.equals("title.txt", ignoreCase = true) ||
                        fileName.equals("author.txt", ignoreCase = true))
                if (rootMetadata) {
                    val bytes = StickerFiles.boundedRead(zip, MAX_METADATA_BYTES)
                    uncompressed += bytes.size
                    require(uncompressed <= MAX_ARCHIVE_BYTES) { "Archive exceeds 75 MB" }
                    wastickersMetadataPresent = true
                    if (fileName.equals("title.txt", ignoreCase = true)) {
                        archiveTitle = decodeMetadataText(bytes)
                    }
                    zip.closeEntry()
                    continue
                }

                val bytes = StickerFiles.boundedRead(zip, MAX_IMAGE_BYTES)
                uncompressed += bytes.size
                require(uncompressed <= MAX_ARCHIVE_BYTES) { "Archive exceeds 75 MB" }
                val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (extension !in IMAGE_EXTENSIONS) {
                    skipped++
                    zip.closeEntry()
                    continue
                }

                val animated = WebpSticker.isAnimatedHeader(bytes)
                val recoveredName = if (extension == "webp") StickerPackMetadata.read(bytes)?.packName else null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                assets += Asset(
                    path = path,
                    recoveredName = recoveredName,
                    kind = if (animated) StickerKind.ANIMATED else StickerKind.STATIC,
                    bytes = bytes,
                    width = bounds.outWidth,
                    height = bounds.outHeight
                )
                zip.closeEntry()
            }
        }

        val importable = assets.filterNot { asset ->
            wastickersMetadataPresent && asset.isWastickersTray()
        }
        require(importable.isNotEmpty()) { "Archive has no PNG, JPEG or WebP sticker images" }

        var created = 0
        var imported = 0
        var duplicates = 0
        importable.groupBy { asset -> groupName(asset, archiveTitle, title) to asset.kind }
            .forEach { (identity, group) ->
                group.chunked(30).forEachIndexed { index, chunk ->
                    val suffix = if (index == 0) "" else " ${index + 1}"
                    val packName = (identity.first.take(60 - suffix.length) + suffix).ifBlank { safeFallback(title) }
                    val pack = store.createPack(packName, identity.second)
                    created++
                    chunk.forEach { asset ->
                        when (files.importBytes(
                            pack.id,
                            asset.bytes,
                            label = asset.path.substringAfterLast('/')
                        )) {
                            ImportResult.Added -> imported++
                            ImportResult.Duplicate -> duplicates++
                            is ImportResult.Failed -> skipped++
                        }
                    }
                    if (store.listStickers(pack.id).isEmpty()) {
                        files.deletePack(pack.id)
                        created--
                    }
                }
            }
        return Summary(created, imported, duplicates, skipped)
    }

    private fun groupName(asset: Asset, archiveTitle: String?, fallback: String): String {
        val directory = asset.path.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
        val explicitFlatTitle = archiveTitle?.takeIf { directory == null }
        return (explicitFlatTitle ?: asset.recoveredName ?: directory ?: safeFallback(fallback))
            .trim()
            .take(60)
            .ifBlank { safeFallback(fallback) }
    }

    private fun Asset.isWastickersTray(): Boolean =
        path.substringAfterLast('.', "").equals("png", ignoreCase = true) &&
            width == 96 && height == 96

    private fun decodeMetadataText(bytes: ByteArray): String? =
        bytes.toString(Charsets.UTF_8)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)
            .takeIf { it.isNotBlank() }

    private fun safeFallback(value: String): String = value.trim().take(60).ifBlank { "Imported" }

    companion object {
        private const val MAX_ENTRIES = 1200
        private const val MAX_PATH_LENGTH = 250
        private const val MAX_METADATA_BYTES = 4096
        private const val MAX_IMAGE_BYTES = 1_000_000
        private const val MAX_ARCHIVE_BYTES = 75_000_000L
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
