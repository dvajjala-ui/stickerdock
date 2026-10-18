package app.stickerdock.duo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import org.json.JSONArray
import org.json.JSONObject

data class MaskStroke(val points: List<CutoutPoint>, val radius: Float, val restore: Boolean)

/** Brush coordinates refer to the original, before rotation, captions or crop. */
object ArtworkBrush {
    const val MAX_POINTS = 2048
    const val MAX_STROKES = 100

    fun base(bytes: ByteArray?, outline: List<CutoutPoint>): Bitmap {
        val result = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        if (outline.isEmpty()) result.eraseColor(Color.WHITE) else {
            require(ArtworkCutout.isValid(outline))
            canvas.drawPath(path(outline, true), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
        if (bytes != null) {
            val mask = ArtworkMasks.decode(bytes)
            try { canvas.drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }) }
            finally { mask.recycle() }
        }
        return result
    }

    fun paint(canvas: Canvas, strokes: List<MaskStroke>) {
        for (stroke in strokes) {
            require(stroke.radius in 1f..80f && stroke.points.isNotEmpty())
            val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; strokeWidth = stroke.radius * 2
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                xfermode = PorterDuffXfermode(if (stroke.restore) PorterDuff.Mode.SRC else PorterDuff.Mode.CLEAR)
            }
            if (stroke.points.size == 1) {
                brush.style = Paint.Style.FILL
                canvas.drawCircle(stroke.points[0].x * 512, stroke.points[0].y * 512, stroke.radius, brush)
            } else canvas.drawPath(path(stroke.points, false), brush)
        }
    }

    fun render(base: Bitmap, strokes: List<MaskStroke>): ByteArray {
        val bitmap = base.copy(Bitmap.Config.ARGB_8888, true)
        return try { paint(Canvas(bitmap), strokes); ArtworkMasks.encode(bitmap) } finally { bitmap.recycle() }
    }

    private fun path(points: List<CutoutPoint>, close: Boolean) = Path().apply {
        moveTo(points.first().x * 512, points.first().y * 512)
        points.drop(1).forEach { lineTo(it.x * 512, it.y * 512) }
        if (close) close()
    }

    fun encode(strokes: List<MaskStroke>): String {
        require(strokes.size <= MAX_STROKES && strokes.sumOf { it.points.size } <= MAX_POINTS)
        return JSONArray().apply { strokes.forEach { stroke -> put(JSONObject().apply {
            put("r", stroke.radius.toDouble()); put("restore", stroke.restore)
            put("p", JSONArray().apply { stroke.points.forEach { put((it.x * 4096).toInt()); put((it.y * 4096).toInt()) } })
        }) } }.toString()
    }

    fun decode(value: String): List<MaskStroke> {
        require(value.length <= 40_000)
        val array = JSONArray(value)
        require(array.length() <= MAX_STROKES)
        val strokes = (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            val points = entry.getJSONArray("p")
            require(points.length() in 2..MAX_POINTS * 2 && points.length() % 2 == 0)
            MaskStroke((0 until points.length() step 2).map {
                CutoutPoint(points.getInt(it) / 4096f, points.getInt(it + 1) / 4096f).also { point ->
                    require(point.x in 0f..1f && point.y in 0f..1f)
                }
            }, entry.getDouble("r").toFloat().also { require(it in 1f..80f) }, entry.getBoolean("restore"))
        }
        require(strokes.sumOf { it.points.size } <= MAX_POINTS)
        return strokes
    }
}
