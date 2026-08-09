package com.rtaylor.climbsense.core

import kotlin.math.roundToLong

/**
 * Maintains the effective set of climbs for a route.
 *
 * The Karoo prunes passed/current climbs from NavigatingRoute.climbs while
 * riding, so remembered climbs behind the rider must be kept. But re-matches
 * (reroutes, loop routes) can re-emit the same climbs at shifted distances —
 * so climbs are identified by frame-invariant identity (length + elevation),
 * re-seen climbs adopt the CURRENT frame's position, and a remembered climb
 * still ahead of the rider that the device no longer reports is stale garbage
 * from an old frame and gets dropped.
 */
class ClimbAccumulator {
    private var routeKey: String? = null
    private val seen = LinkedHashMap<Long, ClimbSpan>()
    private var snapshot: List<ClimbSpan> = emptyList()

    /**
     * @param onClimb the climb the native Climber is tracking right now, if any.
     *   It is never pruned: the device drops the current climb from its list
     *   while you ride it, and route progress can lag behind, so without this
     *   the climb under the wheels could be deleted from the model.
     */
    fun update(
        routeKey: String,
        progress: Double?,
        climbs: List<ClimbSpan>,
        onClimb: ClimbPosition? = null,
    ): List<ClimbSpan> {
        if (routeKey != this.routeKey) {
            this.routeKey = routeKey
            seen.clear()
            snapshot = emptyList()
        }
        var changed = false
        val currentKeys = climbs.map { identity(it) }.toSet()

        // Adopt/refresh everything the device currently reports
        climbs.forEach { climb ->
            if (seen.put(identity(climb), climb) != climb) changed = true
        }
        // Drop remembered climbs still ahead of the rider that the device no
        // longer lists (stale frame); keep climbs already passed or underway.
        // Without trustworthy progress (off-route), skip pruning.
        if (progress != null) {
            val protectedClimb = onClimb?.let { ClimbMath.locate(seen.values.toList(), it)?.climb }
            val stale = seen.filterValues {
                it.start > progress && identity(it) !in currentKeys && it != protectedClimb
            }.keys
            if (stale.isNotEmpty()) {
                stale.forEach { seen.remove(it) }
                changed = true
            }
        }

        // The device can omit the climb we're on entirely (it lists only climbs
        // still ahead, so joining a route mid-climb never reports it). Synthesize
        // it from the stream — we know its length and ascent, and route progress
        // tells us where the bottom is. A later real report matches it by identity
        // and corrects the position.
        if (onClimb != null && progress != null && ClimbMath.locate(seen.values.toList(), onClimb) == null) {
            val start = progress - onClimb.distanceFromBottom
            val synthesized = ClimbSpan(
                start = start,
                end = start + onClimb.length,
                grade = if (onClimb.length > 0) onClimb.ascent / onClimb.length * 100.0 else 0.0,
                totalElevation = onClimb.ascent,
            )
            seen[identity(synthesized)] = synthesized
            changed = true
        }

        if (changed || (snapshot.isEmpty() && seen.isNotEmpty())) {
            snapshot = seen.values.sortedBy { it.start }
        }
        return snapshot
    }

    /** Frame-invariant climb identity: length and total elevation (rounded to meters). */
    private fun identity(climb: ClimbSpan): Long =
        (climb.end - climb.start).roundToLong() * 100_000 + climb.totalElevation.roundToLong()
}
