package com.rtaylor.climbsense.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimbPowerTrackerTest {

    @Test
    fun `null while not on a climb`() {
        val t = ClimbPowerTracker()
        assertNull(t.sample(climbKey = null, watts = 250.0))
        assertNull(t.sample(climbKey = null, watts = 300.0))
    }

    @Test
    fun `averages samples while on a climb including zeros`() {
        val t = ClimbPowerTracker()
        assertEquals(250.0, t.sample(1000.0, 250.0)!!, 1e-9)
        assertEquals(275.0, t.sample(1000.0, 300.0)!!, 1e-9)
        assertEquals(200.0, t.sample(1000.0, 50.0)!!, 1e-9)
        assertEquals(150.0, t.sample(1000.0, 0.0)!!, 1e-9)
    }

    @Test
    fun `missing power samples do not poison the average`() {
        val t = ClimbPowerTracker()
        t.sample(1000.0, 200.0)
        assertEquals(200.0, t.sample(1000.0, watts = null)!!, 1e-9) // keeps last avg
        assertEquals(250.0, t.sample(1000.0, 300.0)!!, 1e-9)
    }

    @Test
    fun `on-climb with no power yet is null`() {
        val t = ClimbPowerTracker()
        assertNull(t.sample(1000.0, watts = null))
    }

    @Test
    fun `resets when the climb changes`() {
        val t = ClimbPowerTracker()
        t.sample(1000.0, 300.0)
        t.sample(1000.0, 300.0)
        assertEquals(100.0, t.sample(2000.0, 100.0)!!, 1e-9) // new climb, fresh average
    }

    @Test
    fun `resets after leaving a climb even if the same climb restarts`() {
        val t = ClimbPowerTracker()
        t.sample(1000.0, 300.0)
        assertNull(t.sample(null, 150.0))
        // riding the same climb again (e.g. loop ride): starts fresh
        assertEquals(150.0, t.sample(1000.0, 150.0)!!, 1e-9)
    }
}
