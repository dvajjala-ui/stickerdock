package app.stickerdock.duo

import java.net.URI

internal object CatalogUrls {
    fun normalizeBase(value: String): String {
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "Catalog must use HTTPS" }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "Invalid catalog URL" }
        require(uri.path.endsWith("/")) { "Catalog base URL must end with /" }
        return uri.toASCIIString()
    }

    fun index(base: String) = normalizeBase(base) + "packs.json"

    fun pack(base: String, file: String): String {
        require(Regex("""[a-zA-Z0-9_-]{1,64}\.zip""").matches(file)) { "Invalid pack filename" }
        return normalizeBase(base) + "packs/" + file
    }

    fun isAllowed(base: String, url: String): Boolean {
        val normalized = normalizeBase(base)
        if (url == normalized + "packs.json") return true
        val prefix = normalized + "packs/"
        return url.startsWith(prefix) && try {
            pack(normalized, url.removePrefix(prefix)) == url
        } catch (_: IllegalArgumentException) { false }
    }
}
