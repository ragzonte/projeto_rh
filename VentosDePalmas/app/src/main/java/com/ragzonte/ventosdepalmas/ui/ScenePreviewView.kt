package com.ragzonte.ventosdepalmas.ui

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.ragzonte.ventosdepalmas.geo.GeoLocation
import com.ragzonte.ventosdepalmas.render.AndroidVectorCanvas
import com.ragzonte.ventosdepalmas.scene.Environment
import com.ragzonte.ventosdepalmas.scene.WindScene
import com.ragzonte.ventosdepalmas.weather.WeatherConditions
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Prévia animada do papel de parede dentro do app, com hora e clima que podem ser simulados. */
class ScenePreviewView(context: Context) : View(context) {
    private val scene = WindScene()
    private val adapter = AndroidVectorCanvas()
    private var lastFrameNanos = 0L
    private var lastEnvironmentMillis = 0L

    var location: GeoLocation = GeoLocation.PALMAS_TO
        set(value) {
            if (field == value) return
            field = value
            updateEnvironment(settle = true)
        }

    var liveWeather: WeatherConditions = WeatherConditions.DEFAULT
        set(value) {
            if (field == value) return
            field = value
            updateEnvironment(settle = false)
        }

    /** Hora local do lugar a simular (0 a 24); `null` usa a hora atual. */
    var hourOverride: Float? = null
        set(value) {
            if (field == value) return
            field = value
            updateEnvironment(settle = true)
        }

    /** Clima a simular; `null` usa o clima real. */
    var weatherOverride: WeatherConditions? = null
        set(value) {
            if (field == value) return
            field = value
            updateEnvironment(settle = true)
        }

    var framesPerSecond = 30

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scene.resize(w, h)
        updateEnvironment(settle = true)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            lastFrameNanos = 0L
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        if (hourOverride == null && System.currentTimeMillis() - lastEnvironmentMillis > 30_000L) {
            updateEnvironment(settle = false)
        }
        scene.step(dt)
        adapter.canvas = canvas
        scene.draw(adapter)
        postInvalidateDelayed(1000L / framesPerSecond.coerceAtLeast(1))
    }

    private fun updateEnvironment(settle: Boolean) {
        lastEnvironmentMillis = System.currentTimeMillis()
        scene.update(Environment.at(simulatedInstant(), weatherOverride ?: liveWeather, location))
        // Na prévia, mudar hora ou clima deve aparecer na hora, sem a transição suave do papel de parede.
        if (settle) scene.settle()
        invalidate()
    }

    private fun simulatedInstant(): Instant {
        val hour = hourOverride ?: return Instant.now()
        val seconds = (hour * 3600).toLong().coerceIn(0L, 86_399L)
        val zone = location.zone
        return ZonedDateTime.of(LocalDate.now(zone), LocalTime.ofSecondOfDay(seconds), zone).toInstant()
    }
}
