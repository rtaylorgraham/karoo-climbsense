package com.rtaylor.climbsense.core

import kotlin.math.roundToLong

/**
 * Google encoded polyline codec with a configurable precision factor.
 * Karoo's routeElevationPolyline uses factor 10 (precision 1) with pairs of
 * (distance along route in meters, elevation in meters).
 */
object Polyline {

    fun decode(encoded: String, factor: Double): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var first = 0L
        var second = 0L
        while (index < encoded.length) {
            var shift = 0
            var acc = 0L
            var b: Int
            do {
                b = encoded[index++].code - 63
                acc = acc or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            first += if (acc and 1L != 0L) (acc shr 1).inv() else acc shr 1

            shift = 0
            acc = 0L
            do {
                b = encoded[index++].code - 63
                acc = acc or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            second += if (acc and 1L != 0L) (acc shr 1).inv() else acc shr 1

            result.add(first / factor to second / factor)
        }
        return result
    }

    fun encode(points: List<Pair<Double, Double>>, factor: Double): String {
        val sb = StringBuilder()
        var prevFirst = 0L
        var prevSecond = 0L
        for ((first, second) in points) {
            val f = (first * factor).roundToLong()
            val s = (second * factor).roundToLong()
            encodeValue(f - prevFirst, sb)
            encodeValue(s - prevSecond, sb)
            prevFirst = f
            prevSecond = s
        }
        return sb.toString()
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append((((v and 0x1f) or 0x20) + 63).toInt().toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }
}
