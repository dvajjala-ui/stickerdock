package app.stickerdock.duo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PackSharingTest {
    @Test fun scopedSharedPackRoundTripPreservesArtworkAndFailedFilesRemainRetryable() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        StickerStore(context).use { store ->
            val files = StickerFiles(context, store)
            val pack = store.createPack("My private reactions", rating = PackRating.MATURE)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            assertEquals(ImportResult.Added, files.importBytes(pack.id, bytes, label = "Hello", tags = "wave"))
            val source = store.listStickers(pack.id).single()
            val backup = StickerBackup(context, store, files)
            val sent = PackSharing(context, backup).share(pack)
            val opened = Intent(Intent.ACTION_VIEW).setDataAndType(sent.clipData!!.getItemAt(0).uri, "application/zip")
            val report = IncomingStickers(context, store, files).importReport(opened)
            assertTrue(report.message, report.failedUris.isEmpty())
            val restored = store.listPacks().single { it.id != pack.id }
            assertEquals(PackRating.MATURE, restored.rating)
            val item = store.listStickers(restored.id).single()
            assertEquals(source.sha256, item.sha256)
            assertEquals("Hello", item.label)
            assertEquals("wave", item.tags)
            val missing = Uri.parse("content://" + context.packageName + ".exports/shared/missing-file.zip")
            val failed = IncomingStickers(context, store, files).importReport(Intent(Intent.ACTION_VIEW).setDataAndType(missing, "application/zip"))
            assertEquals(listOf(missing), failed.failedUris)
            assertTrue(failed.message.contains("File unavailable"))
            assertEquals(2, store.listPacks().size)
        }
    }
    @Test fun exportHasReadableTitleAndScopedReadGrantWithoutOverwritingEarlierShare() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        StickerStore(context).use { store ->
            val pack = store.createPack("Weekend / reactions")
            val sharing = PackSharing(context, StickerBackup(context, store, StickerFiles(context, store)))
            val first = sharing.share(pack)
            val second = sharing.share(pack)
            assertEquals("Weekend / reactions", first.getStringExtra(Intent.EXTRA_TITLE))
            val uri = first.clipData!!.getItemAt(0).uri
            assertEquals("content", uri.scheme)
            assertTrue(uri.lastPathSegment!!.startsWith("Weekend  reactions-"))
            assertNotEquals(uri, second.clipData!!.getItemAt(0).uri)
            assertTrue(first.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(listOf(uri), IncomingStickers.streams(first))
            assertEquals(listOf(uri), IncomingStickers.streams(Intent(Intent.ACTION_VIEW, uri)))
        }
    }

    @Test fun incomingFileStreamsRejectNetworkAndRawFilesystemPaths() {
        for (url in listOf("https://example.com/pack.zip", "file:///data/user/0/private.zip")) {
            assertTrue(runCatching { IncomingStickers.streams(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure)
        }
        val excessive = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,
            ArrayList(List(101) { Uri.parse("content://files/pack-$it") }))
        assertTrue(runCatching { IncomingStickers.streams(excessive) }.isFailure)
    }
}
