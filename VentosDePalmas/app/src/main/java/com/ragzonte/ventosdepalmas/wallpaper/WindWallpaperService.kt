package com.ragzonte.ventosdepalmas.wallpaper

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.ragzonte.ventosdepalmas.data.WallpaperSettings
import com.ragzonte.ventosdepalmas.data.WeatherRepository
import com.ragzonte.ventosdepalmas.render.AndroidVectorCanvas
import com.ragzonte.ventosdepalmas.scene.Environment
import com.ragzonte.ventosdepalmas.scene.WindScene
import java.time.Instant

/** Papel de parede animado: céu de Palmas com torres eólicas. */
class WindWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WindEngine()

    private inner class WindEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scene = WindScene()
        private val adapter = AndroidVectorCanvas()
        private val repository = WeatherRepository.get(this@WindWallpaperService)
        private val settings = WallpaperSettings(this@WindWallpaperService)
        private val weatherListener = WeatherRepository.Listener { updateEnvironment() }
        private val frame = Runnable { drawFrame() }

        private var visible = false
        private var frameIntervalMs = 1000L / WallpaperSettings.DEFAULT_FPS
        private var lastFrameNanos = 0L
        private var lastEnvironmentMillis = 0L
        private var hardwareFailures = 0

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            repository.addListener(weatherListener)
            updateEnvironment()
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            repository.removeListener(weatherListener)
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                frameIntervalMs = 1000L / currentFramesPerSecond()
                repository.refreshIfStale()
                updateEnvironment()
                lastFrameNanos = 0L
                drawFrame()
            } else {
                // Invisível (app aberto por cima, tela desligada): não desenha nem busca nada.
                handler.removeCallbacks(frame)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            scene.resize(width, height)
            if (visible) drawFrame()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            scene.parallax = xOffset
        }

        private fun currentFramesPerSecond(): Int {
            val powerSave = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            val chosen = settings.framesPerSecond
            return if (powerSave) minOf(chosen, WallpaperSettings.POWER_SAVE_FPS) else chosen
        }

        private fun updateEnvironment() {
            lastEnvironmentMillis = System.currentTimeMillis()
            scene.update(Environment.at(Instant.now(), repository.conditions()))
        }

        private fun drawFrame() {
            handler.removeCallbacks(frame)
            val startNanos = System.nanoTime()
            val dt = if (lastFrameNanos == 0L) 0f else (startNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = startNanos
            // Sol, Lua e cores mudam devagar: recalcular a cada 30 s basta.
            if (System.currentTimeMillis() - lastEnvironmentMillis > ENVIRONMENT_INTERVAL_MS) updateEnvironment()
            scene.step(dt)
            render()
            if (visible) {
                val spentMs = (System.nanoTime() - startNanos) / 1_000_000
                handler.postDelayed(frame, (frameIntervalMs - spentMs).coerceAtLeast(1L))
            }
        }

        private fun render() {
            val holder = surfaceHolder
            val canvas = lockCanvas(holder) ?: return
            try {
                adapter.canvas = canvas
                scene.draw(adapter)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (e: IllegalStateException) {
                    // Superfície liberada no meio do quadro; o próximo tenta de novo.
                } catch (e: IllegalArgumentException) {
                    // Idem.
                }
            }
        }

        /**
         * Usa o Canvas acelerado por GPU (gradientes e caminhos saem bem mais baratos). Se o sistema não
         * oferecer e o Canvas de software funcionar, passa a usar o de software de vez.
         */
        private fun lockCanvas(holder: SurfaceHolder): Canvas? {
            var hardwareFailed = false
            if (hardwareFailures < MAX_HARDWARE_FAILURES) {
                try {
                    val canvas: Canvas? = holder.lockHardwareCanvas()
                    if (canvas != null) {
                        hardwareFailures = 0
                        return canvas
                    }
                } catch (e: IllegalStateException) {
                    hardwareFailed = true
                } catch (e: IllegalArgumentException) {
                    hardwareFailed = true
                }
            }
            val software = try {
                holder.lockCanvas()
            } catch (e: IllegalStateException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            if (hardwareFailed && software != null) hardwareFailures++
            return software
        }
    }

    private companion object {
        const val ENVIRONMENT_INTERVAL_MS = 30_000L
        const val MAX_HARDWARE_FAILURES = 3
    }
}
