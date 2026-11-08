package com.ragzonte.ventosdepalmas.scene

import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Backend SVG do [VectorCanvas]: gera a mesma cena do papel de parede como arquivo .svg. */
class SvgCanvas(private val width: Int, private val height: Int) : VectorCanvas {
    private val body = StringBuilder()
    private val defs = StringBuilder()
    private val gradientIds = IdentityHashMap<Fill, String>()
    private var matrix = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    private val stack = ArrayDeque<FloatArray>()

    var circles = 0
        private set
    var lines = 0
        private set
    var paths = 0
        private set

    fun toSvg(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""" +
            "<defs>$defs</defs>$body</svg>"

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) =
        rect(left, top, right, bottom, solid(color))

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill) =
        rect(left, top, right, bottom, gradient(fill, 1f))

    override fun drawCircle(cx: Float, cy: Float, radius: Float, color: Int) = circle(cx, cy, radius, solid(color))

    override fun drawCircle(cx: Float, cy: Float, radius: Float, fill: Fill) = circle(cx, cy, radius, gradient(fill, 1f))

    override fun drawPath(path: VPath, color: Int) = path(path, solid(color))

    override fun drawPath(path: VPath, fill: Fill, alpha: Float) = path(path, gradient(fill, alpha))

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, strokeWidth: Float) {
        lines++
        body.append("<line x1=\"${n(x0)}\" y1=\"${n(y0)}\" x2=\"${n(x1)}\" y2=\"${n(y1)}\" stroke=\"${hex(color)}\"")
            .append(" stroke-opacity=\"${n(Colors.alpha(color) / 255f)}\" stroke-width=\"${n(strokeWidth)}\"")
            .append(" stroke-linecap=\"round\"${transform()}/>")
    }

    override fun save() {
        stack.addLast(matrix.copyOf())
    }

    override fun restore() {
        matrix = stack.removeLast()
    }

    override fun translate(dx: Float, dy: Float) {
        matrix[4] += matrix[0] * dx + matrix[2] * dy
        matrix[5] += matrix[1] * dx + matrix[3] * dy
    }

    override fun rotate(degrees: Float) {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val (a, b, cc, d) = listOf(matrix[0], matrix[1], matrix[2], matrix[3])
        matrix[0] = a * c + cc * s
        matrix[1] = b * c + d * s
        matrix[2] = -a * s + cc * c
        matrix[3] = -b * s + d * c
    }

    override fun scale(sx: Float, sy: Float) {
        matrix[0] *= sx
        matrix[1] *= sx
        matrix[2] *= sy
        matrix[3] *= sy
    }

    private fun rect(left: Float, top: Float, right: Float, bottom: Float, paint: String) {
        body.append("<rect x=\"${n(left)}\" y=\"${n(top)}\" width=\"${n(right - left)}\" height=\"${n(bottom - top)}\"")
            .append("$paint${transform()}/>")
    }

    private fun circle(cx: Float, cy: Float, r: Float, paint: String) {
        circles++
        body.append("<circle cx=\"${n(cx)}\" cy=\"${n(cy)}\" r=\"${n(r)}\"$paint${transform()}/>")
    }

    private fun path(path: VPath, paint: String) {
        paths++
        val d = StringBuilder()
        for (command in path.commands) {
            when (command) {
                is VPath.MoveTo -> d.append("M${n(command.x)} ${n(command.y)}")
                is VPath.LineTo -> d.append("L${n(command.x)} ${n(command.y)}")
                is VPath.CubicTo -> d.append(
                    "C${n(command.x1)} ${n(command.y1)} ${n(command.x2)} ${n(command.y2)} ${n(command.x)} ${n(command.y)}",
                )
                VPath.Close -> d.append("Z")
            }
        }
        body.append("<path d=\"$d\"$paint${transform()}/>")
    }

    private fun solid(color: Int) = " fill=\"${hex(color)}\" fill-opacity=\"${n(Colors.alpha(color) / 255f)}\""

    private fun gradient(fill: Fill, alpha: Float): String {
        val id = gradientIds.getOrPut(fill) {
            val id = "g${gradientIds.size}"
            when (fill) {
                is Fill.Linear -> defs.append(
                    "<linearGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" " +
                        "x1=\"${n(fill.x0)}\" y1=\"${n(fill.y0)}\" x2=\"${n(fill.x1)}\" y2=\"${n(fill.y1)}\">",
                )
                is Fill.Radial -> defs.append(
                    "<radialGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" " +
                        "cx=\"${n(fill.cx)}\" cy=\"${n(fill.cy)}\" r=\"${n(fill.radius)}\">",
                )
            }
            fill.colors.forEachIndexed { i, color ->
                defs.append("<stop offset=\"${n(fill.stops[i])}\" stop-color=\"${hex(color)}\" stop-opacity=\"${n(Colors.alpha(color) / 255f)}\"/>")
            }
            defs.append(if (fill is Fill.Linear) "</linearGradient>" else "</radialGradient>")
            id
        }
        return " fill=\"url(#$id)\" fill-opacity=\"${n(alpha)}\""
    }

    private fun transform(): String {
        val m = matrix
        if (m[0] == 1f && m[1] == 0f && m[2] == 0f && m[3] == 1f && m[4] == 0f && m[5] == 0f) return ""
        return " transform=\"matrix(${m.joinToString(" ") { n(it) }})\""
    }

    private fun hex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

    private fun n(value: Float): String {
        val text = String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')
        return if (text == "-0") "0" else text
    }
}
