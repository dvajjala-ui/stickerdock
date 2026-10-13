package app.stickerdock.duo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.Locale

/**
 * On-device collection migration. Users select the directory with Android's
 * system folder picker; no broad storage permission, root, or hidden app APIs.
 *
 * Useful sources:
 *   Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers
 *   Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers
 *   any personal sticker image export directory.
 */
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
        val rejected: Int
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
        onProgress: ((Progress) -> Unit)? = null
    ): Result {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Cannot access the selected folder")
        require(root.isDirectory && root.canRead()) { "Selected folder is not readable" }

        val checkpoint = Checkpoint(context)
        checkpoint.prepareFor(treeUri, fallbackName)?.let { stale ->
            cleanupEmptyPacks(stale.packIds)
        }
        val session = checkpoint.loadOrCreate(treeUri, fallbackName)
        val processed = checkpoint.loadProcessed()
        val resumed = processed.isNotEmpty() || session.filesScanned > 0

        // Rebuild only packs created by this import session. Existing unrelated
        // same-name packs are never filled implicitly after a process restart.
        val groups = mutableMapOf<Group, MutableList<StickerPack>>()
        session.packIds.mapNotNull(store::getPack).sortedBy { it.createdAt }.forEach { pack ->
            groups.getOrPut(Group(pack.name, pack.kind)) { mutableListOf() } += pack
        }
        val packCounts = store.stickerCounts().toMutableMap()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Folder>().apply { add(Folder(root, 0)) }
        var visitedNodes = 0
        var pendingSinceCheckpoint = 0

        fun publishProgress(name: String? = null) {
            onProgress?.invoke(
                Progress(
                    session.filesScanned,
                    session.added,
                    session.duplicates,
                    session.rejected,
                    name,
                    resumed
                )
            )
        }

        fun persistProcessed(source: String, writer: java.io.BufferedWriter) {
            processed += source
            writer.append(source).append('\n')
            pendingSinceCheckpoint++
            if (pendingSinceCheckpoint >= CHECKPOINT_EVERY) {
                writer.flush()
                checkpoint.save(session)
                pendingSinceCheckpoint = 0
                publishProgress()
            }
        }

        publishProgress()
        val writer = checkpoint.openProcessedWriter()
        try {
            while (queue.isNotEmpty()) {
                val folder = queue.removeFirst()
                if (!visited.add(folder.node.uri.toString())) continue
                require(folder.depth <= MAX_DEPTH) { "Folder tree too deep" }
                for (child in folder.node.listFiles()) {
                    if (++visitedNodes > MAX_ENTRIES) error("Folder contains too many entries")
                    if (child.isDirectory) {
                        if (folder.depth < MAX_DEPTH) queue.add(Folder(child, folder.depth + 1))
                        continue
                    }
                    if (!child.isFile) continue
                    val name = child.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (ext !in IMAGE_EXTENSIONS) continue
                    val source = child.uri.toString()
                    if (source in processed) continue
                    if (++session.filesScanned > MAX_IMAGES) error("Import limit of 20,000 images exceeded")

                    if (child.length() > MAX_SOURCE_BYTES) {
                        session.rejected++
                        persistProcessed(source, writer)
                        continue
                    }
                    val image = try {
                        context.contentResolver.openInputStream(child.uri)?.use {
                            StickerFiles.boundedRead(it, MAX_SOURCE_BYTES.toInt())
                        }
                    } catch (_: Exception) {
                        null
                    }
                    if (image == null) {
                        session.rejected++
                        persistProcessed(source, writer)
                        continue
                    }
                    session.totalBytes += image.size
                    require(session.totalBytes <= MAX_TOTAL_BYTES) {
                        "Selected folder contains over 1.5 GB of image data; import in smaller batches"
                    }

                    val kind = if (WebpSticker.isAnimatedHeader(image)) StickerKind.ANIMATED else StickerKind.STATIC
                    val metadata = if (ext == "webp") StickerPackMetadata.read(image) else null
                    val title = (metadata?.packName ?: folder.node.name ?: session.fallbackName)
                        .trim().take(60).ifBlank { session.fallbackName }
                    val key = Group(title, kind)
                    val belongingPacks = groups.getOrPut(key) { mutableListOf() }
                    val current = belongingPacks.lastOrNull()
                    val candidate = if (current == null || (packCounts[current.id] ?: 0) >= 30) {
                        store.createPack(title, kind).also { created ->
                            belongingPacks += created
                            session.packIds += created.id
                            packCounts[created.id] = 0
                            // Persist pack ownership immediately so a process death
                            // cannot orphan it from the resume session.
                            checkpoint.save(session)
                        }
                    } else {
                        current
                    }

                    when (files.importBytes(candidate.id, image, globalDedup = true)) {
                        ImportResult.Added -> {
                            session.added++
                            packCounts[candidate.id] = (packCounts[candidate.id] ?: 0) + 1
                        }
                        ImportResult.Duplicate -> session.duplicates++
                        is ImportResult.Failed -> session.rejected++
                    }
                    persistProcessed(source, writer)
                    if (pendingSinceCheckpoint == 0) publishProgress(name)
                }
            }

            writer.flush()
            checkpoint.save(session)
            cleanupEmptyPacks(session.packIds, packCounts)
            val surviving = session.packIds.count { id -> (packCounts[id] ?: store.listStickers(id).size) > 0 }
            val result = Result(
                session.filesScanned,
                surviving,
                session.added,
                session.duplicates,
                session.rejected
            )
            checkpoint.clear()
            publishProgress()
            return result
        } catch (error: Exception) {
            // Keep a durable checkpoint for explicit resume. The last small tail
            // may be retried; global SHA-256 dedup makes that retry safe.
            writer.flush()
            checkpoint.save(session)
            publishProgress()
            throw error
        } finally {
            writer.close()
        }
    }

    fun discardPendingImport(): Int {
        val checkpoint = Checkpoint(context)
        val session = checkpoint.load() ?: return 0
        val removed = cleanupEmptyPacks(session.packIds)
        checkpoint.clear()
        return removed
    }

    private fun cleanupEmptyPacks(packIds: Set<String>, knownCounts: Map<String, Int>? = null): Int {
        var removed = 0
        packIds.forEach { id ->
            val pack = store.getPack(id) ?: return@forEach
            val count = knownCounts?.get(id) ?: store.listStickers(id).size
            if (count == 0) {
                files.deletePack(pack.id)
                removed++
            }
        }
        return removed
    }

    private class Checkpoint(private val context: Context) {
        data class Session(
            val treeUri: String,
            val fallbackName: String,
            var filesScanned: Int,
            var added: Int,
            var duplicates: Int,
            var rejected: Int,
            var totalBytes: Long,
            val packIds: MutableSet<String>
        )

        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val processedFile = File(context.filesDir, PROCESSED_PATH)

        /** Returns and clears a stale session when a different folder is chosen. */
        fun prepareFor(treeUri: Uri, fallbackName: String): Session? {
            val current = load() ?: return null
            if (current.treeUri == treeUri.toString() && current.fallbackName == fallbackName) return null
            clear()
            return current
        }

        fun loadOrCreate(treeUri: Uri, fallbackName: String): Session {
            load()?.let { return it }
            val created = Session(
                treeUri.toString(),
                fallbackName,
                filesScanned = 0,
                added = 0,
                duplicates = 0,
                rejected = 0,
                totalBytes = 0L,
                packIds = mutableSetOf()
            )
            save(created)
            return created
        }

        fun load(): Session? {
            val uri = prefs.getString(KEY_TREE_URI, null) ?: return null
            return Session(
                uri,
                prefs.getString(KEY_FALLBACK, "Imported") ?: "Imported",
                prefs.getInt(KEY_SCANNED, 0),
                prefs.getInt(KEY_ADDED, 0),
                prefs.getInt(KEY_DUPLICATES, 0),
                prefs.getInt(KEY_REJECTED, 0),
                prefs.getLong(KEY_TOTAL_BYTES, 0L),
                prefs.getStringSet(KEY_PACK_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            )
        }

        fun save(session: Session) {
            check(
                prefs.edit()
                    .putString(KEY_TREE_URI, session.treeUri)
                    .putString(KEY_FALLBACK, session.fallbackName)
                    .putInt(KEY_SCANNED, session.filesScanned)
                    .putInt(KEY_ADDED, session.added)
                    .putInt(KEY_DUPLICATES, session.duplicates)
                    .putInt(KEY_REJECTED, session.rejected)
                    .putLong(KEY_TOTAL_BYTES, session.totalBytes)
                    .putStringSet(KEY_PACK_IDS, session.packIds.toSet())
                    .commit()
            ) { "Could not save phone import checkpoint" }
        }

        fun loadProcessed(): MutableSet<String> {
            if (!processedFile.isFile) return mutableSetOf()
            return processedFile.useLines { lines ->
                lines.filter { it.isNotBlank() }.toMutableSet()
            }
        }

        fun openProcessedWriter(): java.io.BufferedWriter {
            processedFile.parentFile?.mkdirs()
            return FileOutputStream(processedFile, true).bufferedWriter()
        }

        fun clear() {
            prefs.edit().clear().commit()
            processedFile.delete()
            processedFile.parentFile?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
        }

        companion object {
            fun pendingUri(context: Context): Uri? {
                val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_TREE_URI, null) ?: return null
                return Uri.parse(value)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "folder_import_checkpoint"
        private const val PROCESSED_PATH = "folder-import/processed-uris.txt"
        private const val CHECKPOINT_EVERY = 25
        private const val MAX_DEPTH = 6
        private const val MAX_ENTRIES = 40_000
        private const val MAX_IMAGES = 20_000
        private const val MAX_SOURCE_BYTES = 1_000_000L
        private const val MAX_TOTAL_BYTES = 1_500_000_000L
        private val IMAGE_EXTENSIONS = setOf("webp", "png", "jpg", "jpeg")

        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_FALLBACK = "fallback_name"
        private const val KEY_SCANNED = "files_scanned"
        private const val KEY_ADDED = "added"
        private const val KEY_DUPLICATES = "duplicates"
        private const val KEY_REJECTED = "rejected"
        private const val KEY_TOTAL_BYTES = "total_bytes"
        private const val KEY_PACK_IDS = "pack_ids"

        fun pendingImportUri(context: Context): Uri? = Checkpoint.pendingUri(context)
    }
}
