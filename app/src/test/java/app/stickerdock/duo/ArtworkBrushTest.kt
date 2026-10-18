package app.stickerdock.duo

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
class ArtworkBrushTest {
    @Test fun eraseAndRestoreRepairBothAutomaticAndManualMasks() {
        val confidence = FloatArray(512 * 512) { if (it % 512 < 256) 1f else 0f }
        val outline = listOf(CutoutPoint(0f, 0f), CutoutPoint(1f, 0f), CutoutPoint(1f, .5f), CutoutPoint(0f, .5f))
        val base = ArtworkBrush.base(ArtworkMasks.fromConfidence(confidence), outline)
        assertEquals(255, Color.alpha(base.getPixel(100, 100)))
        assertEquals(0, Color.alpha(base.getPixel(400, 100)))
        assertEquals(0, Color.alpha(base.getPixel(100, 400)))
        val strokes = listOf(
            MaskStroke(listOf(CutoutPoint(.2f, .2f), CutoutPoint(.3f, .2f)), 20f, false),
            MaskStroke(listOf(CutoutPoint(.8f, .8f)), 20f, true)
        )
        val result = ArtworkMasks.decode(ArtworkBrush.render(base, strokes))
        assertEquals(0, Color.alpha(result.getPixel(128, 102)))
        assertEquals(255, Color.alpha(result.getPixel(410, 410)))
        assertEquals(255, Color.alpha(base.getPixel(128, 102))) // Edits never mutate the base.
        base.recycle(); result.recycle()
    }

    @Test fun boundedBrushStateSurvivesRecreationAndRejectsInvalidCoordinates() {
        val strokes = listOf(MaskStroke(listOf(CutoutPoint(.25f, .5f)), 12f, true))
        assertEquals(strokes, ArtworkBrush.decode(ArtworkBrush.encode(strokes)))
        assertTrue(runCatching { ArtworkBrush.decode("""[{"r":12,"restore":true,"p":[-1,100]}]""") }.isFailure)
        assertTrue(runCatching { ArtworkBrush.encode(List(101) { strokes[0] }) }.isFailure)
        assertTrue(runCatching { ArtworkBrush.encode(listOf(strokes[0].copy(points = List(2049) { CutoutPoint(.5f, .5f) }))) }.isFailure)
    }
}
