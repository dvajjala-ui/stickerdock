package app.stickerdock.duo

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Small, bounded cache for static grid previews. Sticker source files are immutable. */
class StickerPreviewCache(maxEntries: Int = 32) {
    private val cache = LruCache<String, ImageBitmap>(maxEntries.coerceIn(8, 64))

    @Synchronized
    fun load(path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_PREVIEW_PX || bounds.outHeight / sample > MAX_PREVIEW_PX) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return bitmap.asImageBitmap().also { cache.put(path, it) }
    }

    @Synchronized fun clear() = cache.evictAll()

    companion object { private const val MAX_PREVIEW_PX = 384 }
}
