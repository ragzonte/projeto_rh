package com.ragzonte.satelitecheck.model

/** Versão do Android (15) em que o suporte padrão a satélite e as APIs públicas de detecção chegaram. */
const val MIN_SATELLITE_SDK = 35

enum class Verdict {
    /** O aparelho está usando satélite neste momento. */
    CONNECTED_NOW,

    /** A operadora de algum chip configura serviço via satélite. */
    CARRIER_READY,

    /** O fabricante incluiu um serviço de satélite no sistema. */
    DEVICE_READY,

    /** O aparelho não tem rede móvel. */
    NO_TELEPHONY,

    /** Android anterior ao 15, sem o suporte padrão a satélite. */
    ANDROID_TOO_OLD,

    /** Falta a permissão de telefone para ler a configuração da operadora. */
    NEEDS_PERMISSION,

    /** Nenhum chip ativo. */
    NO_SIM,

    /** Nada indica suporte a satélite. */
    NOT_INDICATED,
}

/**
 * Resume o que foi lido do aparelho em um único veredito.
 *
 * Os sinais mais fortes vêm primeiro: uma conexão ativa por satélite vence tudo, depois o que a
 * operadora declara e por fim o que o fabricante configurou no sistema.
 */
fun diagnose(report: SatelliteReport, live: LiveStatus): Verdict = when {
    live.nonTerrestrialNetwork || live.satelliteData -> Verdict.CONNECTED_NOW
    !report.hasTelephony -> Verdict.NO_TELEPHONY
    report.sims.any { it.flags?.anyIndicated == true } -> Verdict.CARRIER_READY
    !report.device.satelliteServicePackage.isNullOrBlank() -> Verdict.DEVICE_READY
    report.device.sdkInt < MIN_SATELLITE_SDK -> Verdict.ANDROID_TOO_OLD
    !report.hasPhonePermission -> Verdict.NEEDS_PERMISSION
    report.sims.isEmpty() -> Verdict.NO_SIM
    else -> Verdict.NOT_INDICATED
}
