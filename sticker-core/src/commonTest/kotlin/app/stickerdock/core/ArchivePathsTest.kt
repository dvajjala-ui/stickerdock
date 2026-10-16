package app.stickerdock.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchivePathsTest {
    @Test fun allAndUnknownPrivateCategoriesUseTheExistingGeneralWireKey() {
        assertEquals("GENERAL", ArchivePaths.portableCategory("ALL"))
        assertEquals("GENERAL", AppleMediaBridge().portableArchiveCategory("FUNNY"))
        assertEquals("MOVIES", ArchivePaths.portableCategory("MOVIES"))
    }
    private val id = "12345678-1234-1234-abcd-123456789abc"
    @Test fun foundationAndAndroidUUIDSpellingsShareOneCanonicalContract() {
        assertEquals(id, ArchivePaths.canonicalId(id.uppercase()))
        assertEquals(id, AppleMediaBridge().canonicalArchiveID(id.uppercase()))
        assertTrue(ArchivePaths.isEntry("packs/${id.uppercase()}/$id.webp"))
        assertTrue(ArchivePaths.isEntry("manifest.json"))
    }
    @Test fun identitiesNeverAllowTraversalOrFalseImageExtensions() {
        for (bad in listOf("../$id", "-$id", "test-pack", id + ".webp", id.replaceFirst('-', '/'))) {
            assertNull(ArchivePaths.canonicalId(bad))
            assertFalse(ArchivePaths.isEntry("packs/$bad/$id.webp"))
        }
        assertFalse(ArchivePaths.isEntry("packs/$id/../$id.webp"))
        assertFalse(ArchivePaths.isEntry("packs/$id/$id.webp.exe"))
    }
}
