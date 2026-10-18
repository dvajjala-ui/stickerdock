package app.stickerdock.duo

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Accepts image files that other apps choose to share with StickerDock. */
class IncomingStickers(
    private val context: Context,
    private val store: StickerStore,
    private val files: StickerFiles
) {
    @Suppress("DEPRECATION")
    fun import(intent: Intent): String {
        val uris = streams(intent)
        if (uris.isEmpty()) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val saved = StickerlyLinks(context).saveFromText(sharedText)
                return if (saved > 0) "Saved $saved Sticker.ly public pack link(s); open them in Sticker.ly, then import any files you may use." else "No supported sticker pack links found"
            }
            return "No image files or pack links were shared"
        }
        if (uris.size > 100) return "Share at most 100 files at a time."
        var imported = 0
        var rejected = 0
        val images = mutableListOf<Uri>()
        uris.forEach { uri ->
            val filename = files.sourceLabel(uri).lowercase()
            if (intent.type in setOf("application/zip", "application/x-zip-compressed", "application/x-wastickers") || filename.endsWith(".zip") || filename.endsWith(".wastickers")) {
                try {
                    try {
                        val result = StickerBackup(context, store, files).restore(uri)
                        imported += result.stickers
                        rejected += result.rejected
                    } catch (_: Exception) {
                        val report = PackZipImporter(context, store, files).import(uri, "Shared")
                        imported += report.imported
                        rejected += report.skipped + report.duplicates
                    }
                } catch (_: Exception) { rejected++ }
            } else {
                images += uri
            }
        }
        val photos = PhotoPackImporter(context, store, files).import(images, "Shared stickers")
        imported += photos.added
        rejected += photos.duplicates + photos.failed
        return "Shared import: $imported added, $rejected skipped"
    }

    companion object {
        @Suppress("DEPRECATION")
        fun streams(intent: Intent): List<Uri> {
            val uris = when (intent.action) {
                Intent.ACTION_VIEW -> listOfNotNull(intent.data)
                Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
                else -> emptyList()
            }
            require(uris.size <= 100 && uris.all { it.scheme == "content" }) { "Choose up to 100 shared files from your device." }
            return uris
        }
    }
}
