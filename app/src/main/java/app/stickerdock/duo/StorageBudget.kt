package app.stickerdock.duo

import java.io.IOException

/** Leave room for SQLite and the previous saved artwork during atomic saves. */
internal object StorageBudget {
    const val RESERVE_BYTES = 4L * 1024 * 1024

    fun requireAvailable(available: Long, incoming: Long) {
        require(incoming >= 0) { "Invalid save size" }
        if (available < RESERVE_BYTES || incoming > available - RESERVE_BYTES) {
            throw IOException("Not enough device storage. Free some space and retry; your existing stickers are kept.")
        }
    }
}
