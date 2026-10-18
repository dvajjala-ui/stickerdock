package app.stickerdock.duo

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Optional on-device inference. No photo upload or image-service API key. */
object AutomaticCutout {
    suspend fun createMask(original: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        require(original.size <= 100_000 && WebpSticker.isWebpHeader(original) && !WebpSticker.isAnimatedHeader(original))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        require(bounds.outWidth == 512 && bounds.outHeight == 512)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(original, 0, original.size))
        require(bitmap.width == 512 && bitmap.height == 512)
        val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build())
        val task = try { segmenter.process(InputImage.fromBitmap(bitmap, 0)) }
        catch (e: Exception) { bitmap.recycle(); segmenter.close(); throw e }
        // A cancelled/timeout caller must not recycle input still used by inference.
        task.addOnCompleteListener { bitmap.recycle(); segmenter.close() }
        val result = try { Tasks.await(task, 45, TimeUnit.SECONDS) }
        catch (e: Exception) {
            throw IllegalStateException("Automatic cutout is unavailable. First use needs Google Play services and a model download. Try again when online, or use Manual cutout.", e)
        }
        val confidence = requireNotNull(result.foregroundConfidenceMask) { "The cutout model is still preparing. Try again, or use Manual cutout." }
        require(confidence.capacity() == 512 * 512) { "The cutout model returned an unsupported mask" }
        ArtworkMasks.fromConfidence(FloatArray(512 * 512) { confidence.get(it) })
    }
}
