package com.ragzonte.ventosdepalmas.weather

import com.ragzonte.ventosdepalmas.geo.GeoLocation

/** Leitura do clima atual, como veio da API. */
data class WeatherSnapshot(
    val fetchedAtMillis: Long,
    /** Onde a leitura foi feita: evita usar o clima de outra cidade depois de uma viagem. */
    val latitude: Double,
    val longitude: Double,
    /** Código de tempo da OMM (WMO), usado pela Open-Meteo. */
    val weatherCode: Int,
    val cloudCoverPercent: Int,
    /** Precipitação nos últimos 15 minutos, em mm. */
    val precipitationMm: Double,
    val windSpeedKmh: Double,
    /** De onde o vento vem, em graus a partir do norte (90 = vento leste). */
    val windDirectionDeg: Double,
    val temperatureC: Double?,
) {
    val description: String get() = Wmo.description(weatherCode)

    fun distanceKmTo(location: GeoLocation): Double =
        location.distanceKmTo(GeoLocation(latitude, longitude, name = null, zoneId = location.zoneId))
}

/** Quando uma leitura de clima ainda serve e quando buscar outra. */
object WeatherFreshness {
    /** Busca de novo a cada 20 minutos com o papel de parede visível. */
    const val REFRESH_INTERVAL_MS = 20 * 60_000L

    /** Leituras com mais de 3 horas são ignoradas, para não mostrar uma chuva que já passou. */
    const val MAX_AGE_MS = 3 * 60 * 60_000L

    /** Andou mais que isso desde a leitura: busca o clima do lugar novo. */
    const val REFETCH_DISTANCE_KM = 10.0

    /** Leituras de mais longe que isso não representam o lugar atual. */
    const val MAX_DISTANCE_KM = 30.0

    fun isUsable(snapshot: WeatherSnapshot, location: GeoLocation, nowMillis: Long): Boolean =
        nowMillis - snapshot.fetchedAtMillis <= MAX_AGE_MS && snapshot.distanceKmTo(location) <= MAX_DISTANCE_KM

    fun needsRefresh(snapshot: WeatherSnapshot?, location: GeoLocation, nowMillis: Long): Boolean =
        snapshot == null ||
            nowMillis - snapshot.fetchedAtMillis >= REFRESH_INTERVAL_MS ||
            snapshot.distanceKmTo(location) > REFETCH_DISTANCE_KM
}

enum class Precipitation(val intensity: Float) {
    NONE(0f),
    DRIZZLE(0.25f),
    LIGHT(0.45f),
    MODERATE(0.7f),
    HEAVY(1f),
}

/** O que a cena precisa saber do tempo, já interpretado. */
data class WeatherConditions(
    /** Cobertura de nuvens, de 0 a 1. */
    val cloudCover: Float,
    val precipitation: Precipitation,
    val thunderstorm: Boolean,
    val fog: Boolean,
    val windSpeedKmh: Double,
    val windDirectionDeg: Double,
) {
    companion object {
        /** Usado enquanto não há leitura recente do lugar: céu quase limpo e brisa leve. */
        val DEFAULT = WeatherConditions(
            cloudCover = 0.15f,
            precipitation = Precipitation.NONE,
            thunderstorm = false,
            fog = false,
            windSpeedKmh = 8.0,
            windDirectionDeg = 90.0,
        )

        fun from(snapshot: WeatherSnapshot): WeatherConditions {
            val code = snapshot.weatherCode
            val precipitation = maxOf(Wmo.precipitation(code), precipitationFromMillimeters(snapshot.precipitationMm))
            val minimumCover = when {
                precipitation >= Precipitation.LIGHT || Wmo.isThunderstorm(code) -> 0.8f
                precipitation == Precipitation.DRIZZLE || Wmo.isFog(code) -> 0.6f
                code == 3 -> 0.85f
                code == 2 -> 0.3f
                else -> 0f
            }
            return WeatherConditions(
                cloudCover = maxOf(snapshot.cloudCoverPercent.coerceIn(0, 100) / 100f, minimumCover),
                precipitation = precipitation,
                thunderstorm = Wmo.isThunderstorm(code),
                fog = Wmo.isFog(code),
                windSpeedKmh = snapshot.windSpeedKmh.coerceAtLeast(0.0),
                windDirectionDeg = snapshot.windDirectionDeg,
            )
        }

        /** Converte a chuva medida em 15 minutos em intensidade, para quando o código não indica chuva. */
        internal fun precipitationFromMillimeters(mm: Double): Precipitation = when {
            mm < 0.1 -> Precipitation.NONE
            mm < 0.3 -> Precipitation.DRIZZLE
            mm < 0.7 -> Precipitation.LIGHT
            mm < 2.0 -> Precipitation.MODERATE
            else -> Precipitation.HEAVY
        }
    }
}

/** Tabela dos códigos de tempo da OMM (WMO 4677, versão resumida da Open-Meteo). */
object Wmo {
    fun description(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1 -> "Predominantemente limpo"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55 -> "Garoa"
        56, 57 -> "Garoa congelante"
        61 -> "Chuva fraca"
        63 -> "Chuva moderada"
        65 -> "Chuva forte"
        66, 67 -> "Chuva congelante"
        71, 73, 75, 77 -> "Neve"
        80 -> "Pancadas de chuva fracas"
        81 -> "Pancadas de chuva"
        82 -> "Pancadas de chuva fortes"
        85, 86 -> "Pancadas de neve"
        95 -> "Trovoada"
        96, 99 -> "Trovoada com granizo"
        else -> "Tempo indefinido"
    }

    fun precipitation(code: Int): Precipitation = when (code) {
        51, 53, 55, 56, 57 -> Precipitation.DRIZZLE
        61, 66, 80, 71, 73, 77, 85 -> Precipitation.LIGHT
        63, 81, 75, 86 -> Precipitation.MODERATE
        65, 67, 82, 95, 96, 99 -> Precipitation.HEAVY
        else -> Precipitation.NONE
    }

    fun isThunderstorm(code: Int): Boolean = code == 95 || code == 96 || code == 99

    fun isFog(code: Int): Boolean = code == 45 || code == 48
}
