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
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            else -> emptyList()
        }
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
        val packs = mutableMapOf<StickerKind, StickerPack>()
        uris.forEach { uri ->
            if (intent.type == "application/zip" || uri.toString().endsWith(".zip", true)) {
                try {
                    val result = StickerBackup(context, store, files).restore(uri)
                    imported += result.stickers
                    rejected += result.rejected
                } catch (_: Exception) {
                    val report = PackZipImporter(context, store, files).import(uri, "Shared")
                    imported += report.imported
                    rejected += report.skipped + report.duplicates
                }
            } else {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        StickerFiles.boundedRead(it, 1_000_000)
                    } ?: error("Cannot read shared file")
                    val kind = if (WebpSticker.isAnimatedHeader(bytes)) StickerKind.ANIMATED else StickerKind.STATIC
                    var pack = packs[kind]
                    if (pack == null || store.listStickers(pack.id).size >= 30) {
                        pack = store.createPack("Shared ${if (kind == StickerKind.ANIMATED) "Animated" else "Static"}", kind)
                        packs[kind] = pack
                    }
                    when (files.importBytes(pack.id, bytes)) {
                        ImportResult.Added -> imported++
                        else -> rejected++
                    }
                } catch (_: Exception) { rejected++ }
            }
        }
        packs.values.filter { store.listStickers(it.id).isEmpty() }.forEach { files.deletePack(it.id) }
        return "Shared import: $imported added, $rejected skipped"
    }
}
