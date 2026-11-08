package com.ragzonte.ventosdepalmas.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.location.LocationManagerCompat
import com.ragzonte.ventosdepalmas.geo.GeoLocation
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * Onde o aparelho está, para calcular o céu e buscar o clima do lugar certo.
 *
 * Usa só a localização aproximada (precisão de cidade, ~1–3 km), suficiente para o Sol, a Lua e o
 * clima. O papel de parede apenas lê a última posição que o sistema já conhece (sem ligar o GPS);
 * o app, quando aberto, pede uma posição atual. Sem permissão ou sem posição, usa a última salva
 * ou, na primeira vez, Palmas-TO.
 */
class LocationRepository private constructor(context: Context) {

    enum class Problem { NO_PERMISSION, LOCATION_OFF, UNAVAILABLE }

    fun interface Listener {
        fun onLocationChanged()
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("local", Context.MODE_PRIVATE)
    private val locationManager: LocationManager? = appContext.getSystemService(LocationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val geocodeExecutor = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private var requestId = 0
    private var lastPassiveCheckMillis = 0L

    /** Lugar em uso: o último vindo do aparelho, ou Palmas-TO se nunca houve um. */
    var current: GeoLocation = load() ?: GeoLocation.PALMAS_TO
        private set

    /** `true` quando [current] veio do aparelho (e não é o padrão). */
    var fromDevice: Boolean = prefs.contains(KEY_LATITUDE)
        private set

    var isLocating = false
        private set

    var problem: Problem? = null
        private set

    /** Se o app já pediu a permissão uma vez (para não insistir a cada abertura). */
    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED, value) }

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Barato: usa a última posição conhecida pelo sistema, sem ligar o GPS. Chamado pelo papel de
     * parede quando aparece, no máximo a cada 10 minutos.
     */
    @MainThread
    fun refreshFromLastKnown(nowMillis: Long = System.currentTimeMillis()) {
        if (!hasPermission() || nowMillis - lastPassiveCheckMillis < PASSIVE_INTERVAL_MS) return
        lastPassiveCheckMillis = nowMillis
        bestLastKnown()?.let(::accept)
    }

    /** Pede uma posição atual se a última do aparelho tem mais de 1 hora (app aberto). */
    @MainThread
    fun requestCurrentIfStale(nowMillis: Long = System.currentTimeMillis()) {
        val lastFix = prefs.getLong(KEY_FIX_TIME, 0L)
        if (!fromDevice || nowMillis - lastFix > STALE_FIX_MS) requestCurrent()
    }

    /** Pede uma posição atual (use com o app em primeiro plano). */
    @MainThread
    fun requestCurrent() {
        if (!hasPermission()) return fail(Problem.NO_PERMISSION)
        val manager = locationManager ?: return fail(Problem.UNAVAILABLE)
        if (!LocationManagerCompat.isLocationEnabled(manager)) return fail(Problem.LOCATION_OFF)
        if (isLocating) return
        // Uma posição recente do sistema já basta, sem esperar por outra.
        bestLastKnown()?.takeIf { ageMillis(it) < RECENT_FIX_MS }?.let {
            accept(it)
            return
        }
        val provider = pickProvider(manager) ?: return fail(Problem.UNAVAILABLE)
        val id = ++requestId
        isLocating = true
        problem = null
        notifyListeners()
        mainHandler.postDelayed({ finishRequest(id, null) }, REQUEST_TIMEOUT_MS)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestCurrentApi30(manager, provider, id)
            } else {
                requestSingleUpdateLegacy(manager, provider, id)
            }
        } catch (e: SecurityException) {
            finishRequest(id, null)
        } catch (e: IllegalArgumentException) {
            finishRequest(id, null)
        }
    }

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    // ---------------------------------------------------------------------------------------------

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun fail(reason: Problem) {
        problem = reason
        notifyListeners()
    }

    private fun notifyListeners() = listeners.forEach { it.onLocationChanged() }

    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    @SuppressLint("MissingPermission") // hasPermission() é verificado por quem chama.
    private fun bestLastKnown(): Location? {
        val manager = locationManager ?: return null
        val providers = try {
            manager.getProviders(true)
        } catch (e: SecurityException) {
            return null
        }
        return providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }.maxByOrNull { it.elapsedRealtimeNanos }
    }

    private fun pickProvider(manager: LocationManager): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && fusedAvailable(manager)) return LocationManager.FUSED_PROVIDER
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun fusedAvailable(manager: LocationManager) =
        manager.hasProvider(LocationManager.FUSED_PROVIDER) && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private fun requestCurrentApi30(manager: LocationManager, provider: String, id: Int) {
        val cancel = CancellationSignal()
        mainHandler.postDelayed({ cancel.cancel() }, REQUEST_TIMEOUT_MS)
        manager.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(appContext)) { location ->
            finishRequest(id, location)
        }
    }

    @Suppress("DEPRECATION") // requestSingleUpdate é o caminho no Android 8 e 9.
    @SuppressLint("MissingPermission")
    private fun requestSingleUpdateLegacy(manager: LocationManager, provider: String, id: Int) {
        // Implementa todos os métodos: antes do Android 11 eles não têm implementação padrão.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                finishRequest(id, location)
            }

            @Deprecated("Obrigatório antes do Android 10")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        mainHandler.postDelayed({ manager.removeUpdates(listener) }, REQUEST_TIMEOUT_MS)
    }

    private fun finishRequest(id: Int, location: Location?) {
        if (id != requestId || !isLocating) return
        isLocating = false
        if (location != null) accept(location) else fail(Problem.UNAVAILABLE)
    }

    private fun accept(location: Location) {
        val previous = current
        val candidate = GeoLocation(location.latitude, location.longitude, name = null, zoneId = ZoneId.systemDefault().id)
        val moved = !fromDevice || previous.distanceKmTo(candidate) > SAME_PLACE_KM
        current = if (moved) candidate else previous.copy(zoneId = candidate.zoneId)
        fromDevice = true
        problem = null
        prefs.edit { putLong(KEY_FIX_TIME, System.currentTimeMillis()) }
        save(current)
        notifyListeners()
        if (current.name == null) lookUpName(current)
    }

    /** Nome da cidade pelo geocodificador do sistema (quando existe); senão ficam as coordenadas. */
    private fun lookUpName(location: GeoLocation) {
        if (!Geocoder.isPresent()) return
        val geocoder = Geocoder(appContext)
        val deliver: (List<Address>) -> Unit = { addresses ->
            val name = addresses.firstOrNull()?.let(::placeName)
            mainHandler.post {
                if (name != null && current.latitude == location.latitude && current.longitude == location.longitude) {
                    current = current.copy(name = name)
                    save(current)
                    notifyListeners()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(location.latitude, location.longitude, 1) { deliver(it) }
        } else {
            geocodeExecutor.execute {
                try {
                    @Suppress("DEPRECATION")
                    deliver(geocoder.getFromLocation(location.latitude, location.longitude, 1).orEmpty())
                } catch (e: IOException) {
                    // Sem rede ou serviço indisponível: segue mostrando as coordenadas.
                } catch (e: IllegalArgumentException) {
                    // Idem.
                }
            }
        }
    }

    private fun placeName(address: Address): String? {
        val city = address.locality ?: address.subAdminArea
        val state = address.adminArea
        return when {
            city != null && state != null -> "$city, $state"
            else -> city ?: state
        }
    }

    private fun save(location: GeoLocation) = prefs.edit {
        putDouble(KEY_LATITUDE, location.latitude)
        putDouble(KEY_LONGITUDE, location.longitude)
        putString(KEY_ZONE, location.zoneId)
        if (location.name != null) putString(KEY_NAME, location.name) else remove(KEY_NAME)
    }

    private fun load(): GeoLocation? {
        if (!prefs.contains(KEY_LATITUDE)) return null
        return GeoLocation(
            latitude = prefs.getDouble(KEY_LATITUDE, 0.0),
            longitude = prefs.getDouble(KEY_LONGITUDE, 0.0),
            name = prefs.getString(KEY_NAME, null),
            zoneId = prefs.getString(KEY_ZONE, null) ?: ZoneId.systemDefault().id,
        )
    }

    companion object {
        private const val PASSIVE_INTERVAL_MS = 10 * 60_000L
        private const val STALE_FIX_MS = 60 * 60_000L
        private const val RECENT_FIX_MS = 15 * 60_000L
        private const val REQUEST_TIMEOUT_MS = 20_000L
        /** Mudanças menores que isso não trocam o lugar (evita refazer o nome e o clima à toa). */
        private const val SAME_PLACE_KM = 2.0

        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_ZONE = "fuso"
        private const val KEY_NAME = "nome"
        private const val KEY_FIX_TIME = "hora_da_posicao"
        private const val KEY_ASKED = "permissao_pedida"

        @Volatile
        private var instance: LocationRepository? = null

        fun get(context: Context): LocationRepository = instance ?: synchronized(this) {
            instance ?: LocationRepository(context).also { instance = it }
        }
    }
}
