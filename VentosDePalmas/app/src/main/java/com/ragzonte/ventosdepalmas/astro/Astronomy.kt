package com.ragzonte.ventosdepalmas.astro

import java.time.Instant
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class SunPosition(
    /** Altura acima do horizonte, em graus (negativa abaixo do horizonte). */
    val altitudeDeg: Double,
    /** Azimute a partir do norte, em sentido horário (90 = leste). */
    val azimuthDeg: Double,
    /** Ângulo horário entre -180 e 180: negativo antes da passagem pelo meridiano (lado leste). */
    val hourAngleDeg: Double,
)

data class MoonPosition(
    val altitudeDeg: Double,
    val azimuthDeg: Double,
    val hourAngleDeg: Double,
    /** Fração iluminada do disco: 0 na lua nova, 1 na cheia. */
    val illuminatedFraction: Double,
    /** Fase: 0 nova, 0,25 quarto crescente, 0,5 cheia, 0,75 quarto minguante. */
    val phase: Double,
    /**
     * Para onde fica o lado iluminado: um passo de 1° no céu, da Lua em direção ao Sol, expresso como
     * variação de ângulo horário e de altura (graus). Projetado como o Sol e a Lua na tela, faz o lado
     * claro apontar para o Sol, inclusive a lua "deitada" que se vê perto do equador.
     */
    val litSideHourAngleStep: Double,
    val litSideAltitudeStep: Double,
)

/**
 * Posições aproximadas do Sol e da Lua (precisão em torno de 1°, suficiente para desenho).
 * Fórmulas de baixa precisão de Meeus, "Astronomical Algorithms", na forma resumida por aa.quae.nl.
 */
object Astronomy {
    private const val RAD = PI / 180
    private const val DAY_MS = 86_400_000.0
    private const val JULIAN_UNIX_EPOCH = 2_440_587.5
    private const val JULIAN_J2000 = 2_451_545.0
    private const val OBLIQUITY = RAD * 23.4397
    private const val SUN_DISTANCE_KM = 149_598_000.0
    private const val EARTH_RADIUS_KM = 6_378.14

    fun sun(instant: Instant, latitudeDeg: Double, longitudeDeg: Double): SunPosition {
        val d = daysSinceJ2000(instant)
        val phi = RAD * latitudeDeg
        val sun = sunCoordinates(d)
        val hourAngle = siderealTime(d, longitudeDeg) - sun.rightAscension
        return SunPosition(
            altitudeDeg = altitude(hourAngle, phi, sun.declination) / RAD,
            azimuthDeg = northAzimuthDeg(hourAngle, phi, sun.declination),
            hourAngleDeg = normalize180(hourAngle / RAD),
        )
    }

    fun moon(instant: Instant, latitudeDeg: Double, longitudeDeg: Double): MoonPosition {
        val d = daysSinceJ2000(instant)
        val phi = RAD * latitudeDeg
        val moon = moonCoordinates(d)
        val sun = sunCoordinates(d)
        val sidereal = siderealTime(d, longitudeDeg)
        val hourAngle = sidereal - moon.rightAscension

        // A Lua está perto o bastante para a posição na superfície da Terra baixar sua altura (paralaxe).
        val geocentricAltitude = altitude(hourAngle, phi, moon.declination)
        val parallax = asin(EARTH_RADIUS_KM / moon.distanceKm) * cos(geocentricAltitude)

        val elongation = acos(
            sin(sun.declination) * sin(moon.declination) +
                cos(sun.declination) * cos(moon.declination) * cos(sun.rightAscension - moon.rightAscension),
        )
        val phaseAngle = atan2(
            sun.distanceKm * sin(elongation),
            moon.distanceKm - sun.distanceKm * cos(elongation),
        )
        // Ângulo de posição do lado iluminado (Meeus 48.5): só o sinal importa, para crescente/minguante.
        val brightLimb = atan2(
            cos(sun.declination) * sin(sun.rightAscension - moon.rightAscension),
            sin(sun.declination) * cos(moon.declination) -
                cos(sun.declination) * sin(moon.declination) * cos(sun.rightAscension - moon.rightAscension),
        )
        val (stepHourAngle, stepAltitude) = stepTowardSun(moon, sun, sidereal, phi, hourAngle, geocentricAltitude)

        return MoonPosition(
            altitudeDeg = (geocentricAltitude - parallax) / RAD,
            azimuthDeg = northAzimuthDeg(hourAngle, phi, moon.declination),
            hourAngleDeg = normalize180(hourAngle / RAD),
            illuminatedFraction = (1 + cos(phaseAngle)) / 2,
            phase = 0.5 + 0.5 * phaseAngle * (if (brightLimb < 0) -1 else 1) / PI,
            litSideHourAngleStep = stepHourAngle,
            litSideAltitudeStep = stepAltitude,
        )
    }

    /** Anda 1° pelo grande círculo da Lua até o Sol e devolve a variação (ângulo horário, altura) em graus. */
    private fun stepTowardSun(
        moon: Equatorial,
        sun: Equatorial,
        sidereal: Double,
        phi: Double,
        moonHourAngle: Double,
        moonAltitude: Double,
    ): Pair<Double, Double> {
        val m = unitVector(moon)
        val s = unitVector(sun)
        val dot = m[0] * s[0] + m[1] * s[1] + m[2] * s[2]
        val t = DoubleArray(3) { s[it] - dot * m[it] }
        val norm = sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2])
        if (norm < 1e-9) return 0.0 to -1.0
        val step = RAD
        val p = DoubleArray(3) { m[it] * cos(step) + t[it] / norm * sin(step) }
        val pointRightAscension = atan2(p[1], p[0])
        val pointDeclination = asin(p[2].coerceIn(-1.0, 1.0))
        val pointHourAngle = sidereal - pointRightAscension
        val pointAltitude = altitude(pointHourAngle, phi, pointDeclination)
        return normalize180((pointHourAngle - moonHourAngle) / RAD) to (pointAltitude - moonAltitude) / RAD
    }

    private fun unitVector(e: Equatorial) = doubleArrayOf(
        cos(e.declination) * cos(e.rightAscension),
        cos(e.declination) * sin(e.rightAscension),
        sin(e.declination),
    )

    /** Hora sideral local em graus (0 a 360): define a rotação aparente do céu estrelado. */
    fun localSiderealDeg(instant: Instant, longitudeDeg: Double): Double =
        normalize360(siderealTime(daysSinceJ2000(instant), longitudeDeg) / RAD)

    private class Equatorial(val rightAscension: Double, val declination: Double, val distanceKm: Double)

    private fun daysSinceJ2000(instant: Instant) = instant.toEpochMilli() / DAY_MS + JULIAN_UNIX_EPOCH - JULIAN_J2000

    private fun siderealTime(d: Double, longitudeDeg: Double) = RAD * (280.16 + 360.985_623_5 * d + longitudeDeg)

    private fun rightAscension(longitude: Double, latitude: Double) =
        atan2(sin(longitude) * cos(OBLIQUITY) - tan(latitude) * sin(OBLIQUITY), cos(longitude))

    private fun declination(longitude: Double, latitude: Double) =
        asin(sin(latitude) * cos(OBLIQUITY) + cos(latitude) * sin(OBLIQUITY) * sin(longitude))

    private fun altitude(hourAngle: Double, phi: Double, declination: Double) =
        asin(sin(phi) * sin(declination) + cos(phi) * cos(declination) * cos(hourAngle))

    private fun northAzimuthDeg(hourAngle: Double, phi: Double, declination: Double): Double {
        val fromSouth = atan2(sin(hourAngle), cos(hourAngle) * sin(phi) - tan(declination) * cos(phi))
        return normalize360(fromSouth / RAD + 180)
    }

    private fun sunCoordinates(d: Double): Equatorial {
        val meanAnomaly = RAD * (357.5291 + 0.985_600_28 * d)
        val center = RAD * (1.9148 * sin(meanAnomaly) + 0.02 * sin(2 * meanAnomaly) + 0.0003 * sin(3 * meanAnomaly))
        val eclipticLongitude = meanAnomaly + center + RAD * 102.9372 + PI
        return Equatorial(rightAscension(eclipticLongitude, 0.0), declination(eclipticLongitude, 0.0), SUN_DISTANCE_KM)
    }

    private fun moonCoordinates(d: Double): Equatorial {
        val meanLongitude = RAD * (218.316 + 13.176_396 * d)
        val meanAnomaly = RAD * (134.963 + 13.064_993 * d)
        val meanDistance = RAD * (93.272 + 13.229_350 * d)
        val longitude = meanLongitude + RAD * 6.289 * sin(meanAnomaly)
        val latitude = RAD * 5.128 * sin(meanDistance)
        val distanceKm = 385_001 - 20_905 * cos(meanAnomaly)
        return Equatorial(rightAscension(longitude, latitude), declination(longitude, latitude), distanceKm)
    }

    private fun normalize360(deg: Double): Double = ((deg % 360) + 360) % 360

    private fun normalize180(deg: Double): Double {
        val d = normalize360(deg)
        return if (d > 180) d - 360 else d
    }
}
