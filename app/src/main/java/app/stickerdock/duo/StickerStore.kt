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
    GENERAL("General"), REACTIONS("Reactions"), HINGLISH("Hinglish"), MOVIES("Movies"),
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

class StickerStore(context: Context) : SQLiteOpenHelper(context, "stickerdock.db", null, 6) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE packs (id TEXT PRIMARY KEY, name TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'STATIC', rating TEXT NOT NULL DEFAULT 'GENERAL', category TEXT NOT NULL DEFAULT 'GENERAL')")
        db.execSQL("CREATE TABLE stickers (id TEXT PRIMARY KEY, pack_id TEXT NOT NULL REFERENCES packs(id) ON DELETE CASCADE, file_name TEXT NOT NULL, sha256 TEXT NOT NULL, created_at INTEGER NOT NULL, label TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '', UNIQUE(pack_id, sha256), UNIQUE(pack_id, file_name))")
        db.execSQL("CREATE INDEX idx_stickers_pack ON stickers(pack_id)")
        db.execSQL("CREATE INDEX idx_stickers_sha ON stickers(sha256)")
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
    fun getSticker(packId: String, filename: String): StickerItem? {
        readableDatabase.rawQuery("SELECT id,pack_id,file_name,sha256,created_at,label,tags FROM stickers WHERE pack_id=? AND file_name=? LIMIT 1",
            arrayOf(packId, filename)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return readSticker(cursor, 0)
        }
    }
    fun hasStickerHash(hash: String): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM stickers WHERE sha256=? LIMIT 1", arrayOf(hash)).use { cursor ->
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
    fun searchStickers(query: String, limit: Int = 100): List<StickerSearchResult> {
        val needle = StickerMetadata.cleanLabel(query)
        if (needle.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)
        val sql = """
            SELECT s.id,s.pack_id,s.file_name,s.sha256,s.created_at,s.label,s.tags,
                   p.id,p.name,p.version,p.created_at,p.kind,p.rating,p.category
            FROM stickers s
            JOIN packs p ON p.id=s.pack_id
            WHERE instr(lower(s.label), lower(?)) > 0
               OR instr(lower(s.tags), lower(?)) > 0
               OR instr(lower(s.file_name), lower(?)) > 0
               OR instr(lower(p.name), lower(?)) > 0
               OR instr(lower(p.category), lower(?)) > 0
            ORDER BY s.created_at DESC, s.id
            LIMIT $safeLimit
        """.trimIndent()
        val result = mutableListOf<StickerSearchResult>()
        readableDatabase.rawQuery(sql, arrayOf(needle, needle, needle, needle, needle)).use { cursor ->
            while (cursor.moveToNext()) {
                result += StickerSearchResult(readSticker(cursor, 0), readPack(cursor, 7))
            }
        }
        return result
    }
    fun deleteSticker(packId: String, id: String) {
        if (writableDatabase.delete("stickers", "id=? AND pack_id=?", arrayOf(id, packId)) > 0) bumpVersion(packId)
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
    private fun bumpVersion(id: String) { writableDatabase.execSQL("UPDATE packs SET version=version+1 WHERE id=?", arrayOf(id)) }
}
