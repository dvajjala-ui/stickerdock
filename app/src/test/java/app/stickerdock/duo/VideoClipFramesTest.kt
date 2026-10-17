package app.stickerdock.duo

import android.graphics.Color
import android.graphics.Rect
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoClipFramesTest {
    private fun neutral(width: Int, height: Int, y: ByteArray): List<VideoClipFrames.Plane> {
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        return listOf(VideoClipFrames.Plane(ByteBuffer.wrap(y), width, 1),
            VideoClipFrames.Plane(ByteBuffer.wrap(ByteArray(chromaWidth * chromaHeight) { 128.toByte() }), chromaWidth, 1),
            VideoClipFrames.Plane(ByteBuffer.wrap(ByteArray(chromaWidth * chromaHeight) { 128.toByte() }), chromaWidth, 1))
    }

    @Test fun paddedInterleavedPlanesAndPositionsRespectCropWithoutChangingInput() {
        val luma = ByteBuffer.wrap(byteArrayOf(99, 16, 235.toByte(), 16, 235.toByte(), 88, 88, 235.toByte(), 16, 235.toByte(), 16, 88, 88)).apply { position(1) }
        val u = ByteBuffer.wrap(byteArrayOf(99, 128.toByte(), 77, 128.toByte(), 77)).apply { position(1) }
        val v = ByteBuffer.wrap(byteArrayOf(99, 128.toByte(), 66, 128.toByte(), 66)).apply { position(1) }
        val bitmap = VideoClipFrames.pixels(4, 2, Rect(1, 0, 4, 2), listOf(
            VideoClipFrames.Plane(luma, 6, 1), VideoClipFrames.Plane(u, 4, 2), VideoClipFrames.Plane(v, 4, 2)), 0)
        try {
            assertEquals(3, bitmap.width); assertEquals(2, bitmap.height)
            assertEquals(Color.WHITE, bitmap.getPixel(0, 0)); assertEquals(Color.BLACK, bitmap.getPixel(1, 0))
            assertEquals(Color.BLACK, bitmap.getPixel(0, 1)); assertEquals(Color.WHITE, bitmap.getPixel(1, 1))
            assertEquals(1, luma.position()); assertEquals(1, u.position()); assertEquals(1, v.position())
        } finally { bitmap.recycle() }
    }

    @Test fun portraitRotationIsAppliedOnceAndFullRangeBlackIsNotCrushed() {
        val planes = neutral(2, 3, byteArrayOf(16, 235.toByte(), 16, 16, 235.toByte(), 16))
        val rotated = VideoClipFrames.pixels(2, 3, Rect(0, 0, 2, 3), planes, 90)
        val full = VideoClipFrames.pixels(2, 3, Rect(0, 0, 2, 3), planes, 0, true)
        try {
            assertEquals(3, rotated.width); assertEquals(2, rotated.height)
            assertEquals(Color.WHITE, rotated.getPixel(0, 0))
            assertEquals(Color.WHITE, rotated.getPixel(2, 1))
            assertEquals(16, Color.red(full.getPixel(0, 0)))
        } finally { rotated.recycle(); full.recycle() }
    }

    @Test fun largeFramesOnlyAllocateThumbnailPixelsAndInvalidGeometryIsRejected() {
        val planes = neutral(1024, 2, ByteArray(2048) { 100 })
        val bitmap = VideoClipFrames.pixels(1024, 2, Rect(0, 0, 1024, 2), planes, 0)
        try { assertEquals(512, bitmap.width); assertEquals(1, bitmap.height) } finally { bitmap.recycle() }
        assertThrows(IllegalArgumentException::class.java) { VideoClipFrames.pixels(1024, 2, Rect(-1, 0, 5, 2), planes, 0) }
        assertThrows(IllegalArgumentException::class.java) { VideoClipFrames.pixels(1024, 2, Rect(0, 0, 5, 2), planes, 45) }
        assertThrows(IllegalArgumentException::class.java) { VideoClipFrames.pixels(4000, 4000, Rect(0, 0, 4, 2), planes, 0) }
    }

    @Test fun hdVideoUsesBt709ColorCoefficients() {
        // Limited-range BT.709 red: Y=63, Cb=102, Cr=240.
        val planes = listOf(VideoClipFrames.Plane(ByteBuffer.wrap(byteArrayOf(63)), 1, 1),
            VideoClipFrames.Plane(ByteBuffer.wrap(byteArrayOf(102)), 1, 1),
            VideoClipFrames.Plane(ByteBuffer.wrap(byteArrayOf(240.toByte())), 1, 1))
        val bitmap = VideoClipFrames.pixels(1, 1, Rect(0, 0, 1, 1), planes, 0, bt709 = true)
        try {
            val color = bitmap.getPixel(0, 0)
            assertTrue(Color.red(color) >= 254)
            assertTrue(Color.green(color) <= 1)
            assertTrue(Color.blue(color) <= 1)
        } finally { bitmap.recycle() }
    }
}
