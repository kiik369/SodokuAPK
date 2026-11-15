package de.sudoku.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(s: Settings, look: Look, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by remember { mutableStateOf(-1) }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = look.ground,
        contentColor = look.ink
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page >= 0) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(look.surface)
                            .border(1.dp, look.edge, CircleShape)
                            .clickable { page = -1 },
                        contentAlignment = Alignment.Center
                    ) { Text("‹", color = look.ink, fontSize = 26.sp) }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (page >= 0) CATEGORIES[page].title else "Anpassen",
                        color = look.ink,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        if (page >= 0) "${CATEGORIES[page].defs.size} Einstellungen" else "${ALL_DEFS.size} Einstellungen in ${CATEGORIES.size} Gruppen",
                        color = look.ink2,
                        fontSize = 13.sp
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Fertig", color = look.user, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 40.dp)
            ) {
                if (page < 0) {
                    Overview(s, look) { page = it }
                } else {
                    CategoryPage(s, look, CATEGORIES[page])
                }
            }
        }
    }
}

@Composable
private fun CardBox(look: Look, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(look.surface)
            .border(1.dp, look.edge, shape)
    ) { content() }
}

@Composable
private fun Divider(look: Look) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(look.edge))
}

@Composable
private fun Caption(look: Look, text: String) {
    Text(
        text,
        color = look.ink2,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun Overview(s: Settings, look: Look, onOpen: (Int) -> Unit) {
    Caption(look, "Fertige Designs")
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PRESETS.forEach { p -> PresetCard(look, p) { s.applyPreset(p) } }
    }

    Caption(look, "Alle Einstellungen")
    CardBox(look) {
        CATEGORIES.forEachIndexed { idx, c ->
            if (idx > 0) Divider(look)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(idx) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(oklch(0.7, 0.14, (idx * 40.0) % 360.0)),
                    contentAlignment = Alignment.Center
                ) { Text(c.icon, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f)) {
                    Text(c.title, color = look.ink, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(c.subtitle, color = look.ink2, fontSize = 13.sp)
                }
                Text("${c.defs.size}", color = look.ink2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("›", color = look.ink2, fontSize = 22.sp)
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(look.segBg)
            .clickable { s.resetAll() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Alle Einstellungen zurücksetzen", color = look.err, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PresetCard(look: Look, p: Preset, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(118.dp)
            .clip(shape)
            .background(look.surface)
            .border(1.dp, look.edge, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(p.ground)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (r in 0 until 3) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (c in 0 until 3) {
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(p.tile)
                        )
                    }
                }
            }
        }
        Text(p.name, color = look.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryPage(s: Settings, look: Look, cat: Category) {
    Spacer(Modifier.height(10.dp))
    CardBox(look) {
        cat.defs.forEachIndexed { idx, d ->
            if (idx > 0) Divider(look)
            when (d.kind) {
                Kind.TOGGLE -> ToggleRow(s, look, d)
                Kind.CHOICE -> ChoiceRow(s, look, d)
                Kind.SLIDER -> SliderRow(s, look, d)
            }
        }
    }
}

@Composable
private fun Labels(look: Look, d: Def, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(d.label, color = look.ink, fontSize = 16.sp)
        d.hint?.let { Text(it, color = look.ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@Composable
private fun ToggleRow(s: Settings, look: Look, d: Def) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Labels(look, d, Modifier.weight(1f))
        Switch(
            checked = s.bool(d.key),
            onCheckedChange = { s.set(d.key, if (it) "1" else "0") },
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

@Composable
private fun ChoiceRow(s: Settings, look: Look, d: Def) {
    val current = s.str(d.key)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Labels(look, d)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(look.segBg)
                .horizontalScroll(rememberScrollState())
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            d.options.forEach { (value, label) ->
                val sel = value == current
                Box(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (sel) look.accent else Color.Transparent)
                        .clickable { s.set(d.key, value) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (sel) look.onAccent else look.ink2,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(s: Settings, look: Look, d: Def) {
    val v = s.num(d.key).coerceIn(d.min, d.max)
    val steps = (((d.max - d.min) / d.step).roundToInt() - 1).coerceAtLeast(0)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Labels(look, d, Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(look.segBg)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(d.display(v), color = look.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Slider(
            value = v,
            onValueChange = { nv ->
                val snapped = d.min + Math.round((nv - d.min) / d.step) * d.step
                s.set(d.key, String.format(Locale.US, "%.3f", snapped))
            },
            valueRange = d.min..d.max,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = look.user,
                activeTrackColor = look.user,
                inactiveTrackColor = look.segBg,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}
