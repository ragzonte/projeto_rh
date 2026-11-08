package com.ragzonte.ventosdepalmas.weather

import com.ragzonte.ventosdepalmas.geo.GeoLocation
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

/**
 * Clima atual de um lugar pela Open-Meteo (gratuita, sem chave de API).
 * Dados: Open-Meteo.com, licença CC BY 4.0.
 */
object OpenMeteo {
    /**
     * Endereço da consulta. As coordenadas vão arredondadas para 0,01° (≈ 1 km): basta para o clima
     * e não envia a posição exata. Usa ponto decimal mesmo com o celular em português.
     */
    fun endpoint(location: GeoLocation): String =
        "https://api.open-meteo.com/v1/forecast" +
            String.format(Locale.ROOT, "?latitude=%.2f&longitude=%.2f", location.latitude, location.longitude) +
            "&current=temperature_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m" +
            "&wind_speed_unit=kmh&timezone=auto"

    private const val TIMEOUT_MS = 10_000

    /** Faz a requisição de forma bloqueante: chame fora da thread principal. */
    @Throws(IOException::class)
    fun fetch(location: GeoLocation, nowMillis: Long = System.currentTimeMillis()): WeatherSnapshot {
        val connection = URI(endpoint(location)).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "VentosDePalmas/1.0 (Android)")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("Open-Meteo respondeu HTTP $status")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parse(body, nowMillis, location)
        } finally {
            connection.disconnect()
        }
    }

    @Throws(IOException::class)
    fun parse(json: String, fetchedAtMillis: Long, location: GeoLocation): WeatherSnapshot {
        try {
            val current = JSONObject(json).getJSONObject("current")
            return WeatherSnapshot(
                fetchedAtMillis = fetchedAtMillis,
                latitude = location.latitude,
                longitude = location.longitude,
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
