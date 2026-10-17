package app.stickerdock.duo

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StickerPreviewCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun write(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    @Test fun fullPreviewDoesNotReuseASmallThumbnail() {
        val file = temporary.newFile("source.png"); write(file, Color.RED)
        val source = file.readBytes()
        val cache = StickerPreviewCache()
        assertEquals(128, cache.load(file.path, 128)!!.width)
        val full = cache.load(file.path, 512)!!
        assertEquals(512, full.width); assertEquals(512, full.height)
        assertEquals(Color.RED, full.asAndroidBitmap().getPixel(200, 200))
        assertArrayEquals(source, file.readBytes())
    }

    @Test fun animatedGridPosterUsesOneBoundedBitmapWithoutChangingSavedMedia() {
        val frames = listOf(Color.RED, Color.BLUE).map { color ->
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }.toByteArray() }
            finally { bitmap.recycle() }
        }
        val bytes = WebpAnimation.encode(frames, 100)
        val file = temporary.newFile("animated.webp").apply { writeBytes(bytes) }
        val cache = StickerPreviewCache(8)
        val poster = cache.load(file.path, 128)!!
        assertEquals(128, poster.width)
        assertEquals(128 * 128 * 4, cache.cachedBytes)
        assertTrue(Color.red(poster.asAndroidBitmap().getPixel(0, 0)) > 200)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun fullImagesStayWithinTheByteBudgetAndChangedFilesDoNotStayStale() {
        val cache = StickerPreviewCache(maxEntries = 8) // 2 MB, not eight 1 MB images
        val files = (1..4).map { temporary.newFile("$it.png").also { file -> write(file, Color.RED) } }
        files.forEach { assertNotNull(cache.load(it.path, 512)); assertTrue(cache.cachedBytes <= 2 * 1024 * 1024) }
        val first = files.first()
        assertEquals(Color.RED, cache.load(first.path, 512)!!.asAndroidBitmap().getPixel(0, 0))
        write(first, Color.BLUE); assertTrue(first.setLastModified(first.lastModified() + 2000))
        assertEquals(Color.BLUE, cache.load(first.path, 512)!!.asAndroidBitmap().getPixel(0, 0))
        assertTrue(cache.cachedBytes <= 2 * 1024 * 1024)
        cache.clear(); assertEquals(0, cache.cachedBytes)
    }
}
