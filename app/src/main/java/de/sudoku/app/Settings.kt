package de.sudoku.app

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Kind { TOGGLE, CHOICE, SLIDER }

/** Eine Einstellung. Alle Werte werden als Text gespeichert ("1"/"0" für Schalter). */
data class Def(
    val key: String,
    val label: String,
    val kind: Kind,
    val default: String,
    val options: List<Pair<String, String>> = emptyList(),
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 1f,
    val suffix: String = "",
    val factor: Float = 1f,
    val hint: String? = null
)

data class Category(val title: String, val icon: String, val subtitle: String, val defs: List<Def>)

class Preset(val name: String, val ground: Color, val tile: Color, val values: Map<String, String>)

private fun toggle(key: String, label: String, on: Boolean, hint: String? = null) =
    Def(key, label, Kind.TOGGLE, if (on) "1" else "0", hint = hint)

private fun choice(
    key: String,
    label: String,
    default: String,
    options: List<Pair<String, String>>,
    hint: String? = null
) = Def(key, label, Kind.CHOICE, default, options, hint = hint)

private fun slider(
    key: String,
    label: String,
    default: Float,
    min: Float,
    max: Float,
    step: Float,
    suffix: String = "",
    factor: Float = 1f,
    hint: String? = null
) = Def(key, label, Kind.SLIDER, default.toString(), min = min, max = max, step = step, suffix = suffix, factor = factor, hint = hint)

val CATEGORIES: List<Category> = listOf(
    Category(
        "Farben", "◐", "Modus, Akzent, Hintergrund, Kontrast", listOf(
            choice("theme", "Modus", "system", listOf("system" to "Auto", "light" to "Hell", "dark" to "Dunkel")),
            slider("accent", "Akzent-Farbton", 258f, 0f, 360f, 1f, "°"),
            slider("uc", "Akzent-Sättigung", 1f, 0f, 1.6f, 0.05f, " %", 100f),
            choice("userInk", "Eigene Ziffern", "accent", listOf("accent" to "Akzent", "ink" to "Tinte", "green" to "Grün", "orange" to "Orange")),
            slider("bgHue", "Hintergrund-Farbton", 265f, 0f, 360f, 1f, "°"),
            slider("bgTint", "Hintergrund-Tönung", 1f, 0f, 4f, 0.1f, " ×"),
            choice("bgDark", "Dunkelmodus-Hintergrund", "normal", listOf("normal" to "Normal", "amoled" to "Tiefschwarz"), "Tiefschwarz spart Strom auf OLED-Displays"),
            choice("contrast", "Kontrast", "normal", listOf("normal" to "Normal", "high" to "Hoch")),
            choice("errHue", "Fehlerfarbe", "25", listOf("25" to "Rot", "350" to "Magenta", "60" to "Orange", "300" to "Violett"), "Falls Rot schwer zu erkennen ist")
        )
    ),
    Category(
        "Blöcke und Raster", "▦", "Farbmuster, Abstände, Rundungen, Schatten", listOf(
            choice("boxPattern", "Farbmuster", "quilt", listOf("quilt" to "Quilt", "check" to "Schachbrett", "rows" to "Reihen", "cols" to "Spalten", "one" to "Einfarbig")),
            choice("tint", "Blockfarben", "1.0", listOf("1.0" to "Bunt", "0.4" to "Sanft", "0.0" to "Aus")),
            slider("hueShift", "Farben drehen", 0f, 0f, 359f, 1f, "°"),
            toggle("randomHue", "Neue Farben bei jedem Spiel", false, "Dreht die Blockfarben zufällig"),
            slider("gap", "Abstand zwischen Blöcken", 6f, 0f, 14f, 1f, " dp"),
            slider("ig", "Linienstärke zwischen Feldern", 1f, 0f, 4f, 0.5f, " dp"),
            slider("rb", "Rundung Brett", 22f, 0f, 44f, 1f, " dp"),
            slider("rx", "Rundung Blöcke", 13f, 0f, 32f, 1f, " dp"),
            choice("shadow", "Schatten", "soft", listOf("off" to "Aus", "soft" to "Weich", "strong" to "Stark")),
            slider("aw", "Breite der App", 440f, 320f, 560f, 10f, " dp")
        )
    ),
    Category(
        "Ziffern", "1", "Schrift, Größe, Stärke, Notizen", listOf(
            choice("numFont", "Schrift", "standard", listOf("standard" to "Standard", "serif" to "Serif", "mono" to "Mono", "hand" to "Hand")),
            slider("numSize", "Größe", 1f, 0.8f, 1.3f, 0.02f, " %", 100f),
            slider("gw", "Stärke vorgegebener Ziffern", 750f, 400f, 900f, 50f),
            slider("uw", "Stärke eigener Ziffern", 600f, 400f, 900f, 50f),
            slider("nn", "Größe der Notizen", 1f, 0.7f, 1.4f, 0.05f, " %", 100f),
            slider("nw", "Stärke der Notizen", 600f, 400f, 900f, 50f)
        )
    ),
    Category(
        "Markierung", "✦", "Auswahl, Hervorhebung, gleiche Ziffern", listOf(
            choice("selStyle", "Auswahlstil", "ink", listOf("ink" to "Tinte", "accent" to "Akzent", "ring" to "Rahmen", "soft" to "Sanft")),
            choice("hlMode", "Hervorheben", "all", listOf("all" to "Zeile, Spalte, Block", "lines" to "Zeile, Spalte", "off" to "Aus")),
            toggle("hlSame", "Gleiche Ziffern markieren", true),
            toggle("hlNotes", "Passende Notizen hervorheben", true),
            slider("hlStrength", "Stärke der Hervorhebung", 55f, 0f, 100f, 5f, " %")
        )
    ),
    Category(
        "Eingabe", "☝", "Eingabemodus, Ziffernfeld, Automatik", listOf(
            choice("inputMode", "Eingabemodus", "cell", listOf("cell" to "Feld zuerst", "digit" to "Ziffer zuerst"), "Bei „Ziffer zuerst“ wählst du erst die Ziffer und tippst dann Felder an"),
            choice("autoAdvance", "Automatisch weiterspringen", "off", listOf("off" to "Aus", "next" to "Immer", "correct" to "Nur bei richtig")),
            toggle("autoNotes", "Notizen automatisch aufräumen", true, "Entfernt die gesetzte Ziffer aus den Notizen der Nachbarfelder"),
            choice("doneKeys", "Fertige Ziffern", "grey", listOf("grey" to "Abgrauen", "hide" to "Ausblenden")),
            choice("keyBadge", "Ziffernfeld-Anzeige", "bar", listOf("bar" to "Balken", "count" to "Rest-Zahl", "off" to "Aus")),
            choice("padLayout", "Ziffernfeld", "row", listOf("row" to "Eine Reihe", "grid" to "3 × 3")),
            slider("rk", "Rundung der Tasten", 14f, 0f, 30f, 1f, " dp"),
            slider("kh", "Höhe der Tasten", 60f, 40f, 80f, 2f, " dp")
        )
    ),
    Category(
        "Fehler und Tipps", "!", "Fehleranzeige, Limits, Tipp-Verhalten", listOf(
            toggle("showErrors", "Fehler anzeigen", true),
            choice("errMode", "Fehler erkennen", "solution", listOf("solution" to "Gegen Lösung", "rules" to "Nur Regelverstöße")),
            slider("maxMistakes", "Fehlerlimit", 0f, 0f, 10f, 1f, "", 1f, "0 = unbegrenzt, danach ist das Spiel verloren"),
            toggle("showMist", "Fehlerzähler anzeigen", false),
            slider("hintLimit", "Tipp-Limit", 0f, 0f, 10f, 1f, "", 1f, "0 = unbegrenzt"),
            choice("hintMode", "Tipp-Verhalten", "fill", listOf("fill" to "Ziffer eintragen", "show" to "Feld nur zeigen"))
        )
    ),
    Category(
        "Oberfläche", "▤", "Sichtbare Elemente und Anordnung", listOf(
            toggle("showWord", "Wortmarke anzeigen", true),
            toggle("showMark", "Logo anzeigen", true),
            toggle("showSeg", "Schwierigkeitsauswahl anzeigen", true),
            toggle("showOpen", "Anzahl offener Felder anzeigen", true),
            toggle("showQErr", "Schalter „Fehler zeigen“ anzeigen", true),
            toggle("showTimer", "Stoppuhr anzeigen", true),
            choice("timeFmt", "Zeitformat", "mmss", listOf("mmss" to "mm:ss", "min" to "Minuten")),
            choice("clockStyle", "Uhr-Stil", "pill", listOf("pill" to "Kapsel", "plain" to "Schlicht")),
            choice("toolsPos", "Werkzeugleiste", "below", listOf("below" to "Unter dem Brett", "above" to "Über dem Brett")),
            toggle("toolLabels", "Beschriftung der Werkzeuge", true),
            toggle("showUndo", "Knopf „Zurück“", true),
            toggle("showErase", "Knopf „Löschen“", true),
            toggle("showNotesBtn", "Knopf „Notizen“", true),
            toggle("showHintBtn", "Knopf „Tipp“", true)
        )
    ),
    Category(
        "Gefühl", "♪", "Vibration, Bildschirm", listOf(
            toggle("haptics", "Vibration bei Eingaben", true),
            toggle("wake", "Bildschirm anlassen", false)
        )
    ),
    Category(
        "Spiel", "▶", "Eigene Schwierigkeit, Pausen", listOf(
            slider("customClues", "Vorgaben bei „Eigene“", 34f, 22f, 50f, 1f, "", 1f, "Weniger Vorgaben sind schwerer"),
            toggle("pauseHidden", "Uhr pausiert im Hintergrund", true),
            toggle("pauseSettings", "Uhr pausiert in den Einstellungen", true)
        )
    )
)

val ALL_DEFS: List<Def> = CATEGORIES.flatMap { it.defs }
val DEF_BY_KEY: Map<String, Def> = ALL_DEFS.associateBy { it.key }

/** Die ersten vier Kategorien (Farben bis Markierung) gehören zum Aussehen und werden von Designs überschrieben. */
private val APPEARANCE_KEYS: List<String> = CATEGORIES.take(4).flatMap { c -> c.defs.map { it.key } }

val PRESETS: List<Preset> = listOf(
    Preset("Standard", Color(0xFFCFD3E0), Color(0xFFECEEF5), emptyMap()),
    Preset(
        "Papier", Color(0xFFD9CFB8), Color(0xFFF4EFE2),
        mapOf("bgHue" to "85", "bgTint" to "2.5", "accent" to "40", "tint" to "0.4", "numFont" to "serif", "boxPattern" to "one", "gw" to "700", "uw" to "500")
    ),
    Preset(
        "Nacht", Color(0xFF0D0D0F), Color(0xFF2A2A31),
        mapOf("theme" to "dark", "bgDark" to "amoled", "accent" to "200", "tint" to "0.4", "shadow" to "off")
    ),
    Preset(
        "Neon", Color(0xFF1B0F2A), Color(0xFF4A2A73),
        mapOf("theme" to "dark", "accent" to "330", "uc" to "1.5", "bgHue" to "300", "bgTint" to "2.5", "numFont" to "mono", "gw" to "700")
    ),
    Preset(
        "Pastell", Color(0xFFF0CFD4), Color(0xFFFBE9EC),
        mapOf("accent" to "350", "bgHue" to "20", "bgTint" to "1.8", "rb" to "30", "rx" to "20", "rk" to "20", "gap" to "8")
    ),
    Preset(
        "Wald", Color(0xFFC4D8C8), Color(0xFFE7F1E8),
        mapOf("accent" to "150", "bgHue" to "150", "bgTint" to "2.5", "boxPattern" to "check", "tint" to "0.4", "numFont" to "serif")
    ),
    Preset(
        "Kontrast", Color(0xFF111111), Color(0xFFFFFFFF),
        mapOf("contrast" to "high", "tint" to "0.0", "gw" to "800", "uw" to "800", "numSize" to "1.12", "shadow" to "off", "selStyle" to "accent", "hlStrength" to "80")
    ),
    Preset(
        "Retro", Color(0xFF8A7A55), Color(0xFFEFE6C8),
        mapOf("numFont" to "mono", "rb" to "4", "rx" to "2", "rk" to "4", "gap" to "3", "tint" to "0.0", "bgHue" to "85", "bgTint" to "3", "accent" to "40", "shadow" to "off", "boxPattern" to "one")
    ),
    Preset(
        "Gummi", Color(0xFFD9C9F5), Color(0xFFEFE8FB),
        mapOf("rb" to "38", "rx" to "28", "rk" to "28", "gap" to "10", "ig" to "2", "accent" to "295", "gw" to "650", "hueShift" to "40")
    )
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("sudoku-settings", Context.MODE_PRIVATE)
    private val map = mutableStateMapOf<String, String>()

    init {
        for (d in ALL_DEFS) map[d.key] = prefs.getString(d.key, d.default) ?: d.default
    }

    fun str(key: String): String = map[key] ?: DEF_BY_KEY[key]?.default ?: ""
    fun bool(key: String): Boolean = str(key) == "1"
    fun num(key: String): Float = str(key).toFloatOrNull() ?: DEF_BY_KEY[key]?.default?.toFloatOrNull() ?: 0f
    fun int(key: String): Int = num(key).roundToInt()

    fun set(key: String, value: String) {
        map[key] = value
        prefs.edit().putString(key, value).apply()
    }

    fun resetAll() {
        val e = prefs.edit()
        for (d in ALL_DEFS) {
            map[d.key] = d.default
            e.putString(d.key, d.default)
        }
        e.apply()
    }

    fun applyPreset(p: Preset) {
        val e = prefs.edit()
        for (key in APPEARANCE_KEYS) {
            val v = p.values[key] ?: DEF_BY_KEY.getValue(key).default
            map[key] = v
            e.putString(key, v)
        }
        e.apply()
    }
}

fun Def.display(v: Float): String {
    val x = v * factor
    val text = if (abs(x - x.roundToInt()) < 0.01f) x.roundToInt().toString() else String.format(Locale.US, "%.1f", x)
    return text + suffix
}
