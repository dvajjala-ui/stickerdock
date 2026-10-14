package app.stickerdock.duo

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

/** Imports user-selected folders through Android's Storage Access Framework. */
class FolderStickerImporter(
    private val context: Context,
    private val store: StickerStore,
    private val files: StickerFiles
) {
    data class Result(
        val filesScanned: Int,
        val packsCreated: Int,
        val added: Int,
        val duplicates: Int,
        val rejected: Int,
        val paused: Boolean = false,
        val skippedDirectories: Int = 0
    )

    data class Progress(
        val filesScanned: Int,
        val added: Int,
        val duplicates: Int,
        val rejected: Int,
        val currentFile: String?,
        val resumed: Boolean
    )

    private data class Folder(val node: DocumentFile, val depth: Int)
    private data class Group(val title: String, val kind: StickerKind)

    fun import(
        treeUri: Uri,
        fallbackName: String = "Imported",
        shouldPause: () -> Boolean = { Thread.currentThread().isInterrupted },
        onProgress: ((Progress) -> Unit)? = null
    ): Result {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Cannot access the selected folder")
        require(root.isDirectory && root.canRead()) { "Selected folder is not readable" }

        migrateLegacyCheckpoint()
        var session = store.pendingFolderImport()
        if (session != null &&
            (session.treeUri != treeUri.toString() || session.fallbackName != fallbackName)) {
            cleanupEmptyPacks(store.folderImportPackIds(session.id))
            store.deleteFolderImport(session.id)
            session = null
        }
        val active = session ?: store.createFolderImport(treeUri.toString(), fallbackName)
        val processed = store.folderImportProcessedSources(active.id)
        val resumed = processed.isNotEmpty() || store.folderImportPackIds(active.id).isNotEmpty()

        val groups = mutableMapOf<Group, MutableList<StickerPack>>()
        val packIds = store.folderImportPackIds(active.id).toMutableSet()
        packIds.mapNotNull(store::getPack).sortedBy { it.createdAt }.forEach { pack ->
            files.deleteOrphanedPackFiles(pack.id)
            groups.getOrPut(Group(pack.name, pack.kind)) { mutableListOf() } += pack
        }
        val packCounts = store.stickerCounts().toMutableMap()
        var stats = store.folderImportStats(active.id)
        var filesScanned = stats.filesScanned
        var duplicates = stats.duplicates
        var rejected = stats.rejected
        var totalBytes = stats.totalBytes
        var skippedDirectories = 0
        var sinceProgress = 0
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Folder>().apply { add(Folder(root, 0)) }
        var visitedNodes = 0

        fun addedCount(): Int = packIds.sumOf { packCounts[it] ?: store.stickerCount(it) }

        fun publishProgress(name: String? = null) {
            onProgress?.invoke(Progress(filesScanned, addedCount(), duplicates, rejected, name, resumed))
        }

        fun result(paused: Boolean): Result {
            cleanupEmptyPacks(packIds, packCounts)
            val surviving = packIds.count { id -> (packCounts[id] ?: store.stickerCount(id)) > 0 }
            return Result(filesScanned, surviving, addedCount(), duplicates, rejected,
                paused, skippedDirectories)
        }

        fun record(source: String, outcome: String, sourceBytes: Long) {
            store.recordFolderImportItem(active.id, source, outcome, sourceBytes)
            processed += source
            filesScanned++
            when (outcome) {
                "DUPLICATE" -> duplicates++
                "REJECTED" -> rejected++
            }
            totalBytes += sourceBytes
            sinceProgress++
        }

        publishProgress()
        try {
            while (queue.isNotEmpty()) {
                if (shouldPause()) {
                    publishProgress()
                    return result(paused = true)
                }
                val folder = queue.removeFirst()
                if (!visited.add(folder.node.uri.toString())) continue
                for (child in folder.node.listFiles()) {
                    if (shouldPause()) {
                        publishProgress()
                        return result(paused = true)
                    }
                    if (++visitedNodes > MAX_ENTRIES) error("Folder contains too many entries")
                    if (child.isDirectory) {
                        if (folder.depth < MAX_DEPTH) queue.add(Folder(child, folder.depth + 1))
                        else skippedDirectories++
                        continue
                    }
                    if (!child.isFile) continue
                    val name = child.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (ext !in IMAGE_EXTENSIONS) continue
                    val source = child.uri.toString()
                    if (source in processed) continue
                    if (filesScanned + 1 > MAX_IMAGES) error("Import limit of 20,000 images exceeded")

                    if (child.length() > MAX_SOURCE_BYTES) {
                        record(source, "REJECTED", 0)
                        continue
                    }
                    val image = try {
                        context.contentResolver.openInputStream(child.uri)?.use {
                            StickerFiles.boundedRead(it, MAX_SOURCE_BYTES.toInt())
                        }
                    } catch (_: Exception) { null }
                    if (image == null) {
                        record(source, "REJECTED", 0)
                        continue
                    }
                    require(totalBytes + image.size <= MAX_TOTAL_BYTES) {
                        "Selected folder contains over 1.5 GB of image data; import in smaller batches"
                    }

                    val kind = if (WebpSticker.isAnimatedHeader(image)) StickerKind.ANIMATED else StickerKind.STATIC
                    val metadata = if (ext == "webp") StickerPackMetadata.read(image) else null
                    val title = (metadata?.packName ?: folder.node.name ?: active.fallbackName)
                        .trim().take(60).ifBlank { active.fallbackName }
                    val key = Group(title, kind)
                    val belongingPacks = groups.getOrPut(key) { mutableListOf() }
                    val current = belongingPacks.lastOrNull()
                    val candidate = if (current == null || (packCounts[current.id] ?: 0) >= 30) {
                        val created = store.createPack(title, kind)
                        try {
                            store.addFolderImportPack(active.id, created.id)
                        } catch (error: Exception) {
                            files.deletePack(created.id)
                            throw error
                        }
                        belongingPacks += created
                        packIds += created.id
                        packCounts[created.id] = 0
                        created
                    } else current

                    val importResult = store.transaction {
                        val imported = files.importBytes(candidate.id, image, globalDedup = true, label = name)
                        val outcome = when (imported) {
                            ImportResult.Added -> "ADDED"
                            ImportResult.Duplicate -> "DUPLICATE"
                            is ImportResult.Failed -> "REJECTED"
                        }
                        record(source, outcome, image.size.toLong())
                        imported
                    }
                    if (importResult == ImportResult.Added) {
                        packCounts[candidate.id] = (packCounts[candidate.id] ?: 0) + 1
                    }
                    if (sinceProgress >= PROGRESS_EVERY) {
                        publishProgress(name)
                        sinceProgress = 0
                    }
                }
            }

            val completed = result(paused = false)
            store.deleteFolderImport(active.id)
            clearLegacyCheckpoint()
            publishProgress()
            return completed
        } catch (error: Exception) {
            publishProgress()
            throw error
        }
    }

    fun discardPendingImport(): Int {
        migrateLegacyCheckpoint()
        val session = store.pendingFolderImport() ?: return 0
        val removed = cleanupEmptyPacks(store.folderImportPackIds(session.id))
        store.deleteFolderImport(session.id)
        clearLegacyCheckpoint()
        return removed
    }

    private fun cleanupEmptyPacks(packIds: Set<String>, knownCounts: Map<String, Int>? = null): Int {
        var removed = 0
        packIds.forEach { id ->
            val pack = store.getPack(id) ?: return@forEach
            val count = knownCounts?.get(id) ?: store.stickerCount(id)
            if (count == 0) {
                files.deletePack(pack.id)
                removed++
            }
        }
        return removed
    }

    /** Moves the pre-v7 SharedPreferences/text checkpoint into the SQLite journal. */
    private fun migrateLegacyCheckpoint() {
        if (store.pendingFolderImport() != null) return
        val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val uri = prefs.getString(KEY_TREE_URI, null) ?: return
        val fallback = prefs.getString(KEY_FALLBACK, "Imported") ?: "Imported"
        val sources = legacyProcessedFile().takeIf(File::isFile)?.readLines()
            ?.filter(String::isNotBlank).orEmpty().distinct()
        store.transaction {
            val session = store.createFolderImport(uri, fallback)
            val packIds = prefs.getStringSet(KEY_PACK_IDS, emptySet()).orEmpty()
            packIds.filter { store.getPack(it) != null }
                .forEach { store.addFolderImportPack(session.id, it) }
            var added = prefs.getInt(KEY_ADDED, 0)
            var duplicates = prefs.getInt(KEY_DUPLICATES, 0)
            var rejected = prefs.getInt(KEY_REJECTED, 0)
            val totalBytes = prefs.getLong(KEY_TOTAL_BYTES, 0L)
            sources.forEachIndexed { index, source ->
                val outcome = when {
                    added > 0 -> "ADDED".also { added-- }
                    duplicates > 0 -> "DUPLICATE".also { duplicates-- }
                    rejected > 0 -> "REJECTED".also { rejected-- }
                    else -> "DUPLICATE"
                }
                store.recordFolderImportItem(session.id, source, outcome,
                    if (index == 0) totalBytes else 0)
            }
        }
        clearLegacyCheckpoint()
    }

    private fun legacyProcessedFile() = File(context.filesDir, LEGACY_PROCESSED_PATH)

    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun clearLegacyCheckpoint() {
        // Synchronous removal prevents a migrated checkpoint from being observed again
        // if the process immediately checks whether an import can be resumed.
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val processed = legacyProcessedFile()
        processed.delete()
        processed.parentFile?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
    }

    companion object {
        private const val PROGRESS_EVERY = 25
        private const val MAX_DEPTH = 6
        private const val MAX_ENTRIES = 40_000
        private const val MAX_IMAGES = 20_000
        private const val MAX_SOURCE_BYTES = 1_000_000L
        private const val MAX_TOTAL_BYTES = 1_500_000_000L
        private val IMAGE_EXTENSIONS = setOf("webp", "png", "jpg", "jpeg")

        private const val LEGACY_PREFS_NAME = "folder_import_checkpoint"
        private const val LEGACY_PROCESSED_PATH = "folder-import/processed-uris.txt"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_FALLBACK = "fallback_name"
        private const val KEY_ADDED = "added"
        private const val KEY_DUPLICATES = "duplicates"
        private const val KEY_REJECTED = "rejected"
        private const val KEY_TOTAL_BYTES = "total_bytes"
        private const val KEY_PACK_IDS = "pack_ids"

        fun pendingImportUri(context: Context): Uri? {
            val store = StickerStore(context.applicationContext)
            try {
                store.pendingFolderImport()?.let { return Uri.parse(it.treeUri) }
            } finally {
                store.close()
            }
            val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TREE_URI, null) ?: return null
            return Uri.parse(legacy)
        }
    }
}
