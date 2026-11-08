package com.ragzonte.ventosdepalmas.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ragzonte.ventosdepalmas.R
import com.ragzonte.ventosdepalmas.data.WallpaperSettings
import com.ragzonte.ventosdepalmas.data.WeatherRepository
import com.ragzonte.ventosdepalmas.ui.theme.VentosTheme
import com.ragzonte.ventosdepalmas.wallpaper.WindWallpaperService

class MainActivity : ComponentActivity() {
    private lateinit var repository: WeatherRepository
    private lateinit var settings: WallpaperSettings

    private var state by mutableStateOf(WeatherUiState())
    private var wallpaperActive by mutableStateOf(false)
    private var framesPerSecond by mutableIntStateOf(WallpaperSettings.DEFAULT_FPS)

    private val weatherListener = WeatherRepository.Listener { syncWeather() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = WeatherRepository.get(this)
        settings = WallpaperSettings(this)
        framesPerSecond = settings.framesPerSecond
        setContent {
            VentosTheme {
                MainScreen(
                    weather = state,
                    wallpaperActive = wallpaperActive,
                    framesPerSecond = framesPerSecond,
                    onRefresh = { repository.refresh() },
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
        repository.addListener(weatherListener)
        syncWeather()
        repository.refreshIfStale()
        wallpaperActive = isWallpaperActive()
    }

    override fun onStop() {
        repository.removeListener(weatherListener)
        super.onStop()
    }

    private fun syncWeather() {
        state = WeatherUiState(
            snapshot = repository.latest,
            conditions = repository.conditions(),
            fetching = repository.isFetching,
            error = repository.lastError,
        )
    }

    private fun isWallpaperActive(): Boolean =
        WallpaperManager.getInstance(this).wallpaperInfo?.component == wallpaperComponent()

    private fun wallpaperComponent() = ComponentName(this, WindWallpaperService::class.java)

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
