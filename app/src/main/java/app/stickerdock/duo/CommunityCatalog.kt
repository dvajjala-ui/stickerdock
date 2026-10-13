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
    data class Listing(val id: String, val name: String, val creator: String, val downloadUrl: String, val sha256: String)
    private val base = CatalogUrls.normalizeBase(BuildConfig.COMMUNITY_CATALOG_BASE_URL)
    fun browse(): List<Listing> {
        // Public storage intentionally stays empty until the first reviewed pack is
        // published. A missing index therefore means "no community packs yet", not
        // a broken app. All other HTTP errors and all pack downloads remain strict.
        val bytes = fetchOrNull(CatalogUrls.index(base), 512_000, allowMissing = true)
            ?: return emptyList()
        val doc = JSONObject(bytes.toString(Charsets.UTF_8))
        require(doc.getInt("version") == 1)
        val array = doc.getJSONArray("packs")
        require(array.length() <= 100)
        return (0 until array.length()).mapNotNull { i ->
            val item = array.getJSONObject(i)
            // Safe public catalog until verified moderation and age gate exist.
            if (item.optString("rating", "GENERAL") != "GENERAL") return@mapNotNull null
            val name = item.getString("name")
            val creator = item.getString("creator")
            val id = item.getString("id")
            val file = item.getString("file")
            val hash = item.getString("sha256")
            require(name.length in 1..60 && creator.length in 1..60)
            require(Regex("[a-z0-9-]{3,60}").matches(id))
            require(Regex("""[a-zA-Z0-9_-]{1,64}\.zip""").matches(file))
            require(Regex("[0-9a-f]{64}").matches(hash))
            Listing(id, name, creator, CatalogUrls.pack(base, file), hash)
        }
    }
    fun downloadAndImport(item: Listing): StickerBackup.RestoreSummary {
        require(CatalogUrls.isAllowed(base, item.downloadUrl) &&
            item.downloadUrl.startsWith(base + "packs/"))
        val bytes = fetch(item.downloadUrl, 25_000_000)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(hash == item.sha256) { "Public pack checksum mismatch" }
        val tmp = File.createTempFile("catalog-", ".zip", context.cacheDir)
        return try {
            tmp.writeBytes(bytes)
            backup.restore(Uri.fromFile(tmp))
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
            require(responseCode == HttpURLConnection.HTTP_OK) { "Catalog unavailable: HTTP $responseCode" }
            return connection.inputStream.use { StickerFiles.boundedRead(it, maxBytes) }
        } finally { connection.disconnect() }
    }
}
