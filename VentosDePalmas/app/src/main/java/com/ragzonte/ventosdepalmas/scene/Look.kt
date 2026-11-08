package com.ragzonte.ventosdepalmas.scene

import com.ragzonte.ventosdepalmas.astro.Astronomy
import com.ragzonte.ventosdepalmas.astro.MoonPosition
import com.ragzonte.ventosdepalmas.astro.SunPosition
import com.ragzonte.ventosdepalmas.scene.Colors.lerp
import com.ragzonte.ventosdepalmas.scene.Colors.rgb
import com.ragzonte.ventosdepalmas.weather.Palmas
import com.ragzonte.ventosdepalmas.weather.WeatherConditions
import java.time.Instant
import kotlin.math.max
import kotlin.math.sin

/** Momento + céu + tempo em Palmas: tudo que define a aparência da cena. */
class Environment(
    val instant: Instant,
    val sun: SunPosition,
    val moon: MoonPosition,
    val siderealDeg: Double,
    val weather: WeatherConditions,
    /** Mês em Palmas (1 a 12), para a cor do cerrado. */
    val month: Int,
) {
    companion object {
        fun at(instant: Instant, weather: WeatherConditions): Environment = Environment(
            instant = instant,
            sun = Astronomy.sun(instant, Palmas.LATITUDE, Palmas.LONGITUDE),
            moon = Astronomy.moon(instant, Palmas.LATITUDE, Palmas.LONGITUDE),
            siderealDeg = Astronomy.localSiderealDeg(instant, Palmas.LONGITUDE),
            weather = weather,
            month = instant.atZone(Palmas.ZONE).monthValue,
        )
    }
}

/** Cores e intensidades derivadas do [Environment]. Recalculado só quando o ambiente muda (≈ 1 vez por minuto). */
class Look(
    val skyTop: Int,
    val skyMid: Int,
    val skyHorizon: Int,
    /** Claridade do dia: 0 noite, 1 dia pleno. */
    val daylight: Float,
    /** Luz sobre a paisagem (inclui luar e escurecimento por nuvens). */
    val light: Float,
    val starAlpha: Float,
    val sunAlpha: Float,
    val sunColor: Int,
    val horizonGlow: Float,
    val horizonGlowColor: Int,
    val moonAlpha: Float,
    val moonColor: Int,
    val moonEarthshineAlpha: Float,
    val moonGlowAlpha: Float,
    val cloudCover: Float,
    val cloudTop: Int,
    val cloudBottom: Int,
    val overcastVeil: Float,
    val rain: Float,
    val rainColor: Int,
    val thunderstorm: Boolean,
    val fog: Float,
    val fogColor: Int,
    val farHill: Int,
    val midHill: Int,
    val nearHill: Int,
    /** Lado iluminado e lado em sombra das torres, por camada (longe, meio, perto). */
    val turbineLight: IntArray,
    val turbineShade: IntArray,
    val beaconsOn: Boolean,
    val cityGlow: Float,
    /** Vento em km/h e sentido na tela (+1 = sopra para a direita). */
    val windKmh: Float,
    val windScreenDirection: Float,
) {
    companion object {
        fun from(env: Environment): Look {
            val sunAlt = env.sun.altitudeDeg.toFloat()
            val weather = env.weather
            val cover = weather.cloudCover.coerceIn(0f, 1f)
            val rain = weather.precipitation.intensity
            val fog = if (weather.fog) 1f else 0f
            val storm = weather.thunderstorm

            val daylight = smoothstep(-6f, 10f, sunAlt)
            // Quanto o céu fica cinza: nuvens fechadas, chuva ou tempestade.
            val gloom = maxOf(smoothstep(0.45f, 1f, cover) * 0.9f, rain, if (storm) 1f else 0f, fog * 0.8f)

            val clear = clearSky(sunAlt)
            val grey = overcastSky(daylight, rain)
            val skyTop = lerp(clear[0], grey[0], gloom)
            val skyMid = lerp(clear[1], grey[1], gloom)
            val skyHorizon = lerp(clear[2], grey[2], gloom)

            val moon = env.moon
            val moonUp = smoothstep(-1f, 1.5f, moon.altitudeDeg.toFloat())
            val moonFraction = moon.illuminatedFraction.toFloat()
            val hiddenByClouds = 1f - smoothstep(0.6f, 0.97f, cover)
            val night = 1f - daylight
            val moonAlpha = if (moonFraction < 0.03f) {
                0f
            } else {
                moonUp * lerp(0.55f, 1f, night) * hiddenByClouds * (1f - rain * 0.8f) * (1f - fog * 0.6f)
            }
            val moonlight = night * moonUp * moonFraction * (1f - cover * 0.8f) * 0.10f
            val light = (daylight * (1f - 0.35f * gloom) + moonlight).coerceIn(0f, 1f)

            val starAlpha = smoothstep(-4f, -13f, sunAlt) *
                (1f - cover * 0.95f).coerceAtLeast(0f) *
                (1f - fog * 0.9f) *
                (1f - 0.3f * moonFraction * moonUp)

            val sunAlpha = smoothstep(-1.5f, 0.5f, sunAlt) *
                (1f - smoothstep(0.55f, 0.95f, cover)) * (1f - rain * 0.85f) * (1f - fog * 0.7f)
            val sunColor = lerp(rgb(0xFFA040), rgb(0xFFF6DC), smoothstep(0f, 25f, sunAlt))

            val horizonGlow = smoothstep(-11f, -2f, sunAlt) * (1f - smoothstep(4f, 15f, sunAlt)) * (1f - gloom * 0.75f)
            val horizonGlowColor = lerp(rgb(0xFF5E3A), rgb(0xFFB066), smoothstep(-6f, 5f, sunAlt))

            // Nuvens: brancas de dia, cinza com chuva, azul-escuras à noite, alaranjadas no pôr do sol.
            val dayCloudTop = lerp(lerp(rgb(0xFFFFFF), rgb(0xA4ADB6), gloom), rgb(0x6F7882), rain)
            val dayCloudBottom = lerp(lerp(rgb(0xD5DEE7), rgb(0x7D868F), gloom), rgb(0x4A525B), rain)
            val nightCloudTop = lerp(rgb(0x1E2533), rgb(0x3F4A5F), moonlight * 8f)
            val nightCloudBottom = lerp(rgb(0x121722), rgb(0x2E2622), gloom * 0.5f)
            val cloudTop = lerp(lerp(nightCloudTop, dayCloudTop, daylight), rgb(0xFFC9A8), horizonGlow * 0.3f)
            val cloudBottom = lerp(lerp(nightCloudBottom, dayCloudBottom, daylight), horizonGlowColor, horizonGlow * 0.6f)

            val dryness = DRYNESS[(env.month - 1).coerceIn(0, 11)]
            fun ground(green: Int, dry: Int, nightColor: Int): Int {
                val day = Colors.scale(lerp(green, dry, dryness), 1f - 0.3f * gloom)
                return lerp(nightColor, day, light)
            }
            val farHaze = 0.42f + 0.3f * fog
            val midHaze = 0.2f + 0.3f * fog
            val nearHaze = 0.04f + 0.2f * fog

            val turbineDayLight = lerp(rgb(0xF2F5F8), rgb(0xFFD2A8), horizonGlow * 0.5f)
            val turbineDayShade = lerp(rgb(0xB4BEC8), rgb(0xA7826C), horizonGlow * 0.5f)
            val turbineNight = lerp(rgb(0x161C28), rgb(0x2B3446), moonlight * 8f)
            val turbineLightBase = lerp(turbineNight, Colors.scale(turbineDayLight, 1f - 0.3f * gloom), light)
            val turbineShadeBase = lerp(Colors.scale(turbineNight, 0.8f), Colors.scale(turbineDayShade, 1f - 0.3f * gloom), light)
            val hazes = floatArrayOf(farHaze, midHaze, nearHaze)

            // O vento vem de windDirectionDeg; leste fica à esquerda da tela e oeste à direita,
            // então vento leste (90°) empurra nuvens e chuva para a direita.
            val windFrom = Math.toRadians(weather.windDirectionDeg)
            val windScreenDirection = sin(windFrom).toFloat().let { if (it == 0f) 1f else it }

            return Look(
                skyTop = skyTop,
                skyMid = skyMid,
                skyHorizon = skyHorizon,
                daylight = daylight,
                light = light,
                starAlpha = starAlpha,
                sunAlpha = sunAlpha,
                sunColor = sunColor,
                horizonGlow = horizonGlow,
                horizonGlowColor = horizonGlowColor,
                moonAlpha = moonAlpha,
                moonColor = lerp(rgb(0xF5F2E6), rgb(0xF4F7FB), daylight),
                moonEarthshineAlpha = moonAlpha * night * 0.16f,
                moonGlowAlpha = moonAlpha * night * moonFraction * 0.4f,
                cloudCover = cover,
                cloudTop = cloudTop,
                cloudBottom = cloudBottom,
                overcastVeil = smoothstep(0.7f, 1f, cover) * 0.85f,
                rain = rain,
                rainColor = lerp(Colors.withAlpha(rgb(0x8FA0B8), 0.4f), Colors.withAlpha(rgb(0xE4ECF3), 0.5f), daylight),
                thunderstorm = storm,
                fog = fog,
                fogColor = lerp(rgb(0x1F2633), rgb(0xD3D9DE), daylight),
                farHill = lerp(ground(rgb(0x6E9460), rgb(0xA89A70), rgb(0x0E1420)), skyHorizon, farHaze),
                midHill = lerp(ground(rgb(0x4E7D3C), rgb(0x8C7A48), rgb(0x090D15)), skyHorizon, midHaze),
                nearHill = lerp(ground(rgb(0x35602C), rgb(0x6E5E33), rgb(0x05080C)), skyHorizon, nearHaze),
                turbineLight = IntArray(3) { lerp(turbineLightBase, skyHorizon, hazes[it]) },
                turbineShade = IntArray(3) { lerp(turbineShadeBase, skyHorizon, hazes[it]) },
                beaconsOn = light < 0.3f,
                cityGlow = night * (0.12f + 0.25f * max(gloom, fog)),
                windKmh = weather.windSpeedKmh.toFloat(),
                windScreenDirection = windScreenDirection,
            )
        }

        /** Seca do cerrado por mês: 0 = verde (chuvas, nov–abr), 1 = dourado (seca, jun–set). */
        private val DRYNESS = floatArrayOf(0f, 0f, 0f, 0.1f, 0.4f, 0.75f, 1f, 1f, 0.9f, 0.6f, 0.25f, 0.05f)

        /** Céu limpo por altura do Sol (graus): cores de topo, meio e horizonte em cada ponto. */
        private val SKY_ALTITUDES = floatArrayOf(-18f, -12f, -6f, -2f, 1f, 6f, 15f, 40f)
        private val SKY_COLORS = arrayOf(
            intArrayOf(rgb(0x04060E), rgb(0x080D1D), rgb(0x0F1630)),
            intArrayOf(rgb(0x060A1A), rgb(0x0F1834), rgb(0x1C2648)),
            intArrayOf(rgb(0x0E1938), rgb(0x283766), rgb(0x6A5778)),
            intArrayOf(rgb(0x1C3062), rgb(0x5468A2), rgb(0xE5865A)),
            intArrayOf(rgb(0x2A4882), rgb(0x7896C8), rgb(0xF6A55E)),
            intArrayOf(rgb(0x3767AC), rgb(0x86B1DE), rgb(0xF3D3A2)),
            intArrayOf(rgb(0x2D72C6), rgb(0x6EA8E4), rgb(0xBFDCF3)),
            intArrayOf(rgb(0x1E69CC), rgb(0x58A0EA), rgb(0xA8D2F4)),
        )

        internal fun clearSky(sunAlt: Float): IntArray {
            if (sunAlt <= SKY_ALTITUDES.first()) return SKY_COLORS.first()
            if (sunAlt >= SKY_ALTITUDES.last()) return SKY_COLORS.last()
            val upper = SKY_ALTITUDES.indexOfFirst { it >= sunAlt }
            val t = (sunAlt - SKY_ALTITUDES[upper - 1]) / (SKY_ALTITUDES[upper] - SKY_ALTITUDES[upper - 1])
            return IntArray(3) { lerp(SKY_COLORS[upper - 1][it], SKY_COLORS[upper][it], t) }
        }

        private fun overcastSky(daylight: Float, rain: Float): IntArray {
            val night = intArrayOf(rgb(0x0B0F17), rgb(0x131923), rgb(0x2A2830))
            val day = intArrayOf(rgb(0x7C8894), rgb(0xA0AAB4), rgb(0xBAC1C8))
            val rainy = intArrayOf(rgb(0x4B555F), rgb(0x67717B), rgb(0x838C95))
            return IntArray(3) { lerp(night[it], lerp(day[it], rainy[it], rain), daylight) }
        }
    }
}
