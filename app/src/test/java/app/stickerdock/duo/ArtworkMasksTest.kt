package app.stickerdock.duo

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkMasksTest {
    @Test fun softMaskPreservesOriginalTransparencyAndLosslessEdges() {
        val confidence = FloatArray(512 * 512) { index -> when (index % 512) { in 0..199 -> 1f; in 200..299 -> .5f; else -> 0f } }
        val encoded = ArtworkMasks.fromConfidence(confidence)
        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.argb(128, 255, 0, 0)) }
        val result = ArtworkMasks.apply(source, encoded)
        assertEquals(128, Color.alpha(result.getPixel(50, 50)))
        assertTrue(Color.alpha(result.getPixel(250, 50)) in 63..65)
        assertEquals(0, Color.alpha(result.getPixel(450, 50)))
        source.recycle(); result.recycle()
    }

    @Test fun emptyNonFiniteAndWrongSizedMasksFailBeforeApplying() {
        assertTrue(runCatching { ArtworkMasks.fromConfidence(FloatArray(512 * 512)) }.isFailure)
        assertTrue(runCatching { ArtworkMasks.fromConfidence(floatArrayOf(1f)) }.isFailure)
        val bad = FloatArray(512 * 512) { 1f }.apply { this[0] = Float.NaN }
        assertTrue(runCatching { ArtworkMasks.fromConfidence(bad) }.isFailure)
    }
}
