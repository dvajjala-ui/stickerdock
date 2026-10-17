package app.stickerdock.duo

import app.stickerdock.core.WebpMetadata

/** Android facade for shared container metadata; native decoding remains separate. */
object WebpSticker {
    data class Details(val animated: Boolean, val width: Int, val height: Int, val durationMs: Int, val frames: Int)
    fun inspect(bytes: ByteArray): Details? = WebpMetadata.inspect(bytes)?.let {
        Details(it.animated, it.width, it.height, it.durationMs, it.frames)
    }
    fun isAnimatedHeader(bytes: ByteArray) = WebpMetadata.isAnimatedHeader(bytes)
    fun isWebpHeader(bytes: ByteArray) = WebpMetadata.isWebpHeader(bytes)
}
