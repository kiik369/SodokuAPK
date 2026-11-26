package de.sudoku.app

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bedient die echte Oberfläche: Felder antippen, Ziffern eingeben, Werkzeuge,
 * Dialoge und Einstellungen. Jeder Test startet mit Standard-Einstellungen.
 */
@RunWith(AndroidJUnit4::class)
class GameUiTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: GameViewModel

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("sudoku-settings", Context.MODE_PRIVATE).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        waitForGame()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    // ---------- Hilfen ----------

    private fun waitForGame() {
        rule.waitUntil(30_000) { vm.state != null && !vm.loading }
        rule.waitForIdle()
    }

    private fun state(): GameState = rule.runOnIdle { vm.state!! }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun clickText(text: String) {
        rule.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun firstEmpty(): Int {
        val s = state()
        return (0 until 81).first { s.givens[it] == 0 && s.values[it] == 0 }
    }

    /** Eine falsche Ziffer für Feld [i], deren Taste noch aktiv ist. */
    private fun wrongDigit(i: Int): Int {
        val s = state()
        return (1..9).first { d -> d != s.solution[i] && s.values.count { it == d } < 9 }
    }

    private fun enter(i: Int, d: Int) {
        click("cell_$i")
        click("key_$d")
    }

    // ---------- Tests ----------

    @Test
    fun correctDigitIsShownInCell() {
        val i = firstEmpty()
        val d = state().solution[i]
        val openBefore = state().values.count { it == 0 }

        enter(i, d)

        rule.onNodeWithTag("cell_$i").assertTextEquals("$d")
        rule.onNodeWithText("Noch ${openBefore - 1} offen").assertIsDisplayed()
        assertEquals(d, state().values[i])
        assertEquals(0, state().mistakes)
    }

    @Test
    fun givenCellsCannotBeChanged() {
        val s = state()
        val g = (0 until 81).first { s.givens[it] != 0 }
        val other = (1..9).first { it != s.givens[g] }

        enter(g, other)

        assertEquals(s.givens[g], state().values[g])
        rule.onNodeWithTag("cell_$g").assertTextEquals("${s.givens[g]}")
    }

    @Test
    fun wrongDigitCountsMistakeAndEraseClearsIt() {
        val i = firstEmpty()
        val wrong = wrongDigit(i)

        enter(i, wrong)
        assertEquals(wrong, state().values[i])
        assertEquals(1, state().mistakes)

        click("tool_erase")
        assertEquals(0, state().values[i])
    }

    @Test
    fun undoRestoresPreviousValue() {
        val i = firstEmpty()
        enter(i, state().solution[i])
        assertEquals(state().solution[i], state().values[i])

        click("tool_undo")
        assertEquals(0, state().values[i])
    }

    @Test
    fun notesModeAddsAndRemovesNotes() {
        val i = firstEmpty()
        click("tool_notes")
        assertTrue(state().notesMode)

        click("cell_$i")
        click("key_1")
        click("key_2")

        assertEquals(0, state().values[i])
        assertEquals(0b11, state().notes[i])
        rule.onNodeWithTag("cell_$i").assert(hasText("1")).assert(hasText("2"))

        click("key_1")
        assertEquals(0b10, state().notes[i])
    }

    @Test
    fun hintFillsSelectedCell() {
        val i = firstEmpty()
        click("cell_$i")
        click("tool_hint")

        assertEquals(state().solution[i], state().values[i])
        assertEquals(1, state().hints)
        rule.onNodeWithTag("cell_$i").assertTextEquals("${state().solution[i]}")
    }

    @Test
    fun errorSwitchTogglesErrorDisplay() {
        assertTrue(state().showErrors)
        rule.onNode(isToggleable()).performScrollTo().performClick()
        assertFalse(state().showErrors)
    }

    @Test
    fun solvingWholePuzzleShowsCompletionDialog() {
        val s = state()
        for (i in 0 until 81) {
            if (s.givens[i] == 0) enter(i, s.solution[i])
        }

        assertTrue(state().completed)
        rule.onNodeWithText("Geschafft.").assertIsDisplayed()

        rule.onNodeWithText("Neues Spiel").performClick()
        waitForGame()
        assertFalse(state().completed)
        assertTrue(state().values.any { it == 0 })
    }

    @Test
    fun changingDifficultyAsksAndStartsNewGame() {
        assertEquals(Difficulty.EASY, state().difficulty)

        clickText("Schwer")
        rule.onNodeWithText("Neues Spiel starten?").assertIsDisplayed()
        rule.onNodeWithText("Neues Spiel").performClick()

        rule.waitUntil(30_000) { vm.state?.difficulty == Difficulty.HARD && !vm.loading }
        val s = state()
        assertTrue(s.givens.count { it != 0 } >= Difficulty.HARD.clues)
        assertEquals(s.givens, s.values)
    }

    @Test
    fun cancellingDifficultyDialogKeepsGame() {
        val i = firstEmpty()
        enter(i, state().solution[i])

        clickText("Schwer")
        rule.onNodeWithText("Weiterspielen").performClick()

        assertEquals(Difficulty.EASY, state().difficulty)
        assertEquals(state().solution[i], state().values[i])
    }

    @Test
    fun mistakeLimitEndsGameAndUndoRevives() {
        rule.runOnIdle { vm.settings.set("maxMistakes", "1") }
        val i = firstEmpty()

        enter(i, wrongDigit(i))

        assertTrue(state().failed)
        rule.onNodeWithText("Zu viele Fehler.").assertIsDisplayed()
        rule.onNodeWithText("Zug zurück").performClick()

        assertFalse(state().failed)
        assertEquals(0, state().values[i])
    }

    @Test
    fun digitFirstInputMode() {
        rule.runOnIdle { vm.settings.set("inputMode", "digit") }
        val i = firstEmpty()
        val d = state().solution[i]

        click("key_$d")
        assertEquals(d, state().activeDigit)
        click("cell_$i")

        assertEquals(d, state().values[i])
        rule.onNodeWithTag("cell_$i").assertTextEquals("$d")
    }

    @Test
    fun settingsSheetChangesPadLayout() {
        click("settings")
        rule.onNodeWithText("Anpassen").assertIsDisplayed()

        clickText("Eingabe")
        clickText("3 × 3")
        assertEquals("grid", rule.runOnIdle { vm.settings.str("padLayout") })

        rule.onNodeWithText("Fertig").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Anpassen")).fetchSemanticsNodes().isEmpty() }

        // Das Spiel geht mit dem neuen Ziffernfeld weiter
        val i = firstEmpty()
        enter(i, state().solution[i])
        assertEquals(state().solution[i], state().values[i])
    }

    @Test
    fun presetChangesAppearanceSettings() {
        click("settings")
        clickText("Nacht")
        assertEquals("dark", rule.runOnIdle { vm.settings.str("theme") })
        rule.onNodeWithText("Fertig").performClick()
    }
}
