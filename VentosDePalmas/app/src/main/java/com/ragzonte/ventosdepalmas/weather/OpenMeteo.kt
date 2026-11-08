package com.ragzonte.ventosdepalmas.weather

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Clima atual de Palmas pela Open-Meteo (gratuita, sem chave de API).
 * Dados: Open-Meteo.com, licença CC BY 4.0.
 */
object OpenMeteo {
    val ENDPOINT: String = "https://api.open-meteo.com/v1/forecast" +
        "?latitude=${Palmas.LATITUDE}&longitude=${Palmas.LONGITUDE}" +
        "&current=temperature_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m" +
        "&wind_speed_unit=kmh&timezone=America%2FAraguaina"

    private const val TIMEOUT_MS = 10_000

    /** Faz a requisição de forma bloqueante: chame fora da thread principal. */
    @Throws(IOException::class)
    fun fetch(nowMillis: Long = System.currentTimeMillis()): WeatherSnapshot {
        val connection = URI(ENDPOINT).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "VentosDePalmas/1.0 (Android)")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("Open-Meteo respondeu HTTP $status")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(body, nowMillis)
        } finally {
            connection.disconnect()
        }
    }

    @Throws(IOException::class)
    fun parse(json: String, fetchedAtMillis: Long): WeatherSnapshot {
        try {
            val current = JSONObject(json).getJSONObject("current")
            return WeatherSnapshot(
                fetchedAtMillis = fetchedAtMillis,
                weatherCode = current.getInt("weather_code"),
                cloudCoverPercent = current.optInt("cloud_cover", 0),
                precipitationMm = current.number("precipitation", 0.0),
                windSpeedKmh = current.number("wind_speed_10m", 0.0),
                windDirectionDeg = current.number("wind_direction_10m", 90.0),
                temperatureC = current.number("temperature_2m", Double.NaN).takeUnless { it.isNaN() },
            )
        } catch (e: org.json.JSONException) {
            throw IOException("Resposta inesperada da Open-Meteo", e)
        }
    }

    private fun JSONObject.number(key: String, fallback: Double): Double =
        optDouble(key, fallback).takeUnless { it.isNaN() } ?: fallback
}
