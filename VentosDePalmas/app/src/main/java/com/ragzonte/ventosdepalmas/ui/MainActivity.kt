package com.ragzonte.ventosdepalmas.ui

import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ragzonte.ventosdepalmas.R
import com.ragzonte.ventosdepalmas.data.LocationRepository
import com.ragzonte.ventosdepalmas.data.WallpaperSettings
import com.ragzonte.ventosdepalmas.data.WeatherRepository
import com.ragzonte.ventosdepalmas.ui.theme.VentosTheme
import com.ragzonte.ventosdepalmas.wallpaper.WindWallpaperService

class MainActivity : ComponentActivity() {
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var locations: LocationRepository
    private lateinit var settings: WallpaperSettings

    private var weather by mutableStateOf(WeatherUiState())
    private var place by mutableStateOf(LocationUiState())
    private var wallpaperActive by mutableStateOf(false)
    private var framesPerSecond by mutableIntStateOf(WallpaperSettings.DEFAULT_FPS)

    private val weatherListener = WeatherRepository.Listener { syncWeather() }
    private val locationListener = LocationRepository.Listener {
        weatherRepository.refreshIfStale(locations.current)
        syncLocation()
        syncWeather()
    }

    // Com ou sem permissão, tenta obter a posição: sem ela, o repositório registra o motivo para a tela.
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        locations.requestCurrent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        weatherRepository = WeatherRepository.get(this)
        locations = LocationRepository.get(this)
        settings = WallpaperSettings(this)
        framesPerSecond = settings.framesPerSecond
        setContent {
            VentosTheme {
                MainScreen(
                    weather = weather,
                    place = place,
                    wallpaperActive = wallpaperActive,
                    framesPerSecond = framesPerSecond,
                    onRefresh = { weatherRepository.refresh(locations.current) },
                    onUseDeviceLocation = ::useDeviceLocation,
                    onOpenAppSettings = ::openAppSettings,
                    onOpenLocationSettings = ::openLocationSettings,
                    onSetWallpaper = ::openWallpaperPicker,
                    onFramesPerSecondChange = {
                        settings.framesPerSecond = it
                        framesPerSecond = it
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        weatherRepository.addListener(weatherListener)
        locations.addListener(locationListener)
        syncLocation()
        syncWeather()
        when {
            locations.hasPermission() -> locations.requestCurrentIfStale()
            // Na primeira abertura pergunta uma vez; depois, só pelo botão.
            !locations.permissionAsked -> useDeviceLocation()
        }
        weatherRepository.refreshIfStale(locations.current)
        wallpaperActive = isWallpaperActive()
    }

    override fun onStop() {
        weatherRepository.removeListener(weatherListener)
        locations.removeListener(locationListener)
        super.onStop()
    }

    private fun useDeviceLocation() {
        if (locations.hasPermission()) {
            locations.requestCurrent()
        } else {
            locations.permissionAsked = true
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun syncWeather() {
        val location = locations.current
        weather = WeatherUiState(
            snapshot = weatherRepository.usableFor(location),
            conditions = weatherRepository.conditions(location),
            fetching = weatherRepository.isFetching,
            error = weatherRepository.lastError,
        )
    }

    private fun syncLocation() {
        place = LocationUiState(
            location = locations.current,
            fromDevice = locations.fromDevice,
            hasPermission = locations.hasPermission(),
            locating = locations.isLocating,
            problem = locations.problem,
        )
    }

    private fun isWallpaperActive(): Boolean =
        WallpaperManager.getInstance(this).wallpaperInfo?.component == wallpaperComponent()

    private fun wallpaperComponent() = ComponentName(this, WindWallpaperService::class.java)

    private fun openAppSettings() =
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    private fun openLocationSettings() = open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openWallpaperPicker() {
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, wallpaperComponent())
        try {
            startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, R.string.wallpaper_picker_missing, Toast.LENGTH_LONG).show()
            }
        }
    }
}
