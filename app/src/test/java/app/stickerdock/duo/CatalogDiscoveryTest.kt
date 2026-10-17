package app.stickerdock.duo

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogDiscoveryTest {
    @Test fun refreshNewRevisionAndDeletedPackNeverChangeTheLibrary() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        context.getSharedPreferences("catalog_imports", Context.MODE_PRIVATE).edit().clear().commit()
        StickerStore(context).use { store ->
            val local = store.createPack("My private pack")
            val original = CommunityCatalog.Listing("example-pack", "Public pack", "Creator",
                "https://example.org/pack.zip", "a".repeat(64))
            var remote = listOf(original)
            var reads = 0
            val discovery = CatalogDiscovery(context, store) { reads++; remote }
            repeat(3) {
                assertEquals(remote, discovery.refresh().packs)
                assertTrue(discovery.refresh().savedIds.isEmpty())
            }
            assertEquals(listOf(local), store.listPacks())
            CatalogImports(context, store).record(original.id, original.sha256, setOf(local.id))
            assertEquals(setOf(original.id), discovery.refresh().savedIds)
            remote = listOf(original.copy(sha256 = "b".repeat(64)))
            assertTrue(discovery.refresh().savedIds.isEmpty())
            assertEquals(listOf(local), store.listPacks())
            store.deletePack(local.id)
            remote = listOf(original)
            assertTrue(discovery.refresh().savedIds.isEmpty())
            assertTrue(store.listPacks().isEmpty())
            assertTrue(reads >= 9)
        }
    }
}
