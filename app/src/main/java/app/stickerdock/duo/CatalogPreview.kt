package app.stickerdock.duo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Compatibility preview for an older public archive. No extraction into the library or SQLite writes. */
internal object CatalogPreview {
    fun fromArchive(file: File): ByteArray = ZipFile(file).use { zip ->
        val entries = zip.entries().asSequence().take(102).toList()
        require(entries.size in 4..101 && entries.map { it.name }.distinct().size == entries.size) {
            "Invalid preview archive"
        }
        require(entries.all { !it.isDirectory && it.size in 1..500_000 } &&
            entries.sumOf { it.size } <= 50_000_000) { "Preview archive exceeds size limits" }
        val manifest = zip.getEntry("manifest.json") ?: error("Missing preview manifest")
        val document = JSONObject(zip.getInputStream(manifest).use {
            StickerFiles.boundedRead(it, 128_000).toString(Charsets.UTF_8)
        })
        require(document.getInt("schema") in 3..5) { "Unsupported preview archive" }
        val packs = document.getJSONArray("packs")
        require(packs.length() in 1..33) { "Invalid preview pack count" }
        val expected = mutableSetOf("manifest.json")
        val sources = mutableListOf<Pair<String, String>>()
        for (i in 0 until packs.length()) {
            val pack = packs.getJSONObject(i)
            val id = pack.getString("id")
            require(BackupArchivePaths.isId(id) && pack.getString("rating") == "GENERAL") {
                "Only general audience packs can be previewed"
            }
            StickerKind.valueOf(pack.getString("kind"))
            val stickers = pack.getJSONArray("stickers")
            require(stickers.length() in 3..30) { "Invalid preview sticker count" }
            for (j in 0 until stickers.length()) {
                val item = stickers.getJSONObject(j)
                val name = item.getString("name")
                require(BackupArchivePaths.isStickerName(name)) { "Invalid preview sticker name" }
                val path = "packs/$id/$name"
                require(expected.add(path)) { "Duplicate preview sticker" }
                if (sources.size < 6) sources += path to item.getString("sha256")
            }
        }
        require(entries.map { it.name }.toSet() == expected) { "Unexpected preview archive entry" }
        val sheet = Bitmap.createBitmap(576, 384, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(sheet)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            sources.forEachIndexed { index, (path, hash) ->
                val raw = zip.getInputStream(zip.getEntry(path)).use { StickerFiles.boundedRead(it, 500_000) }
                val digest = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
                require(digest == hash) { "Preview sticker checksum mismatch" }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
                require(bounds.outWidth == 512 && bounds.outHeight == 512) { "Invalid sticker dimensions" }
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(raw, 0, raw.size)) { "Cannot decode preview" }
                try {
                    val left = (index % 3) * 192f + 12f
                    val top = (index / 3) * 192f + 12f
                    canvas.drawBitmap(bitmap, null, RectF(left, top, left + 168f, top + 168f), paint)
                } finally { bitmap.recycle() }
            }
            ByteArrayOutputStream().use { output ->
                check(sheet.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, output)) { "Cannot create preview" }
                output.toByteArray().also { require(it.size <= 320_000) { "Preview exceeds size limit" } }
            }
        } finally { sheet.recycle() }
    }
}
