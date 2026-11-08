package com.ragzonte.ventosdepalmas.geo

import java.time.DateTimeException
import java.time.ZoneId
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Lugar usado para calcular o céu e buscar o clima. */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    /** Nome legível (ex.: "Palmas, Paraná"); `null` enquanto não se sabe. */
    val name: String?,
    /** Fuso horário (ID IANA), usado para o mês da estação e para mostrar horários. */
    val zoneId: String,
) {
    val zone: ZoneId
        get() = try {
            ZoneId.of(zoneId)
        } catch (e: DateTimeException) {
            ZoneId.systemDefault()
        }

    /** Nome, ou as coordenadas quando o nome não está disponível. */
    val label: String get() = name ?: coordinatesLabel()

    fun coordinatesLabel(): String = String.format(PT_BR, "%.2f°, %.2f°", latitude, longitude)

    /** Distância em linha reta (fórmula de haversine), em km. */
    fun distanceKmTo(other: GeoLocation): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.longitude - longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    companion object {
        /** Padrão quando a localização do aparelho não está disponível: Palmas, Tocantins. */
        val PALMAS_TO = GeoLocation(-10.1844, -48.3336, "Palmas, Tocantins", "America/Araguaina")

        private const val EARTH_RADIUS_KM = 6371.0088
        private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
    }
}
