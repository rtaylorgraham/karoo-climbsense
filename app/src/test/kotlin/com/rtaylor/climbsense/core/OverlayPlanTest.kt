package com.rtaylor.climbsense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlanTest {

    // Straight-north route: 3 km, climb from 1000m to 2000m at 8%, flat elsewhere
    private fun routeModel(): RouteModel {
        val geo = (0..30).map { 45.0 + it * 0.001 to 10.0 } // ~3336m geometric, scaled to 3000
        val poly = Polyline.encode(geo, 1e5)
        val elev = listOf(0.0 to 100.0, 1000.0 to 100.0, 2000.0 to 180.0, 3000.0 to 180.0)
        val profile = ElevationProfile.fromPolyline(Polyline.encode(elev, 10.0))!!
        return RouteModel(
            routeKey = "test",
            totalDistance = 3000.0,
            profile = profile,
            climbs = listOf(ClimbSpan(1000.0, 2000.0, 8.0, 80.0)),
            path = RoutePath.fromPolyline(poly, 3000.0),
        )
    }

    @Test
    fun `plans one colored polyline for the climb segment`() {
        val plan = OverlayPlan.forRoute(routeModel())
        assertEquals(1, plan.polylines.size)
        val line = plan.polylines[0]
        assertEquals(OverlayPlan.COLOR_HARD, line.color)
        assertTrue(line.id.startsWith("climbsense-seg"))
        // decodes to real geometry roughly spanning the climb (1000-2000m of 3000m north path)
        val pts = Polyline.decode(line.encodedPolyline, 1e5)
        assertTrue(pts.size >= 2)
        assertTrue(pts.first().first > 45.008 && pts.first().first < 45.012)
        assertTrue(pts.last().first > 45.018 && pts.last().first < 45.022)
    }

    @Test
    fun `plans start and summit markers per climb`() {
        val plan = OverlayPlan.forRoute(routeModel())
        assertEquals(2, plan.markers.size)
        val start = plan.markers.first { it.type == "generic" }
        val summit = plan.markers.first { it.type == "summit" }
        assertTrue(start.name.contains("8")) // grade in the label
        assertTrue(start.name.contains("1.0km") || start.name.contains("1,0km"))
        assertEquals(45.010, start.lat, 0.002)
        assertEquals(45.020, summit.lat, 0.002)
        assertTrue(plan.markers.all { it.id.startsWith("climbsense-poi") })
    }

    @Test
    fun `empty plan when profile or path missing`() {
        val m = routeModel()
        assertTrue(OverlayPlan.forRoute(m.copy(profile = null)).polylines.isEmpty())
        assertTrue(OverlayPlan.forRoute(m.copy(path = null)).polylines.isEmpty())
        // markers still possible without profile (climbs + path suffice)
        assertEquals(2, OverlayPlan.forRoute(m.copy(profile = null)).markers.size)
    }

    @Test
    fun `all ids are unique`() {
        val plan = OverlayPlan.forRoute(routeModel())
        val ids = plan.polylines.map { it.id } + plan.markers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
