package app.stickerdock.duo

/** Shared ZIP allowlist for canonical app-generated UUID asset paths. */
internal object BackupArchivePaths {
    fun isId(value: String): Boolean = app.stickerdock.core.ArchivePaths.isId(value)
    fun isStickerName(name: String): Boolean = app.stickerdock.core.ArchivePaths.isStickerName(name)
    fun isEntry(name: String): Boolean = app.stickerdock.core.ArchivePaths.isEntry(name)
}
