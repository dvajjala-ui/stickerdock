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
    data class Report(val message: String, val failedUris: List<Uri> = emptyList())

    fun import(intent: Intent): String = importReport(intent).message

    @Suppress("DEPRECATION")
    fun importReport(intent: Intent): Report {
        val uris = streams(intent)
        if (uris.isEmpty()) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val saved = StickerlyLinks(context).saveFromText(sharedText)
                return Report(if (saved > 0) "Saved $saved Sticker.ly public pack link(s); open them in Sticker.ly, then import any files you may use." else "No supported sticker pack links found")
            }
            return Report("No image files or pack links were shared")
        }
        var imported = 0
        var rejected = 0
        val failed = mutableListOf<Uri>()
        var reason: String? = null
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
                } catch (error: Exception) {
                    rejected++
                    failed += uri
                    if (reason == null) reason = when (error) {
                        is SecurityException -> "Access expired. Open the file again from Files or WhatsApp."
                        is java.io.FileNotFoundException -> "File unavailable. Download it again, then open it."
                        is java.util.zip.ZipException -> "This file isn't a readable sticker pack. Download it again."
                        else -> error.message?.take(160) ?: "Couldn't read this pack. Check the file and try again."
                    }
                }
            } else {
                images += uri
            }
        }
        val photos = PhotoPackImporter(context, store, files).import(images, "Shared stickers")
        imported += photos.added
        rejected += photos.duplicates + photos.failed
        failed += photos.failedUris
        return Report("Shared import: $imported added, $rejected skipped" + (reason?.let { ". $it" } ?: ""), failed)
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
