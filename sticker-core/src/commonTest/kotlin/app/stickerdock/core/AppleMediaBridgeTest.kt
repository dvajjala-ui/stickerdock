package app.stickerdock.core

import kotlin.test.Test
import kotlin.test.assertNull

class AppleMediaBridgeTest {
    @Test fun malformedSwiftInputFailsWithoutThrowingAcrossInterop() {
        val bridge = AppleMediaBridge()
        assertNull(bridge.inspect("%%%"))
        assertNull(bridge.inspect("a".repeat(666_669)))
        assertNull(bridge.mux("a\nb", 0))
        assertNull(bridge.mux("a\n".repeat(41), 100))
        assertNull(bridge.mux("a".repeat(4_000_001), 100))
    }
}
