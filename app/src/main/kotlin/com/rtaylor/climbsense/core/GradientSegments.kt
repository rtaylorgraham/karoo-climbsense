package com.rtaylor.climbsense.core

/**
 * Turns an elevation profile into merged, distance-addressed gradient segments
 * for the map overlay. Flats/gentle grades are unpainted (null bin) so the
 * native route line shows through and the overlay stays readable.
 */
object GradientSegments {

    enum class Bin { DESCENT, CLIMB_EASY, CLIMB_MODERATE, CLIMB_HARD, CLIMB_EXTREME }

    data class Segment(val start: Double, val end: Double, val bin: Bin)

    private const val STEP = 30.0 // matches the elevation profile's native grid
    private const val MIN_RUN = 60.0 // shorter runs are noise: absorbed or unpainted

    fun binFor(grade: Double): Bin? = when {
        grade <= -3.0 -> Bin.DESCENT
        grade < 2.0 -> null
        grade < 5.0 -> Bin.CLIMB_EASY
        grade < 8.0 -> Bin.CLIMB_MODERATE
        grade < 12.0 -> Bin.CLIMB_HARD
        else -> Bin.CLIMB_EXTREME
    }

    fun compute(profile: ElevationProfile): List<Segment> {
        val total = profile.totalDistance

        // 1) One run per contiguous same-bin stretch of STEP-sized samples
        class Run(val start: Double, var end: Double, var bin: Bin?)

        val runs = mutableListOf<Run>()
        var d = 0.0
        while (d < total - 0.5) {
            val end = (d + STEP).coerceAtMost(total)
            // Final partial step: extend the sampling window backward so the grade
            // stays meaningful (avgGrade returns null under MIN_SPAN)
            val from = if (end - d < ElevationProfile.MIN_SPAN) (end - STEP).coerceAtLeast(0.0) else d
            val bin = profile.avgGrade(from, end)?.let(::binFor)
            val last = runs.lastOrNull()
            if (last != null && last.bin == bin) last.end = end else runs.add(Run(d, end, bin))
            d = end
        }

        // 2) Absorb noise: a short run between same-bin neighbors merges them; a
        //    short painted blip with mismatched neighbors gets unpainted.
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < runs.size) {
                val run = runs[i]
                if (run.end - run.start < MIN_RUN) {
                    val prev = runs.getOrNull(i - 1)
                    val next = runs.getOrNull(i + 1)
                    if (prev != null && next != null && prev.bin == next.bin) {
                        prev.end = next.end
                        runs.removeAt(i + 1)
                        runs.removeAt(i)
                        changed = true
                        continue
                    } else if (run.bin != null && runs.size > 1) {
                        run.bin = null
                        changed = true
                        continue
                    }
                }
                i++
            }
        }

        // 3) Painted, meaningfully-long segments only
        return runs
            .filter { it.bin != null && it.end - it.start >= MIN_RUN }
            .map { Segment(it.start, it.end, it.bin!!) }
    }
}
