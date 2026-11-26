package de.sudoku.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LEVEL_DOTS = mapOf(Difficulty.EASY to 1, Difficulty.MEDIUM to 2, Difficulty.HARD to 3)

/** Startmenü: erst die Schwierigkeit wählen, oder ein angefangenes Spiel fortsetzen. */
@Composable
fun StartMenu(vm: GameViewModel, look: Look) {
    val s = vm.settings
    val state = vm.state
    Box(
        Modifier
            .fillMaxSize()
            .background(look.ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .testTag("menu"),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (r in 0 until 3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            for (c in 0 until 3) {
                                Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(look.markHue(r * 3 + c)))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("sudoku", color = look.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
            }

            if (state != null && vm.canContinue) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(look.accent)
                        .testTag("menu_continue")
                        .clickable { vm.closeMenu() }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Weiterspielen", color = look.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    val open = state.values.count { it == 0 }
                    Text(
                        "${state.difficulty.label} · ${formatTime(vm.elapsedMs, "mmss")} · noch $open offen",
                        color = look.onAccent.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }

            Text(
                "NEUES SPIEL",
                color = look.ink2,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    val clues = if (d == Difficulty.CUSTOM) s.int("customClues") else d.clues
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(look.surface)
                            .border(1.dp, look.edge, RoundedCornerShape(16.dp))
                            .testTag("menu_${d.name}")
                            .clickable { vm.newGame(d) }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(d.label, color = look.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("$clues Vorgaben", color = look.ink2, fontSize = 13.sp)
                        }
                        val dots = LEVEL_DOTS[d]
                        if (dots == null) {
                            Text("einstellbar", color = look.ink2, fontSize = 13.sp)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (n in 0 until 3) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (n < dots) look.user else look.edge))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
