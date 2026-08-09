package com.rtaylor.climbsense.core

/**
 * Route elevation profile decoded from Karoo's routeElevationPolyline:
 * (distance along route in m, elevation in m), cumulative distance.
 */
class ElevationProfile private constructor(
    private val points: List<Point>,
) {
    data class Point(val distance: Double, val elevation: Double)

    val totalDistance: Double get() = points.last().distance

    fun elevationAt(distance: Double): Double {
        val d = distance.coerceIn(points.first().distance, points.last().distance)
        var lo = 0
        var hi = points.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (points[mid].distance <= d) lo = mid else hi = mid
        }
        val a = points[lo]
        val b = points[hi]
        if (b.distance == a.distance) return a.elevation
        val t = (d - a.distance) / (b.distance - a.distance)
        return a.elevation + t * (b.elevation - a.elevation)
    }

    /** Average grade (%) between two distances; null when the span is too short to be meaningful. */
    fun avgGrade(from: Double, to: Double): Double? {
        val span = to - from
        if (span < MIN_SPAN) return null
        return (elevationAt(to) - elevationAt(from)) / span * 100.0
    }

    /**
     * Steepest [window]-meter stretch between [from] and [to] (max of window average grades).
     * Degrades to a single short window when less than [window] remains; null below MIN_SPAN.
     */
    fun maxWindowGrade(from: Double, to: Double, window: Double = WINDOW): Double? {
        val span = to - from
        if (span < MIN_SPAN) return null
        if (span <= window) return avgGrade(from, to)

        val lastStart = to - window
        val starts = buildList {
            var s = from
            while (s < lastStart) {
                add(s)
                s += STEP
            }
            add(lastStart)
            // Anchor windows at actual profile points so sharp pitch boundaries aren't stepped over
            points.forEach { p ->
                if (p.distance in from..lastStart) add(p.distance)
            }
        }
        return starts.mapNotNull { s -> avgGrade(s, s + window) }.maxOrNull()
    }

    companion object {
        const val MIN_SPAN = 20.0
        const val WINDOW = 100.0
        private const val STEP = 25.0
        private const val ELEVATION_POLYLINE_FACTOR = 10.0

        fun fromPolyline(encoded: String?): ElevationProfile? {
            if (encoded == null) return null
            val points = Polyline.decode(encoded, ELEVATION_POLYLINE_FACTOR)
                .map { (distance, elevation) -> Point(distance, elevation) }
                .sortedBy { it.distance }
                .distinctBy { it.distance }
            if (points.size < 2) return null
            return ElevationProfile(points)
        }
    }
}
