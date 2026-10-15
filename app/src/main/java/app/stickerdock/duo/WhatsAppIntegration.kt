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

    /** Fail with an actionable message before handing an incomplete pack to WhatsApp. */
    fun validatePack(pack: StickerPack, store: StickerStore, files: StickerFiles) {
        require(store.getPack(pack.id) != null) { "Pack no longer exists" }
        val stickers = store.listStickers(pack.id)
        require(stickers.size in 3..30) { "WhatsApp needs 3–30 stickers in each pack." }
        stickers.forEach { sticker ->
            val file = files.stickerFile(pack.id, sticker.fileName)
            val limit = if (pack.kind == StickerKind.ANIMATED) 500_000L else 100_000L
            require(file.isFile && file.length() in 12..limit) { "${sticker.displayName} is missing or too large. Replace or remove it." }
            val bytes = file.readBytes()
            require(StickerFiles.sha256(bytes) == sticker.sha256) { "${sticker.displayName} is damaged. Replace or remove it." }
            if (pack.kind == StickerKind.ANIMATED) {
                require(WebpSticker.inspect(bytes)?.let { it.animated && it.frames >= 2 } == true) { "${sticker.displayName} is not a compatible animation" }
            } else {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(WebpSticker.isWebpHeader(bytes) && !WebpSticker.isAnimatedHeader(bytes) && bounds.outWidth == 512 && bounds.outHeight == 512) { "${sticker.displayName} must be a static 512 × 512 sticker" }
            }
        }
        files.refreshTray(pack.id)
        val tray = files.trayFile(pack.id)
        require(tray.isFile && tray.length() in 1..50_000) { "Could not prepare the pack icon. Replace the first sticker and try again." }
    }

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
