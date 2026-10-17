package app.stickerdock.duo

import org.junit.Assert.*
import org.junit.Test

class ArtworkCutoutTest {
    @Test fun rejectsTinyDegenerateOutOfBoundsOrOversizedOutlines() {
        assertFalse(ArtworkCutout.isValid(emptyList()))
        assertFalse(ArtworkCutout.isValid(listOf(CutoutPoint(0f, 0f), CutoutPoint(0.5f, 0.5f), CutoutPoint(1f, 1f))))
        assertFalse(ArtworkCutout.isValid(listOf(CutoutPoint(-1f, 0f), CutoutPoint(1f, 0f), CutoutPoint(1f, 1f))))
        assertFalse(ArtworkCutout.isValid(listOf(CutoutPoint(Float.NaN, 0f), CutoutPoint(1f, 0f), CutoutPoint(1f, 1f))))
        assertFalse(ArtworkCutout.isValid(List(65) { CutoutPoint(0f, 0f) }))
        assertFalse(ArtworkCutout.isValid(listOf(CutoutPoint(0f, 0f), CutoutPoint(0.001f, 0f), CutoutPoint(0.001f, 0.001f))))
    }

    @Test fun longTraceRemainsBoundedAndRoundTripsThroughHistoryWithoutLosingUndo() {
        var points = emptyList<CutoutPoint>()
        repeat(500) { index ->
            val angle = index / 499.0 * Math.PI * 2
            points = ArtworkCutout.append(points, CutoutPoint((0.5 + 0.4 * kotlin.math.cos(angle)).toFloat(), (0.5 + 0.4 * kotlin.math.sin(angle)).toFloat()))
        }
        assertTrue(points.size <= ArtworkCutout.MAX_POINTS)
        val canonical = ArtworkCutout.canonical(points)
        assertTrue(ArtworkCutout.isValid(canonical))
        val history = ArtworkHistory().update(ArtworkEdit(cutout = canonical))
        val restored = ArtworkHistory.decodeStrict(history.encode())
        assertEquals(canonical, restored.edit.cutout)
        assertEquals(emptyList<CutoutPoint>(), restored.undo().edit.cutout)
        assertEquals(canonical, restored.undo().redo().edit.cutout)
    }

    @Test fun boundedHistoryWithLongCaptionsAndOutlinesFitsPersistentLimit() {
        val points = (0 until 64).map { index ->
            val angle = index / 64.0 * Math.PI * 2
            CutoutPoint((0.5 + 0.4 * kotlin.math.cos(angle)).toFloat(), (0.5 + 0.4 * kotlin.math.sin(angle)).toFloat())
        }
        var history = ArtworkHistory()
        repeat(100) { history = history.update(ArtworkEdit(quarterTurns = it % 4, topText = "भ".repeat(100), bottomText = "😄".repeat(50), cutout = points)) }
        assertEquals(51, history.entries.size)
        assertTrue(history.encode().length < 100_000)
        assertTrue(ArtworkCutout.isValid(ArtworkHistory.decodeStrict(history.encode()).edit.cutout))
    }

    @Test fun oldSevenFieldHistoryStillLoadsAndCorruptPersistentHistoryIsRejected() {
        val old = "[0,[[1,true,1,0,0,\"Old caption\",\"\"]]]"
        assertEquals("Old caption", ArtworkHistory.decodeStrict(old).edit.topText)
        assertTrue(ArtworkHistory.decodeStrict(old).edit.cutout.isEmpty())
        assertTrue(runCatching { ArtworkHistory.decodeStrict("broken") }.isFailure)
        assertTrue(runCatching { ArtworkHistory.decodeStrict("[0,[[0,false,1,0,0,\"\",\"\",[0,0,1200,0,100,900]]]]") }.isFailure)
    }
}
