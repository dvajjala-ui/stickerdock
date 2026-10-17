package app.stickerdock.duo

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Requested-resolution static previews with a bounded memory budget. */
class StickerPreviewCache(maxEntries: Int = 32) {
    // Preserve the constructor's thumbnail-capacity convention while budgeting
    // by pixels: 32 ordinary 256px thumbnails = 8 MB, or eight full previews.
    private val cache = object : LruCache<String, ImageBitmap>(maxEntries.coerceIn(8, 64) * 256 * 256 * 4) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    internal val cachedBytes: Int @Synchronized get() = cache.size()

    @Synchronized
    fun load(path: String, requestedPx: Int = 256): ImageBitmap? {
        val target = requestedPx.coerceIn(64, 512)
        val file = java.io.File(path)
        val key = "$path|$target|${file.length()}|${file.lastModified()}"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 20_000_000) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    @Synchronized fun clear() = cache.evictAll()

}
