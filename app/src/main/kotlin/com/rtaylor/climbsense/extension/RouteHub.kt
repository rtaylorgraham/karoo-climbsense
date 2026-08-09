package com.rtaylor.climbsense.extension

import com.rtaylor.climbsense.core.ClimbAccumulator
import com.rtaylor.climbsense.core.ClimbPosition
import com.rtaylor.climbsense.core.ClimbSpan
import com.rtaylor.climbsense.core.ElevationProfile
import com.rtaylor.climbsense.core.RouteModel
import com.rtaylor.climbsense.core.RoutePath
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Combined view of the loaded route and the rider's trusted progress along it. */
data class Snapshot(
    val route: RouteModel?,
    val progress: Double?,
)

/**
 * Single owner of the navigation + distance-to-destination subscriptions.
 * Decodes the route geometry once per route change and exposes a shared
 * [Snapshot] state for all data fields and the map layer.
 */
class RouteHub(
    karooSystem: KarooSystemService,
    scope: CoroutineScope,
    connected: StateFlow<Boolean>,
    /**
     * The climb the native Climber is tracking, if any. Supplied by ClimbHub so
     * the accumulator never prunes the climb under the wheels — see
     * [ClimbAccumulator.update].
     */
    private val onClimb: () -> ClimbPosition? = { null },
) {

    private data class NavInfo(
        val state: OnNavigationState.NavigationState.NavigatingRoute?,
        val hasRejoin: Boolean,
    )

    private data class Dtd(val distance: Double, val onRoute: Boolean)

    private var cachedRouteKey: String? = null
    private var cachedProfile: ElevationProfile? = null
    private var cachedPath: RoutePath? = null
    private var cachedModel: RouteModel? = null
    private val climbAccumulator = ClimbAccumulator()

    private val navFlow = karooSystem.consumerFlow<OnNavigationState>()
        .afterConnected(connected)
        .map { nav ->
            val state = nav.state as? OnNavigationState.NavigationState.NavigatingRoute
            NavInfo(
                state = state,
                hasRejoin = state != null && (state.rejoinPolyline != null || state.rejoinDistance != null),
            )
        }
        .onStart { emit(NavInfo(null, false)) }

    private val dtdFlow = karooSystem.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION)
        .afterConnected(connected)
        .map { state ->
            (state as? StreamState.Streaming)?.dataPoint?.values?.let { values ->
                StreamRules.dtdFromValues(values)?.let { distance ->
                    Dtd(distance, StreamRules.onRouteFromValues(values))
                }
            }
        }
        .onStart { emit(null) }

    val snapshots: StateFlow<Snapshot> = combine(navFlow, dtdFlow) { nav, dtd ->
        val state = nav.state
        if (state == null) {
            Snapshot(null, null)
        } else {
            val progress = StreamRules.progressAlongRoute(
                routeDistance = state.routeDistance,
                distanceToDestination = dtd?.distance,
                onRoute = dtd?.onRoute ?: true,
                hasRejoin = nav.hasRejoin,
            )
            Snapshot(modelFor(state, progress), progress)
        }
    }.stateIn(scope, SharingStarted.Eagerly, Snapshot(null, null))

    private fun modelFor(
        state: OnNavigationState.NavigationState.NavigatingRoute,
        progress: Double?,
    ): RouteModel {
        val routeKey = "${state.routePolyline.hashCode()}:${state.reversed}"
        if (routeKey != cachedRouteKey) {
            cachedRouteKey = routeKey
            cachedProfile = ElevationProfile.fromPolyline(state.routeElevationPolyline)
            cachedPath = RoutePath.fromPolyline(state.routePolyline, state.routeDistance)
            cachedModel = null
        }
        // climbs can arrive in a later emission than the polyline, get pruned
        // mid-ride, and re-appear at shifted distances after a re-match â€” the
        // accumulator reconciles all of that against current progress.
        val climbs = climbAccumulator.update(
            routeKey,
            progress,
            state.climbs.map {
                ClimbSpan(
                    start = it.startDistance,
                    end = it.startDistance + it.length,
                    grade = it.grade,
                    totalElevation = it.totalElevation,
                )
            },
            onClimb = onClimb(),
        )
        cachedModel?.takeIf { it.climbs === climbs }?.let { return it }
        return RouteModel(
            routeKey = routeKey,
            totalDistance = state.routeDistance,
            profile = cachedProfile,
            climbs = climbs,
            path = cachedPath,
        ).also { cachedModel = it }
    }
}

