package app.stickerdock.duo

import org.json.JSONObject

internal object BackupPackCategory {
    fun read(pack: JSONObject, schema: Int): PackCategory {
        require(schema in 1..5) { "Unsupported backup schema" }
        return if (schema >= 4) PackCategory.valueOf(pack.getString("category"))
            else PackCategory.GENERAL
    }
}
