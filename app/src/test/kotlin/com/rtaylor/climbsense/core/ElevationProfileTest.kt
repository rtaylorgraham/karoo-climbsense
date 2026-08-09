package com.rtaylor.climbsense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ElevationProfileTest {

    // Helper: encode (distance, elevation) pairs the way Karoo does (factor 10)
    private fun profileOf(vararg points: Pair<Double, Double>): ElevationProfile {
        val encoded = Polyline.encode(points.toList(), 10.0)
        return ElevationProfile.fromPolyline(encoded)!!
    }

    @Test
    fun `fromPolyline returns null for null input`() {
        assertNull(ElevationProfile.fromPolyline(null))
    }

    @Test
    fun `fromPolyline returns null for fewer than two points`() {
        assertNull(ElevationProfile.fromPolyline(""))
        assertNull(ElevationProfile.fromPolyline(Polyline.encode(listOf(0.0 to 100.0), 10.0)))
    }

    @Test
    fun `interpolates elevation linearly between sparse points`() {
        val p = profileOf(0.0 to 100.0, 1000.0 to 180.0)
        assertEquals(100.0, p.elevationAt(0.0), 0.1)
        assertEquals(140.0, p.elevationAt(500.0), 0.1)
        assertEquals(180.0, p.elevationAt(1000.0), 0.1)
    }

    @Test
    fun `clamps elevation queries outside the profile range`() {
        val p = profileOf(0.0 to 100.0, 1000.0 to 180.0)
        assertEquals(100.0, p.elevationAt(-50.0), 0.1)
        assertEquals(180.0, p.elevationAt(2000.0), 0.1)
    }

    @Test
    fun `handles unsorted input by sorting on distance`() {
        val encoded = Polyline.encode(listOf(1000.0 to 180.0, 0.0 to 100.0), 10.0)
        val p = ElevationProfile.fromPolyline(encoded)!!
        assertEquals(140.0, p.elevationAt(500.0), 0.1)
    }

    @Test
    fun `avgGrade of a constant 8 percent climb is 8`() {
        val p = profileOf(0.0 to 0.0, 1000.0 to 80.0)
        assertEquals(8.0, p.avgGrade(0.0, 1000.0)!!, 0.05)
        assertEquals(8.0, p.avgGrade(250.0, 750.0)!!, 0.05)
    }

    @Test
    fun `avgGrade returns null below minimum span`() {
        val p = profileOf(0.0 to 0.0, 1000.0 to 80.0)
        assertNull(p.avgGrade(500.0, 510.0))
        assertNull(p.avgGrade(500.0, 500.0))
        assertNull(p.avgGrade(600.0, 500.0))
    }

    @Test
    fun `maxWindowGrade finds the steepest 100m window in a two pitch climb`() {
        // 500m at 4%, then 500m at 12%
        val p = profileOf(0.0 to 0.0, 500.0 to 20.0, 1000.0 to 80.0)
        assertEquals(12.0, p.maxWindowGrade(0.0, 1000.0)!!, 0.1)
        // Only the shallow pitch in range -> 4%
        assertEquals(4.0, p.maxWindowGrade(0.0, 500.0)!!, 0.1)
    }

    @Test
    fun `maxWindowGrade never reports a descent dip as the max`() {
        // climb, then a -3% dip, then climb again
        val p = profileOf(0.0 to 0.0, 400.0 to 32.0, 600.0 to 26.0, 1000.0 to 60.0)
        val max = p.maxWindowGrade(0.0, 1000.0)!!
        assertEquals(8.5, max, 0.2) // steepest is the final pitch (34m over 400m)
    }

    @Test
    fun `maxWindowGrade degrades to a short window when less than 100m remains`() {
        val p = profileOf(0.0 to 0.0, 1000.0 to 80.0)
        // 60m remaining window, still >= MIN_SPAN
        val g = p.maxWindowGrade(940.0, 1000.0)
        assertNotNull(g)
        assertEquals(8.0, g!!, 0.15)
    }

    @Test
    fun `maxWindowGrade returns null below minimum span`() {
        val p = profileOf(0.0 to 0.0, 1000.0 to 80.0)
        assertNull(p.maxWindowGrade(990.0, 1000.0))
    }

    @Test
    fun `totalDistance is the last point distance`() {
        val p = profileOf(0.0 to 0.0, 1500.0 to 80.0)
        assertEquals(1500.0, p.totalDistance, 0.1)
    }
}
