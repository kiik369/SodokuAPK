package de.sudoku.app

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class GameState(
    val difficulty: Difficulty,
    val givens: List<Int>,
    val solution: List<Int>,
    val values: List<Int>,
    /** Pro Feld eine Bitmaske: Bit (d-1) gesetzt = Notiz für Ziffer d. */
    val notes: List<Int>,
    val selected: Int = -1,
    val notesMode: Boolean = false,
    val showErrors: Boolean = true,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val hints: Int = 0,
    val mistakes: Int = 0,
    /** Nur im Modus "Ziffer zuerst": die aktive Ziffer (0 = keine). */
    val activeDigit: Int = 0
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)

    var state by mutableStateOf<GameState?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var elapsedMs by mutableLongStateOf(0L)
        private set

    /** Wird von der Oberfläche gesetzt, solange die Einstellungen offen sind. */
    var holdTimer = false

    private val history = ArrayDeque<Pair<List<Int>, List<Int>>>()

    private var acc = 0L
    private var startedAt = 0L
    private var running = false

    init {
        val start = runCatching { Difficulty.valueOf(settings.str("startDiff")) }.getOrDefault(Difficulty.EASY)
        newGame(start)
    }

    // ---------- Stoppuhr ----------

    fun tick() {
        if (running) elapsedMs = acc + (SystemClock.elapsedRealtime() - startedAt)
    }

    fun pauseTimer() {
        if (running) {
            acc += SystemClock.elapsedRealtime() - startedAt
            running = false
            elapsedMs = acc
        }
    }

    fun resumeTimer() {
        val s = state ?: return
        if (running || holdTimer || loading || s.completed || s.failed) return
        startedAt = SystemClock.elapsedRealtime()
        running = true
    }

    // ---------- Spiel ----------

    fun newGame(difficulty: Difficulty) {
        loading = true
        pauseTimer()
        acc = 0L
        elapsedMs = 0L
        val clues = if (difficulty == Difficulty.CUSTOM) settings.int("customClues") else difficulty.clues
        if (settings.bool("randomHue")) settings.set("hueShift", Random.nextInt(360).toString())
        viewModelScope.launch {
            val puzzle = withContext(Dispatchers.Default) { SudokuGenerator.generate(clues) }
            history.clear()
            state = GameState(
                difficulty = difficulty,
                givens = puzzle.givens.toList(),
                solution = puzzle.solution.toList(),
                values = puzzle.givens.toList(),
                notes = List(81) { 0 },
                showErrors = settings.bool("showErrors")
            )
            loading = false
            resumeTimer()
        }
    }

    fun select(index: Int) {
        mutate { it.copy(selected = index) }
        val s = state ?: return
        if (settings.str("inputMode") == "digit" && s.activeDigit != 0 && s.givens[index] == 0) {
            inputDigit(s.activeDigit)
        }
    }

    fun toggleNotes() = mutate { it.copy(notesMode = !it.notesMode) }

    fun toggleErrors() {
        val s = state ?: return
        val now = !s.showErrors
        settings.set("showErrors", if (now) "1" else "0")
        state = s.copy(showErrors = now)
    }

    /** Ziffernfeld-Taste: je nach Eingabemodus Ziffer eintragen oder aktive Ziffer wählen. */
    fun onKey(d: Int) {
        if (settings.str("inputMode") == "digit") {
            mutate { it.copy(activeDigit = if (it.activeDigit == d) 0 else d) }
        } else {
            inputDigit(d)
        }
    }

    fun inputDigit(d: Int) {
        val s = state ?: return
        if (loading || s.failed || s.completed) return
        val sel = s.selected
        if (sel < 0 || s.givens[sel] != 0) return

        if (s.notesMode) {
            if (s.values[sel] != 0) return
            push(s)
            val newNotes = s.notes.toMutableList()
            newNotes[sel] = newNotes[sel] xor (1 shl (d - 1))
            state = s.copy(notes = newNotes)
            return
        }

        val wasSame = s.values[sel] == d
        push(s)
        val newValues = s.values.toMutableList()
        val newNotes = s.notes.toMutableList()
        newValues[sel] = d
        newNotes[sel] = 0
        if (settings.bool("autoNotes")) clearPeerNotes(newNotes, sel, d)

        val right = d == s.solution[sel]
        val mistakes = if (!right && !wasSame) s.mistakes + 1 else s.mistakes
        val max = settings.int("maxMistakes")
        val failed = max > 0 && mistakes >= max
        val completed = newValues == s.solution

        var selected = sel
        if (!completed && !failed) {
            val adv = settings.str("autoAdvance")
            if (adv == "next" || (adv == "correct" && right)) selected = nextEmpty(newValues, sel)
        }

        state = s.copy(
            values = newValues,
            notes = newNotes,
            selected = selected,
            mistakes = mistakes,
            failed = failed,
            completed = completed
        )
        if (completed || failed) pauseTimer()
    }

    fun erase() {
        val s = state ?: return
        val sel = s.selected
        if (s.failed || s.completed || sel < 0 || s.givens[sel] != 0) return
        if (s.values[sel] == 0 && s.notes[sel] == 0) return
        push(s)
        val newValues = s.values.toMutableList()
        val newNotes = s.notes.toMutableList()
        newValues[sel] = 0
        newNotes[sel] = 0
        state = s.copy(values = newValues, notes = newNotes)
    }

    fun undo() {
        val s = state ?: return
        val last = history.removeLastOrNull() ?: return
        state = s.copy(values = last.first, notes = last.second, completed = false, failed = false)
        resumeTimer()
    }

    fun hintsLeft(): Int? {
        val limit = settings.int("hintLimit")
        val s = state ?: return null
        return if (limit > 0) (limit - s.hints).coerceAtLeast(0) else null
    }

    /** Füllt das markierte Feld mit der richtigen Ziffer (oder zeigt es nur); ohne Markierung das erste falsche Feld. */
    fun hint() {
        val s = state ?: return
        if (s.completed || s.failed) return
        if (hintsLeft() == 0) return
        val target = when {
            s.selected >= 0 && s.givens[s.selected] == 0 && s.values[s.selected] != s.solution[s.selected] -> s.selected
            else -> (0 until 81).firstOrNull { s.values[it] != s.solution[it] } ?: return
        }
        if (settings.str("hintMode") == "show") {
            state = s.copy(selected = target, hints = s.hints + 1)
            return
        }
        push(s)
        val d = s.solution[target]
        val newValues = s.values.toMutableList()
        val newNotes = s.notes.toMutableList()
        newValues[target] = d
        newNotes[target] = 0
        clearPeerNotes(newNotes, target, d)
        val completed = newValues == s.solution
        state = s.copy(
            values = newValues,
            notes = newNotes,
            selected = target,
            hints = s.hints + 1,
            completed = completed
        )
        if (completed) pauseTimer()
    }

    private fun clearPeerNotes(notes: MutableList<Int>, from: Int, d: Int) {
        for (i in 0 until 81) {
            if (SudokuGenerator.isPeer(from, i)) {
                notes[i] = notes[i] and (1 shl (d - 1)).inv()
            }
        }
    }

    private fun nextEmpty(values: List<Int>, from: Int): Int {
        for (k in 1..81) {
            val n = (from + k) % 81
            if (values[n] == 0) return n
        }
        return from
    }

    private fun push(s: GameState) {
        history.addLast(s.values to s.notes)
        if (history.size > 200) history.removeFirst()
    }

    private fun mutate(block: (GameState) -> GameState) {
        val s = state ?: return
        state = block(s)
    }
}
