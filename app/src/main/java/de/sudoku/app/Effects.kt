package de.sudoku.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Animationswerte eines Feldes: Größe, seitlicher Versatz (in dp) und Lichtblitz. */
class CellFx {
    val scale = Animatable(1f)
    val shift = Animatable(0f)
    val flash = Animatable(0f)
}

/** Hält die 81 Feld-Animationen und spielt die vom ViewModel gemeldeten Effekte ab. */
@Composable
fun rememberBoardFx(vm: GameViewModel): List<CellFx> {
    val fx = remember { List(81) { CellFx() } }
    LaunchedEffect(vm) {
        vm.effects.collect { list ->
            for (e in list) {
                launch {
                    delay(e.delayMs)
                    val c = fx[e.cell]
                    when (e) {
                        is BoardFx.Pop -> {
                            c.scale.snapTo(0.7f)
                            c.scale.animateTo(1.12f, tween(130, easing = FastOutSlowInEasing))
                            c.scale.animateTo(1f, tween(90))
                        }
                        is BoardFx.Shake -> {
                            for (x in listOf(-5f, 5f, -4f, 4f, -2f, 0f)) c.shift.animateTo(x, tween(55))
                        }
                        is BoardFx.Flash -> {
                            val up = (e.durationMs * 0.35f).toInt()
                            val down = e.durationMs - up
                            launch {
                                c.flash.animateTo(e.strength, tween(up, easing = LinearOutSlowInEasing))
                                c.flash.animateTo(0f, tween(down))
                            }
                            c.scale.animateTo(1.12f, tween(up, easing = LinearOutSlowInEasing))
                            c.scale.animateTo(1f, tween(down))
                        }
                    }
                }
            }
        }
    }
    return fx
}

/** Wendet die Animation eines Feldes an: skalieren, verschieben und farbig aufblitzen. */
fun Modifier.cellFx(fx: CellFx, flashColor: Color): Modifier = this
    .graphicsLayer {
        scaleX = fx.scale.value
        scaleY = fx.scale.value
        translationX = fx.shift.value * 1.dp.toPx()
    }
    .drawWithContent {
        drawContent()
        val a = fx.flash.value
        if (a > 0f) drawRect(flashColor.copy(alpha = a))
    }
