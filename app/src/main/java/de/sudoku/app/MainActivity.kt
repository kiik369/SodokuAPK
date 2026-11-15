package de.sudoku.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SudokuScreen(vm) }
    }

    override fun onPause() {
        super.onPause()
        if (vm.settings.bool("pauseHidden")) vm.pauseTimer()
    }

    override fun onResume() {
        super.onResume()
        vm.resumeTimer()
    }
}

fun formatTime(ms: Long, fmt: String): String {
    val total = ms / 1000
    return if (fmt == "min") {
        if (total < 60) "$total s" else "${total / 60} min"
    } else {
        "%02d:%02d".format(total / 60, total % 60)
    }
}

@Composable
fun SudokuScreen(vm: GameViewModel) {
    val s = vm.settings
    val look = Look(s, isSystemInDarkTheme())
    val state = vm.state
    var pending by remember { mutableStateOf<Difficulty?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    val wake = s.bool("wake")
    LaunchedEffect(wake) { view.keepScreenOn = wake }
    LaunchedEffect(Unit) {
        while (true) {
            vm.tick()
            delay(250)
        }
    }
    val holdInSettings = showSettings && s.bool("pauseSettings")
    LaunchedEffect(holdInSettings) {
        vm.holdTimer = holdInSettings
        if (holdInSettings) vm.pauseTimer() else vm.resumeTimer()
    }

    val buzz: () -> Unit = {
        if (s.bool("haptics")) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    MaterialTheme(colorScheme = look.colorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = look.ground) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(Modifier.widthIn(max = look.aw.dp).fillMaxWidth()) {
                    Header(look, s, vm.elapsedMs, onSettings = { showSettings = true })

                    if (state == null) {
                        Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = look.user)
                        }
                    } else {
                        if (s.bool("showSeg")) {
                            Spacer(Modifier.height(16.dp))
                            DifficultySeg(look, state.difficulty) { pending = it }
                        }
                        val above = s.str("toolsPos") == "above"
                        if (above) {
                            Spacer(Modifier.height(12.dp))
                            Tools(look, s, state, vm, buzz)
                        }
                        Spacer(Modifier.height(14.dp))
                        Box(contentAlignment = Alignment.Center) {
                            Board(state, look) { vm.select(it); buzz() }
                            if (vm.loading) CircularProgressIndicator(color = look.user)
                        }
                        Meta(look, s, state) { vm.toggleErrors() }
                        if (!above) {
                            Spacer(Modifier.height(12.dp))
                            Tools(look, s, state, vm, buzz)
                        }
                        Spacer(Modifier.height(10.dp))
                        Pad(look, state) { vm.onKey(it); buzz() }
                    }
                }
            }
        }

        if (showSettings) {
            SettingsSheet(s, look) { showSettings = false }
        }

        pending?.let { d ->
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text("Neues Spiel starten?") },
                text = { Text("Der aktuelle Fortschritt geht verloren.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.newGame(d)
                        pending = null
                    }) { Text("Neues Spiel") }
                },
                dismissButton = { TextButton(onClick = { pending = null }) { Text("Weiterspielen") } }
            )
        }

        if (state != null && state.completed) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Geschafft.") },
                text = { Text("Gelöst in ${formatTime(vm.elapsedMs, "mmss")} mit ${state.mistakes} Fehlern und ${state.hints} Tipps.") },
                confirmButton = { TextButton(onClick = { vm.newGame(state.difficulty) }) { Text("Neues Spiel") } }
            )
        }

        if (state != null && state.failed) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Zu viele Fehler.") },
                text = { Text("Das Fehlerlimit von ${s.int("maxMistakes")} ist erreicht.") },
                confirmButton = { TextButton(onClick = { vm.newGame(state.difficulty) }) { Text("Neues Spiel") } },
                dismissButton = { TextButton(onClick = { vm.undo() }) { Text("Zug zurück") } }
            )
        }
    }
}

@Composable
fun Header(look: Look, s: Settings, elapsedMs: Long, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.bool("showMark")) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (r in 0 until 3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (c in 0 until 3) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(2.5.dp))
                                        .background(look.markHue(r * 3 + c))
                                )
                            }
                        }
                    }
                }
            }
            if (s.bool("showWord")) {
                Text(
                    "sudoku",
                    color = look.ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.bool("showTimer")) {
                val pill = s.str("clockStyle") == "pill"
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .then(if (pill) Modifier.background(look.surface).border(1.dp, look.edge, RoundedCornerShape(50)) else Modifier)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        "⏱ " + formatTime(elapsedMs, s.str("timeFmt")),
                        color = look.ink2,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(look.surface)
                    .border(1.dp, look.edge, CircleShape)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center
            ) {
                Text("⚙", color = look.ink2, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun DifficultySeg(look: Look, current: Difficulty, onPick: (Difficulty) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(look.segBg)
            .padding(4.dp)
    ) {
        Difficulty.entries.forEach { d ->
            val sel = d == current
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) look.surface else Color.Transparent)
                    .clickable { onPick(d) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    d.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sel) look.ink else look.ink2
                )
            }
        }
    }
}

private fun isWrongValue(state: GameState, i: Int, rules: Boolean): Boolean {
    val v = state.values[i]
    if (v == 0 || state.givens[i] != 0) return false
    if (rules) {
        for (j in 0 until 81) {
            if (j != i && SudokuGenerator.isPeer(i, j) && state.values[j] == v) return true
        }
        return false
    }
    return v != state.solution[i]
}

@Composable
fun Board(state: GameState, look: Look, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(look.rb.dp)
    val elevation = when (look.shadow) {
        "soft" -> 8.dp
        "strong" -> 18.dp
        else -> 0.dp
    }
    val rules = look.errRules
    val selectedValue = if (state.selected >= 0) state.values[state.selected] else 0

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation, shape)
            .clip(shape)
            .background(look.groove)
    ) {
        val cellSize = (maxWidth - (look.gap * 4).dp) / 9
        Column(
            Modifier
                .fillMaxSize()
                .padding(look.gap.dp),
            verticalArrangement = Arrangement.spacedBy(look.gap.dp)
        ) {
            for (br in 0 until 3) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(look.gap.dp)
                ) {
                    for (bc in 0 until 3) {
                        val b = br * 3 + bc
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(look.rx.dp))
                                .background(look.line(b)),
                            verticalArrangement = Arrangement.spacedBy(look.ig.dp)
                        ) {
                            for (r in 0 until 3) {
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(look.ig.dp)
                                ) {
                                    for (c in 0 until 3) {
                                        val i = (br * 3 + r) * 9 + (bc * 3 + c)
                                        Cell(
                                            index = i,
                                            box = b,
                                            state = state,
                                            look = look,
                                            selectedValue = selectedValue,
                                            cellSize = cellSize,
                                            rules = rules,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                            onClick = { onSelect(i) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Cell(
    index: Int,
    box: Int,
    state: GameState,
    look: Look,
    selectedValue: Int,
    cellSize: Dp,
    rules: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val value = state.values[index]
    val given = state.givens[index] != 0
    val wrong = state.showErrors && isWrongValue(state, index, rules)
    val selected = index == state.selected
    val peer = state.selected >= 0 && !selected && look.isHighlightPeer(state.selected, index)
    val same = look.hlSame && !selected && selectedValue != 0 && value == selectedValue

    val tile = look.tile(box)
    var bg = tile
    if (peer) bg = look.peer.compositeOver(bg)
    if (same) bg = look.same.compositeOver(bg)
    if (wrong) bg = look.errBg.compositeOver(bg)

    var onSel = false
    var ringSel = false
    if (selected) {
        when (look.selStyle) {
            "accent" -> { bg = look.user; onSel = true }
            "ring" -> { bg = tile; ringSel = true }
            "soft" -> bg = lerp(tile, look.user, 0.3f)
            else -> { bg = look.accent; onSel = true }
        }
    }

    val textColor = when {
        onSel -> look.onAccent
        wrong -> look.err
        given -> look.ink
        else -> look.mine
    }

    Box(
        modifier = modifier
            .background(bg)
            .then(if (same) Modifier.border(2.dp, look.ring) else Modifier)
            .then(if (ringSel) Modifier.border(3.dp, look.user) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (value != 0) {
            Text(
                text = value.toString(),
                fontSize = (cellSize.value * 0.52f * look.numSize).sp,
                fontFamily = look.numFont,
                fontWeight = FontWeight(if (given) look.gw else look.uw),
                color = textColor
            )
        } else if (state.notes[index] != 0) {
            val mask = state.notes[index]
            val noteSize = (cellSize.value * 0.24f * look.nn * look.numSize).sp
            Column(Modifier.fillMaxSize().padding(1.dp)) {
                for (r in 0 until 3) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until 3) {
                            val d = r * 3 + c + 1
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                if ((mask and (1 shl (d - 1))) != 0) {
                                    val hit = look.hlNotes && selectedValue == d
                                    Text(
                                        text = d.toString(),
                                        fontSize = noteSize,
                                        lineHeight = noteSize,
                                        fontWeight = FontWeight(if (hit) 800 else look.nw),
                                        color = when {
                                            onSel -> look.onAccent
                                            hit -> look.user
                                            else -> look.ink2
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Meta(look: Look, s: Settings, state: GameState, onToggleErrors: () -> Unit) {
    val open = state.values.count { it == 0 }
    val max = s.int("maxMistakes")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            if (s.bool("showOpen")) Text("Noch $open offen", color = look.ink2, fontSize = 14.sp)
            if (s.bool("showMist")) {
                val bad = max > 0 && state.mistakes >= max - 1
                Text(
                    "Fehler: ${state.mistakes}" + if (max > 0) " von $max" else "",
                    color = if (bad) look.err else look.ink2,
                    fontSize = 14.sp
                )
            }
        }
        if (s.bool("showQErr")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Fehler zeigen", color = look.ink2, fontSize = 14.sp)
                Switch(
                    checked = state.showErrors,
                    onCheckedChange = { onToggleErrors() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = look.user,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = look.segBg,
                        uncheckedThumbColor = look.ink2,
                        uncheckedBorderColor = look.edge
                    )
                )
            }
        }
    }
}

@Composable
fun Tools(look: Look, s: Settings, state: GameState, vm: GameViewModel, buzz: () -> Unit) {
    val labels = s.bool("toolLabels")
    val hintsLeft = vm.hintsLeft()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.bool("showUndo")) ToolButton(Modifier.weight(1f), look, "↶", "Zurück", labels, false, true) { vm.undo(); buzz() }
        if (s.bool("showErase")) ToolButton(Modifier.weight(1f), look, "⌫", "Löschen", labels, false, true) { vm.erase(); buzz() }
        if (s.bool("showNotesBtn")) ToolButton(Modifier.weight(1f), look, "✎", "Notizen", labels, state.notesMode, true) { vm.toggleNotes(); buzz() }
        if (s.bool("showHintBtn")) {
            val label = if (hintsLeft != null) "Tipp ($hintsLeft)" else "Tipp"
            ToolButton(Modifier.weight(1f), look, "✦", label, labels, false, hintsLeft != 0) { vm.hint(); buzz() }
        }
    }
}

@Composable
fun ToolButton(
    modifier: Modifier,
    look: Look,
    glyph: String,
    label: String,
    showLabel: Boolean,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape((look.rk + 2).dp)
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (active) look.accent else look.surface)
            .border(1.dp, if (active) look.accent else look.edge, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = if (showLabel) 9.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val c = if (active) look.onAccent else look.ink
        Text(glyph, color = c, fontSize = 20.sp)
        if (showLabel) Text(label, color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Pad(look: Look, state: GameState, onKey: (Int) -> Unit) {
    if (look.padLayout == "grid") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 300.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (r in 0 until 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (c in 0 until 3) {
                            val d = r * 3 + c + 1
                            Key(Modifier.weight(1f), look, state, d, onKey)
                        }
                    }
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (d in 1..9) Key(Modifier.weight(1f), look, state, d, onKey)
        }
    }
}

@Composable
fun Key(modifier: Modifier, look: Look, state: GameState, d: Int, onKey: (Int) -> Unit) {
    val count = state.values.count { it == d }
    val used = count >= 9
    val hidden = used && look.doneKeys == "hide"
    val active = state.activeDigit == d
    val shape = RoundedCornerShape(look.rk.dp)
    val textColor = if (active) look.onAccent else look.ink

    Box(
        modifier = modifier
            .height(look.kh.dp)
            .alpha(if (hidden) 0f else 1f)
            .clip(shape)
            .background(if (active) look.accent else look.surface)
            .border(1.dp, if (active) look.accent else look.edge, shape)
            .clickable(enabled = !used) { onKey(d) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = d.toString(),
            fontSize = 26.sp,
            fontFamily = look.numFont,
            fontWeight = FontWeight.SemiBold,
            color = if (used) textColor.copy(alpha = 0.35f) else textColor,
            modifier = Modifier.padding(bottom = if (look.keyBadge == "off") 0.dp else 4.dp)
        )
        when (look.keyBadge) {
            "bar" -> Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 7.dp, end = 7.dp, bottom = 7.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(look.barBg)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((count / 9f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(look.user)
                )
            }
            "count" -> Text(
                text = (9 - count).coerceAtLeast(0).toString(),
                fontSize = 11.sp,
                color = if (active) look.onAccent else look.ink2,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            )
        }
    }
}
