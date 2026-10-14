package app.stickerdock.duo

import android.content.Context
import android.net.Uri
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The fixture contains original synthetic colors, built by the Python admin API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdminPackContractTest {
    @Test fun pythonAdminArchiveRestoresToTheAndroidLibrary() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        val archive = File(context.cacheDir, "admin-contract-pack.zip")
        javaClass.getResourceAsStream("/admin-contract-pack.zip")!!.use { source ->
            archive.outputStream().use { source.copyTo(it) }
        }
        StickerStore(context).use { store ->
            val files = StickerFiles(context, store)
            val summary = StickerBackup(context, store, files).restore(Uri.fromFile(archive))
            assertEquals(1, summary.packs)
            assertEquals(3, summary.stickers)
            val pack = store.listPacks().single()
            assertEquals("Admin contract fixture", pack.name)
            assertEquals(StickerKind.STATIC, pack.kind)
            assertEquals(PackRating.GENERAL, pack.rating)
            val stickers = store.listStickers(pack.id)
            assertTrue(stickers.any { it.label == "Original red reaction" && it.tags == "test, reaction" })
            assertEquals(3, stickers.map { it.sha256 }.distinct().size)
            stickers.forEach { item ->
                assertEquals(item.sha256, StickerFiles.sha256(files.stickerFile(pack.id, item.fileName).readBytes()))
            }
        }
    }
}
