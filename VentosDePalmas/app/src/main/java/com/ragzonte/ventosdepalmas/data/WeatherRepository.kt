package com.ragzonte.ventosdepalmas.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.core.content.edit
import com.ragzonte.ventosdepalmas.geo.GeoLocation
import com.ragzonte.ventosdepalmas.weather.OpenMeteo
import com.ragzonte.ventosdepalmas.weather.WeatherConditions
import com.ragzonte.ventosdepalmas.weather.WeatherFreshness
import com.ragzonte.ventosdepalmas.weather.WeatherSnapshot
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * Guarda o clima do lugar atual e busca de novo quando fica velho ou quando o lugar muda. Usado
 * pelo papel de parede e pela tela do app (mesmo processo). A busca só acontece quando alguém pede,
 * ou seja, com o papel de parede visível ou o app aberto: nada roda em segundo plano gastando bateria.
 */
class WeatherRepository private constructor(private val prefs: SharedPreferences) {

    fun interface Listener {
        fun onWeatherChanged()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private var lastAttemptMillis = 0L

    /** Última leitura salva, mesmo que antiga ou de outro lugar. */
    var latest: WeatherSnapshot? = load()
        private set

    var isFetching = false
        private set

    var lastError: String? = null
        private set

    /** Leitura que vale para [location] agora, ou `null` se é velha ou de outro lugar. */
    fun usableFor(location: GeoLocation, nowMillis: Long = System.currentTimeMillis()): WeatherSnapshot? =
        latest?.takeIf { WeatherFreshness.isUsable(it, location, nowMillis) }

    /** Clima para desenhar em [location]; sem leitura válida, céu quase limpo e brisa leve. */
    fun conditions(location: GeoLocation, nowMillis: Long = System.currentTimeMillis()): WeatherConditions =
        usableFor(location, nowMillis)?.let { WeatherConditions.from(it) } ?: WeatherConditions.DEFAULT

    @MainThread
    fun refreshIfStale(location: GeoLocation, nowMillis: Long = System.currentTimeMillis()) {
        val backingOff = lastError != null && nowMillis - lastAttemptMillis < RETRY_AFTER_ERROR_MS
        if (WeatherFreshness.needsRefresh(latest, location, nowMillis) && !backingOff) refresh(location)
    }

    @MainThread
    fun refresh(location: GeoLocation) {
        if (isFetching) return
        isFetching = true
        lastAttemptMillis = System.currentTimeMillis()
        notifyListeners()
        executor.execute {
            val result: Result<WeatherSnapshot> = try {
                Result.success(OpenMeteo.fetch(location))
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: RuntimeException) {
                Result.failure(e)
            }
            mainHandler.post {
                isFetching = false
                result
                    .onSuccess {
                        latest = it
                        lastError = null
                        save(it)
                    }
                    .onFailure { lastError = it.message ?: it.javaClass.simpleName }
                notifyListeners()
            }
        }
    }

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    private fun notifyListeners() = listeners.forEach { it.onWeatherChanged() }

    private fun save(snapshot: WeatherSnapshot) = prefs.edit {
        putLong(KEY_TIME, snapshot.fetchedAtMillis)
        putDouble(KEY_LATITUDE, snapshot.latitude)
        putDouble(KEY_LONGITUDE, snapshot.longitude)
        putInt(KEY_CODE, snapshot.weatherCode)
        putInt(KEY_COVER, snapshot.cloudCoverPercent)
        putFloat(KEY_PRECIPITATION, snapshot.precipitationMm.toFloat())
        putFloat(KEY_WIND_SPEED, snapshot.windSpeedKmh.toFloat())
        putFloat(KEY_WIND_DIRECTION, snapshot.windDirectionDeg.toFloat())
        val temperature = snapshot.temperatureC
        if (temperature != null) putFloat(KEY_TEMPERATURE, temperature.toFloat()) else remove(KEY_TEMPERATURE)
    }

    private fun load(): WeatherSnapshot? {
        if (!prefs.contains(KEY_TIME)) return null
        // Leituras da versão 1.0 (sem coordenadas) eram sempre de Palmas-TO.
        val fallback = GeoLocation.PALMAS_TO
        return WeatherSnapshot(
            fetchedAtMillis = prefs.getLong(KEY_TIME, 0L),
            latitude = prefs.getDouble(KEY_LATITUDE, fallback.latitude),
            longitude = prefs.getDouble(KEY_LONGITUDE, fallback.longitude),
            weatherCode = prefs.getInt(KEY_CODE, 0),
            cloudCoverPercent = prefs.getInt(KEY_COVER, 0),
            precipitationMm = prefs.getFloat(KEY_PRECIPITATION, 0f).toDouble(),
            windSpeedKmh = prefs.getFloat(KEY_WIND_SPEED, 0f).toDouble(),
            windDirectionDeg = prefs.getFloat(KEY_WIND_DIRECTION, 90f).toDouble(),
            temperatureC = if (prefs.contains(KEY_TEMPERATURE)) prefs.getFloat(KEY_TEMPERATURE, 0f).toDouble() else null,
        )
    }

    companion object {
        private const val RETRY_AFTER_ERROR_MS = 2 * 60_000L

        private const val KEY_TIME = "hora"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_CODE = "codigo"
        private const val KEY_COVER = "nuvens"
        private const val KEY_PRECIPITATION = "chuva_mm"
        private const val KEY_WIND_SPEED = "vento_kmh"
        private const val KEY_WIND_DIRECTION = "vento_direcao"
        private const val KEY_TEMPERATURE = "temperatura"

        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository = instance ?: synchronized(this) {
            instance ?: WeatherRepository(
                context.applicationContext.getSharedPreferences("clima", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}

/** SharedPreferences não guarda Double: salva os bits num Long, sem perder precisão. */
internal fun SharedPreferences.Editor.putDouble(key: String, value: Double): SharedPreferences.Editor =
    putLong(key, java.lang.Double.doubleToRawLongBits(value))

internal fun SharedPreferences.getDouble(key: String, fallback: Double): Double =
    if (contains(key)) java.lang.Double.longBitsToDouble(getLong(key, 0L)) else fallback
