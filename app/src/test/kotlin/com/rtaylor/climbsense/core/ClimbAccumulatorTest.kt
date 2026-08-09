package com.rtaylor.climbsense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbAccumulatorTest {

    private val climbA = ClimbSpan(10800.0, 27030.0, 6.9, 1129.7)
    private val climbB = ClimbSpan(31290.0, 37230.0, 7.0, 414.6)
    private val climbC = ClimbSpan(64710.0, 72810.0, 4.8, 393.1)

    @Test
    fun `keeps climbs the device has pruned from later emissions`() {
        val acc = ClimbAccumulator()
        acc.update("route1", progress = 0.0, climbs = listOf(climbA, climbB, climbC))
        // Karoo prunes passed/current climbs from NavigatingRoute.climbs while riding
        val merged = acc.update("route1", progress = 35000.0, climbs = listOf(climbC))
        assertEquals(listOf(climbA, climbB, climbC), merged)
        // The current climb is still findable mid-climb
        assertNotNull(ClimbMath.currentClimb(merged, 35000.0))
    }

    @Test
    fun `re-emitted climbs in a shifted match frame update rather than duplicate`() {
        val acc = ClimbAccumulator()
        acc.update("route1", progress = 0.0, climbs = listOf(climbA, climbB))
        // Same climbs re-emitted with start distances shifted (route re-match):
        val shiftedA = climbA.copy(start = climbA.start + 480.0, end = climbA.end + 480.0)
        val shiftedB = climbB.copy(start = climbB.start + 480.0, end = climbB.end + 480.0)
        val merged = acc.update("route1", progress = 0.0, climbs = listOf(shiftedA, shiftedB))
        assertEquals(2, merged.size)
        // and the CURRENT frame's positions win
        assertEquals(shiftedA.start, merged[0].start, 1e-9)
    }

    @Test
    fun `drops remembered upcoming climbs the device no longer reports`() {
        val acc = ClimbAccumulator()
        acc.update("route1", progress = 0.0, climbs = listOf(climbA, climbB, climbC))
        // A later emission (rider before all climbs) lists only B and C: A ahead of
        // the rider but missing from the OS list = stale garbage from an old frame
        val merged = acc.update("route1", progress = 5000.0, climbs = listOf(climbB, climbC))
        assertEquals(listOf(climbB, climbC), merged)
    }

    @Test
    fun `never prunes the climb the rider is currently on`() {
        // The nasty real-world combination: route matching is behind (progress
        // says 25 km) AND the device has pruned the current climb from its list.
        // Without protection we would drop climb B — the one we are climbing —
        // and every profile-based field would go blank.
        val acc = ClimbAccumulator()
        acc.update("route1", progress = 0.0, climbs = listOf(climbA, climbB, climbC))
        val onB = ClimbPosition(
            distanceFromBottom = 5714.87,
            distanceToTop = 225.13,
            elevationFromBottom = 403.62,
            elevationToTop = 10.98,
        )
        val merged = acc.update("route1", progress = 25537.0, climbs = listOf(climbC), onClimb = onB)
        // climbA is behind progress so it stays as a completed climb; climbB is
        // ahead of progress and unreported, but protected because we're on it.
        assertTrue(merged.contains(climbB))
        assertEquals(listOf(climbA, climbB, climbC), merged)
    }

    @Test
    fun `synthesizes the climb under the wheels when the device never listed it`() {
        // Observed on device: start following a route while already part-way up a
        // climb and the OS lists only the climbs still ahead — the current climb
        // never appears, so it cannot be matched and every profile field blanks.
        val acc = ClimbAccumulator()
        val nearTopOfB = ClimbPosition(
            distanceFromBottom = 5520.0,
            distanceToTop = 420.0,
            elevationFromBottom = 386.6,
            elevationToTop = 28.0,
        )
        val merged = acc.update("route1", progress = 36810.0, climbs = listOf(climbC), onClimb = nearTopOfB)

        val located = ClimbMath.locate(merged, nearTopOfB)!!
        assertEquals(31290.0, located.climb.start, 5.0)
        assertEquals(37230.0, located.climb.end, 5.0)
        assertEquals(36810.0, located.position, 1.0)
    }

    @Test
    fun `the real climb replaces the synthesized one without duplicating`() {
        val acc = ClimbAccumulator()
        val onB = ClimbPosition(5520.0, 420.0, 386.6, 28.0)
        acc.update("route1", progress = 36810.0, climbs = listOf(climbC), onClimb = onB)
        // The device starts reporting the climb properly
        val merged = acc.update("route1", progress = 36810.0, climbs = listOf(climbB, climbC), onClimb = onB)
        assertEquals(2, merged.size)
        assertEquals(climbB.start, merged[0].start, 1e-9)
    }

    @Test
    fun `returns climbs sorted by start`() {
        val acc = ClimbAccumulator()
        val merged = acc.update("route1", progress = 0.0, climbs = listOf(climbC, climbA, climbB))
        assertEquals(listOf(climbA, climbB, climbC), merged)
    }

    @Test
    fun `resets when the route changes`() {
        val acc = ClimbAccumulator()
        acc.update("route1", progress = 0.0, climbs = listOf(climbA, climbB))
        val merged = acc.update("route2", progress = 0.0, climbs = listOf(climbC))
        assertEquals(listOf(climbC), merged)
    }

    @Test
    fun `does not duplicate identical climbs`() {
        val acc = ClimbAccumulator()
        acc.update("route1", progress = 0.0, climbs = listOf(climbA, climbB))
        val merged = acc.update("route1", progress = 0.0, climbs = listOf(climbA, climbB))
        assertEquals(2, merged.size)
    }

    @Test
    fun `same-key result is reference-stable to avoid needless downstream rebuilds`() {
        val acc = ClimbAccumulator()
        val first = acc.update("route1", progress = 0.0, climbs = listOf(climbA, climbB))
        val second = acc.update("route1", progress = 40000.0, climbs = listOf(climbB))
        assertTrue(first === second)
    }
}

