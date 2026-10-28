package app.stickerdock.duo

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StickerSearchHistoryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences(StickerSearchHistory.PREFERENCES, Context.MODE_PRIVATE)

    @Before fun prepare() { preferences.edit().clear().commit() }
    @After fun cleanUp() { preferences.edit().clear().commit() }

    @Test fun submittedQueriesSurviveReopenAndBlankSearchDoesNotEraseThem() {
        val history = StickerSearchHistory(context)
        assertTrue(history.snapshot().isEmpty())
        history.record("  work\n done  ")
        history.record("thanks")
        assertEquals(listOf("thanks", "work done"), StickerSearchHistory(context).snapshot())
        assertEquals(listOf("thanks", "work done"), history.record("   "))
        history.clear()
        assertTrue(StickerSearchHistory(context).snapshot().isEmpty())
    }

    @Test fun historyIsBoundedAndSelectingAnExistingSituationMovesItToTheFront() {
        val history = StickerSearchHistory(context)
        repeat(8) { history.record("situation $it") }
        assertEquals((7 downTo 2).map { "situation $it" }, history.snapshot())
        assertEquals(listOf("SITUATION 4", "situation 7", "situation 6", "situation 5", "situation 3", "situation 2"),
            history.record("SITUATION 4"))
        assertEquals(80, history.record("x".repeat(200)).first().length)
    }

    @Test fun corruptHistoryDoesNotPreventSearchAndTheNextSubmissionRepairsIt() {
        preferences.edit().putString("queries", "broken json").commit()
        val history = StickerSearchHistory(context)
        assertTrue(history.snapshot().isEmpty())
        history.record("not today")
        assertEquals(listOf("not today"), StickerSearchHistory(context).snapshot())
    }
}
