package com.ragzonte.ventosdepalmas.scene

import com.ragzonte.ventosdepalmas.astro.MoonPosition
import com.ragzonte.ventosdepalmas.astro.SunPosition
import com.ragzonte.ventosdepalmas.weather.WeatherConditions
import com.ragzonte.ventosdepalmas.weather.WeatherPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

class LookTest {

    private fun env(
        sunAlt: Double,
        weather: WeatherConditions = WeatherPreset.CLEAR.conditions,
        moonAlt: Double = -30.0,
        moonFraction: Double = 0.5,
        month: Int = 1,
        latitude: Double = -10.18,
    ) = Environment(
        instant = Instant.EPOCH,
        sun = SunPosition(sunAlt, 90.0, if (sunAlt > 0) -30.0 else 120.0),
        moon = MoonPosition(moonAlt, 270.0, 30.0, moonFraction, 0.25, 0.5, -0.8),
        siderealDeg = 0.0,
        weather = weather,
        month = month,
        latitude = latitude,
    )

    private fun look(
        sunAlt: Double,
        weather: WeatherConditions = WeatherPreset.CLEAR.conditions,
        moonAlt: Double = -30.0,
        moonFraction: Double = 0.5,
        month: Int = 1,
        latitude: Double = -10.18,
    ) = Look.from(env(sunAlt, weather, moonAlt, moonFraction, month, latitude))

    private fun isGreyish(color: Int) = abs(Colors.blue(color) - Colors.red(color)) < 45

    @Test
    fun `meio-dia limpo`() {
        val noon = look(80.0)
        assertEquals(1f, noon.daylight, 0.001f)
        assertEquals(0f, noon.starAlpha, 0.001f)
        assertTrue(noon.sunAlpha > 0.9f)
        assertFalse(noon.beaconsOn)
        assertTrue("céu azul", Colors.blue(noon.skyTop) > Colors.red(noon.skyTop) + 80)
    }

    @Test
    fun `noite limpa tem estrelas e balizas acesas`() {
        val night = look(-40.0)
        assertEquals(0f, night.daylight, 0.001f)
        assertTrue(night.starAlpha > 0.8f)
        assertEquals(0f, night.sunAlpha, 0.001f)
        assertTrue(night.beaconsOn)
        assertTrue("céu noturno escuro (bom para OLED)", Colors.red(night.skyTop) + Colors.green(night.skyTop) + Colors.blue(night.skyTop) < 40)
    }

    @Test
    fun `lua acima do horizonte aparece e apaga um pouco as estrelas`() {
        val withMoon = look(-40.0, moonAlt = 40.0, moonFraction = 1.0)
        val withoutMoon = look(-40.0, moonAlt = -20.0, moonFraction = 1.0)
        assertTrue(withMoon.moonAlpha > 0.9f)
        assertEquals(0f, withoutMoon.moonAlpha, 0.001f)
        assertTrue(withMoon.starAlpha < withoutMoon.starAlpha)
        assertTrue("luar ilumina a paisagem", withMoon.light > withoutMoon.light)
        assertEquals("lua nova não aparece", 0f, look(-40.0, moonAlt = 40.0, moonFraction = 0.01).moonAlpha, 0.001f)
    }

    @Test
    fun `por do sol acende o brilho no horizonte`() {
        val sunset = look(-2.0)
        assertTrue(sunset.horizonGlow > 0.5f)
        assertTrue("horizonte alaranjado", Colors.red(sunset.skyHorizon) > Colors.blue(sunset.skyHorizon) + 60)
        assertEquals(0f, look(60.0).horizonGlow, 0.001f)
    }

    @Test
    fun `nublado esconde o Sol e acinzenta o ceu`() {
        val overcast = look(60.0, WeatherPreset.OVERCAST.conditions)
        assertTrue(overcast.sunAlpha < 0.15f)
        assertTrue(isGreyish(overcast.skyTop))
        assertTrue(overcast.overcastVeil > 0.5f)
        assertEquals(0f, overcast.rain, 0.001f)
    }

    @Test
    fun `chuva e tempestade`() {
        val rain = look(30.0, WeatherPreset.RAIN.conditions)
        assertEquals(0.7f, rain.rain, 0.001f)
        assertFalse(rain.thunderstorm)
        val storm = look(30.0, WeatherPreset.STORM.conditions)
        assertEquals(1f, storm.rain, 0.001f)
        assertTrue(storm.thunderstorm)
        assertTrue("tempestade escurece a paisagem", storm.light < look(30.0).light)
    }

    @Test
    fun `nuvens deixam de mostrar estrelas`() {
        assertTrue(look(-40.0, WeatherPreset.OVERCAST.conditions).starAlpha < 0.1f)
    }

    @Test
    fun `cerrado verde nas chuvas e dourado na seca`() {
        val january = look(60.0, month = 1).nearHill
        val august = look(60.0, month = 8).nearHill
        assertTrue("janeiro mais verde", Colors.green(january) > Colors.red(january))
        assertTrue("agosto mais dourado", Colors.red(august) > Colors.red(january) + 30)
    }

    @Test
    fun `estacao seca depende da latitude`() {
        assertEquals("cerrado (TO) em agosto", 1f, Look.dryness(8, -10.18), 0.001f)
        assertTrue("Paraná em agosto quase verde", Look.dryness(8, -26.48) < 0.4f)
        assertEquals("janeiro verde nos dois", 0f, Look.dryness(1, -26.48), 0.001f)
        assertEquals("hemisfério norte inverte: fevereiro lá é agosto aqui", Look.dryness(8, -10.0), Look.dryness(2, 10.0), 0.001f)
        val parana = look(60.0, month = 8, latitude = -26.48).nearHill
        val tocantins = look(60.0, month = 8, latitude = -10.18).nearHill
        assertTrue("em agosto o Paraná fica mais verde que o Tocantins", Colors.green(parana) - Colors.red(parana) > Colors.green(tocantins) - Colors.red(tocantins))
    }

    @Test
    fun `vento leste empurra para a direita`() {
        val east = WeatherPreset.CLEAR.conditions.copy(windDirectionDeg = 90.0)
        val west = WeatherPreset.CLEAR.conditions.copy(windDirectionDeg = 270.0)
        assertEquals(1f, look(60.0, east).windScreenDirection, 0.01f)
        assertEquals(-1f, look(60.0, west).windScreenDirection, 0.01f)
    }

    @Test
    fun `ceu limpo interpola entre os pontos`() {
        val deepNight = Look.clearSky(-60f)
        val day = Look.clearSky(70f)
        assertTrue(Colors.blue(day[0]) > Colors.blue(deepNight[0]))
        val between = Look.clearSky(3.5f)
        assertTrue(Colors.red(between[2]) in 0xF3..0xF6)
    }
}
