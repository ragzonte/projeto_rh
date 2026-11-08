package com.ragzonte.ventosdepalmas.data

import android.content.Context
import androidx.core.content.edit

/** Preferências do papel de parede. */
class WallpaperSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** Quadros por segundo da animação. 30 é fluido e econômico; a tela de 120 Hz não precisa de mais. */
    var framesPerSecond: Int
        get() = prefs.getInt(KEY_FPS, DEFAULT_FPS).takeIf { it in FPS_OPTIONS } ?: DEFAULT_FPS
        set(value) = prefs.edit { putInt(KEY_FPS, value) }

    companion object {
        const val DEFAULT_FPS = 30
        const val POWER_SAVE_FPS = 15
        val FPS_OPTIONS = listOf(15, 30, 60)
        private const val KEY_FPS = "quadros_por_segundo"
    }
}
