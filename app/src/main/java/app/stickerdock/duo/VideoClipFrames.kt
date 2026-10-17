package app.stickerdock.duo

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.nio.ByteBuffer
import kotlin.math.min

/** Decode forward from a sync frame: timestamp thumbnail APIs repeat the first
 * frame for some WebM files, even with OPTION_CLOSEST. Never fetches a URI. */
internal object VideoClipFrames {
    fun decode(bytes: ByteArray, startMs: Int, count: Int, fps: Int, rotation: Int,
               checkActive: () -> Unit, consume: (Int, Bitmap) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var started = false
        try {
            extractor.setDataSource(memorySource(bytes))
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("No video track in this clip")
            val format = extractor.getTrackFormat(track)
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            require(width > 0 && height > 0 && width.toLong() * height <= 4_000_000) { "Choose a video up to 4 megapixels (1080p)" }
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            // Rotation is applied once to the sampled pixels below, not by an
            // output Surface (there is no Surface in this byte-buffer decoder).
            format.setInteger(MediaFormat.KEY_ROTATION, 0)
            extractor.selectTrack(track)
            extractor.seekTo(startMs.toLong() * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start(); started = true
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var index = 0
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (index < count && !outputDone) {
                checkActive()
                check(SystemClock.elapsedRealtime() < deadline) { "Video decoding timed out. Choose a shorter or simpler clip." }
                if (!inputDone) {
                    val input = decoder.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        val buffer = decoder.getInputBuffer(input) ?: error("Video decoder unavailable")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val output = decoder.dequeueOutputBuffer(info, 10_000)
                if (output >= 0) {
                    try {
                        val wantedUs = startMs.toLong() * 1000 + index * 1_000_000L / fps
                        if (info.size > 0 && info.presentationTimeUs >= wantedUs) {
                            val image = decoder.getOutputImage(output) ?: error("This decoder cannot provide editable frames. Try another video format.")
                            val bitmap = image.use {
                                require(it.format == ImageFormat.YUV_420_888 && it.planes.size == 3) { "Unsupported video color format" }
                                val planes = it.planes.map { plane -> Plane(plane.buffer, plane.rowStride, plane.pixelStride) }
                                val outputFormat = decoder.outputFormat
                                val fullRange = outputFormat.containsKey(MediaFormat.KEY_COLOR_RANGE) && outputFormat.getInteger(MediaFormat.KEY_COLOR_RANGE) == MediaFormat.COLOR_RANGE_FULL
                                val bt709 = outputFormat.containsKey(MediaFormat.KEY_COLOR_STANDARD) && outputFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD) == MediaFormat.COLOR_STANDARD_BT709
                                pixels(it.width, it.height, it.cropRect, planes, rotation, fullRange, bt709)
                            }
                            try {
                                do {
                                    checkActive()
                                    consume(index++, bitmap)
                                } while (index < count && info.presentationTimeUs >= startMs.toLong() * 1000 + index * 1_000_000L / fps)
                            } finally { bitmap.recycle() }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { decoder.releaseOutputBuffer(output, false) }
                }
            }
            require(index >= 2) { "Could not read a moving section. Choose another clip." }
        } finally {
            if (started) runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    internal data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int) {
        fun at(x: Int, y: Int): Int = buffer.get(buffer.position() + y * rowStride + x * pixelStride).toInt() and 255
    }

    /** Sample only thumbnail pixels, respecting crop, row/pixel strides and
     * buffer positions. Do not allocate a full-resolution RGB intermediary. */
    internal fun pixels(width: Int, height: Int, crop: Rect, planes: List<Plane>, rotation: Int, fullRange: Boolean = false, bt709: Boolean = false): Bitmap {
        require(width > 0 && height > 0 && width.toLong() * height <= 4_000_000 && planes.size == 3)
        require(crop.left >= 0 && crop.top >= 0 && crop.right <= width && crop.bottom <= height && crop.width() > 0 && crop.height() > 0)
        require(rotation in listOf(0, 90, 180, 270))
        val scale = min(1f, min(512f / crop.width(), 512f / crop.height()))
        val w = (crop.width() * scale).toInt().coerceAtLeast(1)
        val h = (crop.height() * scale).toInt().coerceAtLeast(1)
        val colors = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = crop.left + x * crop.width() / w
            val sy = crop.top + y * crop.height() / h
            val luma = planes[0].at(sx, sy)
            val u = planes[1].at(sx / 2, sy / 2) - 128
            val v = planes[2].at(sx / 2, sy / 2) - 128
            val base = if (fullRange) luma * 256 else (luma - 16).coerceAtLeast(0) * 298
            val rv = if (bt709) { if (fullRange) 403 else 459 } else { if (fullRange) 359 else 409 }
            val gu = if (bt709) { if (fullRange) 48 else 55 } else { if (fullRange) 88 else 100 }
            val gv = if (bt709) { if (fullRange) 120 else 136 } else { if (fullRange) 183 else 208 }
            val bu = if (bt709) { if (fullRange) 475 else 541 } else { if (fullRange) 454 else 516 }
            val red = ((base + rv * v + 128) shr 8).coerceIn(0, 255)
            val green = ((base - gu * u - gv * v + 128) shr 8).coerceIn(0, 255)
            val blue = ((base + bu * u + 128) shr 8).coerceIn(0, 255)
            colors[y * w + x] = (255 shl 24) or (red shl 16) or (green shl 8) or blue
        }
        val bitmap = Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
        if (rotation == 0) return bitmap
        return try { Bitmap.createBitmap(bitmap, 0, 0, w, h, Matrix().apply { postRotate(rotation.toFloat()) }, true) }
        finally { bitmap.recycle() }
    }

    fun memorySource(bytes: ByteArray) = object : MediaDataSource() {
        override fun getSize() = bytes.size.toLong()
        override fun close() {}
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position < 0 || position >= bytes.size) return -1
            val count = min(size, bytes.size - position.toInt())
            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }
    }
}
