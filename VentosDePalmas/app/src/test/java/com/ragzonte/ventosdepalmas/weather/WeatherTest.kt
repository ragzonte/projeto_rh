package com.ragzonte.ventosdepalmas.weather

import com.ragzonte.ventosdepalmas.geo.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Locale

class WeatherTest {

    private val palmasTo = GeoLocation.PALMAS_TO
    private val palmasPr = GeoLocation(-26.4839, -51.9888, "Palmas, Paraná", "America/Sao_Paulo")

    private fun snapshot(code: Int, cover: Int = 50, mm: Double = 0.0, at: GeoLocation = palmasTo, time: Long = 0) = WeatherSnapshot(
        fetchedAtMillis = time,
        latitude = at.latitude,
        longitude = at.longitude,
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
        val parsed = OpenMeteo.parse(json, fetchedAtMillis = 1234L, location = palmasPr)
        assertEquals(1234L, parsed.fetchedAtMillis)
        assertEquals(palmasPr.latitude, parsed.latitude, 1e-9)
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
        val parsed = OpenMeteo.parse("""{"current":{"weather_code":0}}""", 0L, palmasTo)
        assertEquals(0, parsed.cloudCoverPercent)
        assertEquals(0.0, parsed.precipitationMm, 1e-9)
        assertNull(parsed.temperatureC)
    }

    @Test(expected = IOException::class)
    fun `resposta invalida vira IOException`() {
        OpenMeteo.parse("""{"erro": true}""", 0L, palmasTo)
    }

    @Test
    fun `endereco usa a localizacao com ponto decimal mesmo em portugues`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            val url = OpenMeteo.endpoint(palmasPr)
            assertTrue(url.startsWith("https://api.open-meteo.com/"))
            assertTrue(url, url.contains("latitude=-26.48&longitude=-51.99"))
            assertTrue(OpenMeteo.endpoint(palmasTo).contains("latitude=-10.18&longitude=-48.33"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `leitura vale para o mesmo lugar e por ate 3 horas`() {
        val reading = snapshot(0, at = palmasTo, time = 0L)
        val hour = 60 * 60_000L
        assertTrue(WeatherFreshness.isUsable(reading, palmasTo, 2 * hour))
        assertFalse("velha demais", WeatherFreshness.isUsable(reading, palmasTo, 4 * hour))
        assertFalse("clima do Tocantins não serve para o Paraná", WeatherFreshness.isUsable(reading, palmasPr, 0L))
    }

    @Test
    fun `busca de novo quando envelhece ou quando o lugar muda`() {
        val reading = snapshot(0, at = palmasTo, time = 0L)
        assertTrue(WeatherFreshness.needsRefresh(null, palmasTo, 0L))
        assertFalse(WeatherFreshness.needsRefresh(reading, palmasTo, 5 * 60_000L))
        assertTrue(WeatherFreshness.needsRefresh(reading, palmasTo, 21 * 60_000L))
        assertTrue(WeatherFreshness.needsRefresh(reading, palmasPr, 5 * 60_000L))
        val nearby = palmasTo.copy(latitude = palmasTo.latitude + 0.03)
        assertFalse("3 km não exige nova busca", WeatherFreshness.needsRefresh(reading, nearby, 5 * 60_000L))
    }
}
