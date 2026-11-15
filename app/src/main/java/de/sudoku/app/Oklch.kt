package de.sudoku.app

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** OKLCH nach sRGB, damit dieselben Farben wie in der HTML-Vorschau entstehen. */
fun oklch(l: Double, c: Double, h: Double, alpha: Float = 1f): Color {
    val hr = Math.toRadians(h)
    val a = c * cos(hr)
    val b = c * sin(hr)

    val l_ = l + 0.3963377774 * a + 0.2158037573 * b
    val m_ = l - 0.1055613458 * a - 0.0638541728 * b
    val s_ = l - 0.0894841775 * a - 1.2914855480 * b

    val l3 = l_ * l_ * l_
    val m3 = m_ * m_ * m_
    val s3 = s_ * s_ * s_

    val r = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
    val g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
    val bl = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3

    return Color(encode(r), encode(g), encode(bl), alpha)
}

private fun encode(x: Double): Float {
    val v = x.coerceIn(0.0, 1.0)
    val e = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
    return e.coerceIn(0.0, 1.0).toFloat()
}
