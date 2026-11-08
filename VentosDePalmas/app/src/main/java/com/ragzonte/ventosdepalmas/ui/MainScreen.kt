package com.ragzonte.ventosdepalmas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ragzonte.ventosdepalmas.R
import com.ragzonte.ventosdepalmas.data.WallpaperSettings
import com.ragzonte.ventosdepalmas.weather.Palmas
import com.ragzonte.ventosdepalmas.weather.WeatherConditions
import com.ragzonte.ventosdepalmas.weather.WeatherPreset
import com.ragzonte.ventosdepalmas.weather.WeatherSnapshot
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class WeatherUiState(
    val snapshot: WeatherSnapshot? = null,
    val conditions: WeatherConditions = WeatherConditions.DEFAULT,
    val fetching: Boolean = false,
    val error: String? = null,
)

/** Proporção da tela do Honor Magic7 (1280 × 2800), usada na prévia. */
private const val SCREEN_ASPECT = 1280f / 2800f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    weather: WeatherUiState,
    wallpaperActive: Boolean,
    framesPerSecond: Int,
    onRefresh: () -> Unit,
    onSetWallpaper: () -> Unit,
    onFramesPerSecondChange: (Int) -> Unit,
) {
    var previewHour by rememberSaveable { mutableStateOf<Float?>(null) }
    var previewPreset by rememberSaveable { mutableStateOf<WeatherPreset?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ScenePreviewView(it) },
                    update = { view ->
                        view.liveWeather = weather.conditions
                        view.hourOverride = previewHour
                        view.weatherOverride = previewPreset?.conditions
                        view.framesPerSecond = framesPerSecond
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .aspectRatio(SCREEN_ASPECT)
                        .clip(RoundedCornerShape(28.dp)),
                )
            }

            Button(onClick = onSetWallpaper, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (wallpaperActive) R.string.action_wallpaper_active else R.string.action_set_wallpaper))
            }

            WeatherCard(weather, onRefresh)
            SimulationCard(
                hour = previewHour,
                preset = previewPreset,
                onHourChange = { previewHour = it },
                onPresetChange = { previewPreset = it },
            )
            FramesPerSecondCard(framesPerSecond, onFramesPerSecondChange)

            Text(
                text = stringResource(R.string.credits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherUiState, onRefresh: () -> Unit) {
    SectionCard(title = stringResource(R.string.section_weather)) {
        val snapshot = weather.snapshot
        if (snapshot == null) {
            Text(
                stringResource(if (weather.fetching) R.string.weather_loading else R.string.weather_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            val temperature = snapshot.temperatureC?.let { "${it.roundToInt()} °C · " }.orEmpty()
            Text("$temperature${snapshot.description}", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.weather_details,
                    snapshot.windSpeedKmh.roundToInt(),
                    snapshot.cloudCoverPercent,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            val time = Instant.ofEpochMilli(snapshot.fetchedAtMillis).atZone(Palmas.ZONE).format(HOUR_FORMAT)
            Text(
                stringResource(R.string.weather_updated, time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (weather.error != null && !weather.fetching) {
            Text(
                stringResource(R.string.weather_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onRefresh, enabled = !weather.fetching) {
            Text(stringResource(if (weather.fetching) R.string.weather_loading_short else R.string.action_refresh))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimulationCard(
    hour: Float?,
    preset: WeatherPreset?,
    onHourChange: (Float?) -> Unit,
    onPresetChange: (WeatherPreset?) -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_simulation)) {
        Text(
            stringResource(R.string.simulation_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = preset == null,
                onClick = { onPresetChange(null) },
                label = { Text(stringResource(R.string.simulation_real_weather)) },
            )
            WeatherPreset.entries.forEach { option ->
                FilterChip(
                    selected = preset == option,
                    onClick = { onPresetChange(option) },
                    label = { Text(option.label) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = hour == null,
                onClick = { onHourChange(null) },
                label = { Text(stringResource(R.string.simulation_now)) },
            )
            Text(
                text = hour?.let { stringResource(R.string.simulation_hour, formatHour(it)) }
                    ?: stringResource(R.string.simulation_real_time),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = hour ?: 12f,
            onValueChange = { onHourChange(it) },
            valueRange = 0f..23.99f,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FramesPerSecondCard(selected: Int, onChange: (Int) -> Unit) {
    SectionCard(title = stringResource(R.string.section_fps)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperSettings.FPS_OPTIONS.forEach { fps ->
                val label = when (fps) {
                    15 -> R.string.fps_saver
                    60 -> R.string.fps_smooth
                    else -> R.string.fps_default
                }
                FilterChip(
                    selected = selected == fps,
                    onClick = { onChange(fps) },
                    label = { Text(stringResource(label, fps)) },
                )
            }
        }
        Text(
            stringResource(R.string.fps_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatHour(hour: Float): String {
    val minutes = (hour * 60).roundToInt().coerceIn(0, 23 * 60 + 59)
    return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
}
