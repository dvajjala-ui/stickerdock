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
import org.robolectric.shadows.ShadowContentResolver
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.OpenableColumns

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IncomingMediaTest {
    class MediaProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri) = when (uri.lastPathSegment) {
            "reaction.gif" -> "image/gif"
            "clip.mp4" -> "video/mp4"
            "photo.png" -> "image/png"
            "pack.wastickers" -> "application/octet-stream"
            else -> "application/pdf"
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(uri.lastPathSegment)) }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    }

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun uri(name: String) = Uri.parse("content://motion.test/$name")
    private fun route(vararg names: String): IncomingMedia.Selection? {
        ShadowContentResolver.registerProviderInternal("motion.test", MediaProvider())
        val intent = Intent(if (names.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).setType("image/*")
        if (names.size == 1) intent.putExtra(Intent.EXTRA_STREAM, uri(names.single()))
        else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(names.map(::uri)))
        return IncomingMedia.selection(context, intent)
    }

    @Test fun sharedGifAndVideoOpenMotionEvenWithBroadSenderMime() {
        assertEquals(IncomingMedia.Kind.MOTION, route("reaction.gif")!!.kind)
        assertEquals(IncomingMedia.Kind.MOTION, route("clip.mp4")!!.kind)
        assertEquals(listOf(uri("reaction.gif")), route("reaction.gif")!!.uris)
    }

    @Test fun photoAndGenericMimePackKeepTheirOwnExplicitPreview() {
        assertEquals(IncomingMedia.Kind.PHOTO, route("photo.png")!!.kind)
        assertEquals(IncomingMedia.Kind.PACK, route("pack.wastickers")!!.kind)
        assertEquals(2, route("photo.png", "photo.png")!!.uris.size)
    }

    @Test fun mixedAndMultipleMotionSharesFailBeforeAnyImport() {
        for (names in listOf(arrayOf("photo.png", "reaction.gif"), arrayOf("reaction.gif", "clip.mp4"), arrayOf("photo.png", "pack.wastickers"))) {
            assertTrue(runCatching { route(*names) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun unknownAndRemoteFilesCannotBeOpenedAsPacksOrFetched() {
        assertTrue(runCatching { route("document.pdf") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { IncomingMedia.selection(context, Intent(Intent.ACTION_SEND).setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("https://example.com/clip.mp4"))) }.isFailure)
        assertNull(IncomingMedia.selection(context, Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "SD:some-pack")))
    }

    @Test fun mimeAndExtensionFallbackKeepMotionAndRejectUnrelatedTypes() {
        assertEquals(IncomingMedia.Kind.MOTION, IncomingMedia.classify("application/octet-stream", "REACTION.GIF", "image/*"))
        assertEquals(IncomingMedia.Kind.MOTION, IncomingMedia.classify(null, "", "video/mp4"))
        assertEquals(IncomingMedia.Kind.PHOTO, IncomingMedia.classify("image/webp", "file", "video/mp4"))
        assertNull(IncomingMedia.classify("application/pdf", "file", "image/png"))
    }
}
