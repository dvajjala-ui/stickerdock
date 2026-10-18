package app.stickerdock.duo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream

data class ArtworkEdit(
    val quarterTurns: Int = 0,
    val mirror: Boolean = false,
    val zoom: Float = 1f,
    val horizontal: Float = 0f,
    val vertical: Float = 0f,
    val topText: String = "",
    val bottomText: String = "",
    val cutout: List<CutoutPoint> = emptyList(),
    val maskHash: String = ""
)

/** Local static artwork editor. Every render starts from the original, preventing preview recompression. */
object StickerArtwork {
    fun render(input: ByteArray, edit: ArtworkEdit, mask: ByteArray? = null): Bitmap {
        require(input.size <= 100_000 && WebpSticker.isWebpHeader(input) && !WebpSticker.isAnimatedHeader(input)) { "Choose a static sticker" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
        require(bounds.outWidth == 512 && bounds.outHeight == 512) { "Invalid sticker dimensions" }
        require(edit.cutout.isEmpty() || ArtworkCutout.isValid(edit.cutout)) { "Trace a wider outline around the subject" }
        require(edit.maskHash.isEmpty() || (mask != null && StickerFiles.sha256(mask) == edit.maskHash)) { "Cutout mask is missing or changed" }
        val source = BitmapFactory.decodeByteArray(input, 0, input.size) ?: error("Could not open sticker")
        val result = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        var cutout: Bitmap? = null
        var masked: Bitmap? = null
        try {
            if (edit.maskHash.isNotEmpty()) masked = ArtworkMasks.apply(source, mask!!)
            if (edit.cutout.isNotEmpty()) {
                cutout = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val maskCanvas = Canvas(cutout)
                val path = Path().apply {
                    moveTo(edit.cutout.first().x * 512, edit.cutout.first().y * 512)
                    edit.cutout.drop(1).forEach { lineTo(it.x * 512, it.y * 512) }
                    close()
                }
                maskCanvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                maskCanvas.drawBitmap(masked ?: source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                })
            }
            val canvas = Canvas(result)
            canvas.save()
            canvas.translate(256f + edit.horizontal.coerceIn(-1f, 1f) * 180f, 256f + edit.vertical.coerceIn(-1f, 1f) * 180f)
            canvas.rotate((edit.quarterTurns % 4) * 90f)
            val zoom = edit.zoom.coerceIn(1f, 3f)
            canvas.scale(if (edit.mirror) -zoom else zoom, zoom)
            canvas.drawBitmap(cutout ?: masked ?: source, -256f, -256f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            caption(canvas, edit.topText, false)
            caption(canvas, edit.bottomText, true)
            return result
        } catch (e: Exception) { result.recycle(); throw e }
        finally { cutout?.recycle(); masked?.recycle(); source.recycle() }
    }

    fun encode(bitmap: Bitmap): ByteArray {
        for (quality in 95 downTo 15 step 10) {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, output))
            if (output.size() <= 100_000) return output.toByteArray()
        }
        error("This edit is too detailed for WhatsApp. Shorten the text or reduce zoom.")
    }

    internal fun caption(canvas: Canvas, raw: String, bottom: Boolean) {
        val text = raw.replace(Regex("\\s+"), " ").trim().take(100)
        if (text.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            strokeJoin = Paint.Join.ROUND
        }
        var lines: List<String>
        var size = 46f
        do {
            paint.textSize = size
            lines = wrap(text, paint)
            size -= 2f
        } while (lines.size > 3 && size >= 20f)
        val lineHeight = paint.textSize * 1.18f
        val firstBaseline = if (bottom) 492f - paint.descent() - (lines.size - 1) * lineHeight else 20f - paint.ascent()
        lines.forEachIndexed { index, line ->
            val y = firstBaseline + index * lineHeight
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f; paint.color = Color.BLACK
            canvas.drawText(line, 256f, y, paint)
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            canvas.drawText(line, 256f, y, paint)
        }
    }

    private fun wrap(text: String, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, 464f, null).coerceAtLeast(1)
            val space = remaining.lastIndexOf(' ', count - 1)
            val end = if (count < remaining.length && space > 0) space else count
            lines += remaining.take(end)
            remaining = remaining.drop(end).trimStart()
        }
        return lines
    }
}
