package com.ragzonte.ventosdepalmas.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class GeoLocationTest {
    private val palmasPr = GeoLocation(-26.4839, -51.9888, "Palmas, Paraná", "America/Sao_Paulo")

    @Test
    fun `padrao e Palmas do Tocantins`() {
        val palmas = GeoLocation.PALMAS_TO
        assertEquals(-10.1844, palmas.latitude, 1e-9)
        assertEquals(ZoneId.of("America/Araguaina"), palmas.zone)
        assertEquals("Palmas, Tocantins", palmas.label)
    }

    @Test
    fun `distancia entre as duas Palmas`() {
        assertEquals(1852.7, GeoLocation.PALMAS_TO.distanceKmTo(palmasPr), 1.0)
        assertEquals(0.0, palmasPr.distanceKmTo(palmasPr), 1e-9)
    }

    @Test
    fun `sem nome mostra as coordenadas em portugues`() {
        val unnamed = palmasPr.copy(name = null)
        assertEquals("-26,48°, -51,99°", unnamed.label)
    }

    @Test
    fun `fuso invalido cai no fuso do aparelho`() {
        assertEquals(ZoneId.systemDefault(), palmasPr.copy(zoneId = "Lugar/Inexistente").zone)
        assertTrue(palmasPr.zone.id == "America/Sao_Paulo")
    }
}
