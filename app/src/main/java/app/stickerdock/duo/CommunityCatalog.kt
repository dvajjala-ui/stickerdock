package app.stickerdock.duo

import android.content.Context
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
        val publisherId: String = ""
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
                Listing(id, name, creator, CatalogUrls.pack(normalizedBase, file), hash, categories, publisher)
            }
        }
    }
}
