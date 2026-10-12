package app.stickerdock.duo

/** Shared ZIP allowlist: a dot before webp must be a literal dot. */
internal object BackupArchivePaths {
    private val stickerName = Regex("[0-9a-f-]{36}\\.webp")
    private val assetPath = Regex("packs/[0-9a-f-]{36}/[0-9a-f-]{36}\\.webp")
    fun isStickerName(name: String): Boolean = stickerName.matches(name)
    fun isEntry(name: String): Boolean = name == "manifest.json" || assetPath.matches(name)
}
