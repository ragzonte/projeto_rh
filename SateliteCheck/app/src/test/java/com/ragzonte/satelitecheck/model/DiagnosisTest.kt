package com.ragzonte.satelitecheck.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosisTest {

    private val android15 = DeviceInfo(
        manufacturer = "Fabricante",
        model = "Modelo",
        androidRelease = "15",
        sdkInt = 35,
        basebandVersion = null,
        satelliteServicePackage = null,
    )
    private val android14 = android15.copy(androidRelease = "14", sdkInt = 34)

    private val noFlags = CarrierSatelliteFlags(
        attachSupported = false,
        satellitePlmnsConfigured = false,
        entitlementSupported = false,
        emergencySosSupported = false,
    )

    private fun sim(id: Int = 1, flags: CarrierSatelliteFlags? = noFlags) =
        SimInfo(subscriptionId = id, slotIndex = id - 1, carrierName = "Operadora $id", flags = flags)

    private fun report(
        device: DeviceInfo = android15,
        permission: Boolean = true,
        telephony: Boolean = true,
        sims: List<SimInfo> = listOf(sim()),
    ) = SatelliteReport(device, permission, telephony, sims)

    private val idle = LiveStatus()

    @Test
    fun `conexao por satelite agora vence qualquer outro sinal`() {
        val verdict = diagnose(report(permission = false, sims = emptyList()), LiveStatus(nonTerrestrialNetwork = true))
        assertEquals(Verdict.CONNECTED_NOW, verdict)
    }

    @Test
    fun `dados por satelite contam como conectado`() {
        assertEquals(Verdict.CONNECTED_NOW, diagnose(report(), LiveStatus(satelliteData = true)))
    }

    @Test
    fun `aparelho sem rede movel`() {
        assertEquals(Verdict.NO_TELEPHONY, diagnose(report(telephony = false, sims = emptyList()), idle))
    }

    @Test
    fun `operadora que declara satelite em qualquer chip`() {
        val withSatellite = noFlags.copy(attachSupported = true)
        val sims = listOf(sim(id = 1), sim(id = 2, flags = withSatellite))
        assertEquals(Verdict.CARRIER_READY, diagnose(report(sims = sims), idle))
    }

    @Test
    fun `cada indicador da operadora sozinho ja conta`() {
        listOf(
            noFlags.copy(attachSupported = true),
            noFlags.copy(satellitePlmnsConfigured = true),
            noFlags.copy(entitlementSupported = true),
            noFlags.copy(emergencySosSupported = true),
        ).forEach { flags ->
            assertEquals(Verdict.CARRIER_READY, diagnose(report(sims = listOf(sim(flags = flags))), idle))
        }
    }

    @Test
    fun `operadora vale mesmo em Android anterior ao 15`() {
        val sims = listOf(sim(flags = noFlags.copy(attachSupported = true)))
        assertEquals(Verdict.CARRIER_READY, diagnose(report(device = android14, sims = sims), idle))
    }

    @Test
    fun `servico de satelite do fabricante sem permissao de telefone`() {
        val device = android15.copy(satelliteServicePackage = "com.fabricante.satelite")
        assertEquals(Verdict.DEVICE_READY, diagnose(report(device = device, permission = false, sims = emptyList()), idle))
    }

    @Test
    fun `pacote em branco nao conta como servico do fabricante`() {
        val device = android15.copy(satelliteServicePackage = "  ")
        assertEquals(Verdict.NOT_INDICATED, diagnose(report(device = device), idle))
    }

    @Test
    fun `Android anterior ao 15 sem outros sinais`() {
        assertEquals(Verdict.ANDROID_TOO_OLD, diagnose(report(device = android14, permission = false, sims = emptyList()), idle))
    }

    @Test
    fun `sem permissao de telefone`() {
        assertEquals(Verdict.NEEDS_PERMISSION, diagnose(report(permission = false, sims = emptyList()), idle))
    }

    @Test
    fun `sem chip ativo`() {
        assertEquals(Verdict.NO_SIM, diagnose(report(sims = emptyList()), idle))
    }

    @Test
    fun `configuracao ilegivel nao conta como suporte`() {
        assertEquals(Verdict.NOT_INDICATED, diagnose(report(sims = listOf(sim(flags = null))), idle))
    }

    @Test
    fun `nada indica satelite`() {
        assertEquals(Verdict.NOT_INDICATED, diagnose(report(), idle))
    }
}
