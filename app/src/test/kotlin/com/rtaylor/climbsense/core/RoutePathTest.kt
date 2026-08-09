package com.rtaylor.climbsense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutePathTest {

    // A simple path heading due north: ~111.2m per 0.001 degrees latitude.
    // 4 points, 3 equal legs of ~111.2m => ~333.6m total.
    private val northPolyline = Polyline.encode(
        listOf(
            45.000 to 10.000,
            45.001 to 10.000,
            45.002 to 10.000,
            45.003 to 10.000,
        ),
        1e5,
    )

    @Test
    fun `fromPolyline returns null for null or degenerate input`() {
        assertNull(RoutePath.fromPolyline(null, 1000.0))
        assertNull(RoutePath.fromPolyline(Polyline.encode(listOf(45.0 to 10.0), 1e5), 1000.0))
    }

    @Test
    fun `scales its length to the declared route distance`() {
        // Declared distance differs from geometric length; distances the caller uses
        // (from the elevation profile) must land proportionally on the geometry.
        val path = RoutePath.fromPolyline(northPolyline, 300.0)!!
        val (lat, lng) = path.pointAt(150.0) // halfway of declared -> halfway of geometry
        assertEquals(45.0015, lat, 1e-4)
        assertEquals(10.0, lng, 1e-6)
    }

    @Test
    fun `pointAt interpolates along the path and clamps at the ends`() {
        val path = RoutePath.fromPolyline(northPolyline, 333.6)!!
        assertEquals(45.000, path.pointAt(-5.0).first, 1e-6)
        assertEquals(45.003, path.pointAt(9999.0).first, 1e-6)
        val mid = path.pointAt(166.8)
        assertEquals(45.0015, mid.first, 1e-4)
    }

    @Test
    fun `subPath returns the geometry between two distances including cut points`() {
        val path = RoutePath.fromPolyline(northPolyline, 333.6)!!
        val sub = path.subPath(55.6, 222.4) // half leg 1 -> end of leg 2
        // starts at interpolated cut, includes the two interior vertices region
        assertEquals(45.0005, sub.first().first, 2e-4)
        assertEquals(45.002, sub.last().first, 2e-4)
        // all points on the meridian
        sub.forEach { assertEquals(10.0, it.second, 1e-6) }
        // distances monotonically increase northward
        sub.zipWithNext().forEach { (a, b) -> assert(b.first >= a.first) }
    }

    @Test
    fun `subPath of a full range returns the whole path`() {
        val path = RoutePath.fromPolyline(northPolyline, 333.6)!!
        val sub = path.subPath(0.0, 333.6)
        assertEquals(45.000, sub.first().first, 1e-6)
        assertEquals(45.003, sub.last().first, 1e-6)
    }
}
