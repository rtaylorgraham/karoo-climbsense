package com.rtaylor.climbsense.core

import com.rtaylor.climbsense.core.ClimbBoard.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimbBoardTest {

    // 10km route: C1 1000-2000 @8% (+80m), C2 4000-6000 @6% (+120m), C3 8000-9000 @10% (+100m)
    private fun route(): RouteModel {
        val elev = listOf(
            0.0 to 100.0, 1000.0 to 100.0, 2000.0 to 180.0, 4000.0 to 180.0,
            6000.0 to 300.0, 8000.0 to 300.0, 9000.0 to 400.0, 10000.0 to 400.0,
        )
        return RouteModel(
            routeKey = "r",
            totalDistance = 10000.0,
            profile = ElevationProfile.fromPolyline(Polyline.encode(elev, 10.0))!!,
            climbs = listOf(
                ClimbSpan(1000.0, 2000.0, 8.0, 80.0),
                ClimbSpan(4000.0, 6000.0, 6.0, 120.0),
                ClimbSpan(8000.0, 9000.0, 10.0, 100.0),
            ),
        )
    }

    @Test
    fun `assigns done current and upcoming states from progress`() {
        val board = ClimbBoard.build(route(), progress = 5000.0)
        assertEquals(listOf(State.DONE, State.CURRENT, State.UPCOMING), board.rows.map { it.state })
        assertEquals(listOf(1, 2, 3), board.rows.map { it.index })
    }

    @Test
    fun `current row carries distance and ascent to top`() {
        val board = ClimbBoard.build(route(), progress = 5000.0)
        val current = board.rows[1]
        assertEquals(1000.0, current.distanceToTop!!, 1.0)
        assertEquals(60.0, current.ascentToTop!!, 2.0) // 240->300 remaining
        assertNull(current.distanceToStart)
    }

    @Test
    fun `upcoming rows carry distance to start and max pitch`() {
        val board = ClimbBoard.build(route(), progress = 5000.0)
        val next = board.rows[2]
        assertEquals(3000.0, next.distanceToStart!!, 1.0)
        assertEquals(10.0, next.maxPitch!!, 0.3)
        assertNull(next.distanceToTop)
    }

    @Test
    fun `remaining ascent sums current remainder plus upcoming climbs`() {
        val board = ClimbBoard.build(route(), progress = 5000.0)
        assertEquals(160.0, board.remainingAscent, 3.0) // 60 left on C2 + 100 on C3
    }

    @Test
    fun `next climb is null when on a climb and set when between climbs`() {
        assertEquals(2, ClimbBoard.build(route(), 5000.0).currentIndex)
        assertNull(ClimbBoard.build(route(), 5000.0).nextRow)
        val between = ClimbBoard.build(route(), 3000.0)
        assertNull(between.currentIndex)
        assertEquals(2, between.nextRow!!.index)
    }

    @Test
    fun `all climbs done`() {
        val board = ClimbBoard.build(route(), progress = 9500.0)
        assertEquals(listOf(State.DONE, State.DONE, State.DONE), board.rows.map { it.state })
        assertNull(board.nextRow)
        assertEquals(0.0, board.remainingAscent, 1.0)
    }
}
