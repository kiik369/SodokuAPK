package de.sudoku.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val BOX_HUES = doubleArrayOf(15.0, 55.0, 95.0, 135.0, 175.0, 215.0, 255.0, 295.0, 335.0)

/** Alle Farben und Maße, abgeleitet aus den Einstellungen. Entspricht der HTML-Vorschau. */
class Look(private val s: Settings, systemDark: Boolean) {

    val dark: Boolean = when (s.str("theme")) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    private val k = s.num("bgTint").toDouble()
    private val bgHue = s.num("bgHue").toDouble()
    private val amoled = dark && s.str("bgDark") == "amoled"
    private val accentHue = s.num("accent").toDouble()
    private val uc = s.num("uc").toDouble()
    private val tm = s.num("tint").toDouble()
    private val errHue = s.num("errHue").toDouble()
    private val hl = s.num("hlStrength") / 100f
    private val high = s.str("contrast") == "high"

    private fun o(l: Double, c: Double): Color = oklch(l, c * k, bgHue)

    val ground: Color = when {
        amoled -> Color.Black
        dark -> o(0.2, 0.02)
        else -> o(0.975, 0.006)
    }
    val surface: Color = when {
        amoled -> o(0.16, 0.01)
        dark -> o(0.26, 0.02)
        else -> Color.White
    }
    val ink: Color = if (dark) o(0.96, 0.01) else o(0.24, 0.03)
    val ink2: Color = when {
        high -> ink
        dark -> o(0.76, 0.02)
        else -> o(0.47, 0.02)
    }
    val accent: Color = if (dark) o(0.94, 0.03) else o(0.27, 0.05)
    val onAccent: Color = if (dark) o(0.22, 0.03) else oklch(0.98, 0.005, 265.0)
    val groove: Color = when {
        amoled -> oklch(0.07, 0.0, 0.0)
        dark -> o(0.14, 0.015)
        else -> o(0.87, 0.02)
    }
    val edge: Color = when {
        high -> ink.copy(alpha = 0.4f)
        amoled -> o(0.3, 0.01)
        dark -> o(0.34, 0.02)
        else -> o(0.89, 0.01)
    }
    val segBg: Color = when {
        amoled -> o(0.2, 0.01)
        dark -> o(0.25, 0.02)
        else -> o(0.93, 0.01)
    }
    val barBg: Color = when {
        amoled -> o(0.3, 0.01)
        dark -> o(0.34, 0.02)
        else -> o(0.92, 0.01)
    }

    /** Akzentfarbe für eigene Ziffern, Schalter, Ringe. */
    val user: Color = if (dark) oklch(0.8, 0.12 * uc, accentHue) else oklch(0.5, 0.19 * uc, accentHue)
    val mine: Color = when (s.str("userInk")) {
        "ink" -> ink
        "green" -> if (dark) oklch(0.8, 0.14, 150.0) else oklch(0.5, 0.14, 150.0)
        "orange" -> if (dark) oklch(0.8, 0.14, 60.0) else oklch(0.55, 0.15, 55.0)
        else -> user
    }

    val err: Color = if (dark) oklch(0.78, 0.15, errHue) else oklch(0.52, 0.21, errHue)
    val errBg: Color = if (dark) oklch(0.6, 0.2, errHue, 0.4f) else oklch(0.65, 0.2, errHue, 0.28f)
    val peer: Color = Color.White.copy(alpha = (if (dark) hl * 0.145f else hl).coerceIn(0f, 1f))
    val same: Color = user.copy(alpha = if (dark) 0.22f else 0.14f)
    val ring: Color = user.copy(alpha = if (dark) 0.8f else 0.7f)

    // Maße
    val gap = s.num("gap")
    val ig = s.num("ig")
    val rb = s.num("rb")
    val rx = s.num("rx")
    val rk = s.num("rk")
    val kh = s.num("kh")
    val aw = s.num("aw")
    val shadow = s.str("shadow")

    // Ziffern
    val numSize = s.num("numSize")
    val nn = s.num("nn")
    val gw = s.int("gw")
    val uw = s.int("uw")
    val nw = s.int("nw")
    val numFont: FontFamily = when (s.str("numFont")) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "hand" -> FontFamily.Cursive
        else -> FontFamily.SansSerif
    }

    // Markierung
    val selStyle = s.str("selStyle")
    val hlMode = s.str("hlMode")
    val hlSame = s.bool("hlSame")
    val hlNotes = s.bool("hlNotes")

    // Ziffernfeld
    val errRules = s.str("errMode") == "rules"
    val doneKeys = s.str("doneKeys")
    val keyBadge = s.str("keyBadge")
    val padLayout = s.str("padLayout")

    private fun boxHue(b: Int): Double {
        val br = b / 3
        val bc = b % 3
        val base = when (s.str("boxPattern")) {
            "check" -> if ((br + bc) % 2 == 1) 95.0 else 215.0
            "one" -> accentHue
            "rows" -> doubleArrayOf(15.0, 135.0, 255.0)[br]
            "cols" -> doubleArrayOf(15.0, 135.0, 255.0)[bc]
            else -> BOX_HUES[b]
        }
        return (base + s.num("hueShift")) % 360.0
    }

    fun tile(b: Int): Color =
        if (dark) oklch(0.3, 0.06 * tm, boxHue(b)) else oklch(0.945, 0.045 * tm, boxHue(b))

    fun line(b: Int): Color =
        if (dark) oklch(0.23, 0.05 * tm, boxHue(b)) else oklch(0.885, 0.06 * tm, boxHue(b))

    fun markHue(i: Int): Color = oklch(0.76, 0.13, BOX_HUES[i])

    /** Gilt für Feld [other], wenn [selected] markiert ist (Zeile, Spalte, optional Block). */
    fun isHighlightPeer(selected: Int, other: Int): Boolean {
        if (hlMode == "off") return false
        val ra = selected / 9
        val ca = selected % 9
        val rb2 = other / 9
        val cb = other % 9
        if (ra == rb2 || ca == cb) return true
        if (hlMode == "lines") return false
        return ra / 3 == rb2 / 3 && ca / 3 == cb / 3
    }

    fun colorScheme(): ColorScheme =
        if (dark) {
            darkColorScheme(
                primary = user, onPrimary = onAccent, background = ground, onBackground = ink,
                surface = surface, onSurface = ink, surfaceVariant = segBg, onSurfaceVariant = ink2,
                outline = edge, error = err, surfaceContainerHigh = surface
            )
        } else {
            lightColorScheme(
                primary = user, onPrimary = onAccent, background = ground, onBackground = ink,
                surface = surface, onSurface = ink, surfaceVariant = segBg, onSurfaceVariant = ink2,
                outline = edge, error = err, surfaceContainerHigh = surface
            )
        }
}
