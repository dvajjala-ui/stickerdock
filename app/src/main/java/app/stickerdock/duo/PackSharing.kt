package app.stickerdock.duo

import android.content.Context
import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Produces a portable StickerDock ZIP from one pack, requiring explicit user sharing. */
class PackSharing(private val context: Context, private val backup: StickerBackup) {
    fun share(pack: StickerPack): Intent {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val title = pack.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(48).ifEmpty { "Sticker pack" }
        val file = File(dir, "$title-${UUID.randomUUID().toString().take(8)}.zip")
        try { file.outputStream().use { output -> backup.export(output, pack.id) } }
        catch (error: Exception) { file.delete(); throw error }
        val uri = FileProvider.getUriForFile(context, "app.stickerdock.duo.exports", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, pack.name)
            putExtra(Intent.EXTRA_SUBJECT, pack.name)
            clipData = ClipData.newRawUri(pack.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
