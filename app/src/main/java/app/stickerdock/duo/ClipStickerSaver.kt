package app.stickerdock.duo

/** Explicit preview acceptance; existing packs are never changed on conversion failure. */
class ClipStickerSaver(private val store: StickerStore, private val files: StickerFiles) {
    fun save(bytes: ByteArray, caption: String, targetId: String?, name: String): StickerPack {
        require(bytes.size <= 500_000 && WebpSticker.inspect(bytes)?.let { it.animated && it.frames >= 2 } == true) { "Preview a valid animation before saving" }
        require(caption.length <= 80 && caption.none { it.code < 32 })
        val target = if (targetId == null) {
            require(name.isNotBlank() && name.length <= 60 && name.none { it.code < 32 }) { "Choose a pack name" }
            store.createPack(name.trim(), StickerKind.ANIMATED, category = PackCategory.REACTIONS)
        } else requireNotNull(store.getPack(targetId)) { "This pack was removed. Choose another pack." }
        try {
            require(target.kind == StickerKind.ANIMATED) { "Choose an animated pack" }
            when (val added = files.importBytes(target.id, bytes, label = caption.ifBlank { "Animated reaction" }, tags = "reaction, animated")) {
                ImportResult.Added -> return target
                ImportResult.Duplicate -> error("This animation is already in that pack. Change the caption or choose another moment.")
                is ImportResult.Failed -> error(added.reason)
            }
        } catch (e: Exception) {
            if (targetId == null) files.deletePack(target.id)
            throw e
        }
    }
}
