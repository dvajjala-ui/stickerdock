package app.stickerdock.duo

import org.junit.Assert.*
import org.junit.Test

class ArtworkHistoryTest {
    @Test fun masksUseCompactReferencesAndLegacyRecipesStillOpen() {
        val old = "[0,[[0,false,1,0,0,\"Old caption\",\"\",[]]]]"
        assertEquals("", ArtworkHistory.decodeStrict(old).edit.maskHash)
        var history = ArtworkHistory.decodeStrict(old)
        repeat(60) { index -> history = history.update(history.edit.copy(maskHash = index.toString(16).padStart(64, '0'))) }
        val reopened = ArtworkHistory.decodeStrict(history.encode())
        assertEquals(history.edit, reopened.edit)
        assertTrue(history.encode().length < 100_000)
        assertTrue(runCatching { ArtworkHistory.decodeStrict(history.update(history.edit.copy(maskHash = "../outside")).encode()) }.isFailure)
    }

    @Test fun sliderDragAndCaptionBurstUndoAsSingleCommands() {
        var history = ArtworkHistory()
        for (i in 1..20) history = history.update(history.edit.copy(zoom = 1f + i / 10f), "zoom")
        history = history.endGroup()
        history = history.update(history.edit.copy(topText = "H"), "top")
            .update(history.edit.copy(topText = "Hello"), "top")
        assertEquals(3, history.entries.size)
        history = history.undo()
        assertEquals("", history.edit.topText)
        assertEquals(3f, history.edit.zoom)
        assertEquals(ArtworkEdit(), history.undo().edit)
        assertEquals("Hello", history.redo().edit.topText)
    }

    @Test fun newCommandAfterUndoDropsRedoAndResetIsUndoable() {
        val rotated = ArtworkHistory().update(ArtworkEdit(quarterTurns = 1))
        val mirrored = rotated.update(rotated.edit.copy(mirror = true))
        val branched = mirrored.undo().update(rotated.edit.copy(bottomText = "New"))
        assertFalse(branched.canRedo)
        assertFalse(branched.edit.mirror)
        val reset = branched.update(ArtworkEdit())
        assertFalse(reset.hasChanges)
        assertEquals(branched.edit, reset.undo().edit)
    }

    @Test fun savedStateRestoresDraftAndBothHistoryDirections() {
        val original = ArtworkHistory().update(ArtworkEdit(topText = "हैलो 👋"))
            .update(ArtworkEdit(topText = "हैलो 👋", mirror = true)).undo()
        val restored = ArtworkHistory.decode(original.encode())
        assertEquals(original.edit, restored.edit)
        assertTrue(restored.canUndo)
        assertTrue(restored.canRedo)
        assertTrue(restored.redo().edit.mirror)
    }

    @Test fun historyIsBoundedAndCorruptSavedStateFallsBackSafely() {
        var history = ArtworkHistory()
        repeat(1000) { history = history.update(history.edit.copy(quarterTurns = (it + 1) % 4)) }
        assertEquals(51, history.entries.size)
        assertTrue(history.encode().length < 100_000)
        assertEquals(ArtworkEdit(), ArtworkHistory.decode("bad state").edit)
        assertEquals(ArtworkEdit(), ArtworkHistory.decode("[999,[]]").edit)
    }
}
