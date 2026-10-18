package app.stickerdock.duo

import java.net.URI

/** A public ID resolves only through the reviewed catalog, never a caller's URL. */
object PublicPackLinks {
    private val idPattern = Regex("[a-z0-9-]{3,60}")

    fun code(id: String): String { require(idPattern.matches(id)); return "SD:$id" }
    fun link(id: String): String { require(idPattern.matches(id)); return "stickerdock://pack/$id" }

    fun parse(raw: String): String? {
        val value = raw.trim()
        if (value.length > 256) return null
        if (value.startsWith("SD:")) return value.removePrefix("SD:").takeIf(idPattern::matches)
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme != "stickerdock" || uri.rawAuthority != "pack" || uri.rawQuery != null || uri.rawFragment != null) return null
        return uri.rawPath?.removePrefix("/")?.takeIf { uri.rawPath == "/$it" && idPattern.matches(it) }
    }

    fun fromSharedText(raw: String): String? {
        if (raw.length > 4096) return null
        val ids = raw.lineSequence().mapNotNull(::parse).distinct().toList()
        return ids.singleOrNull()
    }

    fun shareText(name: String, id: String) = "$name\n${code(id)}\n${link(id)}\n\nOpen this code in StickerDock → Discover → Open pack code.\nGet StickerDock: https://stickerdock.vercel.app/download"
}
