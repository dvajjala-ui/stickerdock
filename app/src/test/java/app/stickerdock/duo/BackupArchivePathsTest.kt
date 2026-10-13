package app.stickerdock.duo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchivePathsTest {
    private val id = "12345678-1234-1234-1234-123456789abc"

    @Test fun acceptsGeneratedAndExistingBackupAssets() {
        assertTrue(BackupArchivePaths.isId(id))
        assertTrue(BackupArchivePaths.isStickerName("$id.webp"))
        assertTrue(BackupArchivePaths.isEntry("packs/$id/$id.webp"))
        assertTrue(BackupArchivePaths.isEntry("manifest.json"))
    }

    @Test fun rejectsTraversalAndFalseExtensions() {
        for (name in listOf("$id/webp", "${id}Xwebp", "$id\\.webp", "../$id.webp", "$id.webp.exe")) {
            assertFalse(BackupArchivePaths.isStickerName(name))
            assertFalse(BackupArchivePaths.isEntry("packs/$id/$name"))
        }
        assertFalse(BackupArchivePaths.isEntry("../manifest.json"))
        assertFalse(BackupArchivePaths.isEntry("packs/$id/../$id.webp"))
    }

    @Test fun rejectsMalformedUuidShapesThatMatchTheOldLoosePattern() {
        val malformed = listOf(
            "-2345678-1234-1234-1234-123456789abc",
            "12345678-1234-1234-1234-123456789ab-",
            "12345678--234-1234-1234-123456789abc"
        )
        malformed.forEach { value ->
            assertFalse(BackupArchivePaths.isId(value))
            assertFalse(BackupArchivePaths.isStickerName("$value.webp"))
            assertFalse(BackupArchivePaths.isEntry("packs/$value/$id.webp"))
        }
    }
}
