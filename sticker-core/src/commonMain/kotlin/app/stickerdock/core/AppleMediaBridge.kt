@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package app.stickerdock.core

import kotlin.io.encoding.Base64

/** Bulk transport across Swift/Kotlin; no per-pixel or per-byte interop calls.
 * Failure returns null so malformed imported media never unwinds an ObjC call.
 */
class AppleMediaBridge {
    fun canonicalArchiveID(value: String): String? = ArchivePaths.canonicalId(value)
    fun normalizeStatic(base64: String): String? = runCatching {
        require(base64.length <= 133_336)
        val bytes = AnimatedWebp.staticContainer(Base64.decode(base64))
        require(bytes.size <= 100_000 && WebpMetadata.inspect(bytes)?.animated == false)
        Base64.encode(bytes)
    }.getOrNull()

    fun inspect(base64: String): WebpMetadata.Details? = runCatching {
        require(base64.length <= 666_668)
        WebpMetadata.inspect(Base64.decode(base64))
    }.getOrNull()

    fun mux(framesBase64: String, frameMs: Int): String? = runCatching {
        require(framesBase64.length <= 4_000_000)
        val frames = framesBase64.split('\n')
        require(frames.size in 2..40)
        Base64.encode(AnimatedWebp.encode(frames.map(Base64::decode), frameMs))
    }.getOrNull()
}
