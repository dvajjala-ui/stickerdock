package app.stickerdock.duo

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Read-only, moderator-curated public catalog. No user uploads and no adult material
 * can be served through this client without a separate reviewed age-safety implementation.
 */
class CommunityCatalog(
    private val context: Context,
    private val backup: StickerBackup
) {
    data class Listing(
        val id: String,
        val name: String,
        val creator: String,
        val downloadUrl: String,
        val sha256: String,
        val categories: List<PackCategory> = emptyList(),
        val publisherId: String = "",
        val previewUrl: String? = null,
        val previewSha256: String? = null,
        val stickerCount: Int? = null,
        val kinds: List<StickerKind> = emptyList()
    )
    private val base = CatalogUrls.normalizeBase(BuildConfig.COMMUNITY_CATALOG_BASE_URL)

    fun browse(): List<Listing> {
        // Public storage intentionally stays empty until the first reviewed pack is
        // published. A missing index therefore means "no community packs yet", not
        // a broken app. All other HTTP errors and all pack downloads remain strict.
        val bytes = fetchOrNull(CatalogUrls.index(base), 512_000, allowMissing = true)
            ?: return emptyList()
        val blocked = context.getSharedPreferences("catalog_safety", Context.MODE_PRIVATE).getStringSet("blocked", emptySet()).orEmpty()
        return parseCatalog(bytes, base).filter { (it.publisherId.ifBlank { it.id }) !in blocked }
    }

    fun hide(item: Listing) {
        val prefs = context.getSharedPreferences("catalog_safety", Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet("blocked", emptySet()).orEmpty() + item.publisherId.ifBlank { item.id }
        prefs.edit().putStringSet("blocked", blocked).apply()
    }

    fun clearHidden() { context.getSharedPreferences("catalog_safety", Context.MODE_PRIVATE).edit().remove("blocked").apply() }

    /** Only a small public thumbnail is fetched during browsing. Old ZIPs are read on explicit preview. */
    fun preview(item: Listing, allowLegacyDownload: Boolean = false): ByteArray? {
        val directory = File(context.cacheDir, "catalog-previews").apply { mkdirs() }
        val key = item.previewSha256 ?: "legacy-${item.sha256}"
        require(Regex("(?:legacy-)?[0-9a-f]{64}").matches(key)) { "Invalid preview checksum" }
        val cached = File(directory, "$key.webp")
        if (cached.exists()) {
            val bytes = cached.inputStream().use { StickerFiles.boundedRead(it, 320_000) }
            if ((item.previewSha256 == null || sha256(bytes) == item.previewSha256) && validPreview(bytes)) return bytes
            cached.delete()
        }
        if (item.previewUrl == null && !allowLegacyDownload) return null
        val bytes = if (item.previewUrl != null) {
            fetch(item.previewUrl, 320_000).also {
                require(sha256(it) == item.previewSha256) { "Preview checksum mismatch" }
            }
        } else {
            require(item.downloadUrl.startsWith(base + "packs/")) { "Invalid pack URL" }
            val archive = fetch(item.downloadUrl, 25_000_000)
            require(sha256(archive) == item.sha256) { "Public pack checksum mismatch" }
            val temporary = File.createTempFile("catalog-preview-", ".zip", context.cacheDir)
            try {
                temporary.writeBytes(archive)
                CatalogPreview.fromArchive(temporary)
            } finally { temporary.delete() }
        }
        require(validPreview(bytes)) { "Invalid preview image" }
        val temporary = File.createTempFile("preview-", ".tmp", directory)
        try {
            temporary.writeBytes(bytes)
            // Cache writes are optional. Never expose a partially copied image to a second reader.
            temporary.renameTo(cached)
        } finally { temporary.delete() }
        directory.listFiles()?.filter { it.extension == "webp" }?.sortedByDescending { it.lastModified() }
            ?.drop(24)?.forEach { it.delete() }
        return bytes
    }

    /** Explicit full preview verifies the catalog digest and never imports a sticker. */
    internal fun previewStickers(item: Listing): List<CatalogStickerPreview.Item> {
        require(CatalogUrls.isAllowed(base, item.downloadUrl) && item.downloadUrl.startsWith(base + "packs/")) {
            "Invalid pack URL"
        }
        val bytes = fetch(item.downloadUrl, 25_000_000)
        require(sha256(bytes) == item.sha256) { "Public pack checksum mismatch" }
        val temporary = File.createTempFile("catalog-stickers-", ".zip", context.cacheDir)
        return try {
            temporary.writeBytes(bytes)
            CatalogStickerPreview.fromArchive(temporary)
        } finally { temporary.delete() }
    }

    fun downloadAndImport(item: Listing): StickerBackup.RestoreSummary = synchronized(importLock) {
        StickerStore(context.applicationContext).use { store ->
            val imported = CatalogImports(context, store)
            if (imported.importedPackIds(item.id, item.sha256).isNotEmpty()) {
                return@synchronized StickerBackup.RestoreSummary(0, 0, 0)
            }
        }
        require(CatalogUrls.isAllowed(base, item.downloadUrl) &&
            item.downloadUrl.startsWith(base + "packs/"))
        val bytes = fetch(item.downloadUrl, 25_000_000)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(hash == item.sha256) { "Public pack checksum mismatch" }
        val tmp = File.createTempFile("catalog-", ".zip", context.cacheDir)
        try {
            tmp.writeBytes(bytes)
            StickerStore(context.applicationContext).use { store ->
                val summary = backup.restore(Uri.fromFile(tmp))
                val added = summary.packIds.toSet()
                if (added.isNotEmpty()) CatalogImports(context, store).record(item.id, item.sha256, added)
                summary
            }
        } finally { tmp.delete() }
    }

    private fun fetch(url: String, maxBytes: Int): ByteArray =
        requireNotNull(fetchOrNull(url, maxBytes, allowMissing = false))

    private fun fetchOrNull(url: String, maxBytes: Int, allowMissing: Boolean): ByteArray? {
        require(CatalogUrls.isAllowed(base, url)) { "Only approved catalog URLs are supported" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/octet-stream")
        try {
            val responseCode = connection.responseCode
            if (allowMissing && responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (allowMissing && responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                val error = connection.errorStream?.use { StickerFiles.boundedRead(it, 4096) }
                if (error != null && isMissingObjectResponse(error)) return null
            }
            require(responseCode == HttpURLConnection.HTTP_OK) { "Catalog unavailable: HTTP $responseCode" }
            return connection.inputStream.use { StickerFiles.boundedRead(it, maxBytes) }
        } finally { connection.disconnect() }
    }

    companion object {
        private val importLock = Any()
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        private fun validPreview(bytes: ByteArray): Boolean {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            return bounds.outWidth == 576 && bounds.outHeight == 384 &&
                WebpSticker.isWebpHeader(bytes) && !WebpSticker.isAnimatedHeader(bytes) && bytes.size <= 320_000
        }
        internal fun isMissingObjectResponse(bytes: ByteArray): Boolean = runCatching {
            val error = JSONObject(bytes.toString(Charsets.UTF_8))
            error.optString("code") in setOf("NoSuchKey", "not_found") ||
                (error.optString("statusCode") == "404" && error.optString("message") == "Object not found")
        }.getOrDefault(false)

        /** Parse only explicitly reviewed GENERAL entries; missing moderation data is not safe by default. */
        internal fun parseCatalog(bytes: ByteArray, base: String): List<Listing> {
            val normalizedBase = CatalogUrls.normalizeBase(base)
            val doc = JSONObject(bytes.toString(Charsets.UTF_8))
            require(doc.getInt("version") == 1) { "Unsupported catalog version" }
            val array = doc.getJSONArray("packs")
            require(array.length() <= 100) { "Catalog has too many packs" }
            val seenIds = mutableSetOf<String>()
            val seenFiles = mutableSetOf<String>()

            return (0 until array.length()).mapNotNull { i ->
                val item = array.getJSONObject(i)
                // Fail closed: a missing/unknown rating is never inferred to be GENERAL.
                if (item.optString("rating", "") != "GENERAL") return@mapNotNull null

                val name = item.getString("name")
                val creator = item.getString("creator")
                val id = item.getString("id")
                val file = item.getString("file")
                val hash = item.getString("sha256")
                val categories = item.optJSONArray("categories")?.let { array ->
                    require(array.length() in 1..PackCategory.entries.size) {
                        "Invalid public pack categories"
                    }
                    (0 until array.length()).map { index ->
                        runCatching { PackCategory.valueOf(array.getString(index)) }
                            .getOrElse { throw IllegalArgumentException("Invalid public pack category") }
                    }.also { values ->
                        require(values.distinct().size == values.size) {
                            "Duplicate public pack category"
                        }
                    }
                } ?: emptyList()

                require(name.isNotBlank() && name.length <= 60 && name.none { it.code < 32 }) {
                    "Invalid public pack name"
                }
                require(creator.isNotBlank() && creator.length <= 60 && creator.none { it.code < 32 }) {
                    "Invalid public pack creator"
                }
                require(Regex("[a-z0-9-]{3,60}").matches(id)) { "Invalid public pack id" }
                require(Regex("""[a-zA-Z0-9_-]{1,64}\.zip""").matches(file)) {
                    "Invalid public pack filename"
                }
                require(Regex("[0-9a-f]{64}").matches(hash)) { "Invalid public pack checksum" }
                require(seenIds.add(id)) { "Duplicate public pack id" }
                require(seenFiles.add(file)) { "Duplicate public pack file" }

                val publisher = item.optString("publisher_id", "")
                require(publisher.isEmpty() || Regex("[a-z0-9-]{1,64}").matches(publisher)) { "Invalid publisher" }
                val preview = item.optJSONObject("preview")
                val previewHash = preview?.getString("sha256")
                val previewFile = preview?.getString("file")
                require(previewHash == null || (Regex("[0-9a-f]{64}").matches(previewHash) &&
                    previewFile == "$previewHash.webp")) { "Invalid preview checksum" }
                val count = if (item.has("sticker_count")) item.getInt("sticker_count") else null
                require(count == null || count in 3..100) { "Invalid public sticker count" }
                val kinds = item.optJSONArray("kinds")?.let { values ->
                    require(values.length() in 1..2) { "Invalid public sticker types" }
                    (0 until values.length()).map { StickerKind.valueOf(values.getString(it)) }.also {
                        require(it.distinct().size == it.size) { "Duplicate public sticker type" }
                    }
                }.orEmpty()
                Listing(id, name, creator, CatalogUrls.pack(normalizedBase, file), hash, categories, publisher,
                    previewFile?.let { CatalogUrls.preview(normalizedBase, it) }, previewHash, count, kinds)
            }
        }
    }
}
