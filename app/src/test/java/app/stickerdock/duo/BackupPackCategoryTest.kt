package app.stickerdock.duo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupPackCategoryTest {
    @Test fun olderBackupsAndFactoryPacksRestoreAsGeneral() {
        for (schema in 1..3) {
            assertEquals(PackCategory.GENERAL, BackupPackCategory.read(JSONObject(), schema))
        }
    }

    @Test fun currentBackupRetainsCategory() {
        assertEquals(PackCategory.MOVIES,
            BackupPackCategory.read(JSONObject().put("category", "MOVIES"), 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCategoryCannotSilentlyChangeThePack() {
        BackupPackCategory.read(JSONObject().put("category", "INVALID"), 4)
    }
}
