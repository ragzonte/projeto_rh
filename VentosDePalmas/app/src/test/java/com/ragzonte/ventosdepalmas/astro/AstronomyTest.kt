package com.ragzonte.ventosdepalmas.astro

import com.ragzonte.ventosdepalmas.geo.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.min

/** Valores de referência calculados com PyEphem 4.2.1 para Palmas (TO e PR), sem refração. */
class AstronomyTest {

    private val palmasTo = GeoLocation.PALMAS_TO
    private val palmasPr = GeoLocation(-26.4839, -51.9888, "Palmas, Paraná", "America/Sao_Paulo")

    private fun sun(utc: String, at: GeoLocation = palmasTo) = Astronomy.sun(Instant.parse(utc), at.latitude, at.longitude)
    private fun moon(utc: String) = Astronomy.moon(Instant.parse(utc), palmasTo.latitude, palmasTo.longitude)

    @Test
    fun `Sol perto do zenite ao meio-dia de novembro`() {
        val s = sun("2026-11-08T15:00:00Z")
        assertEquals(83.46, s.altitudeDeg, 1.0)
        assertEquals(186.14, s.azimuthDeg, 6.0)
        assertEquals(0.0, s.hourAngleDeg, 10.0)
    }

    @Test
    fun `Sol nascendo a leste`() {
        val s = sun("2026-11-08T09:00:00Z")
        assertEquals(3.59, s.altitudeDeg, 1.0)
        assertEquals(106.25, s.azimuthDeg, 1.5)
        assertTrue("manhã fica no lado leste (ângulo horário negativo)", s.hourAngleDeg < -60)
    }

    @Test
    fun `Sol logo depois do por do sol`() {
        val s = sun("2026-11-08T21:30:00Z")
        assertEquals(-4.81, s.altitudeDeg, 1.0)
        assertEquals(251.99, s.azimuthDeg, 1.5)
        assertTrue(s.hourAngleDeg > 60)
    }

    @Test
    fun `Sol ao norte no inverno e equinocio`() {
        val winter = sun("2026-06-21T15:00:00Z")
        assertEquals(56.17, winter.altitudeDeg, 1.0)
        assertEquals(6.26, winter.azimuthDeg, 1.5)
        val equinox = sun("2026-03-20T12:00:00Z")
        assertEquals(39.07, equinox.altitudeDeg, 1.0)
        assertEquals(81.67, equinox.azimuthDeg, 1.5)
        assertEquals(-56.54, sun("2027-01-01T03:00:00Z").altitudeDeg, 1.0)
    }

    @Test
    fun `posicao e iluminacao da Lua`() {
        val nearlyFull = moon("2026-11-24T02:00:00Z")
        assertEquals(55.07, nearlyFull.altitudeDeg, 2.0)
        assertEquals(0.994, nearlyFull.illuminatedFraction, 0.02)

        val waning = moon("2026-11-30T06:00:00Z")
        assertEquals(46.14, waning.altitudeDeg, 2.0)
        assertEquals(58.34, waning.azimuthDeg, 3.0)
        assertEquals(0.612, waning.illuminatedFraction, 0.03)

        assertEquals(14.20, moon("2026-11-08T09:00:00Z").altitudeDeg, 2.0)
        assertEquals(0.010, moon("2026-11-08T09:00:00Z").illuminatedFraction, 0.02)
        assertEquals(-12.90, moon("2027-01-01T03:00:00Z").altitudeDeg, 2.0)
        assertEquals(0.366, moon("2027-01-01T03:00:00Z").illuminatedFraction, 0.03)
        assertEquals(0.471, moon("2026-06-21T15:00:00Z").illuminatedFraction, 0.03)
    }

    @Test
    fun `fases nova cheia crescente e minguante`() {
        val newMoon = moon("2026-11-09T07:02:00Z").phase
        assertTrue("lua nova perto de 0 ou 1, veio $newMoon", min(newMoon, 1 - newMoon) < 0.02)
        assertEquals(0.5, moon("2026-11-24T14:53:00Z").phase, 0.02)
        assertTrue(moon("2026-11-12T22:00:00Z").phase in 0.0..0.5)
        assertTrue(moon("2026-11-28T06:00:00Z").phase in 0.5..1.0)
    }

    /** Direção do lado iluminado como na tela: x cresce para oeste (direita), y para baixo. */
    private fun brightSide(moon: MoonPosition): Pair<Double, Double> =
        moon.litSideHourAngleStep to -moon.litSideAltitudeStep

    @Test
    fun `lua crescente ao anoitecer tem o lado claro para baixo e para oeste`() {
        val crescent = moon("2026-11-12T22:15:00Z")
        assertTrue("a lua precisa estar acima do horizonte", crescent.altitudeDeg > 5)
        val (dx, dy) = brightSide(crescent)
        assertTrue("lado claro aponta para oeste (direita), veio dx=$dx", dx > 0)
        assertTrue("lado claro aponta para baixo, veio dy=$dy", dy > 0)
    }

    @Test
    fun `lua minguante de madrugada tem o lado claro para baixo e para leste`() {
        val crescent = moon("2026-11-06T08:00:00Z")
        assertTrue(crescent.altitudeDeg > 5)
        val (dx, dy) = brightSide(crescent)
        assertTrue("lado claro aponta para leste (esquerda), veio dx=$dx", dx < 0)
        assertTrue("lado claro aponta para baixo, veio dy=$dy", dy > 0)
    }

    @Test
    fun `Palmas do Parana tem o Sol mais baixo no inverno e nasce mais tarde`() {
        val winterSunrise = sun("2026-07-15T10:30:00Z", palmasPr)
        assertEquals(2.19, winterSunrise.altitudeDeg, 1.0)
        assertEquals(64.62, winterSunrise.azimuthDeg, 1.5)
        val summerNoon = sun("2026-12-21T15:00:00Z", palmasPr)
        assertEquals(83.35, summerNoon.altitudeDeg, 1.0)
        assertEquals("mesmo instante no Tocantins: Sol já bem mais alto", 12.31, sun("2026-07-15T10:30:00Z").altitudeDeg, 1.0)
    }
}
