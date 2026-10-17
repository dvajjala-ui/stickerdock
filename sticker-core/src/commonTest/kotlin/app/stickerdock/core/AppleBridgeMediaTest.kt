@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
package app.stickerdock.core

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppleBridgeMediaTest {
    private val red = "UklGRiwAAABXRUJQVlA4TCAAAAAv/8F/AAcQ/Y/+BwQCyf7eMxTR/4z//Oc///nPf/7zfw=="
    private val blue = "UklGRiwAAABXRUJQVlA4TCAAAAAv/8F/AAcQ0f/+BwQCyf7eMxTR/4z//Oc///nPf/7zfw=="
    private val alpha = "UklGRiwAAABXRUJQVlA4TCAAAAAv/8F/EAcQ/Y+SBQQCyf7eMxTR/4z//Oc///nPf/7zfw=="

    @Test fun opaqueAndTransparentPlatformFramesNormalizeToValidStaticStickers() {
        val bridge = AppleMediaBridge()
        for (frame in listOf(red, blue, alpha)) {
            val normalized = assertNotNull(bridge.normalizeStatic(frame))
            val info = assertNotNull(bridge.inspect(normalized))
            assertEquals(false, info.animated)
            assertEquals(512, info.width)
            assertEquals(512, info.height)
        }
        assertEquals(0, Base64.decode(assertNotNull(bridge.normalizeStatic(red)))[20].toInt() and 0x10)
        assertEquals(0x10, Base64.decode(assertNotNull(bridge.normalizeStatic(alpha)))[20].toInt() and 0x10)
    }

    @Test fun bulkAnimationTransportKeepsFrameCountAndTiming() {
        val bridge = AppleMediaBridge()
        val animation = assertNotNull(bridge.mux(red + "\n" + blue, 125))
        val info = assertNotNull(bridge.inspect(animation))
        assertEquals(true, info.animated)
        assertEquals(2, info.frames)
        assertEquals(250, info.durationMs)
        assertNull(bridge.mux(red + "\n" + red, 125))
        assertNull(bridge.normalizeStatic(animation))
    }

    @Test fun staticNormalizationRejectsWrongCompressedDimensions() {
        assertNull(AppleMediaBridge().normalizeStatic("UklGRhwAAABXRUJQVlA4TA8AAAAvD8ADAAfQ/4j+ByKi/wEA"))
    }
}
