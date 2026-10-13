package app.stickerdock.duo

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhatsAppStickerProviderContractTest {
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
}
