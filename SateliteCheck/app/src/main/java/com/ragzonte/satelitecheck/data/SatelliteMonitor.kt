package com.ragzonte.satelitecheck.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ragzonte.satelitecheck.model.LiveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/** Acompanha em tempo real se o aparelho está usando satélite. Só observa; não liga nem desliga nada. */
class SatelliteMonitor(private val context: Context) {

    /**
     * Emite o estado atual e cada mudança. As APIs públicas que revelam conexão via satélite
     * existem a partir do Android 15; antes disso o fluxo emite apenas "desconectado".
     *
     * @param subscriptionIds chips a observar; vazio observa o chip padrão.
     */
    fun liveStatus(subscriptionIds: List<Int>): Flow<LiveStatus> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return flowOf(LiveStatus())
        return observe(subscriptionIds)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun observe(subscriptionIds: List<Int>): Flow<LiveStatus> = callbackFlow {
        // Todos os callbacks são entregues na thread principal (ver flowOn abaixo), sem concorrência.
        val chipsOnSatellite = mutableSetOf<Int>()
        val satelliteNetworks = mutableSetOf<Network>()

        fun publish() {
            trySend(
                LiveStatus(
                    nonTerrestrialNetwork = chipsOnSatellite.isNotEmpty(),
                    satelliteData = satelliteNetworks.isNotEmpty(),
                ),
            )
        }

        val telephonyRegistrations = registerServiceStateListeners(subscriptionIds) { chip, usingSatellite ->
            if (usingSatellite) chipsOnSatellite += chip else chipsOnSatellite -= chip
            publish()
        }

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE)) {
                    satelliteNetworks += network
                } else {
                    satelliteNetworks -= network
                }
                publish()
            }

            override fun onLost(network: Network) {
                satelliteNetworks -= network
                publish()
            }
        }
        val networkRegistered = connectivity != null && try {
            // Sem filtros de capacidade: observa todas as redes, inclusive as de banda restrita.
            val everyNetwork = NetworkRequest.Builder().clearCapabilities().build()
            connectivity.registerNetworkCallback(everyNetwork, networkCallback, Handler(Looper.getMainLooper()))
            true
        } catch (e: RuntimeException) {
            // Ex.: limite de callbacks por app. A detecção por rede celular continua funcionando.
            false
        }

        publish()

        awaitClose {
            telephonyRegistrations.forEach { (manager, callback) -> manager.unregisterTelephonyCallback(callback) }
            if (networkRegistered) connectivity?.unregisterNetworkCallback(networkCallback)
        }
    }.flowOn(Dispatchers.Main)

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun registerServiceStateListeners(
        subscriptionIds: List<Int>,
        onChange: (chip: Int, usingSatellite: Boolean) -> Unit,
    ): List<Pair<TelephonyManager, TelephonyCallback>> {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return emptyList()
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        // Sem a permissão de telefone não sabemos quais chips existem: observa o chip padrão.
        val managers = if (subscriptionIds.isEmpty()) {
            listOf(telephony)
        } else {
            subscriptionIds.map { telephony.createForSubscriptionId(it) }
        }
        val executor = ContextCompat.getMainExecutor(context)
        return managers.mapIndexedNotNull { chip, manager ->
            val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    onChange(chip, serviceState.isUsingNonTerrestrialNetwork)
                }
            }
            try {
                manager.registerTelephonyCallback(executor, callback)
                manager to callback
            } catch (e: SecurityException) {
                null
            } catch (e: UnsupportedOperationException) {
                null
            }
        }
    }
}
