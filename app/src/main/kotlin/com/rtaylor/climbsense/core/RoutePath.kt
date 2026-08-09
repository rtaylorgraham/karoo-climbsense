package com.rtaylor.climbsense.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geographic route geometry addressable by distance along the route.
 *
 * Cumulative geometric distances are scaled to the route's declared distance so
 * that positions from the elevation profile / progress land at the right spots
 * even when the geometric (haversine) length differs slightly.
 */
class RoutePath private constructor(
    private val points: List<Pair<Double, Double>>,
    private val cumulative: DoubleArray,
) {
    private val total: Double get() = cumulative.last()

    fun pointAt(distance: Double): Pair<Double, Double> {
        val d = distance.coerceIn(0.0, total)
        val i = segmentIndex(d)
        return interpolate(i, d)
    }

    /**
     * Geometry between two distances: interpolated cut points at both ends plus
     * every route vertex in between.
     */
    fun subPath(from: Double, to: Double): List<Pair<Double, Double>> {
        val f = from.coerceIn(0.0, total)
        val t = to.coerceIn(0.0, total)
        if (t <= f) return listOf(pointAt(f))
        val result = mutableListOf(pointAt(f))
        var i = segmentIndex(f) + 1
        while (i < points.size && cumulative[i] < t) {
            result.add(points[i])
            i++
        }
        result.add(pointAt(t))
        return result
    }

    /** Index of the segment start vertex for a distance (last vertex at or before d). */
    private fun segmentIndex(d: Double): Int {
        var lo = 0
        var hi = points.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (cumulative[mid] <= d) lo = mid else hi = mid
        }
        return lo
    }

    private fun interpolate(i: Int, d: Double): Pair<Double, Double> {
        if (i >= points.size - 1) return points.last()
        val span = cumulative[i + 1] - cumulative[i]
        if (span <= 0.0) return points[i]
        val t = (d - cumulative[i]) / span
        val (lat1, lng1) = points[i]
        val (lat2, lng2) = points[i + 1]
        return lat1 + t * (lat2 - lat1) to lng1 + t * (lng2 - lng1)
    }

    companion object {
        private const val EARTH_RADIUS = 6371000.0
        private const val ROUTE_POLYLINE_FACTOR = 1e5

        fun fromPolyline(encoded: String?, routeDistance: Double): RoutePath? {
            if (encoded == null) return null
            val points = Polyline.decode(encoded, ROUTE_POLYLINE_FACTOR)
            if (points.size < 2) return null
            val cumulative = DoubleArray(points.size)
            for (i in 1 until points.size) {
                cumulative[i] = cumulative[i - 1] + haversine(points[i - 1], points[i])
            }
            val geometricTotal = cumulative.last()
            if (geometricTotal <= 0.0) return null
            // Scale geometric distances into the declared route-distance domain
            if (routeDistance > 0.0) {
                val scale = routeDistance / geometricTotal
                for (i in cumulative.indices) cumulative[i] *= scale
            }
            return RoutePath(points, cumulative)
        }

        private fun haversine(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
            val la1 = Math.toRadians(p1.first)
            val la2 = Math.toRadians(p2.first)
            val dLat = Math.toRadians(p2.first - p1.first)
            val dLng = Math.toRadians(p2.second - p1.second)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(la1) * cos(la2) * sin(dLng / 2) * sin(dLng / 2)
            return 2 * EARTH_RADIUS * asin(sqrt(a))
        }
    }
}
