package app.stickerdock.duo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudSyncPolicyTest {
    private fun receipt() = JSONObject().put("localHash", "local-v1").put("revision", 4).put("fingerprint", "cloud-v4")
    private fun remote() = JSONObject().put("revision", 4).put("fingerprint", "cloud-v4")

    @Test fun verifiedMatchingReceiptCanSkipImagesButMissingCloudCopyCannot() {
        assertTrue(CloudSyncPolicy.canSkip("local-v1", receipt(), remote()))
        assertFalse("A cloud deletion must be repaired even if local artwork did not change",
            CloudSyncPolicy.canSkip("local-v1", receipt(), null))
    }
    @Test fun localEditsAndChangesOnAnotherDeviceRequireServerReconciliation() {
        assertFalse(CloudSyncPolicy.canSkip("edited", receipt(), remote()))
        assertFalse(CloudSyncPolicy.canSkip("local-v1", receipt(), remote().put("revision", 5)))
        assertFalse(CloudSyncPolicy.canSkip("local-v1", receipt(), remote().put("fingerprint", "different")))
    }
    @Test fun oldReceiptsAreRevalidatedWithoutDeletingOrReimportingLocalFiles() {
        assertFalse(CloudSyncPolicy.canSkip("local-v1", receipt().also { it.remove("fingerprint") }, remote()))
        assertFalse(CloudSyncPolicy.canSkip("local-v1", JSONObject(), remote()))
    }
}
