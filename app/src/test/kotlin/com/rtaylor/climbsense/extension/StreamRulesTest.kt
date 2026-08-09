package com.rtaylor.climbsense.extension

import io.hammerhead.karooext.models.DataType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRulesTest {

    @Test
    fun `gradeFromClimbValues divides elevation by distance`() {
        val values = mapOf(
            DataType.Field.DISTANCE_TO_TOP to 2000.0,
            DataType.Field.ELEVATION_TO_TOP to 160.0,
        )
        assertEquals(8.0, StreamRules.gradeFromClimbValues(values)!!, 1e-9)
    }

    @Test
    fun `gradeFromClimbValues is null when fields missing or near the top`() {
        assertNull(StreamRules.gradeFromClimbValues(emptyMap()))
        assertNull(StreamRules.gradeFromClimbValues(mapOf(DataType.Field.DISTANCE_TO_TOP to 500.0)))
        assertNull(
            StreamRules.gradeFromClimbValues(
                mapOf(
                    DataType.Field.DISTANCE_TO_TOP to 10.0, // under MIN_SPAN
                    DataType.Field.ELEVATION_TO_TOP to 1.0,
                ),
            ),
        )
    }

    @Test
    fun `progressAlongRoute subtracts distance to destination from route length`() {
        assertEquals(3500.0, StreamRules.progressAlongRoute(10000.0, 6500.0, onRoute = true, hasRejoin = false)!!, 1e-9)
    }

    @Test
    fun `progressAlongRoute clamps to zero and rejects untrusted states`() {
        // DTD can momentarily exceed route length (GPS jitter at the start)
        assertEquals(0.0, StreamRules.progressAlongRoute(10000.0, 10500.0, onRoute = true, hasRejoin = false)!!, 1e-9)
        assertNull(StreamRules.progressAlongRoute(10000.0, null, onRoute = true, hasRejoin = false))
        assertNull(StreamRules.progressAlongRoute(10000.0, 6500.0, onRoute = false, hasRejoin = false))
    }

    @Test
    fun `live onRoute overrides a stale rejoin flag`() {
        // Observed on device: NavigatingRoute can carry a rejoin leg from an old
        // emission and never re-emit after the rider is back on route. The DTD
        // stream's ON_ROUTE field updates at 1 Hz and is authoritative.
        assertEquals(3500.0, StreamRules.progressAlongRoute(10000.0, 6500.0, onRoute = true, hasRejoin = true)!!, 1e-9)
        assertNull(StreamRules.progressAlongRoute(10000.0, 6500.0, onRoute = false, hasRejoin = true))
    }

    @Test
    fun `dtdFromValues prefers the explicit field over SINGLE`() {
        val multi = mapOf(
            DataType.Field.DISTANCE_TO_DESTINATION to 4200.0,
            DataType.Field.SINGLE to 99.0,
        )
        assertEquals(4200.0, StreamRules.dtdFromValues(multi)!!, 1e-9)
        assertEquals(77.0, StreamRules.dtdFromValues(mapOf(DataType.Field.SINGLE to 77.0))!!, 1e-9)
        assertNull(StreamRules.dtdFromValues(emptyMap()))
    }

    @Test
    fun `onRouteFromValues defaults to true when the field is absent`() {
        assertTrue(StreamRules.onRouteFromValues(emptyMap()))
        assertTrue(StreamRules.onRouteFromValues(mapOf(DataType.Field.ON_ROUTE to 1.0)))
        assertFalse(StreamRules.onRouteFromValues(mapOf(DataType.Field.ON_ROUTE to 0.0)))
    }

    @Test
    fun `climbKeyFromClimbValues identifies the active climb by summit elevation`() {
        val onClimb = mapOf(
            DataType.Field.DISTANCE_TO_TOP to 2000.0,
            DataType.Field.ELEVATION_TO_TOP to 160.0,
            DataType.Field.CLIMB_ELEVATION to 1407.1,
        )
        assertEquals(1407.1, StreamRules.climbKeyFromClimbValues(onClimb)!!, 1e-9)
        // fall back to distinguishing by remaining metrics when CLIMB_ELEVATION is absent
        val noSummit = mapOf(DataType.Field.DISTANCE_TO_TOP to 2000.0)
        assertNotNull(StreamRules.climbKeyFromClimbValues(noSummit))
        // not on a climb: no distance-to-top field at all
        assertNull(StreamRules.climbKeyFromClimbValues(emptyMap()))
        assertNull(StreamRules.climbKeyFromClimbValues(mapOf(DataType.Field.SINGLE to 5.0)))
    }

    @Test
    fun `climbPositionFromValues reads the four climb position fields`() {
        val values = mapOf(
            DataType.Field.DISTANCE_FROM_BOTTOM to 5714.87,
            DataType.Field.DISTANCE_TO_TOP to 225.13,
            DataType.Field.ELEVATION_FROM_BOTTOM to 403.62,
            DataType.Field.ELEVATION_TO_TOP to 10.98,
        )
        val pos = StreamRules.climbPositionFromValues(values)!!
        assertEquals(5940.0, pos.length, 0.1)
        assertEquals(414.6, pos.ascent, 0.1)
        assertEquals(5714.87, pos.distanceFromBottom, 1e-9)
    }

    @Test
    fun `climbPositionFromValues is null when the climb fields are absent`() {
        assertNull(StreamRules.climbPositionFromValues(emptyMap()))
        assertNull(
            StreamRules.climbPositionFromValues(
                mapOf(DataType.Field.DISTANCE_TO_TOP to 500.0), // partial
            ),
        )
    }

    @Test
    fun `roundTenth rounds display values to one decimal`() {
        assertEquals(6.2, StreamRules.roundTenth(6.24), 1e-9)
        assertEquals(6.3, StreamRules.roundTenth(6.25), 1e-9)
        assertEquals(-3.1, StreamRules.roundTenth(-3.14), 1e-9)
    }
}

