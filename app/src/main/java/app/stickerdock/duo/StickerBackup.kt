package app.stickerdock.duo

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class StickerBackup(private val context: Context, private val store: StickerStore, private val files: StickerFiles) {
    data class RestoreSummary(val packs: Int, val stickers: Int, val rejected: Int, val packIds: List<String> = emptyList())
    data class ExportEstimate(val packs: Int, val stickers: Int, val bytes: Long)

    private data class ExportPack(val pack: StickerPack, val stickers: List<StickerItem>)
    private data class ValidatedSticker(
        val stagedFile: File,
        val sha256: String,
        val label: String,
        val tags: String
    )
    private data class ValidatedPack(
        val name: String,
        val kind: StickerKind,
        val rating: PackRating,
        val category: PackCategory,
        val stickers: List<ValidatedSticker>
    )

    fun estimate(singlePackId: String? = null): ExportEstimate {
        val export = prepareExport(singlePackId)
        val manifestBytes = buildManifest(export).size
        return ExportEstimate(export.size, export.sumOf { it.stickers.size },
            manifestBytes + export.sumOf { pack -> pack.stickers.sumOf { sticker ->
                files.stickerFile(pack.pack.id, sticker.fileName).length()
            } })
    }

    fun export(uri: Uri, singlePackId: String? = null) {
        val prepared = prepareExport(singlePackId)
        val manifest = buildManifest(prepared)
        val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot create backup")
        writeExport(output, prepared, manifest)
    }

    fun export(output: OutputStream, singlePackId: String? = null) {
        val prepared = prepareExport(singlePackId)
        writeExport(output, prepared, buildManifest(prepared))
    }

    private fun collectExport(singlePackId: String?): List<ExportPack> {
        val packs = if (singlePackId == null) store.listPacks()
            else listOfNotNull(store.getPack(singlePackId))
        require(packs.isNotEmpty()) { "No packs to export" }
        return packs.map { ExportPack(it, store.listStickers(it.id)) }
    }

    private fun prepareExport(singlePackId: String?): List<ExportPack> {
        val export = collectExport(singlePackId)
        require(export.size <= MAX_PACKS) {
            "Backup supports at most $MAX_PACKS packs; share large libraries as individual pack ZIPs"
        }
        val entryCount = 1 + export.sumOf { it.stickers.size }
        require(entryCount <= MAX_ENTRIES) {
            "Backup supports at most ${MAX_ENTRIES - 1} stickers; share large libraries as individual pack ZIPs"
        }
        var total = 0L
        export.forEach { group ->
            require(group.stickers.size <= 30) { "Pack ${group.pack.name} contains over 30 stickers" }
            group.stickers.forEach { item ->
                val file = files.stickerFile(group.pack.id, item.fileName)
                require(file.isFile) { "Missing sticker file in ${group.pack.name}" }
                val maximum = if (group.pack.kind == StickerKind.ANIMATED) 500_000L else 100_000L
                require(file.length() in 12..maximum) { "Invalid sticker size in ${group.pack.name}" }
                total += file.length()
                require(total <= MAX_TOTAL_BYTES) {
                    "Backup exceeds ${MAX_TOTAL_BYTES / 1_000_000} MB; share packs in smaller batches"
                }
            }
        }
        return export
    }

    private fun buildManifest(export: List<ExportPack>): ByteArray {
        val manifest = JSONObject().put("schema", 5).put("packs", JSONArray().also { array ->
            export.forEach { group ->
                val pack = group.pack
                array.put(JSONObject().put("id", pack.id).put("name", pack.name)
                    .put("kind", pack.kind.name).put("rating", pack.rating.name)
                    .put("category", pack.category.name)
                    .put("stickers", JSONArray().also { stickers ->
                        group.stickers.forEach { item ->
                            stickers.put(JSONObject()
                                .put("name", item.fileName)
                                .put("sha256", item.sha256)
                                .put("label", item.label)
                                .put("tags", item.tags))
                        }
                    }))
            }
        }).toString().toByteArray()
        require(manifest.size <= MAX_MANIFEST_BYTES) { "Backup manifest is too large" }
        val stickerBytes = export.sumOf { group -> group.stickers.sumOf { item ->
            files.stickerFile(group.pack.id, item.fileName).length()
        } }
        require(stickerBytes + manifest.size <= MAX_TOTAL_BYTES) {
            "Backup exceeds ${MAX_TOTAL_BYTES / 1_000_000} MB; share packs in smaller batches"
        }
        return manifest
    }

    private fun writeExport(output: OutputStream, export: List<ExportPack>, manifest: ByteArray) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
            export.forEach { group ->
                group.stickers.forEach { item ->
                    zip.putNextEntry(ZipEntry("packs/${group.pack.id}/${item.fileName}"))
                    files.stickerFile(group.pack.id, item.fileName).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun restore(uri: Uri): RestoreSummary {
        val staging = File(context.cacheDir, "backup-restore-${UUID.randomUUID()}")
        require(staging.mkdir()) { "Cannot prepare backup restore" }
        try {
            val archive = stageArchive(uri, staging)
            val manifestFile = archive["manifest.json"] ?: error("Missing manifest")
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            val schema = manifest.getInt("schema")
            require(schema in 1..5) { "Unsupported backup schema" }
            val packs = manifest.getJSONArray("packs")
            require(packs.length() <= MAX_PACKS) { "Backup has too many packs" }
            val expected = mutableSetOf("manifest.json")
            val seenPackIds = mutableSetOf<String>()
            val validated = mutableListOf<ValidatedPack>()

            for (i in 0 until packs.length()) {
                val pack = packs.getJSONObject(i)
                val id = pack.getString("id")
                require(BackupArchivePaths.isId(id) && seenPackIds.add(id)) { "Invalid or duplicate pack id" }
                val name = pack.getString("name")
                val kind = if (schema >= 2) StickerKind.valueOf(pack.getString("kind")) else StickerKind.STATIC
                val rating = if (schema >= 3) PackRating.valueOf(pack.getString("rating")) else PackRating.GENERAL
                val category = BackupPackCategory.read(pack, schema)
                require(name.isNotBlank() && name.length <= 60 && name.none { it.code < 32 }) {
                    "Invalid pack name"
                }
                val stickers = pack.getJSONArray("stickers")
                require(stickers.length() <= 30) { "Backup pack has too many stickers" }
                val images = mutableListOf<ValidatedSticker>()
                for (j in 0 until stickers.length()) {
                    val item = stickers.getJSONObject(j)
                    val nameOfFile = item.getString("name")
                    require(BackupArchivePaths.isStickerName(nameOfFile)) { "Invalid sticker name" }
                    val path = "packs/$id/$nameOfFile"
                    require(expected.add(path)) { "Duplicate sticker entry" }
                    val staged = archive[path] ?: error("Missing sticker")
                    val maximum = if (kind == StickerKind.ANIMATED) 500_000L else 100_000L
                    require(staged.length() in 12..maximum) { "Invalid sticker size" }
                    val hash = sha256(staged)
                    require(hash.equals(item.getString("sha256"), ignoreCase = true)) {
                        "Backup sticker checksum mismatch"
                    }
                    val label = if (schema >= 5) item.optString("label", "") else ""
                    val tags = if (schema >= 5) item.optString("tags", "") else ""
                    require(label.length <= 80 && tags.length <= 400) { "Sticker metadata too long" }
                    images += ValidatedSticker(staged, hash, label, tags)
                }
                validated += ValidatedPack(name, kind, rating, category, images)
            }
            require(archive.keys == expected) { "Unexpected archive entry" }
            return importValidated(validated)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun stageArchive(uri: Uri, staging: File): Map<String, File> {
        val archive = mutableMapOf<String, File>()
        var total = 0L
        var count = 0
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && BackupArchivePaths.isEntry(entry.name)) { "Invalid ZIP path" }
                require(!archive.containsKey(entry.name) && ++count <= MAX_ENTRIES) {
                    "Duplicate or oversized archive"
                }
                val limit = if (entry.name == "manifest.json") MAX_MANIFEST_BYTES.toLong() else MAX_ENTRY_BYTES
                val staged = File(staging, "entry-$count")
                staged.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var entryBytes = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        total += read
                        require(entryBytes <= limit) { "Backup entry is too large" }
                        require(total <= MAX_TOTAL_BYTES) { "Archive is too large" }
                        output.write(buffer, 0, read)
                    }
                }
                archive[entry.name] = staged
                zip.closeEntry()
            }
        }
        return archive
    }

    private fun importValidated(validated: List<ValidatedPack>): RestoreSummary {
        val createdPackIds = mutableListOf<String>()
        var added = 0
        try {
            validated.forEach { restored ->
                val pack = store.createPack(restored.name, restored.kind, restored.rating, restored.category)
                createdPackIds += pack.id
                restored.stickers.forEach { sticker ->
                    val data = sticker.stagedFile.inputStream().use {
                        StickerFiles.boundedRead(it, MAX_ENTRY_BYTES.toInt())
                    }
                    when (val result = files.restoreBytes(pack.id, data, sticker.sha256,
                        sticker.label, sticker.tags)) {
                        ImportResult.Added -> added++
                        ImportResult.Duplicate -> error("Unexpected duplicate in restored pack")
                        is ImportResult.Failed -> error(result.reason)
                    }
                }
            }
        } catch (error: Exception) {
            createdPackIds.forEach(files::deletePack)
            throw error
        }
        return RestoreSummary(validated.size, added, 0, createdPackIds.toList())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_PACKS = 500
        private const val MAX_ENTRIES = 15_001
        private const val MAX_ENTRY_BYTES = 1_000_000L
        private const val MAX_MANIFEST_BYTES = 10_000_000
        private const val MAX_TOTAL_BYTES = 1_500_000_000L
    }
}
