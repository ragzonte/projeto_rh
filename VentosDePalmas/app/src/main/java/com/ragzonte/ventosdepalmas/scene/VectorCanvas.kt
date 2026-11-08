package com.ragzonte.ventosdepalmas.scene

/**
 * Superfície de desenho vetorial mínima usada pela cena.
 *
 * O app desenha no Canvas do Android (acelerado por GPU); os testes desenham em SVG. Assim a mesma
 * cena vira imagem de prévia sem precisar de um celular.
 */
interface VectorCanvas {
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, color: Int)
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill)
    fun drawCircle(cx: Float, cy: Float, radius: Float, color: Int)
    fun drawCircle(cx: Float, cy: Float, radius: Float, fill: Fill)
    fun drawPath(path: VPath, color: Int)
    /** Preenche com degradê; [alpha] (0 a 1) multiplica a opacidade de todas as cores. */
    fun drawPath(path: VPath, fill: Fill, alpha: Float = 1f)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, strokeWidth: Float)

    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    /** Gira no sentido horário, em graus, em torno da origem atual. */
    fun rotate(degrees: Float)
    fun scale(sx: Float, sy: Float)
}

/** Preenchimento em degradê. Cores sólidas são passadas direto como `Int` ARGB, sem alocar objetos. */
sealed class Fill(val colors: IntArray, val stops: FloatArray) {
    init {
        require(colors.size == stops.size && colors.size >= 2) { "Degradê precisa de 2+ cores com posições" }
    }

    /** Objeto nativo (ex.: Shader) guardado pelo backend para não recriar a cada quadro. */
    @JvmField
    var platformCache: Any? = null

    class Linear(
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        colors: IntArray,
        stops: FloatArray,
    ) : Fill(colors, stops)

    class Radial(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        colors: IntArray,
        stops: FloatArray,
    ) : Fill(colors, stops)
}

/** Caminho vetorial (equivalente ao atributo `d` do SVG). Montado uma vez e reaproveitado a cada quadro. */
class VPath {
    sealed interface Command
    class MoveTo(val x: Float, val y: Float) : Command
    class LineTo(val x: Float, val y: Float) : Command
    class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : Command
    data object Close : Command

    private val _commands = ArrayList<Command>()
    val commands: List<Command> get() = _commands

    /** Objeto nativo (ex.: android.graphics.Path) guardado pelo backend. */
    @JvmField
    var platformCache: Any? = null

    private fun add(command: Command): VPath {
        _commands += command
        platformCache = null
        return this
    }

    fun moveTo(x: Float, y: Float) = add(MoveTo(x, y))
    fun lineTo(x: Float, y: Float) = add(LineTo(x, y))
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = add(CubicTo(x1, y1, x2, y2, x, y))
    fun close() = add(Close)

    /** Círculo no sentido horário; vários círculos no mesmo caminho se unem sem sobreposição visível. */
    fun addCircle(cx: Float, cy: Float, r: Float) = addOval(cx, cy, r, r)

    fun addOval(cx: Float, cy: Float, rx: Float, ry: Float): VPath {
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        moveTo(cx, cy - ry)
        cubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
        cubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
        cubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
        cubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
        return close()
    }

    /** Retângulo no sentido horário. */
    fun addRect(left: Float, top: Float, right: Float, bottom: Float): VPath {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom)
        lineTo(left, bottom)
        return close()
    }

    companion object {
        /** Constante para aproximar um quarto de círculo com uma curva de Bézier cúbica. */
        const val KAPPA = 0.5522848f
    }
}
