package app.stickerdock.duo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

enum class StickerKind { STATIC, ANIMATED }
enum class PackRating { GENERAL, MATURE }
enum class PackCategory(val label: String) {
    GENERAL("All"), REACTIONS("Reactions"), HINGLISH("Hinglish"), MOVIES("Movies"),
    NEWS("News"), ANIMALS("Animals"), LOVE("Love"), WORK("Work"),
    FESTIVALS("Festivals"), OTHER("Other")
}
data class StickerPack(val id: String, val name: String, val version: Long, val createdAt: Long,
    val kind: StickerKind = StickerKind.STATIC, val rating: PackRating = PackRating.GENERAL,
    val category: PackCategory = PackCategory.GENERAL)
data class StickerItem(
    val id: String,
    val packId: String,
    val fileName: String,
    val sha256: String,
    val createdAt: Long,
    val label: String = "",
    val tags: String = ""
) {
    val displayName: String get() = label.ifBlank { fileName }
}
data class StickerSearchResult(val item: StickerItem, val pack: StickerPack)
data class ArtworkProjectRecord(
    val stickerId: String, val sourceSha256: String, val savedSha256: String,
    val savedHistory: String, val draftHistory: String?, val updatedAt: Long
)
data class FolderImportSession(val id: String, val treeUri: String, val fallbackName: String)
data class FolderImportStats(
    val filesScanned: Int,
    val duplicates: Int,
    val rejected: Int,
    val totalBytes: Long
)

class StickerStore(context: Context) : SQLiteOpenHelper(context, "stickerdock.db", null, 8) {
    private val resolver = context.applicationContext.contentResolver
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE packs (id TEXT PRIMARY KEY, name TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'STATIC', rating TEXT NOT NULL DEFAULT 'GENERAL', category TEXT NOT NULL DEFAULT 'GENERAL')")
        db.execSQL("CREATE TABLE stickers (id TEXT PRIMARY KEY, pack_id TEXT NOT NULL REFERENCES packs(id) ON DELETE CASCADE, file_name TEXT NOT NULL, sha256 TEXT NOT NULL, created_at INTEGER NOT NULL, label TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '', UNIQUE(pack_id, sha256), UNIQUE(pack_id, file_name))")
        db.execSQL("CREATE INDEX idx_stickers_pack ON stickers(pack_id)")
        db.execSQL("CREATE INDEX idx_stickers_sha ON stickers(sha256)")
        createFolderImportTables(db)
        createArtworkProjects(db)
    }
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && newVersion >= 2) db.execSQL("ALTER TABLE packs ADD COLUMN kind TEXT NOT NULL DEFAULT 'STATIC'")
        if (oldVersion < 3 && newVersion >= 3) db.execSQL("ALTER TABLE packs ADD COLUMN rating TEXT NOT NULL DEFAULT 'GENERAL'")
        if (oldVersion < 4 && newVersion >= 4) db.execSQL("CREATE INDEX IF NOT EXISTS idx_stickers_sha ON stickers(sha256)")
        if (oldVersion < 5 && newVersion >= 5) db.execSQL("ALTER TABLE packs ADD COLUMN category TEXT NOT NULL DEFAULT 'GENERAL'")
        if (oldVersion < 6 && newVersion >= 6) {
            db.execSQL("ALTER TABLE stickers ADD COLUMN label TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE stickers ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 7 && newVersion >= 7) createFolderImportTables(db)
        if (oldVersion < 8 && newVersion >= 8) createArtworkProjects(db)
    }

    fun listPacks(): List<StickerPack> {
        val result = mutableListOf<StickerPack>()
        readableDatabase.rawQuery("SELECT id,name,version,created_at,kind,rating,category FROM packs ORDER BY created_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) result += readPack(cursor, 0)
        }
        return result
    }
    fun getPack(id: String): StickerPack? {
        readableDatabase.rawQuery("SELECT id,name,version,created_at,kind,rating,category FROM packs WHERE id=? LIMIT 1", arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            return readPack(c, 0)
        }
    }
    fun createPack(name: String, kind: StickerKind = StickerKind.STATIC,
        rating: PackRating = PackRating.GENERAL,
        category: PackCategory = PackCategory.GENERAL): StickerPack {
        val cleaned = name.trim().take(60)
        require(cleaned.isNotBlank()) { "Pack name required" }
        val pack = StickerPack(UUID.randomUUID().toString(), cleaned, 1,
            System.currentTimeMillis(), kind, rating, category)
        writableDatabase.insertOrThrow("packs", null, ContentValues().apply {
            put("id", pack.id); put("name", pack.name); put("version", pack.version)
            put("created_at", pack.createdAt); put("kind", pack.kind.name)
            put("rating", pack.rating.name); put("category", pack.category.name)
        })
        return pack
    }
    fun renamePack(id: String, name: String) {
        val cleaned = name.trim().take(60)
        require(cleaned.isNotBlank())
        require(writableDatabase.update("packs", ContentValues().apply { put("name", cleaned) }, "id=?", arrayOf(id)) == 1)
        bumpVersion(id)
    }
    fun updateCategory(id: String, category: PackCategory) {
        require(writableDatabase.update("packs", ContentValues().apply {
            put("category", category.name)
        }, "id=?", arrayOf(id)) == 1)
        bumpVersion(id)
    }
    fun deletePack(id: String) { writableDatabase.delete("packs", "id=?", arrayOf(id)) }
    fun stickerCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        readableDatabase.rawQuery("SELECT pack_id, COUNT(*) FROM stickers GROUP BY pack_id", null).use { cursor ->
            while (cursor.moveToNext()) counts[cursor.getString(0)] = cursor.getInt(1)
        }
        return counts
    }
    fun listStickers(packId: String): List<StickerItem> {
        val result = mutableListOf<StickerItem>()
        readableDatabase.rawQuery("SELECT id,pack_id,file_name,sha256,created_at,label,tags FROM stickers WHERE pack_id=? ORDER BY created_at,id", arrayOf(packId)).use { c ->
            while (c.moveToNext()) result += readSticker(c, 0)
        }
        return result
    }
    fun stickerPreviews(perPack: Int = 4): Map<String, List<StickerItem>> {
        val result = mutableMapOf<String, MutableList<StickerItem>>()
        readableDatabase.rawQuery("""
            SELECT id,pack_id,file_name,sha256,created_at,label,tags FROM (
                SELECT *, ROW_NUMBER() OVER (PARTITION BY pack_id ORDER BY created_at,id) AS position
                FROM stickers
            ) WHERE position<=CAST(? AS INTEGER) ORDER BY pack_id,created_at,id
        """.trimIndent(), arrayOf(perPack.coerceIn(1, 4).toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val item = readSticker(cursor, 0)
                result.getOrPut(item.packId) { mutableListOf() }.add(item)
            }
        }
        return result
    }

    fun replaceStickerArtwork(item: StickerItem, filename: String, hash: String) = transaction {
        val current = getSticker(item.packId, item.fileName)
        require(current?.id == item.id && current.sha256 == item.sha256) { "Sticker changed; reopen it before editing" }
        require(!hasStickerHash(hash, item.packId) || hash == item.sha256) { "This artwork is already in the pack" }
        require(writableDatabase.update("stickers", ContentValues().apply {
            put("file_name", filename); put("sha256", hash)
        }, "id=? AND pack_id=?", arrayOf(item.id, item.packId)) == 1)
        bumpVersion(item.packId)
    }
    fun getSticker(packId: String, filename: String): StickerItem? {
        readableDatabase.rawQuery("SELECT id,pack_id,file_name,sha256,created_at,label,tags FROM stickers WHERE pack_id=? AND file_name=? LIMIT 1",
            arrayOf(packId, filename)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return readSticker(cursor, 0)
        }
    }
    fun stickerCount(packId: String): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM stickers WHERE pack_id=?", arrayOf(packId)).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }
    fun stickerById(id: String): StickerItem? = readableDatabase.rawQuery(
        "SELECT id,pack_id,file_name,sha256,created_at,label,tags FROM stickers WHERE id=? LIMIT 1", arrayOf(id)
    ).use { if (it.moveToFirst()) readSticker(it, 0) else null }

    /** Batches bind arguments below SQLite's limit without loading an entire large library. */
    fun stickersByIds(ids: Set<String>): List<StickerSearchResult> {
        val result = mutableListOf<StickerSearchResult>()
        ids.toList().chunked(400).forEach { batch ->
            val placeholders = batch.joinToString(",") { "?" }
            readableDatabase.rawQuery("""
                SELECT s.id,s.pack_id,s.file_name,s.sha256,s.created_at,s.label,s.tags,
                       p.id,p.name,p.version,p.created_at,p.kind,p.rating,p.category
                FROM stickers s JOIN packs p ON p.id=s.pack_id
                WHERE s.id IN ($placeholders)
            """.trimIndent(), batch.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) result += StickerSearchResult(readSticker(cursor, 0), readPack(cursor, 7))
            }
        }
        return result.sortedWith(compareByDescending<StickerSearchResult> { it.item.createdAt }.thenBy { it.item.id })
    }

    fun artworkProject(stickerId: String): ArtworkProjectRecord? = readableDatabase.rawQuery(
        "SELECT sticker_id,source_sha256,saved_sha256,saved_history,draft_history,updated_at FROM artwork_projects WHERE sticker_id=?",
        arrayOf(stickerId)
    ).use { if (it.moveToFirst()) ArtworkProjectRecord(it.getString(0), it.getString(1), it.getString(2),
        it.getString(3), if (it.isNull(4)) null else it.getString(4), it.getLong(5)) else null }

    internal fun putArtworkProject(project: ArtworkProjectRecord) {
        require(project.savedHistory.length <= 100_000 && (project.draftHistory?.length ?: 0) <= 100_000)
        writableDatabase.insertWithOnConflict("artwork_projects", null, ContentValues().apply {
            put("sticker_id", project.stickerId); put("source_sha256", project.sourceSha256)
            put("saved_sha256", project.savedSha256); put("saved_history", project.savedHistory)
            put("draft_history", project.draftHistory); put("updated_at", project.updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) { "Could not save editing project" } }
    }

    fun artworkDrafts(limit: Int = 50): List<StickerItem> {
        val result = mutableListOf<StickerItem>()
        readableDatabase.rawQuery("""
            SELECT s.id,s.pack_id,s.file_name,s.sha256,s.created_at,s.label,s.tags FROM artwork_projects a
            JOIN stickers s ON s.id=a.sticker_id WHERE a.draft_history IS NOT NULL
            ORDER BY a.updated_at DESC,s.id LIMIT ${limit.coerceIn(1, 200)}
        """.trimIndent(), null).use { while (it.moveToNext()) result += readSticker(it, 0) }
        return result
    }
    fun hasStickerHash(hash: String, packId: String? = null): Boolean {
        val sql = if (packId == null) "SELECT 1 FROM stickers WHERE sha256=? LIMIT 1"
            else "SELECT 1 FROM stickers WHERE pack_id=? AND sha256=? LIMIT 1"
        val args = if (packId == null) arrayOf(hash) else arrayOf(packId, hash)
        readableDatabase.rawQuery(sql, args).use { cursor ->
            return cursor.moveToFirst()
        }
    }
    fun addSticker(item: StickerItem): Boolean {
        val added = writableDatabase.insertWithOnConflict("stickers", null, ContentValues().apply {
            put("id", item.id); put("pack_id", item.packId); put("file_name", item.fileName)
            put("sha256", item.sha256); put("created_at", item.createdAt)
            put("label", StickerMetadata.cleanLabel(item.label))
            put("tags", StickerMetadata.cleanTags(item.tags))
        }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        if (added) bumpVersion(item.packId)
        return added
    }
    fun updateStickerMetadata(packId: String, id: String, label: String, tags: String) {
        val updated = writableDatabase.update("stickers", ContentValues().apply {
            put("label", StickerMetadata.cleanLabel(label))
            put("tags", StickerMetadata.cleanTags(tags))
        }, "id=? AND pack_id=?", arrayOf(id, packId))
        require(updated == 1) { "Sticker not found" }
        bumpVersion(packId)
    }
    /** Pack discovery must not inherit the bounded sticker-preview query's limit. */
    fun searchStickerPackIds(query: String): Set<String> {
        val terms = StickerMetadata.searchTerms(query)
        if (terms.isEmpty()) return emptySet()
        val fields = listOf("s.label", "s.tags", "s.file_name", "p.name", "p.category")
        val where = terms.joinToString(" AND ") { fields.joinToString(" OR ", "(", ")") { field -> "instr(lower($field), lower(?)) > 0" } }
        val args = terms.flatMap { term -> List(fields.size) { term } }.toTypedArray()
        val result = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT DISTINCT s.pack_id FROM stickers s JOIN packs p ON p.id=s.pack_id WHERE $where", args).use { cursor ->
            while (cursor.moveToNext()) result += cursor.getString(0)
        }
        return result
    }

    fun searchStickers(query: String, limit: Int = 100, kind: StickerKind? = null,
                       category: PackCategory? = null): List<StickerSearchResult> {
        val terms = StickerMetadata.searchTerms(query)
        if (terms.isEmpty()) return emptyList()
        val phrase = StickerMetadata.cleanLabel(query)
        val safeLimit = limit.coerceIn(1, 200)
        val fields = listOf("s.label", "s.tags", "s.file_name", "p.name", "p.category")
        val predicates = terms.map { fields.joinToString(" OR ", "(", ")") { field -> "instr(lower($field), lower(?)) > 0" } }.toMutableList()
        val filterArguments = mutableListOf<String>()
        kind?.let { predicates += "p.kind=?"; filterArguments += it.name }
        category?.let { predicates += "p.category=?"; filterArguments += it.name }
        val where = predicates.joinToString(" AND ")
        val sql = """
            SELECT s.id,s.pack_id,s.file_name,s.sha256,s.created_at,s.label,s.tags,
                   p.id,p.name,p.version,p.created_at,p.kind,p.rating,p.category
            FROM stickers s
            JOIN packs p ON p.id=s.pack_id
            WHERE $where
            ORDER BY CASE
                WHEN lower(s.label)=lower(?) THEN 0
                WHEN instr(lower(s.label), lower(?)) > 0 THEN 1
                WHEN instr(lower(s.tags), lower(?)) > 0 THEN 2
                WHEN instr(lower(p.name), lower(?)) > 0 THEN 3
                WHEN instr(lower(p.category), lower(?)) > 0 THEN 4
                WHEN instr(lower(s.file_name), lower(?)) > 0 THEN 5
                ELSE 6 END,
                s.created_at DESC, s.id
            LIMIT $safeLimit
        """.trimIndent()
        val result = mutableListOf<StickerSearchResult>()
        val arguments = terms.flatMap { term -> List(fields.size) { term } } + filterArguments + List(6) { phrase }
        readableDatabase.rawQuery(sql, arguments.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) {
                result += StickerSearchResult(readSticker(cursor, 0), readPack(cursor, 7))
            }
        }
        return result
    }
    fun deleteSticker(packId: String, id: String) {
        if (writableDatabase.delete("stickers", "id=? AND pack_id=?", arrayOf(id, packId)) > 0) bumpVersion(packId)
    }

    fun pendingFolderImport(): FolderImportSession? {
        readableDatabase.rawQuery(
            "SELECT id,tree_uri,fallback_name FROM folder_import_sessions ORDER BY created_at DESC LIMIT 1",
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return FolderImportSession(cursor.getString(0), cursor.getString(1), cursor.getString(2))
        }
    }

    fun createFolderImport(treeUri: String, fallbackName: String): FolderImportSession {
        val session = FolderImportSession(UUID.randomUUID().toString(), treeUri, fallbackName)
        writableDatabase.insertOrThrow("folder_import_sessions", null, ContentValues().apply {
            put("id", session.id)
            put("tree_uri", session.treeUri)
            put("fallback_name", session.fallbackName)
            put("created_at", System.currentTimeMillis())
        })
        return session
    }

    fun deleteFolderImport(sessionId: String) {
        writableDatabase.delete("folder_import_sessions", "id=?", arrayOf(sessionId))
    }

    fun addFolderImportPack(sessionId: String, packId: String) {
        writableDatabase.insertOrThrow("folder_import_packs", null, ContentValues().apply {
            put("session_id", sessionId)
            put("pack_id", packId)
        })
    }

    fun folderImportPackIds(sessionId: String): Set<String> {
        val result = linkedSetOf<String>()
        readableDatabase.rawQuery(
            "SELECT pack_id FROM folder_import_packs WHERE session_id=? ORDER BY rowid",
            arrayOf(sessionId)
        ).use { cursor -> while (cursor.moveToNext()) result += cursor.getString(0) }
        return result
    }

    fun folderImportProcessedSources(sessionId: String): MutableSet<String> {
        val result = mutableSetOf<String>()
        readableDatabase.rawQuery(
            "SELECT source_uri FROM folder_import_items WHERE session_id=?",
            arrayOf(sessionId)
        ).use { cursor -> while (cursor.moveToNext()) result += cursor.getString(0) }
        return result
    }

    fun folderImportStats(sessionId: String): FolderImportStats {
        readableDatabase.rawQuery(
            """SELECT COUNT(*),
                COALESCE(SUM(CASE WHEN outcome='DUPLICATE' THEN 1 ELSE 0 END),0),
                COALESCE(SUM(CASE WHEN outcome='REJECTED' THEN 1 ELSE 0 END),0),
                COALESCE(SUM(source_bytes),0)
                FROM folder_import_items WHERE session_id=?""".trimIndent(),
            arrayOf(sessionId)
        ).use { cursor ->
            cursor.moveToFirst()
            return FolderImportStats(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getLong(3))
        }
    }

    fun recordFolderImportItem(
        sessionId: String,
        sourceUri: String,
        outcome: String,
        sourceBytes: Long
    ) {
        require(outcome in setOf("ADDED", "DUPLICATE", "REJECTED"))
        writableDatabase.insertOrThrow("folder_import_items", null, ContentValues().apply {
            put("session_id", sessionId)
            put("source_uri", sourceUri)
            put("outcome", outcome)
            put("source_bytes", sourceBytes)
        })
    }

    fun <T> transaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }
    private fun readSticker(cursor: Cursor, offset: Int) = StickerItem(
        cursor.getString(offset), cursor.getString(offset + 1), cursor.getString(offset + 2),
        cursor.getString(offset + 3), cursor.getLong(offset + 4), cursor.getString(offset + 5),
        cursor.getString(offset + 6)
    )
    private fun readPack(cursor: Cursor, offset: Int) = StickerPack(
        cursor.getString(offset), cursor.getString(offset + 1), cursor.getLong(offset + 2),
        cursor.getLong(offset + 3), StickerKind.valueOf(cursor.getString(offset + 4)),
        PackRating.valueOf(cursor.getString(offset + 5)), PackCategory.valueOf(cursor.getString(offset + 6))
    )
    private fun bumpVersion(id: String) {
        writableDatabase.execSQL("UPDATE packs SET version=version+1 WHERE id=?", arrayOf(id))
        val base = "content://${WhatsAppStickerProvider.AUTHORITY}"
        resolver.notifyChange(android.net.Uri.parse("$base/metadata"), null)
        resolver.notifyChange(android.net.Uri.parse("$base/metadata/$id"), null)
        resolver.notifyChange(android.net.Uri.parse("$base/stickers/$id"), null)
    }

    companion object {
        private fun createArtworkProjects(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS artwork_projects (sticker_id TEXT PRIMARY KEY REFERENCES stickers(id) ON DELETE CASCADE, source_sha256 TEXT NOT NULL, saved_sha256 TEXT NOT NULL, saved_history TEXT NOT NULL, draft_history TEXT, updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_artwork_drafts ON artwork_projects(updated_at DESC) WHERE draft_history IS NOT NULL")
        }
        private fun createFolderImportTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS folder_import_sessions (id TEXT PRIMARY KEY, tree_uri TEXT NOT NULL, fallback_name TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS folder_import_packs (session_id TEXT NOT NULL REFERENCES folder_import_sessions(id) ON DELETE CASCADE, pack_id TEXT NOT NULL REFERENCES packs(id) ON DELETE CASCADE, PRIMARY KEY(session_id, pack_id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS folder_import_items (session_id TEXT NOT NULL REFERENCES folder_import_sessions(id) ON DELETE CASCADE, source_uri TEXT NOT NULL, outcome TEXT NOT NULL CHECK(outcome IN ('ADDED','DUPLICATE','REJECTED')), source_bytes INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(session_id, source_uri))")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_folder_import_items_session ON folder_import_items(session_id)")
        }
    }
}
