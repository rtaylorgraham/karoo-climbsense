package com.rtaylor.climbsense.extension

import com.rtaylor.climbsense.core.ClimbMath
import com.rtaylor.climbsense.core.ClimbPosition
import com.rtaylor.climbsense.core.ClimbPowerTracker
import com.rtaylor.climbsense.core.LocatedClimb
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Everything derived from the native CLIMB stream, shared by every field.
 *
 * The Climber keeps tracking climbs even when route matching reports off-route
 * (observed on device with loop routes and reroutes), so this stream — not
 * route progress — is the reliable answer to "are we climbing, and where in
 * the climb are we". [ClimbMath.locate] converts that into a position on the
 * route's distance axis for the profile-based fields.
 */
class ClimbHub(
    karooSystem: KarooSystemService,
    scope: CoroutineScope,
    connected: StateFlow<Boolean>,
) {
    private val tracker = ClimbPowerTracker()

    val climbState: StateFlow<StreamState> = karooSystem.streamDataFlow(DataType.Type.CLIMB)
        .afterConnected(connected)
        .stateIn(scope, SharingStarted.Eagerly, StreamState.Idle)

    val avgPower: StateFlow<Double?> = karooSystem.streamDataFlow(DataType.Type.POWER)
        .afterConnected(connected)
        .map { state ->
            val watts = (state as? StreamState.Streaming)?.dataPoint?.values?.let { values ->
                values[DataType.Field.POWER] ?: values[DataType.Field.SINGLE]
            }
            tracker.sample(currentClimbKey(), watts)
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val climbValues: Map<String, Double>?
        get() = (climbState.value as? StreamState.Streaming)?.dataPoint?.values

    fun currentClimbKey(): Double? = climbValues?.let(StreamRules::climbKeyFromClimbValues)

    fun climbPosition(): ClimbPosition? = climbValues?.let(StreamRules::climbPositionFromValues)

    /** Grade remaining straight from the stream (Garmin's "Grad Remain"). */
    fun streamGradeRemain(): Double? = climbValues?.let(StreamRules::gradeFromClimbValues)

    /**
     * Best position on the route's distance axis: the CLIMB stream when it
     * matches a route climb, else route progress.
     */
    fun positionOn(snapshot: Snapshot): Double? =
        ClimbMath.effectivePosition(snapshot.route, snapshot.progress, climbPosition())

    /** The climb we're on, resolved onto the route (null when not climbing). */
    fun locatedClimb(snapshot: Snapshot): LocatedClimb? {
        val route = snapshot.route ?: return null
        val position = climbPosition() ?: return null
        return ClimbMath.locate(route.climbs, position, hint = snapshot.progress)
    }
}

/**
 * GRADE REMAIN — average gradient from here to the top of the current climb
 * (Garmin ClimbPro's "Grad Remain").
 *
 * Primary source: the Karoo's own CLIMB stream (remaining ascent / remaining
 * distance). Fallback: the route elevation profile.
 */
class GradeRemainDataType(
    extension: String,
    scope: CoroutineScope,
    private val climbHub: ClimbHub,
    private val routeHub: RouteHub,
) : NumericDataType(extension, "grade-remain", scope) {

    override fun values(): Flow<Double?> =
        combine(climbHub.climbState, routeHub.snapshots) { _, snapshot ->
            climbHub.streamGradeRemain() ?: fromProfile(snapshot)
        }

    private fun fromProfile(snapshot: Snapshot): Double? {
        val located = climbHub.locatedClimb(snapshot) ?: return null
        return ClimbMath.gradeRemain(snapshot.route?.profile, located.climb, located.position)
    }
}

/** MAX AHEAD — steepest ~100 m pitch still remaining in the current climb. */
class MaxAheadDataType(
    extension: String,
    scope: CoroutineScope,
    private val climbHub: ClimbHub,
    private val routeHub: RouteHub,
) : NumericDataType(extension, "max-ahead", scope) {

    override fun values(): Flow<Double?> =
        combine(climbHub.climbState, routeHub.snapshots) { _, snapshot ->
            // Position from the CLIMB stream, so this keeps working when route
            // matching is confused — the failure that left this field always blank.
            val located = climbHub.locatedClimb(snapshot) ?: return@combine null
            ClimbMath.maxAhead(snapshot.route?.profile, located.climb, located.position)
        }
}

/**
 * CLIMB POWER — average power since the start of the current climb.
 * Watts formatting borrowed from the native power field; -- off-climb.
 */
class ClimbPowerDataType(
    extension: String,
    scope: CoroutineScope,
    private val climbHub: ClimbHub,
) : NumericDataType(extension, "climb-power", scope, formatDataTypeId = DataType.Type.POWER) {

    override fun displayValue(value: Double): Double = value.roundToInt().toDouble()

    override fun values(): Flow<Double?> =
        combine(climbHub.avgPower, climbHub.climbState) { avg, _ ->
            // Gate on the live CLIMB state so the tile clears after the summit
            // even if the power stream goes quiet
            if (climbHub.currentClimbKey() != null) avg else null
        }
}

/** NEXT 500m — average gradient of the next 500 m of route, on any terrain. */
class Next500DataType(
    extension: String,
    scope: CoroutineScope,
    private val climbHub: ClimbHub,
    private val routeHub: RouteHub,
) : NumericDataType(extension, "next-500", scope) {

    override fun values(): Flow<Double?> =
        combine(climbHub.climbState, routeHub.snapshots) { _, snapshot ->
            val position = climbHub.positionOn(snapshot) ?: return@combine null
            ClimbMath.next500(snapshot.route?.profile, position)
        }
}

