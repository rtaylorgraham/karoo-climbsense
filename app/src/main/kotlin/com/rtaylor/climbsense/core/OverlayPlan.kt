package com.rtaylor.climbsense.core

/**
 * Pure plan of what the map layer should draw for a route: gradient-colored
 * polylines plus climb start/summit markers. The extension layer converts this
 * into karoo-ext MapEffects.
 */
data class OverlayPlan(
    val polylines: List<Line>,
    val markers: List<Marker>,
) {
    data class Line(val id: String, val encodedPolyline: String, val color: Int, val width: Int)

    data class Marker(val id: String, val lat: Double, val lng: Double, val type: String, val name: String)

    companion object {
        const val COLOR_DESCENT = 0xFF2E86DE.toInt()
        const val COLOR_EASY = 0xFFF7D354.toInt()
        const val COLOR_MODERATE = 0xFFF39C12.toInt()
        const val COLOR_HARD = 0xFFE74C3C.toInt()
        const val COLOR_EXTREME = 0xFF96281B.toInt()

        val EMPTY = OverlayPlan(emptyList(), emptyList())

        private const val LINE_WIDTH = 8
        private const val ROUTE_POLYLINE_FACTOR = 1e5

        fun forRoute(route: RouteModel): OverlayPlan {
            val path = route.path ?: return EMPTY
            val lines = route.profile?.let { profile ->
                GradientSegments.compute(profile).mapIndexed { i, seg ->
                    Line(
                        id = "climbsense-seg-$i",
                        encodedPolyline = Polyline.encode(path.subPath(seg.start, seg.end), ROUTE_POLYLINE_FACTOR),
                        color = colorFor(seg.bin),
                        width = LINE_WIDTH,
                    )
                }
            } ?: emptyList()
            val markers = route.climbs.flatMapIndexed { i, climb ->
                val (startLat, startLng) = path.pointAt(climb.start)
                val (topLat, topLng) = path.pointAt(climb.end)
                val km = (climb.end - climb.start) / 1000.0
                val label = String.format(
                    java.util.Locale.US,
                    "C%d %.1fkm @ %.0f%%",
                    i + 1,
                    km,
                    climb.grade,
                )
                listOf(
                    Marker("climbsense-poi-start-$i", startLat, startLng, "generic", label),
                    Marker("climbsense-poi-top-$i", topLat, topLng, "summit", "Top C${i + 1}"),
                )
            }
            return OverlayPlan(lines, markers)
        }

        fun colorFor(bin: GradientSegments.Bin): Int = when (bin) {
            GradientSegments.Bin.DESCENT -> COLOR_DESCENT
            GradientSegments.Bin.CLIMB_EASY -> COLOR_EASY
            GradientSegments.Bin.CLIMB_MODERATE -> COLOR_MODERATE
            GradientSegments.Bin.CLIMB_HARD -> COLOR_HARD
            GradientSegments.Bin.CLIMB_EXTREME -> COLOR_EXTREME
        }
    }
}
