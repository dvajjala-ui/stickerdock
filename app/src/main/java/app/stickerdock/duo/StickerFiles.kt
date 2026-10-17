package app.stickerdock.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
        val hash = sha256(image)
        if (store.hasStickerHash(hash, if (globalDedup) null else packId)) return ImportResult.Duplicate
        if (store.stickerCount(packId) >= 30) return ImportResult.Failed("Pack full")
        val id = UUID.randomUUID().toString()
        val name = "$id.webp"
        val dest = stickerFile(packId, name)
        writeSticker(dest, image)
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
        runCatching { refreshTray(packId) }
        ImportResult.Added
    } catch (e: Exception) { ImportResult.Failed(e.message ?: "Image conversion failed") }
    }

    /** Restores already-normalized StickerDock bytes without lossy recompression. */
    fun restoreBytes(
        packId: String,
        input: ByteArray,
        expectedSha256: String,
        label: String = "",
        tags: String = "",
        commitSticker: (StickerItem) -> Unit = {}
    ): ImportResult {
        return try {
            val pack = store.getPack(packId) ?: return ImportResult.Failed("Pack not found")
            if (!WebpSticker.isWebpHeader(input)) return ImportResult.Failed("Backup sticker is not WebP")
            if (pack.kind == StickerKind.ANIMATED) {
                if (input.size > 500_000 || WebpSticker.inspect(input)?.animated != true)
                    return ImportResult.Failed("Invalid animated backup sticker")
            } else {
                if (input.size > 100_000 || WebpSticker.isAnimatedHeader(input))
                    return ImportResult.Failed("Invalid static backup sticker")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
                if (bounds.outWidth != 512 || bounds.outHeight != 512)
                    return ImportResult.Failed("Static backup sticker must be 512x512")
            }
            val hash = sha256(input)
            if (!hash.equals(expectedSha256, ignoreCase = true))
                return ImportResult.Failed("Backup sticker checksum mismatch")
            if (store.hasStickerHash(hash, packId)) return ImportResult.Duplicate
            if (store.stickerCount(packId) >= 30) return ImportResult.Failed("Pack full")

            val id = UUID.randomUUID().toString()
            val name = "$id.webp"
            val dest = stickerFile(packId, name)
            writeSticker(dest, input)
            val item = StickerItem(id, packId, name, hash, System.currentTimeMillis(), label, tags)
            val added = try {
                store.transaction {
                    val added = store.addSticker(item)
                    if (added) commitSticker(item)
                    added
                }
            } catch (e: Exception) { dest.delete(); ArtworkProjects.deleteLocalSource(context, id); throw e }
            if (!added) { dest.delete(); return ImportResult.Duplicate }
            runCatching { refreshTray(packId) }
            ImportResult.Added
        } catch (e: Exception) {
            ImportResult.Failed(e.message ?: "Backup restore failed")
        }
    }

    fun transferSticker(item: StickerItem, targetPackId: String, move: Boolean): ImportResult {
        return try {
        if (targetPackId == item.packId) return ImportResult.Failed("Choose another pack")
        val origin = store.getPack(item.packId) ?: return ImportResult.Failed("Source pack not found")
        val target = store.getPack(targetPackId) ?: return ImportResult.Failed("Target pack not found")
        if (origin.kind != target.kind) return ImportResult.Failed("Static and animated stickers must use separate packs")
        if (store.hasStickerHash(item.sha256, targetPackId)) return ImportResult.Duplicate
        if (store.stickerCount(targetPackId) >= 30) return ImportResult.Failed("Target pack full")
        val id = UUID.randomUUID().toString()
        val filename = "$id.webp"
        val dest = stickerFile(targetPackId, filename)
        val source = stickerFile(item.packId, item.fileName).readBytes()
        require(sha256(source) == item.sha256 && WebpSticker.isWebpHeader(source)) { "Source sticker is damaged. Restore it from a backup before copying or moving." }
        writeSticker(dest, source)
        val targetItem = StickerItem(
                id = id,
                packId = targetPackId,
                fileName = filename,
                sha256 = item.sha256,
                createdAt = System.currentTimeMillis(),
                label = item.label,
                tags = item.tags
            )
        val added = try {
            store.transaction {
                val added = store.addSticker(targetItem)
                if (added) ArtworkProjects(context, store, this).let { projects ->
                    projects.snapshot(item)?.let { projects.restoreSnapshot(targetItem, it, includeDraft = move) }
                }
                added
            }
        } catch (e: Exception) { dest.delete(); ArtworkProjects.deleteLocalSource(context, id); throw e }
        if (!added) { dest.delete(); return ImportResult.Duplicate }
        runCatching { refreshTray(targetPackId) }
        if (move) deleteSticker(item)
        ImportResult.Added
    } catch (e: Exception) { ImportResult.Failed(e.message ?: "Transfer failed") }
    }

    fun deleteSticker(item: StickerItem) {
        store.deleteSticker(item.packId, item.id)
        stickerFile(item.packId, item.fileName).delete()
        ArtworkProjects.deleteLocalSource(context, item.id)
        refreshTray(item.packId)
    }
    fun deletePack(packId: String) {
        val items = store.listStickers(packId)
        store.deletePack(packId)
        File(stickersDir, packId).deleteRecursively()
        items.forEach { ArtworkProjects.deleteLocalSource(context, it.id) }
        trayFile(packId).delete()
    }
    fun deleteOrphanedPackFiles(packId: String): Int {
        val directory = File(stickersDir, packId)
        if (!directory.isDirectory) return 0
        val known = store.listStickers(packId).mapTo(mutableSetOf()) { it.fileName }
        var deleted = 0
        directory.listFiles().orEmpty().forEach { candidate ->
            if (candidate.isFile && candidate.name !in known && candidate.delete()) deleted++
        }
        if (deleted > 0) runCatching { refreshTray(packId) }
        return deleted
    }
    fun refreshTray(packId: String) {
        val first = store.listStickers(packId).firstOrNull()
        val tray = trayFile(packId)
        if (first == null) { tray.delete(); return }
        val bytes = stickerFile(packId, first.fileName).readBytes()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val small = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
        try {
            check(traysDir.isDirectory || traysDir.mkdirs()) { "Could not create pack artwork folder" }
            val atomic = AtomicFile(tray)
            val output = atomic.startWrite()
            try {
                check(small.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not save pack artwork" }
                output.fd.sync()
                atomic.finishWrite(output)
            } catch (e: Exception) { atomic.failWrite(output); throw e }
        } finally {
            if (small !== bitmap) small.recycle()
            bitmap.recycle()
        }
    }
    internal fun sourceLabel(uri: Uri): String {
        val displayName = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Exception) { null }
        return displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
    }
    /** New immutable filename avoids stale WhatsApp/thumbnail caches after editing. */
    fun replaceStaticArtwork(item: StickerItem, bytes: ByteArray, commitProject: (String) -> Unit = {}) {
        require(store.getPack(item.packId)?.kind == StickerKind.STATIC) { "Artwork editing supports static stickers" }
        require(bytes.size <= 100_000 && WebpSticker.isWebpHeader(bytes) && !WebpSticker.isAnimatedHeader(bytes)) { "Invalid static sticker" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth == 512 && bounds.outHeight == 512) { "Sticker must be 512 × 512" }
        val filename = "${UUID.randomUUID()}.webp"
        val destination = stickerFile(item.packId, filename)
        writeSticker(destination, bytes)
        try {
            store.transaction {
                val hash = sha256(bytes)
                store.replaceStickerArtwork(item, filename, hash)
                commitProject(hash)
            }
        } catch (e: Exception) { destination.delete(); throw e }
        stickerFile(item.packId, item.fileName).delete()
        runCatching { refreshTray(item.packId) }
    }

    /** UUID files become visible through SQLite only after their bytes reach storage.
     * Closing a stream alone left an edited file empty after an emulator cold boot.
     * A failed write/sync must leave the old row and artwork untouched.
     */
    private fun writeSticker(destination: File, bytes: ByteArray) {
        require(bytes.isNotEmpty()) { "Sticker is empty" }
        check(!destination.exists()) { "Sticker file already exists" }
        destination.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "Could not create sticker folder" } }
        try {
            FileOutputStream(destination).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            check(destination.length() == bytes.size.toLong() && sha256(destination.readBytes()) == sha256(bytes)) {
                "Sticker could not be verified after saving"
            }
        } catch (e: Exception) { destination.delete(); throw e }
    }
    private fun normalize(bytes: ByteArray): ByteArray {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        require(opts.outWidth in 1..16000 && opts.outHeight in 1..16000) { "Invalid image dimensions" }
        var sample = 1
        while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) sample *= 2
        val original = android.graphics.ImageDecoder.decodeBitmap(
            android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
        ) { decoder, _, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sample)
        }
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
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

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
