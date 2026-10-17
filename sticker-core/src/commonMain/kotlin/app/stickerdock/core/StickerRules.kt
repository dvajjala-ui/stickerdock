package app.stickerdock.core

/** Shared product/export constraints. Platform adapters still decode actual
 * media and implement file permissions, persistence and WhatsApp handoff. */
object StickerRules {
    const val PIXELS = 512
    const val STATIC_BYTES = 100_000
    const val ANIMATED_BYTES = 500_000
    const val MIN_PACK_SIZE = 3
    const val MAX_PACK_SIZE = 30
    const val MAX_ANIMATION_MS = 10_000
    const val MIN_FRAME_MS = 8
    const val TRAY_PIXELS = 96
    const val TRAY_BYTES = 50_000

    fun requireExportCount(count: Int) {
        require(count in MIN_PACK_SIZE..MAX_PACK_SIZE) { "WhatsApp needs 3–30 stickers in each pack." }
    }

    fun requireCaption(caption: String) {
        require(caption.length <= 80 && caption.none { it.code < 32 }) { "Use a caption up to 80 characters" }
    }
}

/** Portable edit settings; image decoding/composition is a native adapter. */
data class ClipRecipe(
    val startMs: Int = 0,
    val durationMs: Int = 2500,
    val caption: String = "",
    val cover: Boolean = true,
    val fps: Int = 8
) {
    fun validate(sourceDurationMs: Int) {
        StickerRules.requireCaption(caption)
        require(startMs >= 0 && durationMs in 500..4000 && startMs.toLong() + durationMs <= sourceDurationMs) {
            "Choose a 0.5–4 second section inside the clip"
        }
        require(fps in listOf(6, 8, 10)) { "Choose 6, 8 or 10 fps" }
    }

    val frameCount: Int get() = ((durationMs.toLong() * fps + 999) / 1000).toInt()
    val frameDurationMs: Int get() = 1000 / fps
}
