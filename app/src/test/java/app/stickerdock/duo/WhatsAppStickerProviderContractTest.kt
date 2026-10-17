package app.stickerdock.duo

import android.net.Uri
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhatsAppStickerProviderContractTest {
    @Test
    fun whitelistQueriesIdentifyBothTheProviderAndRequestedPack() {
        WhatsAppTarget.entries.forEach { target ->
            val uri = WhatsAppIntegration.whitelistUri("pack with spaces", target)
            assertEquals(target.packageName + ".provider.sticker_whitelist_check", uri.authority)
            assertEquals("/is_whitelisted", uri.path)
            assertEquals(WhatsAppStickerProvider.AUTHORITY, uri.getQueryParameter("authority"))
            assertEquals("pack with spaces", uri.getQueryParameter("identifier"))
        }
    }

    @Test
    fun exposesOfficialProviderMimeTypes() {
        val controller = Robolectric.buildContentProvider(WhatsAppStickerProvider::class.java).create()
        val provider = controller.get()
        val authority = WhatsAppStickerProvider.AUTHORITY

        assertEquals(
            "vnd.android.cursor.dir/vnd.$authority.metadata",
            provider.getType(Uri.parse("content://$authority/metadata"))
        )
        assertEquals(
            "vnd.android.cursor.item/vnd.$authority.metadata",
            provider.getType(Uri.parse("content://$authority/metadata/pack-id"))
        )
        assertEquals(
            "vnd.android.cursor.dir/vnd.$authority.stickers",
            provider.getType(Uri.parse("content://$authority/stickers/pack-id"))
        )
        assertEquals(
            "image/webp",
            provider.getType(Uri.parse("content://$authority/stickers_asset/pack-id/sticker.webp"))
        )
        assertEquals(
            "image/png",
            provider.getType(Uri.parse("content://$authority/stickers_asset/pack-id/tray.png"))
        )
    }

    @Test
    fun createsAddPackIntentForRequestedWhatsAppPackage() {
        val pack = StickerPack(
            id = "pack-id",
            name = "Test pack",
            version = 3,
            createdAt = 1234
        )

        val intent = WhatsAppStickerProvider.installIntent(pack, "com.whatsapp.w4b")

        assertEquals(WhatsAppStickerProvider.ACTION_ENABLE, intent.action)
        assertEquals("com.whatsapp.w4b", intent.`package`)
        assertEquals("pack-id", intent.getStringExtra("sticker_pack_id"))
        assertEquals(WhatsAppStickerProvider.AUTHORITY, intent.getStringExtra("sticker_pack_authority"))
        assertEquals("Test pack", intent.getStringExtra("sticker_pack_name"))
    }

    @Test
    fun repeatedQueriesAndAssetOpensRemainUsableAfterScopedDatabaseClose() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        File(context.filesDir, "stickers").deleteRecursively()
        val store = StickerStore(context)
        val pack = store.createPack("Provider pack")
        repeat(3) { index ->
            assertTrue(store.addSticker(StickerItem("id-$index", pack.id, "$index.webp",
                "hash-$index", index.toLong())))
        }
        val asset = StickerFiles(context, store).stickerFile(pack.id, "0.webp")
        asset.parentFile?.mkdirs()
        asset.writeBytes("test-provider-asset".toByteArray())
        store.close()
        val provider = Robolectric.buildContentProvider(WhatsAppStickerProvider::class.java).create().get()
        val authority = WhatsAppStickerProvider.AUTHORITY

        repeat(25) {
            provider.query(Uri.parse("content://$authority/metadata/${pack.id}"),
                null, null, null, null).use { cursor ->
                assertArrayEquals(WhatsAppStickerProvider.PACK_COLUMNS, cursor.columnNames)
                assertEquals(1, cursor.count)
            }
            provider.query(Uri.parse("content://$authority/stickers/${pack.id}"),
                null, null, null, null).use { cursor ->
                assertArrayEquals(WhatsAppStickerProvider.STICKER_COLUMNS, cursor.columnNames)
                assertEquals(3, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("0.webp", cursor.getString(2))
            }
            provider.openAssetFile(
                Uri.parse("content://$authority/stickers_asset/${pack.id}/0.webp"), "r"
            ).use { descriptor -> assertTrue(descriptor.length > 0 || descriptor.declaredLength < 0) }
        }

        context.deleteDatabase("stickerdock.db")
        File(context.filesDir, "stickers").deleteRecursively()
    }
}
