package de.sudoku.app

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max
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
) {
    val over: Boolean get() = completed || failed
}

/** Ein Animationsschritt für ein Feld; die Oberfläche spielt ihn nach [delayMs] ab. */
sealed interface BoardFx {
    val cell: Int
    val delayMs: Long

    /** Ziffer ploppt beim Setzen auf. */
    data class Pop(override val cell: Int, override val delayMs: Long = 0) : BoardFx

    /** Falsche Ziffer wackelt. */
    data class Shake(override val cell: Int, override val delayMs: Long = 0) : BoardFx

    /** Lichtblitz als Teil einer Welle. */
    data class Flash(
        override val cell: Int,
        override val delayMs: Long,
        val strength: Float,
        val durationMs: Int
    ) : BoardFx
}

class GameViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    private val savePrefs = app.getSharedPreferences(SAVE_PREFS, Context.MODE_PRIVATE)

    var state by mutableStateOf<GameState?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var elapsedMs by mutableLongStateOf(0L)
        private set

    /** Startmenü sichtbar. Die App startet immer im Menü. */
    var menuOpen by mutableStateOf(true)
        private set

    /** Läuft gerade die große Welle nach dem Lösen? Dann wartet der „Geschafft“-Dialog. */
    var celebrating by mutableStateOf(false)
        private set

    /** Wird von der Oberfläche gesetzt, solange die Einstellungen offen sind. */
    var holdTimer = false

    /** Für Tests: Animationen erzwingen (true) oder abschalten (false), sonst Systemeinstellung. */
    var motionOverride: Boolean? = null

    /** Für Tests: die zuletzt ausgelösten Animationen. */
    var lastEffects: List<BoardFx> = emptyList()
        private set

    private val _effects = MutableSharedFlow<List<BoardFx>>(extraBufferCapacity = 16)
    val effects: SharedFlow<List<BoardFx>> = _effects

    private val history = ArrayDeque<Pair<List<Int>, List<Int>>>()

    private var acc = 0L
    private var startedAt = 0L
    private var running = false

    init {
        load()
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
        if (running || holdTimer || loading || menuOpen || s.over) return
        startedAt = SystemClock.elapsedRealtime()
        running = true
    }

    // ---------- Menü ----------

    fun openMenu() {
        menuOpen = true
        pauseTimer()
        save()
    }

    fun closeMenu() {
        if (state == null || loading) return
        menuOpen = false
        resumeTimer()
    }

    val canContinue: Boolean get() = state?.over == false

    // ---------- Spiel ----------

    fun newGame(difficulty: Difficulty) {
        loading = true
        menuOpen = false
        celebrating = false
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
            save()
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
        if (loading || s.over) return
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

        val next = s.copy(
            values = newValues,
            notes = newNotes,
            selected = selected,
            mistakes = mistakes,
            failed = failed,
            completed = completed
        )
        state = next
        if (completed || failed) pauseTimer()
        emitEffects(s, next)
        if (completed || failed) save()
    }

    fun erase() {
        val s = state ?: return
        val sel = s.selected
        if (s.over || sel < 0 || s.givens[sel] != 0) return
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
        celebrating = false
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
        if (s.over) return
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
        val next = s.copy(
            values = newValues,
            notes = newNotes,
            selected = target,
            hints = s.hints + 1,
            completed = completed
        )
        state = next
        if (completed) pauseTimer()
        emitEffects(s, next)
        if (completed) save()
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

    // ---------- Animationen ----------

    private fun motionEnabled(): Boolean {
        motionOverride?.let { return it }
        val scale = android.provider.Settings.Global.getFloat(
            getApplication<Application>().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale > 0f
    }

    private fun isWrong(s: GameState, i: Int): Boolean {
        val v = s.values[i]
        if (v == 0 || s.givens[i] != 0) return false
        if (settings.str("errMode") == "rules") {
            return (0 until 81).any { j -> j != i && SudokuGenerator.isPeer(i, j) && s.values[j] == v }
        }
        return v != s.solution[i]
    }

    /** Vergleicht vorher/nachher und löst Aufploppen, Wackeln, Einheiten-Welle oder Siegeswelle aus. */
    private fun emitEffects(before: GameState, after: GameState) {
        if (!motionEnabled()) {
            lastEffects = emptyList()
            return
        }
        val fx = mutableListOf<BoardFx>()
        val won = after.completed && !before.completed
        val changed = (0 until 81).filter { before.values[it] != after.values[it] && after.values[it] != 0 }

        for (i in changed) {
            if (after.values[i] != after.solution[i]) {
                if (after.showErrors && isWrong(after, i)) fx += BoardFx.Shake(i)
                continue
            }
            fx += BoardFx.Pop(i)
            if (won) continue // die große Welle übernimmt
            val waved = mutableSetOf<Int>()
            for (unit in unitsOf(i)) {
                if (solved(after, unit) && !solved(before, unit)) {
                    for (j in unit) {
                        if (waved.add(j)) fx += BoardFx.Flash(j, (60 + dist(i, j) * 55).toLong(), 0.5f, 520)
                    }
                }
            }
        }

        if (won) {
            val from = changed.lastOrNull() ?: 40
            var maxDelay = 0.0
            for (j in 0 until 81) {
                val d = 120 + dist(from, j) * 70
                maxDelay = max(maxDelay, d)
                fx += BoardFx.Flash(j, d.toLong(), 0.6f, 650)
            }
            // Zweite, sanftere Welle zurück
            for (j in 0 until 81) {
                val d = maxDelay + 250 + (maxDelay - dist(from, j) * 70) * 0.5
                fx += BoardFx.Flash(j, d.toLong(), 0.3f, 600)
            }
            celebrating = true
            viewModelScope.launch {
                kotlinx.coroutines.delay((maxDelay * 1.6 + 900).toLong())
                celebrating = false
            }
        }

        lastEffects = fx
        if (fx.isNotEmpty()) _effects.tryEmit(fx)
    }

    private fun unitsOf(i: Int): List<List<Int>> {
        val r = i / 9
        val c = i % 9
        val br = r / 3 * 3
        val bc = c / 3 * 3
        return listOf(
            List(9) { r * 9 + it },
            List(9) { it * 9 + c },
            List(9) { (br + it / 3) * 9 + bc + it % 3 }
        )
    }

    private fun solved(s: GameState, unit: List<Int>) = unit.all { s.values[it] == s.solution[it] }

    private fun dist(a: Int, b: Int) = hypot((a / 9 - b / 9).toDouble(), (a % 9 - b % 9).toDouble())

    // ---------- Spielstand speichern ----------

    /** Speichert das laufende Spiel, damit „Weiterspielen“ auch nach einem Neustart der App geht. */
    fun save() {
        val s = state ?: return
        tick()
        val json = JSONObject()
            .put("difficulty", s.difficulty.name)
            .put("givens", JSONArray(s.givens))
            .put("solution", JSONArray(s.solution))
            .put("values", JSONArray(s.values))
            .put("notes", JSONArray(s.notes))
            .put("showErrors", s.showErrors)
            .put("completed", s.completed)
            .put("failed", s.failed)
            .put("hints", s.hints)
            .put("mistakes", s.mistakes)
            .put("elapsed", if (running) acc + (SystemClock.elapsedRealtime() - startedAt) else acc)
        savePrefs.edit().putString(SAVE_KEY, json.toString()).apply()
    }

    private fun load() {
        val raw = savePrefs.getString(SAVE_KEY, null) ?: return
        runCatching {
            val o = JSONObject(raw)
            fun ints(key: String): List<Int> {
                val a = o.getJSONArray(key)
                require(a.length() == 81)
                return List(81) { a.getInt(it) }
            }
            state = GameState(
                difficulty = Difficulty.valueOf(o.getString("difficulty")),
                givens = ints("givens"),
                solution = ints("solution"),
                values = ints("values"),
                notes = ints("notes"),
                showErrors = o.optBoolean("showErrors", true),
                completed = o.optBoolean("completed"),
                failed = o.optBoolean("failed"),
                hints = o.optInt("hints"),
                mistakes = o.optInt("mistakes")
            )
            acc = o.optLong("elapsed")
            elapsedMs = acc
        }.onFailure {
            savePrefs.edit().remove(SAVE_KEY).apply()
        }
    }

    companion object {
        const val SAVE_PREFS = "sudoku-game"
        const val SAVE_KEY = "game"
    }
}
