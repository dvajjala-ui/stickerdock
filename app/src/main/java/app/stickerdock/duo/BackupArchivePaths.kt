package app.stickerdock.duo

/** Shared ZIP allowlist for canonical app-generated UUID asset paths. */
internal object BackupArchivePaths {
    private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val id = Regex(UUID_PATTERN)
    private val stickerName = Regex("$UUID_PATTERN\\.webp")
    private val assetPath = Regex("packs/$UUID_PATTERN/$UUID_PATTERN\\.webp")

    fun isId(value: String): Boolean = id.matches(value)
    fun isStickerName(name: String): Boolean = stickerName.matches(name)
    fun isEntry(name: String): Boolean = name == "manifest.json" || assetPath.matches(name)
}
