package app.stickerdock.duo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

enum class StickerKind { STATIC, ANIMATED }
enum class PackRating { GENERAL, MATURE }
data class StickerPack(val id: String, val name: String, val version: Long, val createdAt: Long, val kind: StickerKind = StickerKind.STATIC, val rating: PackRating = PackRating.GENERAL)
data class StickerItem(val id: String, val packId: String, val fileName: String, val sha256: String, val createdAt: Long)

class StickerStore(context: Context) : SQLiteOpenHelper(context, "stickerdock.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE packs (id TEXT PRIMARY KEY, name TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'STATIC', rating TEXT NOT NULL DEFAULT 'GENERAL')")
        db.execSQL("CREATE TABLE stickers (id TEXT PRIMARY KEY, pack_id TEXT NOT NULL REFERENCES packs(id) ON DELETE CASCADE, file_name TEXT NOT NULL, sha256 TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE(pack_id, sha256), UNIQUE(pack_id, file_name))")
        db.execSQL("CREATE INDEX idx_stickers_pack ON stickers(pack_id)")
    }
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && newVersion >= 2) db.execSQL("ALTER TABLE packs ADD COLUMN kind TEXT NOT NULL DEFAULT 'STATIC'")
        if (oldVersion < 3 && newVersion >= 3) db.execSQL("ALTER TABLE packs ADD COLUMN rating TEXT NOT NULL DEFAULT 'GENERAL'")
    }

    fun listPacks(): List<StickerPack> {
        val result = mutableListOf<StickerPack>()
        readableDatabase.rawQuery("SELECT id,name,version,created_at,kind,rating FROM packs ORDER BY created_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) result += StickerPack(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3), StickerKind.valueOf(cursor.getString(4)), PackRating.valueOf(cursor.getString(5)))
        }
        return result
    }
    fun getPack(id: String) = listPacks().firstOrNull { it.id == id }
    fun createPack(name: String, kind: StickerKind = StickerKind.STATIC, rating: PackRating = PackRating.GENERAL): StickerPack {
        val cleaned = name.trim().take(60)
        require(cleaned.isNotBlank()) { "Pack name required" }
        val pack = StickerPack(UUID.randomUUID().toString(), cleaned, 1, System.currentTimeMillis(), kind, rating)
        writableDatabase.insertOrThrow("packs", null, ContentValues().apply {
            put("id", pack.id); put("name", pack.name); put("version", pack.version); put("created_at", pack.createdAt); put("kind", pack.kind.name); put("rating", pack.rating.name)
        })
        return pack
    }
    fun renamePack(id: String, name: String) {
        val cleaned = name.trim().take(60)
        require(cleaned.isNotBlank())
        require(writableDatabase.update("packs", ContentValues().apply { put("name", cleaned) }, "id=?", arrayOf(id)) == 1)
        bumpVersion(id)
    }
    fun deletePack(id: String) { writableDatabase.delete("packs", "id=?", arrayOf(id)) }
    fun listStickers(packId: String): List<StickerItem> {
        val result = mutableListOf<StickerItem>()
        readableDatabase.rawQuery("SELECT id,pack_id,file_name,sha256,created_at FROM stickers WHERE pack_id=? ORDER BY created_at,id", arrayOf(packId)).use { c ->
            while (c.moveToNext()) result += StickerItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4))
        }
        return result
    }
    fun getSticker(packId: String, filename: String) = listStickers(packId).firstOrNull { it.fileName == filename }
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
