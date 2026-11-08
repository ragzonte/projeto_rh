package com.ragzonte.ventosdepalmas.scene

import kotlin.math.roundToInt

/** Cores ARGB em `Int`, sem depender do Android. */
object Colors {
    fun rgb(hex: Int): Int = hex or OPAQUE

    fun alpha(color: Int): Int = color ushr 24
    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun lerp(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * k).roundToInt()
        return argb(
            mix(alpha(from), alpha(to)),
            mix(red(from), red(to)),
            mix(green(from), green(to)),
            mix(blue(from), blue(to)),
        )
    }

    /** Troca a opacidade (0 a 1), mantendo o RGB. */
    fun withAlpha(color: Int, alpha: Float): Int =
        (color and RGB_MASK) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

    /** Escurece (fator < 1) ou clareia (fator > 1) o RGB. */
    fun scale(color: Int, factor: Float): Int = argb(
        alpha(color),
        (red(color) * factor).roundToInt(),
        (green(color) * factor).roundToInt(),
        (blue(color) * factor).roundToInt(),
    )

    private const val OPAQUE = 0xFF shl 24
    private const val RGB_MASK = 0x00FFFFFF
}

/** Transição suave de 0 a 1 entre [edge0] e [edge1] (aceita bordas invertidas). */
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
