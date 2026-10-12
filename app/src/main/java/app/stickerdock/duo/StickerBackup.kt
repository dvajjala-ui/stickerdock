package app.stickerdock.duo

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class StickerBackup(private val context: Context, private val store: StickerStore, private val files: StickerFiles) {
    data class RestoreSummary(val packs: Int, val stickers: Int, val rejected: Int)
    fun export(uri: Uri, singlePackId: String? = null) {
        val packs = if (singlePackId == null) store.listPacks() else listOfNotNull(store.getPack(singlePackId))
        require(packs.isNotEmpty()) { "No packs to export" }
        val manifest = JSONObject().put("schema", 2).put("packs", JSONArray().also { array ->
            packs.forEach { pack ->
                array.put(JSONObject().put("id", pack.id).put("name", pack.name).put("kind", pack.kind.name)
                    .put("stickers", JSONArray().also { stickers ->
                        store.listStickers(pack.id).forEach { item ->
                            stickers.put(JSONObject().put("name", item.fileName).put("sha256", item.sha256))
                        }
                    }))
            }
        })
        val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot create backup")
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            packs.forEach { pack ->
                store.listStickers(pack.id).forEach { item ->
                    zip.putNextEntry(ZipEntry("packs/${pack.id}/${item.fileName}"))
                    files.stickerFile(pack.id, item.fileName).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
    fun restore(uri: Uri): RestoreSummary {
        val archive = mutableMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && (entry.name == "manifest.json" ||
                    Regex("packs/[0-9a-f-]{36}/[0-9a-f-]{36}\\.webp").matches(entry.name))) { "Invalid ZIP path" }
                require(!archive.containsKey(entry.name) && ++count <= 4000) { "Duplicate/oversized archive" }
                val data = StickerFiles.boundedRead(zip, 1_000_000)
                total += data.size
                require(total <= 75_000_000L) { "Archive too large" }
                archive[entry.name] = data
                zip.closeEntry()
            }
        }
        val manifest = JSONObject(String(archive["manifest.json"] ?: error("Missing manifest")))
        val schema = manifest.getInt("schema")
        require(schema == 1 || schema == 2) { "Unsupported backup schema" }
        val packs = manifest.getJSONArray("packs")
        require(packs.length() <= 500)
        val expected = mutableSetOf("manifest.json")
        val validated = mutableListOf<Triple<String, StickerKind, List<ByteArray>>>()
        for (i in 0 until packs.length()) {
            val pack = packs.getJSONObject(i)
            val id = pack.getString("id")
            require(Regex("[0-9a-f-]{36}").matches(id))
            val name = pack.getString("name")
            val kind = if (schema == 2) StickerKind.valueOf(pack.getString("kind")) else StickerKind.STATIC
            require(name.isNotBlank() && name.length <= 60)
            val stickers = pack.getJSONArray("stickers")
            require(stickers.length() <= 30)
            val images = mutableListOf<ByteArray>()
            for (j in 0 until stickers.length()) {
                val item = stickers.getJSONObject(j)
                val nameOfFile = item.getString("name")
                require(Regex("[0-9a-f-]{36}\\.webp").matches(nameOfFile))
                val path = "packs/$id/$nameOfFile"
                require(expected.add(path))
                val data = archive[path] ?: error("Missing sticker")
                require(data.size in 12..(if (kind == StickerKind.ANIMATED) 500_000 else 100_000))
                val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
                require(hash.equals(item.getString("sha256"), ignoreCase = true))
                images.add(data)
            }
            validated.add(Triple(name, kind, images))
        }
        require(archive.keys == expected) { "Unexpected archive entry" }
        var added = 0
        var rejected = 0
        validated.forEach { (name, kind, images) ->
            val pack = store.createPack(name, kind)
            images.forEach {
                when (files.importBytes(pack.id, it)) {
                    ImportResult.Added -> added++
                    else -> rejected++
                }
            }
        }
        return RestoreSummary(validated.size, added, rejected)
    }
}
