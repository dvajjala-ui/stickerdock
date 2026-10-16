package app.stickerdock.duo

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

data class CutoutPoint(val x: Float, val y: Float)

/** One bounded outline in coordinates of the retained 512px starting image. */
object ArtworkCutout {
    const val MAX_POINTS = 64
    fun canonical(points: List<CutoutPoint>) = points.map { CutoutPoint((it.x * 1024).roundToInt() / 1024f, (it.y * 1024).roundToInt() / 1024f) }
    fun isValid(points: List<CutoutPoint>): Boolean {
        if (points.size !in 3..MAX_POINTS || points.any { it.x !in 0f..1f || it.y !in 0f..1f }) return false
        val area = points.indices.sumOf { i ->
            val next = points[(i + 1) % points.size]
            (points[i].x * next.y - next.x * points[i].y).toDouble()
        }
        return abs(area) / 2 >= 0.002 // Ignore accidental taps/tiny traces.
    }

    fun append(points: List<CutoutPoint>, point: CutoutPoint): List<CutoutPoint> {
        val clipped = CutoutPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        if (points.lastOrNull()?.let { hypot(it.x - clipped.x, it.y - clipped.y) < 0.006f } == true) return points
        val kept = if (points.size >= MAX_POINTS) points.filterIndexed { index, _ -> index % 2 == 0 || index == points.lastIndex } else points
        return kept + clipped
    }
}
