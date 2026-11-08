package com.ragzonte.ventosdepalmas.weather

/** Climas prontos para simular na prévia do app. */
enum class WeatherPreset(val label: String, val conditions: WeatherConditions) {
    CLEAR(
        "Limpo",
        WeatherConditions(0.05f, Precipitation.NONE, thunderstorm = false, fog = false, windSpeedKmh = 10.0, windDirectionDeg = 90.0),
    ),
    PARTLY_CLOUDY(
        "Poucas nuvens",
        WeatherConditions(0.4f, Precipitation.NONE, thunderstorm = false, fog = false, windSpeedKmh = 14.0, windDirectionDeg = 100.0),
    ),
    OVERCAST(
        "Nublado",
        WeatherConditions(0.95f, Precipitation.NONE, thunderstorm = false, fog = false, windSpeedKmh = 12.0, windDirectionDeg = 80.0),
    ),
    RAIN(
        "Chuva",
        WeatherConditions(1f, Precipitation.MODERATE, thunderstorm = false, fog = false, windSpeedKmh = 16.0, windDirectionDeg = 70.0),
    ),
    STORM(
        "Tempestade",
        WeatherConditions(1f, Precipitation.HEAVY, thunderstorm = true, fog = false, windSpeedKmh = 32.0, windDirectionDeg = 60.0),
    ),
    FOG(
        "Neblina",
        WeatherConditions(0.8f, Precipitation.NONE, thunderstorm = false, fog = true, windSpeedKmh = 4.0, windDirectionDeg = 90.0),
    ),
}
