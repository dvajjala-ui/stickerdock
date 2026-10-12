package app.stickerdock.duo

import android.content.ContentValues
import android.content.Context
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
data class StickerItem(val id: String, val packId: String, val fileName: String, val sha256: String, val createdAt: Long)

class StickerStore(context: Context) : SQLiteOpenHelper(context, "stickerdock.db", null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE packs (id TEXT PRIMARY KEY, name TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'STATIC', rating TEXT NOT NULL DEFAULT 'GENERAL', category TEXT NOT NULL DEFAULT 'GENERAL')")
        db.execSQL("CREATE TABLE stickers (id TEXT PRIMARY KEY, pack_id TEXT NOT NULL REFERENCES packs(id) ON DELETE CASCADE, file_name TEXT NOT NULL, sha256 TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE(pack_id, sha256), UNIQUE(pack_id, file_name))")
        db.execSQL("CREATE INDEX idx_stickers_pack ON stickers(pack_id)")
        db.execSQL("CREATE INDEX idx_stickers_sha ON stickers(sha256)")
    }
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && newVersion >= 2) db.execSQL("ALTER TABLE packs ADD COLUMN kind TEXT NOT NULL DEFAULT 'STATIC'")
        if (oldVersion < 3 && newVersion >= 3) db.execSQL("ALTER TABLE packs ADD COLUMN rating TEXT NOT NULL DEFAULT 'GENERAL'")
        if (oldVersion < 4 && newVersion >= 4) db.execSQL("CREATE INDEX IF NOT EXISTS idx_stickers_sha ON stickers(sha256)")
        if (oldVersion < 5 && newVersion >= 5) db.execSQL("ALTER TABLE packs ADD COLUMN category TEXT NOT NULL DEFAULT 'GENERAL'")
    }

    fun listPacks(): List<StickerPack> {
        val result = mutableListOf<StickerPack>()
        readableDatabase.rawQuery("SELECT id,name,version,created_at,kind,rating,category FROM packs ORDER BY created_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) result += StickerPack(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3), StickerKind.valueOf(cursor.getString(4)), PackRating.valueOf(cursor.getString(5)), PackCategory.valueOf(cursor.getString(6)))
        }
        return result
    }
    fun getPack(id: String): StickerPack? {
        readableDatabase.rawQuery("SELECT id,name,version,created_at,kind,rating,category FROM packs WHERE id=? LIMIT 1", arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            return StickerPack(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3),
                StickerKind.valueOf(c.getString(4)), PackRating.valueOf(c.getString(5)),
                PackCategory.valueOf(c.getString(6)))
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
        readableDatabase.rawQuery("SELECT id,pack_id,file_name,sha256,created_at FROM stickers WHERE pack_id=? ORDER BY created_at,id", arrayOf(packId)).use { c ->
            while (c.moveToNext()) result += StickerItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4))
        }
        return result
    }
    fun getSticker(packId: String, filename: String): StickerItem? {
        readableDatabase.rawQuery("SELECT id,pack_id,file_name,sha256,created_at FROM stickers WHERE pack_id=? AND file_name=? LIMIT 1",
            arrayOf(packId, filename)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return StickerItem(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3), cursor.getLong(4))
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
        }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        if (added) bumpVersion(item.packId)
        return added
    }
    fun deleteSticker(packId: String, id: String) {
        if (writableDatabase.delete("stickers", "id=? AND pack_id=?", arrayOf(id, packId)) > 0) bumpVersion(packId)
    }
    private fun bumpVersion(id: String) { writableDatabase.execSQL("UPDATE packs SET version=version+1 WHERE id=?", arrayOf(id)) }
}
