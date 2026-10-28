package app.stickerdock.duo

import android.content.Context
import org.json.JSONArray
import java.util.Locale

/** Device-local history contains only submitted searches, never typing prefixes. */
internal class StickerSearchHistory(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun snapshot(): List<String> = try {
        val saved = JSONArray(preferences.getString("queries", "[]") ?: "[]")
        (0 until saved.length()).mapNotNull { saved.opt(it) as? String }
            .map(StickerMetadata::cleanLabel).filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.ROOT) }.take(6)
    } catch (_: org.json.JSONException) {
        emptyList()
    } catch (_: ClassCastException) {
        emptyList()
    }

    fun record(query: String): List<String> {
        val cleaned = StickerMetadata.cleanLabel(query)
        if (cleaned.isBlank()) return snapshot()
        val result = (listOf(cleaned) + snapshot())
            .distinctBy { it.lowercase(Locale.ROOT) }.take(6)
        preferences.edit().putString("queries", JSONArray(result).toString()).apply()
        return result
    }

    fun clear() {
        preferences.edit().remove("queries").apply()
    }

    companion object { const val PREFERENCES = "sticker_search_history" }
}
