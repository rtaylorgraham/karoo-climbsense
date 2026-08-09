package com.rtaylor.climbsense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimbLocatorTest {

    // The user's route, as reported by the OS
    private val c1 = ClimbSpan(10800.0, 27030.0, 6.9, 1129.7)
    private val c2 = ClimbSpan(31290.0, 37230.0, 7.0, 414.6)
    private val c3 = ClimbSpan(64710.0, 72810.0, 4.8, 393.1)
    private val climbs = listOf(c1, c2, c3)

    // Real CLIMB-stream sample captured mid-climb on c2
    private val onC2 = ClimbPosition(
        distanceFromBottom = 5714.87,
        distanceToTop = 225.13,
        elevationFromBottom = 403.62,
        elevationToTop = 10.98,
    )

    @Test
    fun `derives length and ascent from the stream fields`() {
        assertEquals(5940.0, onC2.length, 0.1)
        assertEquals(414.6, onC2.ascent, 0.1)
    }

    @Test
    fun `locates the climb by length and ascent, ignoring the distance frame`() {
        val located = ClimbMath.locate(climbs, onC2)!!
        assertEquals(c2, located.climb)
        // position = climb start + how far up we are
        assertEquals(31290.0 + 5714.87, located.position, 0.1)
    }

    @Test
    fun `locating works regardless of what route progress claims`() {
        // This is the real-world failure: progress said 25 km (matcher on the
        // wrong pass of a loop) while we were physically 5.7 km up climb 2.
        val located = ClimbMath.locate(climbs, onC2)!!
        assertEquals(c2, located.climb)
    }

    @Test
    fun `rejects a climb the route does not contain`() {
        val unknown = ClimbPosition(100.0, 900.0, 10.0, 90.0) // 1 km, 100 m
        assertNull(ClimbMath.locate(climbs, unknown))
    }

    @Test
    fun `tolerates small drift in the stream values`() {
        val drifted = onC2.copy(distanceFromBottom = 5714.87 + 40.0, elevationToTop = 10.98 + 6.0)
        assertEquals(c2, ClimbMath.locate(climbs, drifted)!!.climb)
    }

    @Test
    fun `disambiguates identical climbs using a progress hint`() {
        // A loop that rides the same climb twice
        val first = ClimbSpan(5000.0, 10940.0, 7.0, 414.6)
        val second = ClimbSpan(45000.0, 50940.0, 7.0, 414.6)
        val both = listOf(first, second)
        assertEquals(first, ClimbMath.locate(both, onC2, hint = 8000.0)!!.climb)
        assertEquals(second, ClimbMath.locate(both, onC2, hint = 47000.0)!!.climb)
        // no hint: first match wins
        assertEquals(first, ClimbMath.locate(both, onC2)!!.climb)
    }

    @Test
    fun `effectivePosition prefers the climb stream over route progress`() {
        val route = RouteModel("r", 90112.0, null, climbs)
        assertEquals(
            31290.0 + 5714.87,
            ClimbMath.effectivePosition(route, dtdProgress = 25537.0, climbPosition = onC2)!!,
            0.1,
        )
    }

    @Test
    fun `effectivePosition falls back to route progress when not on a climb`() {
        val route = RouteModel("r", 90112.0, null, climbs)
        assertEquals(25537.0, ClimbMath.effectivePosition(route, 25537.0, null)!!, 0.1)
        assertNull(ClimbMath.effectivePosition(route, null, null))
        // unmatched climb + no progress -> nothing
        assertNull(ClimbMath.effectivePosition(route, null, ClimbPosition(1.0, 999.0, 1.0, 99.0)))
    }

    @Test
    fun `effectivePosition needs a route`() {
        assertNull(ClimbMath.effectivePosition(null, 100.0, onC2))
    }
}
