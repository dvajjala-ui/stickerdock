package app.stickerdock.duo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri

/**
 * Saves public Sticker.ly share links without copying or redistributing
 * another creator's sticker assets. Accepts pack codes or public /s/ URLs.
 */
class StickerlyLinks(context: Context) : SQLiteOpenHelper(context, "stickerly-links.db", null, 1) {
    data class Link(val code: String, val url: String, val savedAt: Long)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE public_links (code TEXT PRIMARY KEY, saved_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("No migration defined from $oldVersion to $newVersion")
    }

    fun list(): List<Link> {
        val entries = mutableListOf<Link>()
        readableDatabase.rawQuery("SELECT code,saved_at FROM public_links ORDER BY saved_at DESC, code", null).use { c ->
            while (c.moveToNext()) entries += Link(c.getString(0), url(c.getString(0)), c.getLong(1))
        }
        return entries
    }

    fun save(raw: String): Boolean {
        val code = parseCode(raw) ?: return false
        return writableDatabase.insertWithOnConflict("public_links", null, ContentValues().apply {
            put("code", code); put("saved_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun remove(code: String) {
        writableDatabase.delete("public_links", "code=?", arrayOf(code))
    }

    fun saveFromText(text: String): Int {
        require(text.length <= 100_000) { "Too many links in one import" }
        val urls = Regex("""https?://(?:www\.)?sticker\.ly/s/[a-zA-Z0-9]{4,16}/?""", RegexOption.IGNORE_CASE)
            .findAll(text).map { it.value }.toList()
        val candidates = if (urls.isEmpty()) text.lines().map { it.trim() }.filter { it.isNotEmpty() } else urls
        require(candidates.size <= 2000) { "Up to 2,000 links per import" }
        return candidates.count { save(it) }
    }

    companion object {
        private val CODE = Regex("[A-Za-z0-9]{4,16}")
        fun url(code: String): String {
            require(CODE.matches(code))
            return "https://sticker.ly/s/$code"
        }
        fun parseCode(raw: String): String? {
            val value = raw.trim()
            if (CODE.matches(value)) return value.uppercase()
            val uri = try { Uri.parse(value) } catch (_: Exception) { return null }
            if (uri.scheme != "https" || uri.host?.lowercase() !in setOf("sticker.ly", "www.sticker.ly")) return null
            val segments = uri.pathSegments
            if (segments.size != 2 || segments[0] != "s" || !CODE.matches(segments[1])) return null
            return segments[1].uppercase()
        }
    }
}
