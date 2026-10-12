package app.stickerdock.duo

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

class WhatsAppStickerProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "app.stickerdock.duo.stickers"
        const val ACTION_ENABLE = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
        val PACK_COLUMNS = arrayOf("sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
            "sticker_pack_icon", "android_play_store_link", "ios_app_download_link",
            "sticker_pack_publisher_email", "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
            "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack")
        val STICKER_COLUMNS = arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text")
        fun installIntent(pack: StickerPack, packageName: String) = Intent(ACTION_ENABLE).apply {
            setPackage(packageName)
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", AUTHORITY)
            putExtra("sticker_pack_name", pack.name)
        }
    }
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val segments = uri.pathSegments
        val ctx = requireNotNull(context)
        val store = StickerStore(ctx)
        return when {
            segments.size == 1 && segments[0] == "metadata" -> packCursor(uri, store.listPacks().filter { store.listStickers(it.id).size in 3..30 })
            segments.size == 2 && segments[0] == "metadata" ->
                packCursor(uri, listOfNotNull(store.getPack(segments[1])).filter { store.listStickers(it.id).size in 3..30 })
            segments.size == 2 && segments[0] == "stickers" -> {
                val cursor = MatrixCursor(STICKER_COLUMNS)
                if (store.getPack(segments[1]) != null) store.listStickers(segments[1]).forEach {
                    cursor.addRow(arrayOf(it.fileName, "😀", "Custom sticker"))
                }
                cursor.setNotificationUri(ctx.contentResolver, uri)
                cursor
            }
            else -> throw IllegalArgumentException("Unknown provider URI")
        }
    }
    private fun packCursor(uri: Uri, packs: List<StickerPack>): Cursor {
        val cursor = MatrixCursor(PACK_COLUMNS)
        packs.forEach { pack ->
            cursor.addRow(arrayOf(pack.id, pack.name, "StickerDock", "tray.png", "", "", "", "", "", "", pack.version.toString(), 1, 0))
        }
        cursor.setNotificationUri(requireNotNull(context).contentResolver, uri)
        return cursor
    }
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val parts = uri.pathSegments
        if (parts.size != 3 || parts[0] != "stickers_asset") throw FileNotFoundException("Invalid URI")
        val ctx = requireNotNull(context)
        val store = StickerStore(ctx)
        val packId = parts[1]
        val name = parts[2]
        if (store.getPack(packId) == null) throw FileNotFoundException("Unknown pack")
        val files = StickerFiles(ctx, store)
        val file = when {
            name == "tray.png" -> files.trayFile(packId)
            store.getSticker(packId, name) != null -> files.stickerFile(packId, name)
            else -> throw FileNotFoundException("Unknown sticker")
        }
        if (!file.isFile) throw FileNotFoundException("Missing sticker")
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }
    override fun getType(uri: Uri): String = when (uri.pathSegments.firstOrNull()) {
        "metadata" -> "vnd.android.cursor.item/vnd.$AUTHORITY.metadata"
        "stickers" -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
        "stickers_asset" -> if (uri.lastPathSegment == "tray.png") "image/png" else "image/webp"
        else -> throw IllegalArgumentException("Unknown URI")
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
}
