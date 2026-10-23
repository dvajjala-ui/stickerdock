package app.stickerdock.duo

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Read every sticker of a verified public ZIP for preview without touching the library. */
internal object CatalogStickerPreview {
    data class Item(val bytes: ByteArray, val sha256: String, val label: String, val kind: StickerKind)

    fun fromArchive(file: File): List<Item> = ZipFile(file).use { zip ->
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
        val schema = document.getInt("schema")
        val packs = document.getJSONArray("packs")
        require(packs.length() in 1..33) { "Invalid preview pack count" }
        val expected = mutableSetOf("manifest.json")
        val seenIds = mutableSetOf<String>()
        val items = mutableListOf<Item>()
        for (i in 0 until packs.length()) {
            val pack = packs.getJSONObject(i)
            val id = pack.getString("id")
            require(BackupArchivePaths.isId(id) && seenIds.add(id.lowercase()) &&
                pack.getString("rating") == "GENERAL") { "Only distinct general-audience packs can be previewed" }
            val kind = StickerKind.valueOf(pack.getString("kind"))
            val stickers = pack.getJSONArray("stickers")
            require(stickers.length() in 3..30) { "Invalid preview sticker count" }
            for (j in 0 until stickers.length()) {
                val sticker = stickers.getJSONObject(j)
                val name = sticker.getString("name")
                require(BackupArchivePaths.isStickerName(name)) { "Invalid preview sticker name" }
                val path = "packs/$id/$name"
                require(expected.add(path)) { "Duplicate preview sticker" }
                val entry = zip.getEntry(path) ?: error("Missing preview sticker")
                val bytes = zip.getInputStream(entry).use { StickerFiles.boundedRead(it, 500_000) }
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                require(hash == sticker.getString("sha256")) { "Preview sticker checksum mismatch" }
                val maximum = if (kind == StickerKind.ANIMATED) 500_000 else 100_000
                require(bytes.size in 30..maximum) { "Preview sticker exceeds its type limit" }
                val info = WebpSticker.inspect(bytes)
                require(info != null && info.width == 512 && info.height == 512 &&
                    info.animated == (kind == StickerKind.ANIMATED)) {
                    "Invalid preview sticker media"
                }
                val label = if (schema >= 5) sticker.optString("label", "") else ""
                require(label.length <= 80) { "Preview sticker label is too long" }
                items += Item(bytes, hash, label, kind)
            }
        }
        require(items.size in 3..100 && entries.map { it.name }.toSet() == expected) {
            "Unexpected preview archive entry"
        }
        items
    }
}
