package com.ragzonte.satelitecheck.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ragzonte.satelitecheck.R
import com.ragzonte.satelitecheck.data.SatelliteInspector
import com.ragzonte.satelitecheck.data.SatelliteMonitor
import com.ragzonte.satelitecheck.model.DeviceInfo
import com.ragzonte.satelitecheck.model.LiveStatus
import com.ragzonte.satelitecheck.model.MIN_SATELLITE_SDK
import com.ragzonte.satelitecheck.model.SatelliteReport
import com.ragzonte.satelitecheck.model.SimInfo
import com.ragzonte.satelitecheck.model.Verdict
import com.ragzonte.satelitecheck.model.diagnose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val inspector = remember(appContext) { SatelliteInspector(appContext) }
    val monitor = remember(appContext) { SatelliteMonitor(appContext) }

    var report by remember { mutableStateOf(inspector.buildReport()) }
    val refresh = { report = inspector.buildReport() }

    // Ao voltar para o app (ex.: depois de mexer nas Configurações), lê tudo de novo.
    LifecycleResumeEffect(inspector) {
        refresh()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
    }

    val subscriptionIds = remember(report) { report.sims.map { it.subscriptionId } }
    val liveFlow = remember(monitor, subscriptionIds) { monitor.liveStatus(subscriptionIds) }
    val live by liveFlow.collectAsStateWithLifecycle(initialValue = LiveStatus())

    val verdict = diagnose(report, live)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VerdictCard(verdict)

            if (report.hasTelephony && !report.hasPhonePermission) {
                PermissionCard(
                    onGrant = { permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                    onOpenAppSettings = { openAppSettings(context) },
                )
            }

            NowCard(report, live)
            report.sims.forEach { SimCard(it) }
            DeviceCard(report.device)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = refresh, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_refresh))
                }
                Button(onClick = { openSettings(context, Settings.ACTION_WIRELESS_SETTINGS) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_open_settings))
                }
            }
            Text(
                text = stringResource(R.string.settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HowItWorksCard()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VerdictCard(verdict: Verdict) {
    val colors = MaterialTheme.colorScheme
    val (container, content, icon) = when (verdict) {
        Verdict.CONNECTED_NOW, Verdict.CARRIER_READY, Verdict.DEVICE_READY ->
            Triple(colors.primaryContainer, colors.onPrimaryContainer, Icons.Filled.CheckCircle)
        Verdict.NEEDS_PERMISSION, Verdict.NO_SIM ->
            Triple(colors.secondaryContainer, colors.onSecondaryContainer, Icons.Filled.Info)
        Verdict.NOT_INDICATED, Verdict.ANDROID_TOO_OLD, Verdict.NO_TELEPHONY ->
            Triple(colors.errorContainer, colors.onErrorContainer, Icons.Filled.Warning)
    }
    val (title, body) = verdictTexts(verdict)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun verdictTexts(verdict: Verdict): Pair<Int, Int> = when (verdict) {
    Verdict.CONNECTED_NOW -> R.string.verdict_connected_title to R.string.verdict_connected_body
    Verdict.CARRIER_READY -> R.string.verdict_carrier_title to R.string.verdict_carrier_body
    Verdict.DEVICE_READY -> R.string.verdict_device_title to R.string.verdict_device_body
    Verdict.NO_TELEPHONY -> R.string.verdict_no_telephony_title to R.string.verdict_no_telephony_body
    Verdict.ANDROID_TOO_OLD -> R.string.verdict_old_android_title to R.string.verdict_old_android_body
    Verdict.NEEDS_PERMISSION -> R.string.verdict_permission_title to R.string.verdict_permission_body
    Verdict.NO_SIM -> R.string.verdict_no_sim_title to R.string.verdict_no_sim_body
    Verdict.NOT_INDICATED -> R.string.verdict_not_indicated_title to R.string.verdict_not_indicated_body
}

@Composable
private fun PermissionCard(onGrant: () -> Unit, onOpenAppSettings: () -> Unit) {
    InfoCard(title = stringResource(R.string.section_permission)) {
        Text(stringResource(R.string.permission_explanation), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_grant_permission))
        }
        TextButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_app_settings))
        }
    }
}

@Composable
private fun NowCard(report: SatelliteReport, live: LiveStatus) {
    InfoCard(title = stringResource(R.string.section_now)) {
        if (report.device.sdkInt < MIN_SATELLITE_SDK) {
            Text(stringResource(R.string.now_requires_android15), style = MaterialTheme.typography.bodyMedium)
        } else {
            InfoRow(stringResource(R.string.now_ntn), yesNo(live.nonTerrestrialNetwork))
            InfoRow(stringResource(R.string.now_data), yesNo(live.satelliteData))
        }
    }
}

@Composable
private fun SimCard(sim: SimInfo) {
    val carrier = sim.carrierName.ifBlank { stringResource(R.string.unknown) }
    val title = if (sim.slotIndex >= 0) {
        stringResource(R.string.section_sim, sim.slotIndex + 1, carrier)
    } else {
        stringResource(R.string.section_sim_no_slot, carrier)
    }
    InfoCard(title = title) {
        val flags = sim.flags
        if (flags == null) {
            Text(stringResource(R.string.sim_flags_unavailable), style = MaterialTheme.typography.bodyMedium)
        } else {
            InfoRow(stringResource(R.string.flag_attach), yesNo(flags.attachSupported))
            InfoRow(stringResource(R.string.flag_plmns), yesNo(flags.satellitePlmnsConfigured))
            InfoRow(stringResource(R.string.flag_entitlement), yesNo(flags.entitlementSupported))
            InfoRow(stringResource(R.string.flag_esos), yesNo(flags.emergencySosSupported))
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo) {
    InfoCard(title = stringResource(R.string.section_device)) {
        InfoRow(stringResource(R.string.device_manufacturer), device.manufacturer)
        InfoRow(stringResource(R.string.device_model), device.model)
        InfoRow(
            stringResource(R.string.device_android),
            stringResource(R.string.device_android_value, device.androidRelease, device.sdkInt),
        )
        InfoRow(stringResource(R.string.device_baseband), device.basebandVersion ?: stringResource(R.string.unknown))
        InfoRow(
            stringResource(R.string.device_satellite_service),
            device.satelliteServicePackage ?: stringResource(R.string.no),
        )
    }
}

@Composable
private fun HowItWorksCard() {
    InfoCard(title = stringResource(R.string.section_how)) {
        Text(stringResource(R.string.how_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun yesNo(value: Boolean): String = stringResource(if (value) R.string.yes else R.string.no)

private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
