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
class CatalogImportsTest {
    @Test fun catalogImportsRequireMatchingHashAndSurvivingPacks() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("stickerdock.db")
        context.getSharedPreferences("catalog_imports", Context.MODE_PRIVATE).edit().clear().commit()
        StickerStore(context).use { store ->
            val first = store.createPack("Factory first")
            val second = store.createPack("Factory second")
            val tracker = CatalogImports(context, store)
            tracker.record("daily-example", "original-hash", setOf(first.id, second.id))
            assertEquals(2, tracker.importedPackIds("daily-example", "original-hash").size)
            assertTrue(tracker.importedPackIds("daily-example", "new-hash").isEmpty())
            store.deletePack(first.id)
            assertTrue(tracker.importedPackIds("daily-example", "original-hash").isEmpty())
            assertTrue("Auto delivery must respect a user's deletion", tracker.wasImported("daily-example", "original-hash"))
            assertFalse("New published revisions remain eligible", tracker.wasImported("daily-example", "new-hash"))
        }
    }
}
