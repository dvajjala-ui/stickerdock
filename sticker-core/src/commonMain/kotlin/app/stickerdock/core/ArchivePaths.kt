package app.stickerdock.core

/** Shared ZIP identity contract: writers use lower-case UUIDs; readers retain
 * compatibility with Foundation's upper-case UUID spelling. No path segments
 * supplied by an archive are used as local output directories.
 */
object ArchivePaths {
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    fun canonicalId(value: String): String? = if (value.length == 36) value.lowercase().takeIf(uuid::matches) else null
    fun isId(value: String): Boolean = canonicalId(value) != null
    fun isStickerName(value: String): Boolean = value.endsWith(".webp") && isId(value.removeSuffix(".webp"))
    fun isEntry(value: String): Boolean {
        if (value == "manifest.json") return true
        val parts = value.split('/')
        return parts.size == 3 && parts[0] == "packs" && isId(parts[1]) && isStickerName(parts[2])
    }
}
