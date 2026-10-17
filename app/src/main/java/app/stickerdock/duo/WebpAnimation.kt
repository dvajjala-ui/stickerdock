package app.stickerdock.duo

import app.stickerdock.core.AnimatedWebp

/** Android facade; the same container writer is available to the iOS adapter. */
object WebpAnimation {
    fun encode(frames: List<ByteArray>, frameMs: Int, maximumBytes: Int = 500_000): ByteArray =
        AnimatedWebp.encode(frames, frameMs, maximumBytes)
}
