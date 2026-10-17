package app.stickerdock.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StickerRulesTest {
    @Test fun incompleteAndOversizedPacksCannotBeExported() {
        for (count in listOf(-1, 0, 1, 2, 31, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { StickerRules.requireExportCount(count) }
        }
        StickerRules.requireExportCount(3)
        StickerRules.requireExportCount(30)
    }

    @Test fun clipRecipeHandlesLastSectionWithoutOverflow() {
        ClipRecipe(startMs = 3500, durationMs = 500).validate(4000)
        assertFailsWith<IllegalArgumentException> { ClipRecipe(startMs = 3501, durationMs = 500).validate(4000) }
        assertFailsWith<IllegalArgumentException> { ClipRecipe(startMs = Int.MAX_VALUE, durationMs = 500).validate(Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { ClipRecipe(startMs = -1).validate(4000) }
        assertFailsWith<IllegalArgumentException> { ClipRecipe(durationMs = 4001).validate(5000) }
    }

    @Test fun requestedMotionRatesProduceBoundedFramesAndDurations() {
        for (fps in listOf(6, 8, 10)) {
            val recipe = ClipRecipe(durationMs = 4000, fps = fps)
            recipe.validate(5000)
            assertTrue(recipe.frameCount <= 40)
            assertTrue(recipe.frameDurationMs >= StickerRules.MIN_FRAME_MS)
            assertTrue(recipe.frameCount * recipe.frameDurationMs <= StickerRules.MAX_ANIMATION_MS)
        }
        assertEquals(5, ClipRecipe(durationMs = 501, fps = 8).frameCount)
        assertFailsWith<IllegalArgumentException> { ClipRecipe(fps = 30).validate(4000) }
    }

    @Test fun captionsRetainUnicodeAndRejectControlCharacters() {
        StickerRules.requireCaption("Bas kar bhai 😂")
        StickerRules.requireCaption("")
        StickerRules.requireCaption("x".repeat(80))
        assertFailsWith<IllegalArgumentException> { StickerRules.requireCaption("x".repeat(81)) }
        assertFailsWith<IllegalArgumentException> { StickerRules.requireCaption("hello\nworld") }
    }

    @Test fun malformedAndOversizedRiffChunksFailWithoutExceptions() {
        assertNull(WebpMetadata.inspect(ByteArray(0)))
        val bad = ByteArray(40)
        "RIFF".encodeToByteArray().copyInto(bad)
        bad[4] = 32
        "WEBPVP8X".encodeToByteArray().copyInto(bad, 8)
        repeat(4) { bad[16 + it] = 255.toByte() }
        assertNull(WebpMetadata.inspect(bad))
        assertFailsWith<IllegalArgumentException> { AnimatedWebp.encode(listOf(bad, bad), 100) }
    }
}
