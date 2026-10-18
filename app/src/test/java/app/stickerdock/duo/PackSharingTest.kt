package app.stickerdock.duo

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PackSharingTest {
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
