package com.ragzonte.satelitecheck.model

/** Dados do aparelho que ajudam a entender o suporte a satélite. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val basebandVersion: String?,
    /**
     * Pacote do serviço de satélite que o fabricante configurou no sistema
     * (recurso `config_satellite_service_package` do Android). `null` quando não há.
     */
    val satelliteServicePackage: String?,
)

/** Indicadores de satélite que a operadora publica na configuração do chip (CarrierConfig). */
data class CarrierSatelliteFlags(
    val attachSupported: Boolean,
    val satellitePlmnsConfigured: Boolean,
    val entitlementSupported: Boolean,
    val emergencySosSupported: Boolean,
) {
    val anyIndicated: Boolean
        get() = attachSupported || satellitePlmnsConfigured || entitlementSupported || emergencySosSupported
}

data class SimInfo(
    val subscriptionId: Int,
    /** Posição do chip no aparelho, começando em 0; negativo quando o sistema não informa. */
    val slotIndex: Int,
    val carrierName: String,
    /** `null` quando a configuração da operadora não pôde ser lida. */
    val flags: CarrierSatelliteFlags?,
)

/** Retrato estático do aparelho e dos chips, refeito a cada atualização. */
data class SatelliteReport(
    val device: DeviceInfo,
    val hasPhonePermission: Boolean,
    val hasTelephony: Boolean,
    val sims: List<SimInfo>,
)

/** Estado da conexão observado em tempo real (Android 15+). */
data class LiveStatus(
    /** Algum chip está registrado numa rede celular não terrestre (satélite). */
    val nonTerrestrialNetwork: Boolean = false,
    /** Alguma rede de dados ativa usa transporte por satélite. */
    val satelliteData: Boolean = false,
)
