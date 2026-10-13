package app.stickerdock.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max

sealed interface ImportResult {
    data object Added : ImportResult
    data object Duplicate : ImportResult
    data class Failed(val reason: String) : ImportResult
}

class StickerFiles(private val context: Context, private val store: StickerStore) {
    private val stickersDir get() = File(context.filesDir, "stickers")
    private val traysDir get() = File(context.filesDir, "trays")
    fun stickerFile(packId: String, name: String) = File(File(stickersDir, packId), name)
    fun trayFile(packId: String) = File(traysDir, "$packId.png")

    fun importUri(packId: String, uri: Uri): ImportResult {
        return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { boundedRead(it, 20_000_000) }
            ?: return ImportResult.Failed("Could not open file")
        importBytes(packId, bytes, label = sourceLabel(uri))
    } catch (e: Exception) { ImportResult.Failed(e.message ?: "Import failed") }
    }

    fun importBytes(
        packId: String,
        input: ByteArray,
        globalDedup: Boolean = false,
        label: String = "",
        tags: String = ""
    ): ImportResult {
        return try {
        val pack = store.getPack(packId) ?: return ImportResult.Failed("Pack not found")
        if (store.listStickers(packId).size >= 30) return ImportResult.Failed("Pack full")
        val image = if (pack.kind == StickerKind.ANIMATED) {
            if (input.size > 500_000 || !WebpSticker.isAnimatedHeader(input))
                return ImportResult.Failed("Animated packs require animated WebP files up to 500 KB")
            val inspected = WebpSticker.inspect(input)
            if (inspected == null || !inspected.animated)
                return ImportResult.Failed("Animation must be 512x512, each frame >=8ms, max 10 seconds")
            input
        } else {
            if (isGif(input) || WebpSticker.isAnimatedHeader(input))
                return ImportResult.Failed("Animated stickers need a separate animated pack")
            normalize(input)
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(image).joinToString("") { "%02x".format(it) }
        if ((globalDedup && store.hasStickerHash(hash)) || store.listStickers(packId).any { it.sha256 == hash }) return ImportResult.Duplicate
        val id = UUID.randomUUID().toString()
        val name = "$id.webp"
        val dest = stickerFile(packId, name)
        dest.parentFile?.mkdirs()
        dest.writeBytes(image)
        val added = try {
            store.addSticker(StickerItem(
                id = id,
                packId = packId,
                fileName = name,
                sha256 = hash,
                createdAt = System.currentTimeMillis(),
                label = label,
                tags = tags
            ))
        } catch (e: Exception) { dest.delete(); throw e }
        if (!added) { dest.delete(); return ImportResult.Duplicate }
        refreshTray(packId)
        ImportResult.Added
    } catch (e: Exception) { ImportResult.Failed(e.message ?: "Image conversion failed") }
    }

    fun transferSticker(item: StickerItem, targetPackId: String, move: Boolean): ImportResult {
        return try {
        if (targetPackId == item.packId) return ImportResult.Failed("Choose another pack")
        val origin = store.getPack(item.packId) ?: return ImportResult.Failed("Source pack not found")
        val target = store.getPack(targetPackId) ?: return ImportResult.Failed("Target pack not found")
        if (origin.kind != target.kind) return ImportResult.Failed("Static and animated stickers must use separate packs")
        if (store.listStickers(targetPackId).size >= 30) return ImportResult.Failed("Target pack full")
        if (store.listStickers(targetPackId).any { it.sha256 == item.sha256 }) return ImportResult.Duplicate
        val id = UUID.randomUUID().toString()
        val filename = "$id.webp"
        val dest = stickerFile(targetPackId, filename)
        dest.parentFile?.mkdirs()
        stickerFile(item.packId, item.fileName).copyTo(dest)
        val added = try {
            store.addSticker(StickerItem(
                id = id,
                packId = targetPackId,
                fileName = filename,
                sha256 = item.sha256,
                createdAt = System.currentTimeMillis(),
                label = item.label,
                tags = item.tags
            ))
        } catch (e: Exception) { dest.delete(); throw e }
        if (!added) { dest.delete(); return ImportResult.Duplicate }
        refreshTray(targetPackId)
        if (move) deleteSticker(item)
        ImportResult.Added
    } catch (e: Exception) { ImportResult.Failed(e.message ?: "Transfer failed") }
    }

    fun deleteSticker(item: StickerItem) {
        store.deleteSticker(item.packId, item.id)
        stickerFile(item.packId, item.fileName).delete()
        refreshTray(item.packId)
    }
    fun deletePack(packId: String) {
        store.deletePack(packId)
        File(stickersDir, packId).deleteRecursively()
        trayFile(packId).delete()
    }
    fun refreshTray(packId: String) {
        val first = store.listStickers(packId).firstOrNull()
        val tray = trayFile(packId)
        if (first == null) { tray.delete(); return }
        val bitmap = BitmapFactory.decodeFile(stickerFile(packId, first.fileName).path) ?: return
        val small = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
        traysDir.mkdirs()
        tray.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (small !== bitmap) small.recycle()
        bitmap.recycle()
    }
    private fun sourceLabel(uri: Uri): String {
        val displayName = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Exception) { null }
        return displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
    }
    private fun normalize(bytes: ByteArray): ByteArray {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        require(opts.outWidth in 1..16000 && opts.outHeight in 1..16000) { "Invalid image dimensions" }
        var sample = 1
        while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) sample *= 2
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Cannot decode image")
        val dest = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val scale = 512f / max(original.width, original.height)
        val width = original.width * scale
        val height = original.height * scale
        Canvas(dest).drawBitmap(original, null,
            android.graphics.RectF((512-width)/2f, (512-height)/2f, (512+width)/2f, (512+height)/2f),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        original.recycle()
        try {
            for (q in 95 downTo 15 step 10) {
                val output = ByteArrayOutputStream()
                check(dest.compress(Bitmap.CompressFormat.WEBP_LOSSY, q, output))
                if (output.size() <= 100_000) return output.toByteArray()
            }
            error("Image cannot fit WhatsApp static sticker size")
        } finally { dest.recycle() }
    }
    private fun isGif(data: ByteArray) = data.size >= 3 && String(data, 0, 3, Charsets.US_ASCII) == "GIF"
    companion object {
        fun boundedRead(stream: java.io.InputStream, limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                require(out.size().toLong() + n <= limit) { "File too large" }
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
