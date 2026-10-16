package app.stickerdock.duo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogUrlsTest {
    private val base = "https://example.supabase.co/storage/v1/object/public/factory/catalog/"

    @Test fun buildsStrictCatalogUrls() {
        assertEquals(base + "packs.json", CatalogUrls.index(base))
        assertEquals(base + "packs/daily-2026-10-12.zip",
            CatalogUrls.pack(base, "daily-2026-10-12.zip"))
        assertTrue(CatalogUrls.isAllowed(base, base + "packs/a.zip"))
        val thumbnail = "b".repeat(64) + ".webp"
        assertEquals(base + "previews/" + thumbnail, CatalogUrls.preview(base, thumbnail))
        assertTrue(CatalogUrls.isAllowed(base, base + "previews/" + thumbnail))
        assertFalse(CatalogUrls.isAllowed(base, base + "previews/" + thumbnail + "?redirect=private"))
        assertFalse(CatalogUrls.isAllowed(base, base + "previews/../private.webp"))
    }

    @Test fun rejectsUnsafeBasesAndFilenames() {
        for (bad in listOf("http://example.com/catalog/", "https://user@example.com/catalog/",
            "https://example.com/catalog/?x=1", "https://example.com/catalog")) {
            try { CatalogUrls.normalizeBase(bad); throw AssertionError("Accepted $bad") }
            catch (_: IllegalArgumentException) { }
        }
        for (bad in listOf("../a.zip", "a.zip.exe", "a/pack.zip", "aXzip")) {
            try { CatalogUrls.pack(base, bad); throw AssertionError("Accepted $bad") }
            catch (_: IllegalArgumentException) { }
        }
        assertFalse(CatalogUrls.isAllowed(base, "https://evil.example/packs/a.zip"))
    }
}
