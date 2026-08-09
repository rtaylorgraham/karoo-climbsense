package com.rtaylor.climbsense.extension

import com.rtaylor.climbsense.core.ClimbPosition
import com.rtaylor.climbsense.core.ElevationProfile
import io.hammerhead.karooext.models.DataType
import kotlin.math.roundToInt

/**
 * Pure decision rules for interpreting Karoo stream DataPoints.
 * Kept free of Android/coroutine types so they are JVM-unit-testable.
 */
object StreamRules {

    /**
     * Grade Remain from the OS CLIMB stream: remaining ascent over remaining distance.
     * This is the same arithmetic Garmin's ClimbPro "Grad Remain" uses.
     */
    fun gradeFromClimbValues(values: Map<String, Double>): Double? {
        val dist = values[DataType.Field.DISTANCE_TO_TOP] ?: return null
        val elev = values[DataType.Field.ELEVATION_TO_TOP] ?: return null
        if (dist < ElevationProfile.MIN_SPAN) return null
        return elev / dist * 100.0
    }

    /**
     * Rider's distance along the route, or null when it can't be trusted
     * (off-route or no distance-to-destination yet).
     *
     * The live ON_ROUTE field (1 Hz on the DTD stream) is authoritative;
     * [hasRejoin] comes from NavigatingRoute, which is NOT re-emitted when the
     * rider gets back on route (observed on device) — a stale rejoin leg must
     * not veto progress while ON_ROUTE says we're on the line.
     */
    fun progressAlongRoute(
        routeDistance: Double,
        distanceToDestination: Double?,
        onRoute: Boolean,
        @Suppress("UNUSED_PARAMETER") hasRejoin: Boolean,
    ): Double? {
        distanceToDestination ?: return null
        if (!onRoute) return null
        return (routeDistance - distanceToDestination).coerceAtLeast(0.0)
    }

    /**
     * The DISTANCE_TO_DESTINATION DataPoint carries several fields; singleValue
     * grabs an arbitrary one, so always key the explicit field first.
     */
    fun dtdFromValues(values: Map<String, Double>): Double? =
        values[DataType.Field.DISTANCE_TO_DESTINATION] ?: values[DataType.Field.SINGLE]

    /** Missing ON_ROUTE field means the stream doesn't report it; assume on-route. */
    fun onRouteFromValues(values: Map<String, Double>): Boolean =
        values[DataType.Field.ON_ROUTE]?.let { it == 1.0 } ?: true

    // roundToInt (half-up), not kotlin.math.round (banker's rounding: 6.25 would show 6.2)
    /**
     * Where the rider is inside the climb the native Climber is tracking.
     * Present whenever the Climber is active, independent of route matching.
     */
    fun climbPositionFromValues(values: Map<String, Double>): ClimbPosition? {
        val fromBottom = values[DataType.Field.DISTANCE_FROM_BOTTOM] ?: return null
        val toTop = values[DataType.Field.DISTANCE_TO_TOP] ?: return null
        val elevFromBottom = values[DataType.Field.ELEVATION_FROM_BOTTOM] ?: return null
        val elevToTop = values[DataType.Field.ELEVATION_TO_TOP] ?: return null
        return ClimbPosition(fromBottom, toTop, elevFromBottom, elevToTop)
    }

    /**
     * Identifier for the climb the native Climber is currently tracking, from the
     * CLIMB stream's DataPoint — progress-free, so it works even when route
     * matching goes off-route. CLIMB_ELEVATION (summit altitude) is stable for a
     * climb and distinct between climbs; fall back to a constant when absent so
     * being on ANY climb still accumulates. Null when not on a climb.
     */
    fun climbKeyFromClimbValues(values: Map<String, Double>): Double? {
        values[DataType.Field.DISTANCE_TO_TOP] ?: return null
        return values[DataType.Field.CLIMB_ELEVATION] ?: 0.0
    }

    // roundToInt (half-up), not kotlin.math.round (banker's rounding: 6.25 would show 6.2)
    fun roundTenth(value: Double): Double = (value * 10.0).roundToInt() / 10.0
}
