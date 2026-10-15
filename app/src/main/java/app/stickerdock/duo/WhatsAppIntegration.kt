package app.stickerdock.duo

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri

enum class WhatsAppTarget(
    val label: String,
    val packageName: String
) {
    PERSONAL("WhatsApp", "com.whatsapp"),
    BUSINESS("WhatsApp Business", "com.whatsapp.w4b");

    val whitelistAuthority: String get() = "$packageName.provider.sticker_whitelist_check"
}

data class WhatsAppPackStatus(
    val installed: Boolean,
    val added: Boolean
)

/** Read-only integration status exposed by the official WhatsApp sticker API. */
object WhatsAppIntegration {
    private const val RESULT_COLUMN = "result"

    fun status(context: Context, packId: String, target: WhatsAppTarget): WhatsAppPackStatus {
        if (!isInstalled(context.packageManager, target.packageName)) {
            return WhatsAppPackStatus(installed = false, added = false)
        }
        return WhatsAppPackStatus(
            installed = true,
            added = runCatching { isAdded(context, packId, target) }.getOrDefault(false)
        )
    }

    internal fun whitelistUri(packId: String, target: WhatsAppTarget): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(target.whitelistAuthority)
        .appendPath("is_whitelisted")
        .appendQueryParameter("authority", WhatsAppStickerProvider.AUTHORITY)
        .appendQueryParameter("identifier", packId)
        .build()

    private fun isAdded(context: Context, packId: String, target: WhatsAppTarget): Boolean {
        if (context.packageManager.resolveContentProvider(target.whitelistAuthority, 0) == null) {
            return false
        }
        context.contentResolver.query(whitelistUri(packId, target), null, null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return false
            val column = cursor.getColumnIndex(RESULT_COLUMN)
            return column >= 0 && cursor.getInt(column) == 1
        }
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(packageManager: PackageManager, packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
