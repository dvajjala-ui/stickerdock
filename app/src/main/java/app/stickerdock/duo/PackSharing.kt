package app.stickerdock.duo

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Produces a portable StickerDock ZIP from one pack, requiring explicit user sharing. */
class PackSharing(private val context: Context, private val backup: StickerBackup) {
    fun share(pack: StickerPack): Intent {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${pack.id}.zip")
        file.outputStream().use { output -> backup.export(output, pack.id) }
        val uri = FileProvider.getUriForFile(context, "app.stickerdock.duo.exports", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
