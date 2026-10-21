package app.stickerdock.duo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** Routes a granted local share to an explicit creator/import preview, never a save. */
object IncomingMedia {
    enum class Kind { PHOTO, MOTION, PACK }
    data class Selection(val kind: Kind, val uris: List<Uri>)

    /** Provider queries belong on IO; decoding/size checks remain in the creator/importer. */
    fun selection(context: Context, intent: Intent): Selection? {
        val uris = IncomingStickers.streams(intent)
        if (uris.isEmpty()) return null
        val kinds = uris.map { uri ->
            val type = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }.orEmpty()
            classify(type, name, intent.type)
                ?: throw IllegalArgumentException("Choose photos, a sticker pack, or one video/GIF. This file type isn't supported.")
        }
        require(kinds.distinct().size == 1) { "Share photos, sticker packs, or one video/GIF separately." }
        val kind = kinds.first()
        require(kind != Kind.MOTION || uris.size == 1) { "Choose one video or GIF at a time to trim and preview." }
        return Selection(kind, uris)
    }

    internal fun classify(providerType: String?, name: String, declaredType: String?): Kind? {
        val type = providerType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val declared = declaredType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        // Specific provider information takes precedence over a sender's broad MIME.
        fun fromType(value: String?): Kind? = when {
            value == "image/gif" || value?.startsWith("video/") == true -> Kind.MOTION
            value in setOf("image/png", "image/jpeg", "image/webp") -> Kind.PHOTO
            value in setOf("application/zip", "application/x-zip-compressed", "application/x-wastickers") -> Kind.PACK
            else -> null
        }
        fromType(type)?.let { return it }
        when (extension) {
            "gif", "mp4", "webm", "mov", "m4v", "3gp", "mkv" -> return Kind.MOTION
            "png", "jpg", "jpeg", "webp" -> return Kind.PHOTO
            "zip", "wastickers" -> return Kind.PACK
        }
        // A generic/missing provider MIME may still have a useful sender type.
        return if (type.isNullOrBlank() || type in setOf("application/octet-stream", "image/*", "video/*", "*/*")) fromType(declared) else null
    }
}
