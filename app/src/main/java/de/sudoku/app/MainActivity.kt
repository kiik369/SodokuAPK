package de.sudoku.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SudokuTheme {
                SudokuScreen()
            }
        }
    }
}

@Composable
fun SudokuTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun SudokuScreen(vm: GameViewModel = viewModel()) {
    val state = vm.state
    var pendingDifficulty by remember { mutableStateOf<Difficulty?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sudoku",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            if (state == null) {
                CircularProgressIndicator()
            } else {
                DifficultyRow(current = state.difficulty, onPick = { pendingDifficulty = it })
                Spacer(Modifier.height(12.dp))

                Box(contentAlignment = Alignment.Center) {
                    Board(state = state, onSelect = vm::select)
                    if (vm.loading) CircularProgressIndicator()
                }

                Spacer(Modifier.height(16.dp))
                ActionRow(state = state, vm = vm)
                Spacer(Modifier.height(8.dp))
                NumberPad(state = state, onDigit = vm::inputDigit)
                Spacer(Modifier.height(8.dp))
                ErrorSwitch(checked = state.showErrors, onChange = { vm.toggleErrors() })
            }
        }
    }

    pendingDifficulty?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingDifficulty = null },
            title = { Text("Neues Spiel?") },
            text = { Text("Der aktuelle Fortschritt geht verloren.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.newGame(d)
                    pendingDifficulty = null
                }) { Text("Neues Spiel") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDifficulty = null }) { Text("Abbrechen") }
            }
        )
    }

    if (state != null && state.completed) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Geschafft!") },
            text = { Text("Du hast das Sudoku gelöst.") },
            confirmButton = {
                TextButton(onClick = { vm.newGame(state.difficulty) }) { Text("Neues Spiel") }
            }
        )
    }
}

@Composable
fun DifficultyRow(current: Difficulty, onPick: (Difficulty) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Difficulty.entries.forEach { d ->
            FilterChip(
                selected = d == current,
                onClick = { onPick(d) },
                label = { Text(d.label) }
            )
        }
    }
}

@Composable
fun Board(state: GameState, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val thinLine = colors.outlineVariant
    val thickLine = colors.onSurface
    val selectedValue = if (state.selected >= 0) state.values[state.selected] else 0

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        val cellSize = maxWidth / 9

        Column(Modifier.fillMaxSize()) {
            for (r in 0 until 9) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until 9) {
                        val i = r * 9 + c
                        Cell(
                            index = i,
                            state = state,
                            selectedValue = selectedValue,
                            cellSize = cellSize,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onSelect(i) }
                        )
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / 9f
            for (k in 0..9) {
                val thick = k % 3 == 0
                val stroke = if (thick) 3.dp.toPx() else 1.dp.toPx()
                val color = if (thick) thickLine else thinLine
                drawLine(color, Offset(k * step, 0f), Offset(k * step, size.height), stroke)
                drawLine(color, Offset(0f, k * step), Offset(size.width, k * step), stroke)
            }
        }
    }
}

@Composable
fun Cell(
    index: Int,
    state: GameState,
    selectedValue: Int,
    cellSize: Dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val value = state.values[index]
    val isGiven = state.givens[index] != 0
    val isWrong = state.showErrors && value != 0 && !isGiven && value != state.solution[index]
    val isSelected = index == state.selected
    val isPeer = state.selected >= 0 && SudokuGenerator.isPeer(state.selected, index)
    val sameDigit = selectedValue != 0 && value == selectedValue

    val background = when {
        isSelected -> colors.primary.copy(alpha = 0.35f)
        isWrong -> colors.error.copy(alpha = 0.25f)
        sameDigit -> colors.primary.copy(alpha = 0.20f)
        isPeer -> colors.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier.background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (value != 0) {
            Text(
                text = value.toString(),
                fontSize = (cellSize.value * 0.55f).sp,
                fontWeight = if (isGiven) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isWrong -> colors.error
                    isGiven -> colors.onSurface
                    else -> colors.primary
                }
            )
        } else if (state.notes[index] != 0) {
            val mask = state.notes[index]
            val noteSize = (cellSize.value * 0.24f).sp
            Column(Modifier.fillMaxSize().padding(1.dp)) {
                for (r in 0 until 3) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until 3) {
                            val d = r * 3 + c + 1
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                if ((mask and (1 shl (d - 1))) != 0) {
                                    Text(
                                        text = d.toString(),
                                        fontSize = noteSize,
                                        lineHeight = noteSize,
                                        color = colors.onSurfaceVariant
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
fun ActionRow(state: GameState, vm: GameViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ActionButton("Zurück", Modifier.weight(1f), onClick = vm::undo)
        ActionButton("Löschen", Modifier.weight(1f), onClick = vm::erase)
        ActionButton(
            label = if (state.notesMode) "Notizen an" else "Notizen",
            modifier = Modifier.weight(1f),
            active = state.notesMode,
            onClick = vm::toggleNotes
        )
        ActionButton("Tipp", Modifier.weight(1f), onClick = vm::hint)
    }
}

@Composable
fun ActionButton(
    label: String,
    modifier: Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val colors = if (active) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(label, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun NumberPad(state: GameState, onDigit: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (d in 1..9) {
            val used = state.values.count { it == d } >= 9
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant)
                    .clickable(enabled = !used) { onDigit(d) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = d.toString(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (used) colors.onSurface.copy(alpha = 0.3f) else colors.onSurface
                )
            }
        }
    }
}

@Composable
fun ErrorSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Fehler anzeigen")
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
