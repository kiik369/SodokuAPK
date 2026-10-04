package de.sudoku.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val completed: Boolean = false
)

class GameViewModel : ViewModel() {

    var state by mutableStateOf<GameState?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    private val history = ArrayDeque<Pair<List<Int>, List<Int>>>()

    init {
        newGame(Difficulty.EASY)
    }

    fun newGame(difficulty: Difficulty) {
        loading = true
        viewModelScope.launch {
            val puzzle = withContext(Dispatchers.Default) { SudokuGenerator.generate(difficulty) }
            history.clear()
            state = GameState(
                difficulty = difficulty,
                givens = puzzle.givens.toList(),
                solution = puzzle.solution.toList(),
                values = puzzle.givens.toList(),
                notes = List(81) { 0 }
            )
            loading = false
        }
    }

    fun select(index: Int) = mutate { it.copy(selected = index) }

    fun toggleNotes() = mutate { it.copy(notesMode = !it.notesMode) }

    fun toggleErrors() = mutate { it.copy(showErrors = !it.showErrors) }

    fun inputDigit(d: Int) {
        val s = state ?: return
        val sel = s.selected
        if (sel < 0 || s.givens[sel] != 0 || s.completed) return

        if (s.notesMode) {
            if (s.values[sel] != 0) return
            push(s)
            val newNotes = s.notes.toMutableList()
            newNotes[sel] = newNotes[sel] xor (1 shl (d - 1))
            state = s.copy(notes = newNotes)
        } else {
            push(s)
            val newValues = s.values.toMutableList()
            val newNotes = s.notes.toMutableList()
            newValues[sel] = d
            newNotes[sel] = 0
            // Die gesetzte Ziffer aus den Notizen aller Nachbarfelder entfernen
            for (i in 0 until 81) {
                if (SudokuGenerator.isPeer(sel, i)) {
                    newNotes[i] = newNotes[i] and (1 shl (d - 1)).inv()
                }
            }
            state = s.copy(
                values = newValues,
                notes = newNotes,
                completed = newValues == s.solution
            )
        }
    }

    fun erase() {
        val s = state ?: return
        val sel = s.selected
        if (sel < 0 || s.givens[sel] != 0 || s.completed) return
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
        state = s.copy(values = last.first, notes = last.second, completed = false)
    }

    /** Füllt das markierte Feld mit der richtigen Ziffer; ohne Markierung das erste leere oder falsche Feld. */
    fun hint() {
        val s = state ?: return
        if (s.completed) return
        val target = when {
            s.selected >= 0 && s.givens[s.selected] == 0 && s.values[s.selected] != s.solution[s.selected] -> s.selected
            else -> (0 until 81).firstOrNull { s.values[it] != s.solution[it] } ?: return
        }
        push(s)
        val d = s.solution[target]
        val newValues = s.values.toMutableList()
        val newNotes = s.notes.toMutableList()
        newValues[target] = d
        newNotes[target] = 0
        for (i in 0 until 81) {
            if (SudokuGenerator.isPeer(target, i)) {
                newNotes[i] = newNotes[i] and (1 shl (d - 1)).inv()
            }
        }
        state = s.copy(
            values = newValues,
            notes = newNotes,
            selected = target,
            completed = newValues == s.solution
        )
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
