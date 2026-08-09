package com.rtaylor.climbsense.core

/** SDK-free model of a climb's extent along the route (meters). */
data class ClimbSpan(
    val start: Double,
    val end: Double,
    val grade: Double,
    val totalElevation: Double,
)

/** Everything the fields need about the loaded route, decoded once per route change. */
data class RouteModel(
    val routeKey: String,
    val totalDistance: Double,
    val profile: ElevationProfile?,
    val climbs: List<ClimbSpan>,
    val path: RoutePath? = null,
)

/**
 * Where the rider is inside the climb the native Climber is tracking, straight
 * from the CLIMB stream. Frame-independent: it says nothing about where the
 * climb sits on the route, only how far up it we are.
 */
data class ClimbPosition(
    val distanceFromBottom: Double,
    val distanceToTop: Double,
    val elevationFromBottom: Double,
    val elevationToTop: Double,
) {
    val length: Double get() = distanceFromBottom + distanceToTop
    val ascent: Double get() = elevationFromBottom + elevationToTop
}

/** A CLIMB-stream position resolved onto the route's distance axis. */
data class LocatedClimb(val climb: ClimbSpan, val position: Double)

object ClimbMath {
    private const val LENGTH_TOLERANCE = 150.0
    private const val ASCENT_TOLERANCE = 30.0

    /** The climb the rider is currently on, if any. */
    fun currentClimb(climbs: List<ClimbSpan>, progress: Double): ClimbSpan? =
        climbs.firstOrNull { progress >= it.start && progress < it.end }

    /**
     * Match a CLIMB-stream position to one of the route's climbs by its length
     * and total ascent — both invariant under route re-matching — and convert it
     * into a position on the route's distance axis.
     *
     * This is what makes the profile-based fields work when route matching is
     * confused (loop routes, reroutes): the native Climber still knows which
     * climb we're on and how far up it we are, even when progress is nonsense.
     */
    fun locate(climbs: List<ClimbSpan>, position: ClimbPosition, hint: Double? = null): LocatedClimb? {
        val candidates = climbs.filter { climb ->
            val lengthDiff = kotlin.math.abs((climb.end - climb.start) - position.length)
            val ascentDiff = kotlin.math.abs(climb.totalElevation - position.ascent)
            lengthDiff <= LENGTH_TOLERANCE && ascentDiff <= ASCENT_TOLERANCE
        }
        val climb = when {
            candidates.isEmpty() -> return null
            hint != null -> candidates.minByOrNull { kotlin.math.abs(it.start - hint) }!!
            else -> candidates.first()
        }
        return LocatedClimb(climb, climb.start + position.distanceFromBottom)
    }

    /**
     * Best available position on the route's distance axis: the CLIMB stream
     * when it can be matched to a route climb, otherwise route progress.
     */
    fun effectivePosition(
        route: RouteModel?,
        dtdProgress: Double?,
        climbPosition: ClimbPosition?,
    ): Double? {
        route ?: return null
        val located = climbPosition?.let { locate(route.climbs, it, hint = dtdProgress) }
        return located?.position ?: dtdProgress
    }

    /**
     * Average grade (%) from the rider's position to the top of [climb],
     * computed from the real elevation profile (a mid-climb dip is averaged through,
     * exactly like Garmin ClimbPro's "Grad Remain"). Null without a profile or near the top.
     */
    fun gradeRemain(profile: ElevationProfile?, climb: ClimbSpan, progress: Double): Double? {
        profile ?: return null
        val from = progress.coerceIn(climb.start, climb.end)
        return profile.avgGrade(from, climb.end)
    }

    /** Steepest ~100 m pitch still remaining in [climb]. */
    fun maxAhead(profile: ElevationProfile?, climb: ClimbSpan, progress: Double): Double? {
        profile ?: return null
        val from = progress.coerceIn(climb.start, climb.end)
        return profile.maxWindowGrade(from, climb.end)
    }

    /** Average grade of the next [lookahead] meters of route, clamped at the route end. */
    fun next500(profile: ElevationProfile?, progress: Double, lookahead: Double = 500.0): Double? {
        profile ?: return null
        val to = (progress + lookahead).coerceAtMost(profile.totalDistance)
        return profile.avgGrade(progress, to)
    }
}
