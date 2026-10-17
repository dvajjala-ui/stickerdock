package app.stickerdock.duo

import org.json.JSONObject

/** A local receipt alone never proves that a cloud backup still exists. */
internal object CloudSyncPolicy {
    fun canSkip(localHash: String, checkpoint: JSONObject, remote: JSONObject?): Boolean =
        remote != null && checkpoint.optString("localHash") == localHash &&
            checkpoint.optInt("revision", 0) == remote.getInt("revision") &&
            checkpoint.optString("fingerprint").isNotEmpty() &&
            checkpoint.optString("fingerprint") == remote.getString("fingerprint")
}
