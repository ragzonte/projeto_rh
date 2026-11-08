package com.ragzonte.ventosdepalmas.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.ragzonte.ventosdepalmas.scene.Fill
import com.ragzonte.ventosdepalmas.scene.VPath
import com.ragzonte.ventosdepalmas.scene.VectorCanvas
import kotlin.math.roundToInt

/**
 * Liga a cena ao Canvas do Android. Paint, Path e Shader são reaproveitados entre quadros, então o
 * desenho não gera lixo para o coletor de memória (importante para não engasgar a 60/120 Hz).
 */
class AndroidVectorCanvas : VectorCanvas {
    lateinit var canvas: Canvas

    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shaded = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        canvas.drawRect(left, top, right, bottom, solid(color))
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill) {
        canvas.drawRect(left, top, right, bottom, shaded(fill, 1f))
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, color: Int) {
        canvas.drawCircle(cx, cy, radius, solid(color))
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, fill: Fill) {
        canvas.drawCircle(cx, cy, radius, shaded(fill, 1f))
    }

    override fun drawPath(path: VPath, color: Int) {
        canvas.drawPath(nativePath(path), solid(color))
    }

    override fun drawPath(path: VPath, fill: Fill, alpha: Float) {
        canvas.drawPath(nativePath(path), shaded(fill, alpha))
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, strokeWidth: Float) {
        stroke.color = color
        stroke.strokeWidth = strokeWidth
        canvas.drawLine(x0, y0, x1, y1, stroke)
    }

    override fun save() {
        canvas.save()
    }

    override fun restore() {
        canvas.restore()
    }

    override fun translate(dx: Float, dy: Float) {
        canvas.translate(dx, dy)
    }

    override fun rotate(degrees: Float) {
        canvas.rotate(degrees)
    }

    override fun scale(sx: Float, sy: Float) {
        canvas.scale(sx, sy)
    }

    private fun solid(color: Int): Paint {
        solid.color = color
        return solid
    }

    private fun shaded(fill: Fill, alpha: Float): Paint {
        shaded.shader = shaderFor(fill)
        shaded.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        return shaded
    }

    private fun shaderFor(fill: Fill): Shader {
        (fill.platformCache as? Shader)?.let { return it }
        val shader = when (fill) {
            is Fill.Linear -> LinearGradient(
                fill.x0, fill.y0, fill.x1, fill.y1, fill.colors, fill.stops, Shader.TileMode.CLAMP,
            )
            is Fill.Radial -> RadialGradient(
                fill.cx, fill.cy, fill.radius, fill.colors, fill.stops, Shader.TileMode.CLAMP,
            )
        }
        fill.platformCache = shader
        return shader
    }

    private fun nativePath(path: VPath): Path {
        (path.platformCache as? Path)?.let { return it }
        val native = Path()
        for (command in path.commands) {
            when (command) {
                is VPath.MoveTo -> native.moveTo(command.x, command.y)
                is VPath.LineTo -> native.lineTo(command.x, command.y)
                is VPath.CubicTo -> native.cubicTo(command.x1, command.y1, command.x2, command.y2, command.x, command.y)
                VPath.Close -> native.close()
            }
        }
        path.platformCache = native
        return native
    }
}
