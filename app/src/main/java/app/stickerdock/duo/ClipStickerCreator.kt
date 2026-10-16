package app.stickerdock.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import app.stickerdock.core.ClipRecipe
import java.io.ByteArrayOutputStream
import java.io.Closeable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.min

/** Bounded, offline GIF/video creation. All work belongs on a background dispatcher. */
class ClipStickerCreator private constructor(
    val durationMs: Int, private val movie: Movie?, private val videoBytes: ByteArray?, private val rotation: Int = 0
) : Closeable {
    data class Edit(val startMs: Int = 0, val durationMs: Int = 2500, val caption: String = "", val cover: Boolean = true, val fps: Int = 8)
    data class Result(val bytes: ByteArray, val frames: Int, val durationMs: Int)

    suspend fun render(edit: Edit, progress: (String) -> Unit = {}): Result {
        val recipe = ClipRecipe(edit.startMs, edit.durationMs, edit.caption, edit.cover, edit.fps)
        recipe.validate(durationMs)
        val count = recipe.frameCount
        val frames = mutableListOf<Bitmap>()
        try {
            val coroutine = currentCoroutineContext()
            if (videoBytes != null) VideoClipFrames.decode(videoBytes, edit.startMs, count, edit.fps, rotation,
                { coroutine.ensureActive() }) { index, source ->
                progress("Reading frame ${index + 1} of $count")
                frames += compose(source, edit)
            } else repeat(count) { index ->
                coroutine.ensureActive()
                progress("Reading frame ${index + 1} of $count")
                val source = frame(edit.startMs + index * 1000 / edit.fps)
                try { frames += compose(source, edit) } finally { source.recycle() }
            }
            for (quality in listOf(85, 65, 45, 25)) {
                currentCoroutineContext().ensureActive()
                progress("Compressing animation")
                val encoded = frames.map { bitmap ->
                    currentCoroutineContext().ensureActive()
                    ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, it)) }.toByteArray()
                }
                val result = try { WebpAnimation.encode(encoded, 1000 / edit.fps) }
                catch (e: IllegalArgumentException) {
                    if (e.message?.startsWith("Animation is too detailed") == true) continue else throw e
                }
                return Result(result, frames.size, frames.size * (1000 / edit.fps))
            }
            error("Animation is too detailed. Shorten the clip or choose 6 fps.")
        } finally { frames.forEach(Bitmap::recycle) }
    }

    @Synchronized private fun frame(atMs: Int): Bitmap {
        if (movie != null) {
            val scale = min(512f / movie.width(), 512f / movie.height())
            val result = Bitmap.createBitmap((movie.width() * scale).toInt().coerceAtLeast(1),
                (movie.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.scale(scale, scale)
            movie.setTime(atMs)
            movie.draw(canvas, 0f, 0f)
            return result
        }
        error("Could not read this section. Choose a different clip.")
    }

    private fun compose(source: Bitmap, edit: Edit): Bitmap {
        val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scale = if (edit.cover) max(484f / source.width, 484f / source.height) else min(484f / source.width, 484f / source.height)
        val width = source.width * scale; val height = source.height * scale
        val left = (512 - width) / 2; val top = (512 - height) / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        val path = android.graphics.Path().apply { addRoundRect(RectF(14f, 14f, 498f, 498f), 24f, 24f, android.graphics.Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), paint)
        canvas.restore()
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
        if (edit.cover) canvas.drawRoundRect(RectF(12f, 12f, 500f, 500f), 26f, 26f, paint)
        StickerArtwork.caption(canvas, edit.caption, true)
        return output
    }

    @Synchronized override fun close() {}

    companion object {
        fun open(context: Context, uri: Uri): ClipStickerCreator {
            require(uri.scheme == "content") { "Choose media through the system file picker" }
            val bytes = context.contentResolver.openInputStream(uri)?.use { StickerFiles.boundedRead(it, 20_000_000) }
                ?: error("Could not open clip")
            require(bytes.size >= 12) { "This file is empty or damaged" }
            if (String(bytes, 0, 6, Charsets.US_ASCII) in listOf("GIF87a", "GIF89a")) {
                GifClipPolicy.validate(bytes)
                val movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: error("Could not decode GIF")
                require(movie.duration() in 500..600_000) { "Choose a moving GIF between 0.5 seconds and ten minutes" }
                return ClipStickerCreator(movie.duration(), movie, null)
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(VideoClipFrames.memorySource(bytes))
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                require(duration in 500..600_000) { "Choose a video between 0.5 seconds and ten minutes" }
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                require(width > 0 && height > 0 && width.toLong() * height <= 4_000_000) { "Choose a video up to 4 megapixels (1080p)" }
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                require(rotation in listOf(0, 90, 180, 270)) { "Unsupported video rotation" }
                return ClipStickerCreator(duration.toInt(), null, bytes, rotation)
            } catch (e: Exception) { throw IllegalArgumentException("Could not open video. Choose a supported MP4/WebM/MOV or GIF up to 4 megapixels.", e) }
            finally { retriever.close() }
        }
    }
}
