package com.rtaylor.climbsense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimbMathTest {

    private fun profileOf(vararg points: Pair<Double, Double>): ElevationProfile =
        ElevationProfile.fromPolyline(Polyline.encode(points.toList(), 10.0))!!

    // A 1 km climb at constant 8%, starting 2 km into the route
    private val constantClimb = ClimbSpan(start = 2000.0, end = 3000.0, grade = 8.0, totalElevation = 80.0)
    private val constantProfile = profileOf(
        0.0 to 50.0, 2000.0 to 50.0, 3000.0 to 130.0, 4000.0 to 130.0,
    )

    // Two-pitch climb at route start: 500 m @ 4%, then 500 m @ 12%
    private val twoPitchClimb = ClimbSpan(start = 0.0, end = 1000.0, grade = 8.0, totalElevation = 80.0)
    private val twoPitchProfile = profileOf(0.0 to 0.0, 500.0 to 20.0, 1000.0 to 80.0)

    @Test
    fun `currentClimb finds the climb containing progress`() {
        val climbs = listOf(twoPitchClimb, constantClimb)
        assertEquals(twoPitchClimb, ClimbMath.currentClimb(climbs, 500.0))
        assertEquals(constantClimb, ClimbMath.currentClimb(climbs, 2500.0))
    }

    @Test
    fun `currentClimb is null between and past climbs`() {
        val climbs = listOf(twoPitchClimb, constantClimb)
        assertNull(ClimbMath.currentClimb(climbs, 1500.0))
        assertNull(ClimbMath.currentClimb(climbs, 3000.0)) // exactly at end = done
        assertNull(ClimbMath.currentClimb(climbs, 5000.0))
    }

    @Test
    fun `gradeRemain on a constant climb is the climb grade everywhere`() {
        assertEquals(8.0, ClimbMath.gradeRemain(constantProfile, constantClimb, 2000.0)!!, 0.05)
        assertEquals(8.0, ClimbMath.gradeRemain(constantProfile, constantClimb, 2500.0)!!, 0.05)
        assertEquals(8.0, ClimbMath.gradeRemain(constantProfile, constantClimb, 2900.0)!!, 0.1)
    }

    @Test
    fun `gradeRemain rises as the easy pitch is ridden off`() {
        // At the base: whole climb average = 8%
        assertEquals(8.0, ClimbMath.gradeRemain(twoPitchProfile, twoPitchClimb, 0.0)!!, 0.05)
        // Halfway: only the 12% pitch remains
        assertEquals(12.0, ClimbMath.gradeRemain(twoPitchProfile, twoPitchClimb, 500.0)!!, 0.05)
        // Mid first pitch: mix of 250m@4 + 500m@12
        assertEquals((10.0 + 60.0) / 750.0 * 100.0, ClimbMath.gradeRemain(twoPitchProfile, twoPitchClimb, 250.0)!!, 0.05)
    }

    @Test
    fun `gradeRemain averages through a dip`() {
        // 400m @ 8, 200m dip @ -3, 400m @ 8.5 -> total 60m over 1000m
        val dipProfile = profileOf(0.0 to 0.0, 400.0 to 32.0, 600.0 to 26.0, 1000.0 to 60.0)
        val climb = ClimbSpan(0.0, 1000.0, 6.0, 60.0)
        assertEquals(6.0, ClimbMath.gradeRemain(dipProfile, climb, 0.0)!!, 0.05)
    }

    @Test
    fun `gradeRemain is null near the top and without a profile`() {
        assertNull(ClimbMath.gradeRemain(constantProfile, constantClimb, 2990.0)) // 10m left < MIN_SPAN
        assertNull(ClimbMath.gradeRemain(null, constantClimb, 2500.0))
    }

    @Test
    fun `gradeRemain clamps progress into the climb`() {
        // slightly before the start behaves like the start
        assertEquals(8.0, ClimbMath.gradeRemain(constantProfile, constantClimb, 1990.0)!!, 0.05)
    }

    @Test
    fun `maxAhead reports the steepest remaining pitch`() {
        assertEquals(12.0, ClimbMath.maxAhead(twoPitchProfile, twoPitchClimb, 0.0)!!, 0.1)
        assertEquals(12.0, ClimbMath.maxAhead(twoPitchProfile, twoPitchClimb, 700.0)!!, 0.1)
        assertEquals(8.0, ClimbMath.maxAhead(constantProfile, constantClimb, 2500.0)!!, 0.1)
    }

    @Test
    fun `maxAhead is null near the top and without a profile`() {
        assertNull(ClimbMath.maxAhead(constantProfile, constantClimb, 2990.0))
        assertNull(ClimbMath.maxAhead(null, twoPitchClimb, 0.0))
    }

    @Test
    fun `next500 averages the next 500m of route`() {
        assertEquals(8.0, ClimbMath.next500(twoPitchProfile, 250.0)!!, 0.05) // 250@4 + 250@12
        assertEquals(4.0, ClimbMath.next500(twoPitchProfile, 0.0)!!, 0.05)   // all shallow pitch
    }

    @Test
    fun `next500 works on flats and descents`() {
        val descent = profileOf(0.0 to 100.0, 1000.0 to 50.0)
        assertEquals(-5.0, ClimbMath.next500(descent, 100.0)!!, 0.05)
    }

    @Test
    fun `next500 clamps at the end of the route`() {
        // 300m of route left -> average over those 300m
        assertEquals(12.0, ClimbMath.next500(twoPitchProfile, 700.0)!!, 0.05)
    }

    @Test
    fun `next500 is null when almost no route remains or profile missing`() {
        assertNull(ClimbMath.next500(twoPitchProfile, 990.0))
        assertNull(ClimbMath.next500(null, 0.0))
    }
}
