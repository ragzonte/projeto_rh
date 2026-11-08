package com.ragzonte.ventosdepalmas.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WeatherTest {

    private fun snapshot(code: Int, cover: Int = 50, mm: Double = 0.0) = WeatherSnapshot(
        fetchedAtMillis = 0,
        weatherCode = code,
        cloudCoverPercent = cover,
        precipitationMm = mm,
        windSpeedKmh = 12.0,
        windDirectionDeg = 90.0,
        temperatureC = 30.0,
    )

    @Test
    fun `codigos de chuva viram intensidade`() {
        assertEquals(Precipitation.DRIZZLE, WeatherConditions.from(snapshot(53)).precipitation)
        assertEquals(Precipitation.LIGHT, WeatherConditions.from(snapshot(61)).precipitation)
        assertEquals(Precipitation.MODERATE, WeatherConditions.from(snapshot(81)).precipitation)
        assertEquals(Precipitation.HEAVY, WeatherConditions.from(snapshot(65)).precipitation)
        assertEquals(Precipitation.NONE, WeatherConditions.from(snapshot(2)).precipitation)
    }

    @Test
    fun `trovoada e neblina`() {
        val storm = WeatherConditions.from(snapshot(95))
        assertTrue(storm.thunderstorm)
        assertEquals(Precipitation.HEAVY, storm.precipitation)
        assertTrue(WeatherConditions.from(snapshot(45)).fog)
        assertFalse(WeatherConditions.from(snapshot(3)).fog)
    }

    @Test
    fun `chuva medida sobe a intensidade mesmo sem codigo de chuva`() {
        assertEquals(Precipitation.MODERATE, WeatherConditions.from(snapshot(2, mm = 1.0)).precipitation)
        assertEquals(Precipitation.HEAVY, WeatherConditions.from(snapshot(61, mm = 3.0)).precipitation)
        assertEquals(Precipitation.NONE, WeatherConditions.precipitationFromMillimeters(0.05))
        assertEquals(Precipitation.DRIZZLE, WeatherConditions.precipitationFromMillimeters(0.2))
    }

    @Test
    fun `cobertura minima coerente com o codigo`() {
        assertEquals(0.85f, WeatherConditions.from(snapshot(3, cover = 40)).cloudCover, 0.001f)
        assertEquals(0.8f, WeatherConditions.from(snapshot(63, cover = 10)).cloudCover, 0.001f)
        assertEquals(0.05f, WeatherConditions.from(snapshot(0, cover = 5)).cloudCover, 0.001f)
        assertEquals(1f, WeatherConditions.from(snapshot(0, cover = 140)).cloudCover, 0.001f)
    }

    @Test
    fun `descricoes em portugues`() {
        assertEquals("Céu limpo", Wmo.description(0))
        assertEquals("Chuva moderada", Wmo.description(63))
        assertEquals("Trovoada", Wmo.description(95))
        assertEquals("Tempo indefinido", Wmo.description(42))
    }

    @Test
    fun `le a resposta da Open-Meteo`() {
        val json = """
            {"latitude":-10.25,"longitude":-48.375,"timezone":"America/Araguaina",
             "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C"},
             "current":{"time":"2026-11-08T15:00","interval":900,"temperature_2m":33.4,"precipitation":0.2,
                        "weather_code":80,"cloud_cover":72,"wind_speed_10m":9.4,"wind_direction_10m":101}}
        """.trimIndent()
        val parsed = OpenMeteo.parse(json, fetchedAtMillis = 1234L)
        assertEquals(1234L, parsed.fetchedAtMillis)
        assertEquals(80, parsed.weatherCode)
        assertEquals(72, parsed.cloudCoverPercent)
        assertEquals(0.2, parsed.precipitationMm, 1e-9)
        assertEquals(9.4, parsed.windSpeedKmh, 1e-9)
        assertEquals(101.0, parsed.windDirectionDeg, 1e-9)
        assertEquals(33.4, parsed.temperatureC!!, 1e-9)
        assertEquals("Pancadas de chuva fracas", parsed.description)
    }

    @Test
    fun `campos opcionais ausentes usam padroes`() {
        val parsed = OpenMeteo.parse("""{"current":{"weather_code":0}}""", 0L)
        assertEquals(0, parsed.cloudCoverPercent)
        assertEquals(0.0, parsed.precipitationMm, 1e-9)
        assertNull(parsed.temperatureC)
    }

    @Test(expected = IOException::class)
    fun `resposta invalida vira IOException`() {
        OpenMeteo.parse("""{"erro": true}""", 0L)
    }

    @Test
    fun `endereco da API aponta para Palmas`() {
        assertTrue(OpenMeteo.ENDPOINT.startsWith("https://api.open-meteo.com/"))
        assertTrue(OpenMeteo.ENDPOINT.contains("latitude=-10.1844"))
        assertTrue(OpenMeteo.ENDPOINT.contains("longitude=-48.3336"))
    }
}
