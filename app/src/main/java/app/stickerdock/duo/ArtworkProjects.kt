package app.stickerdock.duo

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Private local recipes. ZIPs, WhatsApp and cloud sync only see the saved export. */
class ArtworkProjects(private val context: Context, private val store: StickerStore, private val files: StickerFiles) {
    data class Session(val original: ByteArray, val history: ArtworkHistory, val savedHistory: ArtworkHistory)
    data class Snapshot(val record: ArtworkProjectRecord, val original: ByteArray)

    fun open(item: StickerItem): Session = store.transaction {
        requireCurrent(item)
        require(store.getPack(item.packId)?.kind == StickerKind.STATIC) { "Choose a static sticker" }
        var record = store.artworkProject(item.id)
        if (record == null) {
            val original = readImage(files.stickerFile(item.packId, item.fileName), item.sha256)
            writeOriginal(item.id, item.sha256, original)
            record = ArtworkProjectRecord(item.id, item.sha256, item.sha256,
                ArtworkHistory().encode(), null, System.currentTimeMillis())
            store.putArtworkProject(record)
        }
        require(record.savedSha256 == item.sha256) { "This sticker changed outside the editor. Your previous project is kept; restore its saved version before editing." }
        val saved = ArtworkHistory.decodeStrict(record.savedHistory)
        Session(readImage(sourceFile(item.id, record.sourceSha256), record.sourceSha256),
            record.draftHistory?.let(ArtworkHistory::decodeStrict) ?: saved, saved)
    }

    fun saveDraft(item: StickerItem, history: ArtworkHistory) = store.transaction {
        requireCurrent(item)
        val record = requireNotNull(store.artworkProject(item.id)) { "Reopen the editor before saving" }
        require(record.savedSha256 == item.sha256) { "Sticker changed; reopen it before editing" }
        val encoded = validated(history)
        val draft = if (history.edit == ArtworkHistory.decodeStrict(record.savedHistory).edit) null else encoded
        if (draft != record.draftHistory) store.putArtworkProject(record.copy(draftHistory = draft, updatedAt = System.currentTimeMillis()))
    }

    fun discardDraft(item: StickerItem) = store.transaction {
        requireCurrent(item)
        store.artworkProject(item.id)?.let { record ->
            require(record.savedSha256 == item.sha256) { "Sticker changed; reopen it before editing" }
            store.putArtworkProject(record.copy(draftHistory = null, updatedAt = System.currentTimeMillis()))
        }
    }

    fun save(item: StickerItem, history: ArtworkHistory) {
        val session = open(item)
        val encodedHistory = validated(history)
        val edit = ArtworkHistory.decodeStrict(encodedHistory).edit
        // Reset returns exactly the retained bytes, rather than another lossy encode.
        val bytes = if (edit == ArtworkEdit()) session.original else {
            val bitmap = StickerArtwork.render(session.original, edit)
            try { StickerArtwork.encode(bitmap) } finally { bitmap.recycle() }
        }
        files.replaceStaticArtwork(item, bytes) { hash ->
            val record = requireNotNull(store.artworkProject(item.id))
            require(record.savedSha256 == item.sha256) { "Sticker changed; reopen it before editing" }
            store.putArtworkProject(record.copy(savedSha256 = hash, savedHistory = encodedHistory,
                draftHistory = null, updatedAt = System.currentTimeMillis()))
        }
    }

    /** Snapshot allows Undo deletion to retain the same editable starting image/recipe. */
    fun snapshot(item: StickerItem): Snapshot? = store.artworkProject(item.id)?.let { record ->
        require(record.savedSha256 == item.sha256) { "Sticker project changed" }
        Snapshot(record, readImage(sourceFile(item.id, record.sourceSha256), record.sourceSha256))
    }

    internal fun restoreSnapshot(item: StickerItem, snapshot: Snapshot, includeDraft: Boolean = true) {
        requireCurrent(item)
        require(snapshot.record.savedSha256 == item.sha256) { "Project does not match saved artwork" }
        ArtworkHistory.decodeStrict(snapshot.record.savedHistory)
        snapshot.record.draftHistory?.let(ArtworkHistory::decodeStrict)
        writeOriginal(item.id, snapshot.record.sourceSha256, snapshot.original)
        store.putArtworkProject(snapshot.record.copy(stickerId = item.id,
            draftHistory = if (includeDraft) snapshot.record.draftHistory else null, updatedAt = System.currentTimeMillis()))
    }

    private fun requireCurrent(item: StickerItem) {
        val current = store.stickerById(item.id)
        require(current?.packId == item.packId && current.fileName == item.fileName && current.sha256 == item.sha256) {
            "Sticker changed; reopen it before editing"
        }
    }

    private fun validated(history: ArtworkHistory): String = history.encode().also { ArtworkHistory.decodeStrict(it) }

    internal fun sourceFile(stickerId: String, hash: String): File {
        require(BackupArchivePaths.isId(stickerId) && hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid project path" }
        return File(sourceDirectory(context, stickerId), "$hash.webp")
    }

    private fun writeOriginal(stickerId: String, hash: String, bytes: ByteArray) {
        require(digest(bytes) == hash) { "Original artwork checksum mismatch" }
        val file = sourceFile(stickerId, hash)
        if (file.exists()) { readImage(file, hash); return }
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Could not create project folder" }
        try {
            FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
            readImage(file, hash)
        } catch (e: Exception) { file.delete(); throw e }
    }

    private fun readImage(file: File, expectedHash: String): ByteArray {
        require(file.isFile && file.length() in 12..100_000) { "Starting artwork is missing or damaged. Restore this sticker from a backup." }
        val bytes = file.readBytes()
        require(digest(bytes) == expectedHash && WebpSticker.isWebpHeader(bytes) && !WebpSticker.isAnimatedHeader(bytes)) {
            "Starting artwork is damaged. Your saved sticker has not been changed."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth == 512 && bounds.outHeight == 512) { "Invalid starting artwork dimensions" }
        return bytes
    }

    companion object {
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun sourceDirectory(context: Context, stickerId: String): File {
            require(BackupArchivePaths.isId(stickerId)) { "Invalid sticker id" }
            return File(File(context.filesDir, "artwork-originals"), stickerId)
        }
        internal fun deleteLocalSource(context: Context, stickerId: String) {
            if (!BackupArchivePaths.isId(stickerId)) return // Legacy stickers cannot have project paths.
            sourceDirectory(context, stickerId).deleteRecursively()
        }
    }
}
