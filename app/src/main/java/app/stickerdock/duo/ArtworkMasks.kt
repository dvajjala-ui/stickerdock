package app.stickerdock.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

/** Lossless masks live beside originals; recipes contain only their checksum. */
object ArtworkMasks {
    const val MAX_BYTES = 512_000

    fun fromConfidence(values: FloatArray): ByteArray {
        require(values.size == 512 * 512 && values.all { it.isFinite() }) { "Invalid subject mask" }
        require(values.count { it > .5f } >= 512) { "No clear subject found. Try tracing the cutout manually." }
        val pixels = IntArray(values.size) { Color.argb((values[it].coerceIn(0f, 1f) * 255).roundToInt(), 255, 255, 255) }
        val bitmap = Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888)
        return try { encode(bitmap) } finally { bitmap.recycle() }
    }

    fun encode(bitmap: Bitmap): ByteArray {
        require(bitmap.width == 512 && bitmap.height == 512)
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        return output.toByteArray().also { require(it.size <= MAX_BYTES) { "Cutout is too detailed" } }
    }

    fun decode(bytes: ByteArray): Bitmap {
        require(bytes.size in 12..MAX_BYTES) { "Invalid cutout mask" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth == 512 && bounds.outHeight == 512 && bounds.outMimeType == "image/png") { "Invalid cutout mask dimensions" }
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Cutout mask is damaged" }
    }

    fun apply(source: Bitmap, bytes: ByteArray): Bitmap {
        val mask = decode(bytes)
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            Canvas(result).drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            return result
        } catch (e: Exception) { result.recycle(); throw e }
        finally { mask.recycle() }
    }
}

class ArtworkMaskStore(private val context: Context) {
    internal fun file(stickerId: String, hash: String): File {
        require(BackupArchivePaths.isId(stickerId) && hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid mask path" }
        return File(File(File(context.filesDir, "artwork-originals"), stickerId), "$hash.mask.png")
    }

    fun read(stickerId: String, hash: String): ByteArray {
        val path = file(stickerId, hash)
        require(path.isFile && path.length() in 12..ArtworkMasks.MAX_BYTES.toLong()) { "This cutout mask is missing. Your saved sticker is unchanged." }
        val bytes = path.readBytes()
        require(StickerFiles.sha256(bytes) == hash) { "Cutout mask is damaged" }
        ArtworkMasks.decode(bytes).recycle()
        return bytes
    }

    fun write(stickerId: String, bytes: ByteArray): String {
        ArtworkMasks.decode(bytes).recycle()
        val hash = StickerFiles.sha256(bytes)
        val destination = file(stickerId, hash)
        if (destination.exists()) { read(stickerId, hash); return hash }
        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
        val temporary = File(destination.parentFile, UUID.randomUUID().toString() + ".tmp")
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            check(temporary.renameTo(destination)) { "Could not retain the cutout. Check device storage." }
        } finally { temporary.delete() }
        return hash
    }
}
