package app.stickerdock.duo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.drawable.AnimatedImageDrawable
import java.io.ByteArrayOutputStream
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
class WebpAnimationTest {
    private fun frame(color: Int, size: Int = 512, lossless: Boolean = false): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(size / 2f, size / 2f, size / 3f, Paint().apply { this.color = color })
        return try { ByteArrayOutputStream().also {
            assertTrue(bitmap.compress(if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY, 80, it))
        }.toByteArray() } finally { bitmap.recycle() }
    }

    @Test fun compressedAlphaFramesDecodeAsRealAnimationWithExactDuration() {
        val encoded = WebpAnimation.encode(listOf(frame(Color.RED), frame(Color.BLUE)), 125)
        val details = WebpSticker.inspect(encoded)!!
        assertTrue(details.animated)
        assertEquals(2, details.frames)
        assertEquals(250, details.durationMs)
        assertEquals(512, details.width)
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(encoded)))
        assertTrue(drawable is AnimatedImageDrawable)
        assertEquals(512, drawable.intrinsicWidth)
    }

    @Test fun losslessFramesUseTheSameContainerContract() {
        val encoded = WebpAnimation.encode(listOf(frame(Color.GREEN, lossless = true), frame(Color.YELLOW, lossless = true)), 100)
        assertEquals(2, WebpSticker.inspect(encoded)!!.frames)
        assertTrue(ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(encoded))) is AnimatedImageDrawable)
    }

    @Test fun noMotionWrongSizeDamagedRiffAndExistingAnimationAreRejected() {
        val first = frame(Color.RED); val second = frame(Color.BLUE)
        assertThrows(IllegalArgumentException::class.java) { WebpAnimation.encode(listOf(first, first), 100) }
        assertThrows(IllegalArgumentException::class.java) { WebpAnimation.encode(listOf(first, frame(Color.BLUE, 200)), 100) }
        val damaged = second.clone().also { it[4] = 0 }
        assertThrows(IllegalArgumentException::class.java) { WebpAnimation.encode(listOf(first, damaged), 100) }
        val animation = WebpAnimation.encode(listOf(first, second), 100)
        assertThrows(IllegalArgumentException::class.java) { WebpAnimation.encode(listOf(first, animation), 100) }
    }

    @Test fun frameDurationAndOutputBudgetsAreHardLimits() {
        val first = frame(Color.RED); val second = frame(Color.BLUE)
        assertThrows(IllegalArgumentException::class.java) { WebpAnimation.encode(listOf(first, second), 7) }
        assertThrows(IllegalArgumentException::class.java) { WebpAnimation.encode(List(60) { if (it % 2 == 0) first else second }, 200) }
        assertThrows(IllegalArgumentException::class.java) { WebpAnimation.encode(listOf(first, second), 100, maximumBytes = 1000) }
        assertThrows(IllegalArgumentException::class.java) { WebpAnimation.encode(listOf(first), 100) }
    }
}
