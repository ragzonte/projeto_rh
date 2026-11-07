package com.ragzonte.satelitecheck.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.ragzonte.satelitecheck.model.CarrierSatelliteFlags
import com.ragzonte.satelitecheck.model.DeviceInfo
import com.ragzonte.satelitecheck.model.SatelliteReport
import com.ragzonte.satelitecheck.model.SimInfo

/**
 * Lê, apenas por APIs de leitura, o que o sistema e a operadora informam sobre satélite.
 * Nada aqui altera configurações do aparelho.
 */
class SatelliteInspector(private val context: Context) {

    fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun buildReport(): SatelliteReport {
        val hasPermission = hasPhonePermission()
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        return SatelliteReport(
            device = readDevice(),
            hasPhonePermission = hasPermission,
            hasTelephony = hasTelephony,
            sims = if (hasPermission && hasTelephony) readSims() else emptyList(),
        )
    }

    private fun readDevice() = DeviceInfo(
        manufacturer = Build.MANUFACTURER.replaceFirstChar { it.titlecase() },
        model = Build.MODEL,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        basebandVersion = Build.getRadioVersion()?.takeIf { it.isNotBlank() },
        satelliteServicePackage = readSatelliteServicePackage(),
    )

    /**
     * O Android só ativa o serviço de satélite quando o fabricante preenche o recurso interno
     * `config_satellite_service_package`; vazio (padrão do AOSP) significa que não há serviço.
     * Não existe API pública para isso, então o valor é lido pelo nome do recurso.
     */
    @SuppressLint("DiscouragedApi")
    private fun readSatelliteServicePackage(): String? {
        val resources = context.resources
        val id = resources.getIdentifier("config_satellite_service_package", "string", "android")
        if (id == 0) return null
        return try {
            resources.getString(id).trim().takeIf { it.isNotEmpty() }
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    // A permissão READ_PHONE_STATE é verificada em buildReport() antes desta chamada.
    @SuppressLint("MissingPermission")
    private fun readSims(): List<SimInfo> {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val subscriptions = try {
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: UnsupportedOperationException) {
            return emptyList()
        }
        val carrierConfigManager = context.getSystemService(CarrierConfigManager::class.java)
        return subscriptions.map { info ->
            SimInfo(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                carrierName = listOf(info.carrierName, info.displayName)
                    .firstOrNull { !it.isNullOrBlank() }
                    ?.toString()
                    .orEmpty(),
                flags = carrierConfigManager?.let { readCarrierFlags(it, info.subscriptionId) },
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun readCarrierFlags(manager: CarrierConfigManager, subscriptionId: Int): CarrierSatelliteFlags? {
        val config: PersistableBundle = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Pede só as chaves de satélite em vez da configuração inteira da operadora.
                manager.getConfigForSubId(subscriptionId, *CarrierKeys.ALL)
            } else {
                @Suppress("DEPRECATION")
                manager.getConfigForSubId(subscriptionId)
            }
        } catch (e: SecurityException) {
            null
        } catch (e: UnsupportedOperationException) {
            null
        } catch (e: IllegalStateException) {
            // Serviço de configuração da operadora indisponível no momento.
            null
        } ?: return null
        return CarrierSatelliteFlags(
            attachSupported = config.getBoolean(CarrierKeys.SATELLITE_ATTACH_SUPPORTED, false),
            satellitePlmnsConfigured = config.getPersistableBundle(CarrierKeys.SATELLITE_SERVICES_PER_PROVIDER)
                ?.isEmpty == false,
            entitlementSupported = config.getBoolean(CarrierKeys.SATELLITE_ENTITLEMENT_SUPPORTED, false),
            emergencySosSupported = config.getBoolean(CarrierKeys.SATELLITE_ESOS_SUPPORTED, false),
        )
    }

    /**
     * Nomes das chaves de satélite do [CarrierConfigManager] no AOSP. Usados como texto para
     * funcionar em qualquer versão: quando a chave não existe, a leitura devolve `false`.
     */
    private object CarrierKeys {
        const val SATELLITE_ATTACH_SUPPORTED = "satellite_attach_supported_bool"
        const val SATELLITE_SERVICES_PER_PROVIDER = "carrier_supported_satellite_services_per_provider_bundle"
        const val SATELLITE_ENTITLEMENT_SUPPORTED = "satellite_entitlement_supported_bool"
        const val SATELLITE_ESOS_SUPPORTED = "satellite_esos_supported_bool"

        val ALL = arrayOf(
            SATELLITE_ATTACH_SUPPORTED,
            SATELLITE_SERVICES_PER_PROVIDER,
            SATELLITE_ENTITLEMENT_SUPPORTED,
            SATELLITE_ESOS_SUPPORTED,
        )
    }
}
