package com.ragzonte.ventosdepalmas.scene

import com.ragzonte.ventosdepalmas.weather.WeatherConditions
import com.ragzonte.ventosdepalmas.weather.WeatherPreset
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * Desenha a cena em SVG em vários horários e climas. Além de testar que nada quebra, grava as
 * imagens em build/previews para conferir o visual sem um celular.
 */
class SceneRenderTest {

    /** Tamanho da tela do Honor Magic7 Pro (1280 × 2800). */
    private val width = 1280
    private val height = 2800

    private class Scenario(val name: String, val utc: String, val weather: WeatherConditions, val seconds: Float = 2f) {
        constructor(name: String, utc: String, preset: WeatherPreset, seconds: Float = 2f) :
            this(name, utc, preset.conditions, seconds)
    }

    private val scenarios = listOf(
        Scenario("01-amanhecer", "2026-11-08T08:50:00Z", WeatherPreset.CLEAR),
        Scenario("02-manha-poucas-nuvens", "2026-11-08T12:00:00Z", WeatherPreset.PARTLY_CLOUDY),
        Scenario("03-meio-dia", "2026-11-08T15:30:00Z", WeatherPreset.CLEAR),
        Scenario("04-tarde-chuva", "2026-11-08T18:00:00Z", WeatherPreset.RAIN),
        Scenario("05-por-do-sol", "2026-11-08T21:10:00Z", WeatherPreset.PARTLY_CLOUDY),
        Scenario("06-crepusculo", "2026-11-08T21:40:00Z", WeatherPreset.CLEAR),
        Scenario("07-noite-lua-crescente", "2026-11-12T22:15:00Z", WeatherPreset.CLEAR),
        Scenario("08-noite-lua-gibosa", "2026-11-22T00:00:00Z", WeatherPreset.PARTLY_CLOUDY),
        Scenario("09-noite-tempestade", "2026-11-09T01:00:00Z", WeatherPreset.STORM, seconds = 4.05f),
        Scenario("10-neblina", "2026-11-08T09:30:00Z", WeatherPreset.FOG),
        Scenario("11-noite-nublada", "2026-11-09T02:00:00Z", WeatherPreset.OVERCAST),
        Scenario("12-madrugada-seca", "2026-08-10T06:00:00Z", WeatherPreset.CLEAR),
        Scenario("13-noite-chuva", "2026-11-09T00:30:00Z", WeatherPreset.RAIN),
        Scenario("14-tarde-seca", "2026-08-10T19:00:00Z", WeatherPreset.PARTLY_CLOUDY),
    )

    private fun render(scenario: Scenario): SvgCanvas {
        val scene = WindScene()
        scene.resize(width, height)
        scene.update(Environment.at(Instant.parse(scenario.utc), scenario.weather))
        scene.settle()
        var elapsed = 0f
        while (elapsed < scenario.seconds) {
            scene.step(1 / 30f)
            elapsed += 1 / 30f
        }
        return SvgCanvas(width, height).also { scene.draw(it) }
    }

    @Test
    fun `desenha todos os cenarios e grava as previas`() {
        val dir = File("build/previews").apply { mkdirs() }
        for (scenario in scenarios) {
            val canvas = render(scenario)
            val svg = canvas.toSvg()
            assertTrue("${scenario.name}: SVG muito pequeno", svg.length > 2_000)
            assertTrue("${scenario.name}: sem torres", canvas.paths >= 40)
            File(dir, "${scenario.name}.svg").writeText(svg)
        }
    }

    @Test
    fun `noite limpa tem estrelas e chuva tem gotas`() {
        val night = render(scenarios.first { it.name == "07-noite-lua-crescente" })
        assertTrue("estrelas: ${night.circles}", night.circles > 100)
        assertTrue("sem chuva à noite limpa", night.lines == 0)

        val rain = render(scenarios.first { it.name == "04-tarde-chuva" })
        assertTrue("gotas: ${rain.lines}", rain.lines > 150)
    }

    @Test
    fun `tempestade desenha o raio`() {
        val stormScenario = scenarios.first { it.name == "09-noite-tempestade" }
        val storm = render(stormScenario)
        val noThunder = render(
            Scenario("sem-trovao", stormScenario.utc, stormScenario.weather.copy(thunderstorm = false), stormScenario.seconds),
        )
        assertTrue("linhas do raio além da chuva: ${storm.lines} vs ${noThunder.lines}", storm.lines > noThunder.lines + 10)
    }

    @Test
    fun `sem ambiente desenha apenas fundo escuro`() {
        val scene = WindScene()
        scene.resize(width, height)
        val canvas = SvgCanvas(width, height)
        scene.draw(canvas)
        assertTrue(canvas.toSvg().contains("#05070F"))
    }
}
