package com.rtaylor.climbsense.core

import com.rtaylor.climbsense.core.GradientSegments.Bin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientSegmentsTest {

    private fun profileOf(vararg points: Pair<Double, Double>): ElevationProfile =
        ElevationProfile.fromPolyline(Polyline.encode(points.toList(), 10.0))!!

    @Test
    fun `flat terrain produces no painted segments`() {
        val p = profileOf(0.0 to 100.0, 500.0 to 101.0, 1000.0 to 100.0)
        assertTrue(GradientSegments.compute(p).isEmpty())
    }

    @Test
    fun `a constant climb produces one merged segment of the right bin`() {
        // 1km at 6% -> single CLIMB_MODERATE segment covering the climb
        val p = profileOf(0.0 to 0.0, 1000.0 to 60.0)
        val segs = GradientSegments.compute(p)
        assertEquals(1, segs.size)
        assertEquals(Bin.CLIMB_MODERATE, segs[0].bin)
        assertEquals(0.0, segs[0].start, 1.0)
        assertEquals(1000.0, segs[0].end, 1.0)
    }

    @Test
    fun `bins map to expected grade ranges`() {
        assertEquals(Bin.DESCENT, GradientSegments.binFor(-5.0))
        assertEquals(null, GradientSegments.binFor(-1.0))
        assertEquals(null, GradientSegments.binFor(1.0))
        assertEquals(Bin.CLIMB_EASY, GradientSegments.binFor(3.0))
        assertEquals(Bin.CLIMB_MODERATE, GradientSegments.binFor(6.5))
        assertEquals(Bin.CLIMB_HARD, GradientSegments.binFor(10.0))
        assertEquals(Bin.CLIMB_EXTREME, GradientSegments.binFor(14.0))
    }

    @Test
    fun `a two pitch climb with a descent produces ordered distinct segments`() {
        // 500m @ 4% (EASY), 200m @ -5% (DESCENT), 500m @ 9% (HARD)
        val p = profileOf(0.0 to 0.0, 500.0 to 20.0, 700.0 to 10.0, 1200.0 to 55.0)
        val segs = GradientSegments.compute(p)
        assertEquals(3, segs.size)
        assertEquals(Bin.CLIMB_EASY, segs[0].bin)
        assertEquals(Bin.DESCENT, segs[1].bin)
        assertEquals(Bin.CLIMB_HARD, segs[2].bin)
        // contiguous, ordered
        assertTrue(segs[0].end <= segs[1].start + 1.0)
        assertTrue(segs[1].end <= segs[2].start + 1.0)
    }

    @Test
    fun `adjacent same-bin stretches merge into one segment`() {
        // two 5-6% stretches separated by a 5.5% stretch: all CLIMB_MODERATE -> one segment
        val p = profileOf(0.0 to 0.0, 300.0 to 16.0, 600.0 to 33.0, 900.0 to 51.0)
        val segs = GradientSegments.compute(p)
        assertEquals(1, segs.size)
        assertEquals(Bin.CLIMB_MODERATE, segs[0].bin)
    }

    @Test
    fun `short blips are absorbed rather than painted`() {
        // 1km steady 6% with one 30m flat blip in the middle: still one segment
        val p = profileOf(
            0.0 to 0.0, 480.0 to 28.8, 510.0 to 28.8, 1000.0 to 58.2,
        )
        val segs = GradientSegments.compute(p)
        assertEquals(1, segs.size)
        assertEquals(Bin.CLIMB_MODERATE, segs[0].bin)
    }
}
