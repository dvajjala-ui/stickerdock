package app.stickerdock.duo

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Private Android projects surround an unchanged v5 portable sticker archive. */
class EditableBackup(private val context: Context, private val store: StickerStore, private val files: StickerFiles) {
    private val saved = StickerBackup(context, store, files)
    private val projects = ArtworkProjects(context, store, files)

    fun export(uri: Uri) = prepare { staging, manifest ->
        val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot create backup")
        write(output, staging, manifest)
    }

    fun export(output: OutputStream) = prepare { staging, manifest -> write(output, staging, manifest) }

    private fun prepare(consume: (File, ByteArray) -> Unit) {
        val staging = stageDirectory()
        try {
            val library = File(staging, LIBRARY)
            library.outputStream().use { saved.export(it) }
            var total = library.length()
            require(total <= MAX_TOTAL) { "Editable backup is too large" }
            val blobs = File(staging, "blobs").apply { check(mkdir()) }
            val entries = JSONArray()
            var metadataBytes = 0
            var count = 2
            fun retain(bytes: ByteArray, suffix: String): String {
                val name = StickerFiles.sha256(bytes) + suffix
                val target = File(blobs, name)
                if (!target.exists()) {
                    require(++count <= MAX_ENTRIES && total + bytes.size <= MAX_TOTAL) { "Editable backup is too large. Use a saved-sticker backup." }
                    target.writeBytes(bytes)
                    total += bytes.size
                }
                return "blobs/$name"
            }
            // Match against the immutable saved archive, including edits that changed
            // the filename without changing the sticker's database ID.
            val index = libraryIndex(library)
            store.listPacks().forEach { pack -> store.listStickers(pack.id).forEach items@ { item ->
                val snapshot = projects.snapshot(item) ?: return@items
                val path = "packs/${pack.id}/${item.fileName}"
                require(index[path]?.hash == item.sha256) { "Library changed during backup. Try again." }
                val originalPath = retain(snapshot.original, ".webp")
                require(originalPath == "blobs/${snapshot.record.sourceSha256}.webp") { "Starting artwork checksum mismatch" }
                val maskList = JSONArray()
                snapshot.masks.toSortedMap().forEach { (hash, bytes) ->
                    require(retain(bytes, ".png") == "blobs/$hash.png") { "Cutout checksum mismatch" }
                    maskList.put(hash)
                }
                val entry = JSONObject().put("sticker", path).put("source_sha256", snapshot.record.sourceSha256)
                    .put("saved_sha256", item.sha256).put("saved_history", snapshot.record.savedHistory)
                    .put("draft_history", snapshot.record.draftHistory ?: JSONObject.NULL).put("masks", maskList)
                metadataBytes += entry.toString().toByteArray(Charsets.UTF_8).size
                require(metadataBytes <= MAX_MANIFEST - 1024 && entries.length() < MAX_PROJECTS) { "Editable project metadata is too large" }
                entries.put(entry)
            } }
            val manifest = JSONObject().put("format", FORMAT).put("schema", 1)
                .put("library_sha256", digest(library)).put("projects", entries).toString().toByteArray(Charsets.UTF_8)
            require(manifest.size <= MAX_MANIFEST && total + manifest.size <= MAX_TOTAL) { "Editable backup is too large" }
            consume(staging, manifest)
        } finally { staging.deleteRecursively() }
    }

    private fun write(output: OutputStream, staging: File, manifest: ByteArray) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest); zip.closeEntry()
            val paths = listOf(File(staging, LIBRARY)) + File(staging, "blobs").listFiles().orEmpty().sortedBy { it.name }
            for (file in paths) {
                zip.putNextEntry(ZipEntry(file.relativeTo(staging).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun restoreOrSaved(uri: Uri): StickerBackup.RestoreSummary {
        val editable = context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { it.nextEntry?.name == MANIFEST }
        } ?: error("Cannot open backup")
        return if (editable) restore(uri) else saved.restore(uri)
    }

    fun restore(uri: Uri): StickerBackup.RestoreSummary {
        val staging = stageDirectory()
        try {
            val archive = stage(uri, staging)
            val document = JSONObject(requireNotNull(archive[MANIFEST]) { "Missing editable backup manifest" }.readText(Charsets.UTF_8))
            require(document.getString("format") == FORMAT && document.getInt("schema") == 1) { "Unsupported editable backup" }
            val library = requireNotNull(archive[LIBRARY]) { "Missing saved stickers" }
            require(digest(library) == document.getString("library_sha256")) { "Saved sticker archive checksum mismatch" }
            val index = libraryIndex(library)
            val entries = document.getJSONArray("projects")
            require(entries.length() <= MAX_PROJECTS) { "Too many editable projects" }
            val expected = mutableSetOf(MANIFEST, LIBRARY)
            val seen = mutableSetOf<String>()
            val validated = mutableListOf<Pair<String, ArtworkProjectRecord>>()
            // Validate every source, recipe and referenced mask before creating packs.
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val path = entry.getString("sticker")
                require(BackupArchivePaths.isEntry(path) && path != "manifest.json" && seen.add(path)) { "Invalid or repeated project sticker" }
                val source = entry.getString("source_sha256")
                val hash = entry.getString("saved_sha256")
                require(isHash(source) && isHash(hash) && index[path]?.hash == hash && index[path]?.kind == StickerKind.STATIC) { "Project does not match saved static artwork" }
                val originalPath = "blobs/$source.webp"
                expected += originalPath
                val original = requireNotNull(archive[originalPath]) { "Missing starting artwork" }
                validateOriginal(original, source)
                val history = entry.getString("saved_history")
                val draft = if (entry.isNull("draft_history")) null else entry.getString("draft_history")
                val requiredMasks = maskHashes(ArtworkHistory.decodeStrict(history)) + draft?.let { maskHashes(ArtworkHistory.decodeStrict(it)) }.orEmpty()
                val masks = entry.getJSONArray("masks")
                require(masks.length() <= 102) { "Too many project masks" }
                val provided = (0 until masks.length()).map { masks.getString(it).also { value -> require(isHash(value)) } }
                require(provided.size == provided.toSet().size && provided.toSet() == requiredMasks) { "Project mask references do not match" }
                for (mask in provided) {
                    val maskPath = "blobs/$mask.png"
                    expected += maskPath
                    val file = requireNotNull(archive[maskPath]) { "Missing cutout mask" }
                    require(digest(file) == mask) { "Cutout checksum mismatch" }
                    ArtworkMasks.decode(file.readBytes()).recycle()
                }
                validated += path to ArtworkProjectRecord(UUID.randomUUID().toString(), source, hash, history, draft, System.currentTimeMillis())
            }
            require(archive.keys == expected) { "Unexpected editable backup content" }
            val restored = saved.restore(Uri.fromFile(library))
            try {
                for ((path, record) in validated) {
                    val item = requireNotNull(restored.stickerIdsByArchivePath[path]?.let(store::stickerById)) { "Restored project sticker is missing" }
                    require(store.getPack(item.packId)?.kind == StickerKind.STATIC) { "Editable project must use a static sticker" }
                    val masks = maskHashes(ArtworkHistory.decodeStrict(record.savedHistory)) + record.draftHistory?.let { maskHashes(ArtworkHistory.decodeStrict(it)) }.orEmpty()
                    projects.restoreSnapshot(item, ArtworkProjects.Snapshot(record, archive.getValue("blobs/${record.sourceSha256}.webp").readBytes(),
                        masks.associateWith { archive.getValue("blobs/$it.png").readBytes() }))
                }
            } catch (error: Exception) {
                // Roll back only packs created by this restore. Existing data stays intact.
                restored.packIds.forEach(files::deletePack)
                throw error
            }
            return restored
        } finally { staging.deleteRecursively() }
    }

    private fun stage(uri: Uri, directory: File): Map<String, File> {
        val paths = mutableMapOf<String, File>()
        var total = 0L
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read editable backup")
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!entry.isDirectory && allowed(name) && paths.size < MAX_ENTRIES && !paths.containsKey(name)) { "Invalid or repeated editable backup path" }
                if (paths.isEmpty()) require(name == MANIFEST) { "Editable backup manifest must be first" }
                val maximum = when { name == MANIFEST -> MAX_MANIFEST.toLong(); name == LIBRARY -> MAX_TOTAL; name.endsWith(".png") -> ArtworkMasks.MAX_BYTES.toLong(); else -> 100_000L }
                val file = File(directory, "entry-${paths.size}")
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var count = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        count += read; total += read
                        require(count <= maximum && total <= MAX_TOTAL) { "Editable backup expands beyond its limit" }
                        output.write(buffer, 0, read)
                    }
                }
                paths[name] = file
                zip.closeEntry()
            }
        }
        return paths
    }

    private data class SavedSticker(val hash: String, val kind: StickerKind)

    private fun libraryIndex(library: File): Map<String, SavedSticker> {
        val document = library.inputStream().use { raw -> ZipInputStream(raw).use { zip ->
            require(zip.nextEntry?.name == "manifest.json") { "Saved archive manifest must be first" }
            JSONObject(String(StickerFiles.boundedRead(zip, MAX_MANIFEST), Charsets.UTF_8))
        } }
        require(document.getInt("schema") in 1..5) { "Unsupported saved sticker format" }
        val packs = document.getJSONArray("packs")
        require(packs.length() in 1..500) { "Invalid saved pack count" }
        val result = mutableMapOf<String, SavedSticker>()
        for (i in 0 until packs.length()) {
            val pack = packs.getJSONObject(i)
            val id = pack.getString("id")
            val kind = if (document.getInt("schema") >= 2) StickerKind.valueOf(pack.getString("kind")) else StickerKind.STATIC
            require(BackupArchivePaths.isId(id))
            val items = pack.getJSONArray("stickers")
            require(items.length() <= 30)
            for (j in 0 until items.length()) {
                val item = items.getJSONObject(j)
                val name = item.getString("name")
                require(BackupArchivePaths.isStickerName(name))
                val hash = item.getString("sha256").lowercase()
                require(isHash(hash) && result.put("packs/$id/$name", SavedSticker(hash, kind)) == null) { "Invalid or duplicate saved sticker" }
            }
        }
        return result
    }

    private fun validateOriginal(file: File, hash: String) {
        require(file.length() in 12..100_000 && digest(file) == hash) { "Starting artwork checksum mismatch" }
        val bytes = file.readBytes()
        require(WebpSticker.isWebpHeader(bytes) && !WebpSticker.isAnimatedHeader(bytes)) { "Invalid starting artwork" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth == 512 && bounds.outHeight == 512) { "Invalid starting artwork dimensions" }
    }

    private fun stageDirectory() = File(context.cacheDir, "editable-backup-${UUID.randomUUID()}").also { check(it.mkdir()) { "Cannot prepare editable backup" } }
    private fun digest(file: File): String = file.inputStream().use { stream ->
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun maskHashes(history: ArtworkHistory) = history.entries.map { it.maskHash }.filter { it.isNotEmpty() }.toSet()
    private fun isHash(value: String) = Regex("[0-9a-f]{64}").matches(value)
    private fun allowed(path: String) = path in setOf(MANIFEST, LIBRARY) || Regex("blobs/[0-9a-f]{64}\\.(?:webp|png)").matches(path)

    companion object {
        const val MANIFEST = "editable-backup.json"
        private const val LIBRARY = "library.zip"
        private const val FORMAT = "StickerDock editable backup"
        private const val MAX_MANIFEST = 10_000_000
        private const val MAX_TOTAL = 1_500_000_000L
        private const val MAX_ENTRIES = 30_002
        private const val MAX_PROJECTS = 15_000
    }
}
