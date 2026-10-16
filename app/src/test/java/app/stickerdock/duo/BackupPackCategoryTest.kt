package app.stickerdock.duo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupPackCategoryTest {
    @Test fun sharedPortableCategoriesMatchEveryAndroidWireValue() {
        PackCategory.entries.forEach { category ->
            assertEquals(category.name, app.stickerdock.core.ArchivePaths.portableCategory(category.name))
        }
    }
    @Test fun olderBackupsAndFactoryPacksRestoreAsGeneral() {
        for (schema in 1..3) {
            assertEquals(PackCategory.GENERAL, BackupPackCategory.read(JSONObject(), schema))
        }
    }

    @Test fun currentBackupsRetainCategory() {
        for (schema in 4..5) {
            assertEquals(PackCategory.MOVIES,
                BackupPackCategory.read(JSONObject().put("category", "MOVIES"), schema))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCategoryCannotSilentlyChangeThePack() {
        BackupPackCategory.read(JSONObject().put("category", "INVALID"), 5)
    }
}
