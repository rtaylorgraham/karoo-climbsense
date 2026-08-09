package com.rtaylor.climbsense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineTest {

    @Test
    fun `decodes known google reference vector at precision 5`() {
        val points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 1e5)
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].first, 1e-6)
        assertEquals(-120.2, points[0].second, 1e-6)
        assertEquals(40.7, points[1].first, 1e-6)
        assertEquals(-120.95, points[1].second, 1e-6)
        assertEquals(43.252, points[2].first, 1e-6)
        assertEquals(-126.453, points[2].second, 1e-6)
    }

    @Test
    fun `round trips distance-elevation pairs at factor 10`() {
        // factor 10 = precision 1: how routeElevationPolyline encodes (distance m, elevation m)
        val original = listOf(0.0 to 100.0, 250.5 to 112.3, 1000.0 to 180.0, 1500.2 to 175.6)
        val encoded = Polyline.encode(original, 10.0)
        val decoded = Polyline.decode(encoded, 10.0)
        assertEquals(original.size, decoded.size)
        original.zip(decoded).forEach { (o, d) ->
            assertEquals(o.first, d.first, 0.051)
            assertEquals(o.second, d.second, 0.051)
        }
    }

    @Test
    fun `decodes empty string to empty list`() {
        assertTrue(Polyline.decode("", 10.0).isEmpty())
    }
}
