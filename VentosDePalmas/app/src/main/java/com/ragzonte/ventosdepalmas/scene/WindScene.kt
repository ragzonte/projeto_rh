package com.ragzonte.ventosdepalmas.scene

import com.ragzonte.ventosdepalmas.scene.Colors.rgb
import com.ragzonte.ventosdepalmas.scene.Colors.withAlpha
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Paisagem de Palmas com torres eólicas, desenhada só com vetores.
 *
 * Ciclo de uso: [resize] quando a superfície muda, [update] quando o ambiente muda (≈ 1 vez por
 * minuto ou quando chega clima novo), [step] e [draw] a cada quadro. O desenho não aloca objetos
 * por quadro: caminhos e degradês são montados em [resize]/[update] e reaproveitados.
 */
class WindScene(private val seed: Long = 20_261_108L) {

    var width = 0f
        private set
    var height = 0f
        private set

    /** Posição das telas iniciais do launcher (0 a 1), para dar profundidade ao arrastar. */
    var parallax = 0.5f

    private var environment: Environment? = null
    private var look: Look? = null
    private var time = 0.0
    private val events = Random(seed * 31 + 7)

    // ---- Geometria fixa (unidade = altura da torre) ----
    private val towerPath = VPath()
        .moveTo(-0.022f, 0f).lineTo(-0.011f, -1f).lineTo(0.011f, -1f).lineTo(0.022f, 0f).close()
    private val nacellePath = VPath().addRect(-0.03f, -1.026f, 0.034f, -0.984f)
    private val bladePath = VPath()
        .moveTo(-0.010f, 0f)
        .lineTo(-0.016f, -0.04f)
        .cubicTo(-0.020f, -0.20f, -0.008f, -0.40f, -0.002f, -0.48f)
        .lineTo(0.002f, -0.48f)
        .cubicTo(0.020f, -0.33f, 0.036f, -0.12f, 0.024f, -0.045f)
        .lineTo(0.010f, 0f)
        .close()
    private val beaconGlow = Fill.Radial(
        0f, 0f, 1f,
        intArrayOf(withAlpha(BEACON_RED, 0.75f), withAlpha(BEACON_RED, 0.25f), withAlpha(BEACON_RED, 0f)),
        floatArrayOf(0f, 0.35f, 1f),
    )

    // ---- Torres: camada (0 longe, 1 meio, 2 perto), posição, tamanho e rotação ----
    private val turbineLayer = intArrayOf(0, 0, 0, 0, 0, 0, 1, 1, 1, 2)
    private val turbineX = floatArrayOf(0.06f, 0.17f, 0.29f, 0.47f, 0.58f, 0.92f, 0.12f, 0.37f, 0.87f, 0.66f)
    private val turbineSize = floatArrayOf(0.042f, 0.040f, 0.044f, 0.041f, 0.043f, 0.040f, 0.085f, 0.08f, 0.088f, 0.20f)
    private val turbineSpeed = FloatArray(turbineX.size)
    private val turbineAngle = FloatArray(turbineX.size)

    // ---- Relevo: base (fração da altura), amplitude e frequências por camada ----
    private val hillBase = floatArrayOf(0.705f, 0.765f, 0.865f)
    private val hillAmplitude = floatArrayOf(0.010f, 0.018f, 0.026f)
    private val hillFrequency = arrayOf(floatArrayOf(2.3f, 5.1f), floatArrayOf(1.7f, 4.3f), floatArrayOf(1.3f, 3.1f))
    private val hillPhase = Array(3) { FloatArray(2) }
    private val hillPaths = arrayOfNulls<VPath>(3)

    // ---- Estrelas ----
    private val starX = FloatArray(STAR_COUNT)
    private val starY = FloatArray(STAR_COUNT)
    private val starRadius = FloatArray(STAR_COUNT)
    private val starBrightness = FloatArray(STAR_COUNT)
    private val starPhase = FloatArray(STAR_COUNT)
    private val starSpeed = FloatArray(STAR_COUNT)
    private val starColor = IntArray(STAR_COUNT)

    // ---- Nuvens ----
    private class Cloud(
        val path: VPath,
        val high: Boolean,
        var x: Float,
        val y: Float,
        val widthFraction: Float,
        val speedFactor: Float,
    ) {
        var alpha = 0f
        var target = 0f
    }

    private val clouds = ArrayList<Cloud>(CLOUD_POOL)

    // ---- Chuva ----
    private val dropX = FloatArray(MAX_DROPS)
    private val dropY = FloatArray(MAX_DROPS)
    private val dropLength = FloatArray(MAX_DROPS)
    private val dropSpeed = FloatArray(MAX_DROPS)
    private val dropNear = BooleanArray(MAX_DROPS)
    private var activeDrops = 0f

    // ---- Raios ----
    private var nextStrikeAt = 4.0
    private var strikeAt = -100.0
    private val bolt = FloatArray(BOLT_POINTS * 2)
    private var boltPointCount = 0

    // ---- Preenchimentos dependentes do Look ----
    private var skyFill: Fill? = null
    private var horizonGlowFill: Fill? = null
    private var cityGlowFill: Fill? = null
    private var sunGlowFill: Fill? = null
    private var moonGlowFill: Fill? = null
    private var veilFill: Fill? = null
    private var cloudFill: Fill? = null
    private var fogFill: Fill? = null
    private val towerFills = arrayOfNulls<Fill>(3)
    private val hillFills = arrayOfNulls<Fill>(3)
    private var moonLitPath = VPath()
    private var moonLitFraction = -1.0

    init {
        val random = Random(seed)
        for (i in turbineX.indices) {
            turbineSpeed[i] = 0.9f + random.nextFloat() * 0.2f
            turbineAngle[i] = random.nextFloat() * 120f
        }
        for (layer in 0..2) for (k in 0..1) hillPhase[layer][k] = random.nextFloat() * 6.28f
        for (i in 0 until STAR_COUNT) {
            starX[i] = random.nextFloat()
            starY[i] = random.nextFloat() * random.nextFloat() * 0.68f + random.nextFloat() * 0.02f
            val bright = random.nextFloat() < 0.06f
            starRadius[i] = if (bright) 1.25f + random.nextFloat() * 0.35f else 0.35f + random.nextFloat() * 0.65f
            starBrightness[i] = if (bright) 1f else 0.35f + random.nextFloat() * 0.55f
            starPhase[i] = random.nextFloat() * 6.28f
            starSpeed[i] = 0.6f + random.nextFloat() * 1.8f
            starColor[i] = when (random.nextInt(10)) {
                0 -> rgb(0xCFE0FF)
                1 -> rgb(0xFFE8C8)
                else -> rgb(0xFFFFFF)
            }
        }
        repeat(CLOUD_POOL) { i ->
            val high = i % 3 == 0
            clouds += Cloud(
                path = cloudShape(random),
                high = high,
                x = random.nextFloat() * 1.2f - 0.1f,
                y = if (high) 0.07f + random.nextFloat() * 0.22f else 0.26f + random.nextFloat() * 0.32f,
                widthFraction = if (high) 0.38f + random.nextFloat() * 0.3f else 0.3f + random.nextFloat() * 0.25f,
                speedFactor = if (high) 0.6f + random.nextFloat() * 0.3f else 1f + random.nextFloat() * 0.5f,
            )
        }
    }

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width.toFloat() == this.width && height.toFloat() == this.height) return
        this.width = width.toFloat()
        this.height = height.toFloat()
        for (layer in 0..2) hillPaths[layer] = buildHill(layer)
        val random = Random(seed + 99)
        for (i in 0 until MAX_DROPS) resetDrop(i, random.nextFloat() * this.height, random)
        rebuildFills()
    }

    fun update(environment: Environment) {
        this.environment = environment
        val look = Look.from(environment)
        this.look = look
        val activeClouds = if (look.cloudCover < 0.06f) 0 else (look.cloudCover * CLOUD_POOL).roundToInt()
        clouds.forEachIndexed { i, cloud -> cloud.target = if (i < activeClouds) 1f else 0f }
        if (abs(environment.moon.illuminatedFraction - moonLitFraction) > 0.002) {
            moonLitFraction = environment.moon.illuminatedFraction
            buildMoonLitPath(moonLitFraction.toFloat())
        }
        rebuildFills()
    }

    /** Coloca as nuvens já no estado final (sem a transição de alguns segundos). Útil para prévias. */
    fun settle() {
        clouds.forEach { it.alpha = it.target }
        activeDrops = targetDrops()
    }

    fun step(dtSeconds: Float) {
        val look = look ?: return
        val dt = dtSeconds.coerceIn(0f, 0.25f)
        time += dt

        val rpm = (2f + look.windKmh * 0.55f).coerceIn(1.5f, 16f)
        for (i in turbineAngle.indices) {
            turbineAngle[i] = (turbineAngle[i] + rpm * 6f * turbineSpeed[i] * dt) % 360f
        }

        val windDrift = (0.004f + look.windKmh * 0.0005f) * look.windScreenDirection
        for (cloud in clouds) {
            cloud.alpha = approach(cloud.alpha, cloud.target, dt * 0.25f)
            cloud.x += windDrift * cloud.speedFactor * dt
            if (windDrift > 0 && cloud.x > 1.08f) cloud.x = -cloud.widthFraction - events.nextFloat() * 0.2f
            if (windDrift < 0 && cloud.x + cloud.widthFraction < -0.08f) cloud.x = 1.08f + events.nextFloat() * 0.2f
        }

        activeDrops = approach(activeDrops, targetDrops(), dt * 120f)
        if (activeDrops >= 1f && height > 0f) {
            val slant = (look.windKmh / 45f).coerceAtMost(0.45f) * look.windScreenDirection
            for (i in 0 until activeDrops.toInt()) {
                dropY[i] += dropSpeed[i] * dt
                dropX[i] += slant * dropSpeed[i] * dt
                if (dropY[i] - dropLength[i] > height) resetDrop(i, -events.nextFloat() * height * 0.15f, events)
            }
        }

        if (look.thunderstorm && time >= nextStrikeAt) {
            strikeAt = time
            nextStrikeAt = time + 5.0 + events.nextDouble() * 9.0
            buildBolt()
        }
    }

    fun draw(canvas: VectorCanvas) {
        val look = look
        val env = environment
        if (look == null || env == null || width <= 0f) {
            if (width > 0f) canvas.drawRect(0f, 0f, width, height, rgb(0x05070F))
            return
        }
        val w = width
        val h = height
        val horizonY = h * HORIZON
        val unit = w / 400f

        canvas.drawRect(0f, 0f, w, h, skyFill!!)

        val sunX = skyX(env.sun.hourAngleDeg)
        if (look.horizonGlow > 0.01f) drawEllipse(canvas, sunX.coerceIn(0.05f * w, 0.95f * w), horizonY, 0.42f, horizonGlowFill!!)
        if (look.cityGlow > 0.01f) drawEllipse(canvas, 0.28f * w, horizonY, 0.32f, cityGlowFill!!)

        if (look.starAlpha > 0.02f) drawStars(canvas, look, env, unit)
        if (look.moonAlpha > 0.01f) drawMoon(canvas, look, env)
        if (look.sunAlpha > 0.01f) {
            val sunY = skyY(env.sun.altitudeDeg)
            val r = SUN_RADIUS * w
            canvas.save()
            canvas.translate(sunX, sunY)
            canvas.drawCircle(0f, 0f, w * 0.55f, sunGlowFill!!)
            canvas.drawCircle(0f, 0f, r * 1.35f, withAlpha(look.sunColor, 0.25f * look.sunAlpha))
            canvas.drawCircle(0f, 0f, r, withAlpha(look.sunColor, look.sunAlpha))
            // Miolo quase branco: mantém o Sol mais claro que o céu alaranjado no nascer e no pôr do sol.
            canvas.drawCircle(0f, 0f, r * 0.72f, withAlpha(SUN_CORE, 0.85f * look.sunAlpha))
            canvas.restore()
        }

        if (look.overcastVeil > 0.01f) canvas.drawRect(0f, 0f, w, h * 0.74f, veilFill!!)
        drawClouds(canvas, look)
        val strikeAge = (time - strikeAt).toFloat()
        if (strikeAge < 0.3f && boltPointCount > 1) drawBolt(canvas, unit, 1f - strikeAge / 0.3f)

        for (layer in 0..2) {
            canvas.save()
            canvas.translate((0.5f - parallax) * w * LAYER_PARALLAX[layer], 0f)
            canvas.drawPath(hillPaths[layer]!!, hillFills[layer]!!)
            for (i in turbineX.indices) if (turbineLayer[i] == layer) drawTurbine(canvas, look, i)
            canvas.restore()
        }

        if (look.fog > 0.01f) canvas.drawRect(0f, h * 0.38f, w, h, fogFill!!)
        if (activeDrops >= 1f) drawRain(canvas, look, unit)
        if (strikeAge < 0.7f) {
            val flash = flashIntensity(strikeAge)
            if (flash > 0.01f) canvas.drawRect(0f, 0f, w, h, withAlpha(rgb(0xE8EEFF), flash * 0.45f))
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun skyX(hourAngleDeg: Double): Float = width * (0.5f + (hourAngleDeg / 180.0).toFloat() * 0.8f)

    private fun skyY(altitudeDeg: Double): Float {
        val horizonY = height * HORIZON
        return horizonY - (altitudeDeg / 90.0).toFloat() * (horizonY - height * 0.07f)
    }

    private fun layerColor(look: Look, layer: Int) = when (layer) {
        0 -> look.farHill
        1 -> look.midHill
        else -> look.nearHill
    }

    private fun drawEllipse(canvas: VectorCanvas, cx: Float, cy: Float, squash: Float, fill: Fill) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(1f, squash)
        canvas.drawCircle(0f, 0f, width, fill)
        canvas.restore()
    }

    private fun drawStars(canvas: VectorCanvas, look: Look, env: Environment, unit: Float) {
        // O céu gira com a hora sideral: as estrelas atravessam a tela de leste (esquerda) para oeste.
        val drift = (env.siderealDeg / 360.0 * 2.0).toFloat()
        for (i in 0 until STAR_COUNT) {
            val twinkle = 0.75f + 0.25f * sin(time.toFloat() * starSpeed[i] + starPhase[i])
            val alpha = look.starAlpha * starBrightness[i] * twinkle
            if (alpha < 0.03f) continue
            var x = (starX[i] + drift) % 1f
            if (x < 0) x += 1f
            canvas.drawCircle(x * width, starY[i] * height, starRadius[i] * unit, withAlpha(starColor[i], alpha))
        }
    }

    private fun drawMoon(canvas: VectorCanvas, look: Look, env: Environment) {
        val moon = env.moon
        val x = skyX(moon.hourAngleDeg)
        val y = skyY(moon.altitudeDeg)
        val r = MOON_RADIUS * width
        // O lado claro aponta para o Sol, projetado do mesmo jeito que as posições na tela.
        val towardSunX = moon.litSideHourAngleStep * width * 0.8f / 180f
        val towardSunY = -moon.litSideAltitudeStep * (height * HORIZON - height * 0.07f) / 90f
        val rotation = Math.toDegrees(atan2(towardSunY, towardSunX)).toFloat()
        canvas.save()
        canvas.translate(x, y)
        if (look.moonGlowAlpha > 0.01f) canvas.drawCircle(0f, 0f, width * 0.24f, moonGlowFill!!)
        if (look.moonEarthshineAlpha > 0.01f) canvas.drawCircle(0f, 0f, r, withAlpha(rgb(0x5A6680), look.moonEarthshineAlpha))
        canvas.rotate(rotation)
        canvas.scale(r, r)
        canvas.drawPath(moonLitPath, withAlpha(look.moonColor, look.moonAlpha))
        canvas.restore()
    }

    private fun drawClouds(canvas: VectorCanvas, look: Look) {
        val fill = cloudFill ?: return
        val shift = (0.5f - parallax) * width * 0.015f
        for (cloud in clouds) {
            if (cloud.alpha < 0.01f) continue
            val cloudWidth = cloud.widthFraction * width * (if (look.cloudCover > 0.85f) 1.25f else 1f)
            canvas.save()
            canvas.translate(cloud.x * width + shift, cloud.y * height)
            canvas.scale(cloudWidth, if (cloud.high) cloudWidth * 0.45f else cloudWidth)
            canvas.drawPath(cloud.path, fill, cloud.alpha * (if (cloud.high) 0.6f else 1f))
            canvas.restore()
        }
    }

    private fun drawTurbine(canvas: VectorCanvas, look: Look, i: Int) {
        val layer = turbineLayer[i]
        val size = turbineSize[i] * height
        val baseX = turbineX[i] * width
        val baseY = hillY(layer, turbineX[i]) + height * 0.004f
        val light = look.turbineLight[layer]
        canvas.save()
        canvas.translate(baseX, baseY)
        canvas.scale(size, size)
        canvas.drawPath(towerPath, towerFills[layer]!!)
        canvas.drawPath(nacellePath, look.turbineShade[layer])
        canvas.translate(0f, -1f)
        for (blade in 0..2) {
            canvas.save()
            canvas.rotate(turbineAngle[i] + blade * 120f)
            canvas.drawPath(bladePath, light)
            canvas.restore()
        }
        canvas.drawCircle(0f, 0f, 0.026f, light)
        // Balizas vermelhas de aviação piscam juntas (1 s acesa, 1 s apagada), só à noite.
        if (look.beaconsOn && layer > 0 && (time % 2.0) < 1.0) {
            val glow = (if (layer == 2) 16f else 10f) * width / 1264f / size
            canvas.translate(0f, -0.04f)
            canvas.save()
            canvas.scale(glow, glow)
            canvas.drawCircle(0f, 0f, 1f, beaconGlow)
            canvas.restore()
            canvas.drawCircle(0f, 0f, glow * 0.22f, BEACON_RED)
        }
        canvas.restore()
    }

    private fun drawRain(canvas: VectorCanvas, look: Look, unit: Float) {
        val slant = (look.windKmh / 45f).coerceAtMost(0.45f) * look.windScreenDirection
        val far = withAlpha(look.rainColor, Colors.alpha(look.rainColor) / 255f * 0.6f)
        for (i in 0 until activeDrops.toInt()) {
            val length = dropLength[i]
            val x = dropX[i]
            val y = dropY[i]
            if (dropNear[i]) {
                canvas.drawLine(x, y, x - slant * length, y - length, look.rainColor, unit * 0.6f)
            } else {
                canvas.drawLine(x, y, x - slant * length, y - length, far, unit * 0.35f)
            }
        }
    }

    private fun drawBolt(canvas: VectorCanvas, unit: Float, alpha: Float) {
        val glow = withAlpha(rgb(0xBFD0FF), 0.35f * alpha)
        val core = withAlpha(rgb(0xFFFFFF), alpha)
        for (pass in 0..1) {
            for (p in 0 until boltPointCount - 1) {
                val x0 = bolt[p * 2]
                val y0 = bolt[p * 2 + 1]
                val x1 = bolt[p * 2 + 2]
                val y1 = bolt[p * 2 + 3]
                if (x1.isNaN() || x0.isNaN()) continue
                if (pass == 0) canvas.drawLine(x0, y0, x1, y1, glow, unit * 2.4f)
                else canvas.drawLine(x0, y0, x1, y1, core, unit * 0.7f)
            }
        }
    }

    private fun flashIntensity(age: Float): Float = when {
        age < 0.07f -> 1f
        age < 0.15f -> 0.3f
        age < 0.24f -> 0.8f
        else -> 0.8f * exp(-(age - 0.24f) * 9f)
    }

    // ---- Construção da geometria ----------------------------------------------------------------

    private fun hillY(layer: Int, xFraction: Float): Float {
        val f = hillFrequency[layer]
        val p = hillPhase[layer]
        val wave = 0.6f * sin(xFraction * f[0] * 3.1416f + p[0]) + 0.4f * sin(xFraction * f[1] * 3.1416f + p[1])
        return height * (hillBase[layer] + hillAmplitude[layer] * wave)
    }

    private fun buildHill(layer: Int): VPath {
        val path = VPath()
        val steps = 72
        for (s in 0..steps) {
            val xf = -0.08f + 1.16f * s / steps
            val y = hillY(layer, xf)
            if (s == 0) path.moveTo(xf * width, y) else path.lineTo(xf * width, y)
        }
        path.lineTo(1.08f * width, height + 2f)
        path.lineTo(-0.08f * width, height + 2f)
        return path.close()
    }

    /** Nuvem em unidades de largura 1, com a base em y = 0: vários "pufes" unidos num só caminho. */
    private fun cloudShape(random: Random): VPath {
        val path = VPath().addRect(0.1f, -0.09f, 0.9f, 0f)
        path.addCircle(0.1f, -0.045f, 0.045f)
        path.addCircle(0.9f, -0.045f, 0.045f)
        val puffs = 4 + random.nextInt(4)
        for (k in 0 until puffs) {
            val cx = 0.16f + 0.68f * (k + 0.5f) / puffs + (random.nextFloat() - 0.5f) * 0.06f
            val middle = 1f - abs(cx - 0.5f) * 1.4f
            val r = (0.08f + random.nextFloat() * 0.07f) * (0.7f + middle * 0.5f)
            path.addCircle(cx, -r * 0.75f, r)
        }
        return path
    }

    /** Parte iluminada da Lua num disco de raio 1, com o lado claro voltado para +x. */
    private fun buildMoonLitPath(fraction: Float) {
        val k = VPath.KAPPA
        // Terminador: meia elipse que passa por x = 1 - 2f (nova: some na borda; cheia: disco inteiro).
        val xt = 1f - 2f * fraction.coerceIn(0f, 1f)
        moonLitPath = VPath()
            .moveTo(0f, -1f)
            .cubicTo(k, -1f, 1f, -k, 1f, 0f)
            .cubicTo(1f, k, k, 1f, 0f, 1f)
            .cubicTo(xt * k, 1f, xt, k, xt, 0f)
            .cubicTo(xt, -k, xt * k, -1f, 0f, -1f)
            .close()
    }

    private fun buildBolt() {
        val startX = width * (0.15f + events.nextFloat() * 0.7f)
        var x = startX
        var y = height * (0.22f + events.nextFloat() * 0.08f)
        val endY = height * (HORIZON + 0.01f)
        val stepY = (endY - y) / (BOLT_POINTS - 1)
        for (p in 0 until BOLT_POINTS) {
            bolt[p * 2] = x
            bolt[p * 2 + 1] = y
            x += (events.nextFloat() - 0.5f) * width * 0.06f
            y += stepY * (0.7f + events.nextFloat() * 0.6f)
        }
        boltPointCount = BOLT_POINTS
    }

    private fun resetDrop(i: Int, y: Float, random: Random) {
        dropNear[i] = random.nextFloat() < 0.45f
        dropX[i] = random.nextFloat() * width * 1.3f - width * 0.15f
        dropY[i] = y
        dropLength[i] = height * (if (dropNear[i]) 0.035f else 0.022f) * (0.8f + random.nextFloat() * 0.4f)
        dropSpeed[i] = height * (if (dropNear[i]) 1.35f else 0.95f) * (0.85f + random.nextFloat() * 0.3f)
    }

    private fun targetDrops(): Float {
        val look = look ?: return 0f
        return (look.rain * 380f * width / 1264f).coerceAtMost(MAX_DROPS.toFloat())
    }

    private fun rebuildFills() {
        val look = look ?: return
        val env = environment ?: return
        if (width <= 0f) return
        val w = width
        val h = height
        val horizonY = h * HORIZON

        skyFill = Fill.Linear(
            0f, 0f, 0f, horizonY + h * 0.02f,
            intArrayOf(look.skyTop, look.skyMid, look.skyHorizon),
            floatArrayOf(0f, 0.55f, 1f),
        )
        horizonGlowFill = radial(w, look.horizonGlowColor, look.horizonGlow * 0.85f, 0.35f)
        cityGlowFill = radial(w * 0.75f, rgb(0xFF9A52), look.cityGlow, 0.3f)
        sunGlowFill = radial(w * 0.55f, look.sunColor, 0.5f * look.sunAlpha, 0.2f)
        moonGlowFill = radial(w * 0.24f, look.moonColor, look.moonGlowAlpha, 0.25f)
        val veilColor = Colors.lerp(look.cloudTop, look.cloudBottom, 0.5f)
        veilFill = Fill.Linear(
            0f, 0f, 0f, h * 0.74f,
            intArrayOf(withAlpha(veilColor, look.overcastVeil), withAlpha(veilColor, look.overcastVeil * 0.6f), withAlpha(veilColor, 0f)),
            floatArrayOf(0f, 0.6f, 1f),
        )
        cloudFill = Fill.Linear(
            0f, -0.36f, 0f, 0f,
            intArrayOf(withAlpha(look.cloudTop, 0.96f), withAlpha(look.cloudBottom, 0.96f)),
            floatArrayOf(0f, 1f),
        )
        val fogTop = h * 0.38f
        fogFill = Fill.Linear(
            0f, fogTop, 0f, h,
            intArrayOf(withAlpha(look.fogColor, 0f), withAlpha(look.fogColor, 0.78f * look.fog), withAlpha(look.fogColor, 0.5f * look.fog)),
            floatArrayOf(0f, (h * 0.72f - fogTop) / (h - fogTop), 1f),
        )
        // Morros mais escuros na base: dá volume e deixa a área dos ícones do launcher mais calma.
        for (layer in 0..2) {
            val color = layerColor(look, layer)
            hillFills[layer] = Fill.Linear(
                0f, h * (hillBase[layer] - hillAmplitude[layer]), 0f, h,
                intArrayOf(color, Colors.scale(color, 0.7f)),
                floatArrayOf(0f, 1f),
            )
        }
        // Lado iluminado das torres segue o Sol (manhã: leste/esquerda) ou a Lua à noite.
        val lightHourAngle = if (look.daylight > 0.2f) env.sun.hourAngleDeg else env.moon.hourAngleDeg
        val lightFromLeft = lightHourAngle < 0
        for (layer in 0..2) {
            val lit = look.turbineLight[layer]
            val shade = look.turbineShade[layer]
            towerFills[layer] = Fill.Linear(
                -0.022f, 0f, 0.022f, 0f,
                if (lightFromLeft) intArrayOf(lit, lit, shade) else intArrayOf(shade, lit, lit),
                floatArrayOf(0f, 0.5f, 1f),
            )
        }
    }

    private fun radial(radius: Float, color: Int, alpha: Float, middle: Float) = Fill.Radial(
        0f, 0f, radius,
        intArrayOf(withAlpha(color, alpha), withAlpha(color, alpha * 0.4f), withAlpha(color, 0f)),
        floatArrayOf(0f, middle, 1f),
    )

    private fun approach(value: Float, target: Float, maxDelta: Float): Float = when {
        value < target -> minOf(target, value + maxDelta)
        value > target -> maxOf(target, value - maxDelta)
        else -> value
    }

    companion object {
        /** Linha do horizonte, em fração da altura (referência para Sol, Lua e relevo). */
        const val HORIZON = 0.70f
        private const val SUN_RADIUS = 0.042f
        private const val MOON_RADIUS = 0.032f
        private const val STAR_COUNT = 170
        private const val CLOUD_POOL = 14
        private const val MAX_DROPS = 420
        private const val BOLT_POINTS = 12
        private val LAYER_PARALLAX = floatArrayOf(0.01f, 0.025f, 0.05f)
        private val BEACON_RED = rgb(0xFF2A2A)
        private val SUN_CORE = rgb(0xFFF8E8)
    }
}
