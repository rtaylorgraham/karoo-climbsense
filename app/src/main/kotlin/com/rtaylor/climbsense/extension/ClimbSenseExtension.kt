package com.rtaylor.climbsense.extension

import android.util.Log
import com.rtaylor.climbsense.BuildConfig
import com.rtaylor.climbsense.core.OverlayPlan
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ClimbSense: three numeric climb data fields the native Karoo Climber lacks —
 * Grade Remain, Max Ahead, and Next 500m.
 */
class ClimbSenseExtension : KarooExtension("climbsense", BuildConfig.VERSION_NAME) {

    private lateinit var karooSystem: KarooSystemService
    private lateinit var routeHub: RouteHub
    private lateinit var climbHub: ClimbHub
    private lateinit var scope: CoroutineScope
    private val connected = MutableStateFlow(false)

    override val types: List<DataTypeImpl> by lazy {
        listOf(
            GradeRemainDataType(extension, scope, climbHub, routeHub),
            MaxAheadDataType(extension, scope, climbHub, routeHub),
            Next500DataType(extension, scope, climbHub, routeHub),
            ClimbPowerDataType(extension, scope, climbHub),
            NextClimbDataType(extension, scope, climbHub, routeHub),
            ClimbListDataType(extension, scope, climbHub, routeHub),
        )
    }

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect { isConnected ->
            Log.i(TAG, "Karoo system connected=$isConnected")
            connected.value = isConnected
        }
        climbHub = ClimbHub(karooSystem, scope, connected)
        routeHub = RouteHub(karooSystem, scope, connected, onClimb = { climbHub.climbPosition() })
    }

    /**
     * Map layer: gradient-colored route segments (descents blue, climbs
     * yellow->red by steepness) plus climb start/summit markers.
     */
    override fun startMap(emitter: Emitter<MapEffect>) {
        var shown = OverlayPlan.EMPTY
        val job = scope.launch {
            routeHub.snapshots
                .map { it.route }
                .distinctUntilChanged { a, b ->
                    a?.routeKey == b?.routeKey && a?.climbs === b?.climbs && a?.profile === b?.profile
                }
                .collect { route ->
                    val plan = route?.let(OverlayPlan::forRoute) ?: OverlayPlan.EMPTY
                    // Hide what's no longer in the plan
                    shown.polylines
                        .filter { old -> plan.polylines.none { it.id == old.id } }
                        .forEach { emitter.onNext(HidePolyline(it.id)) }
                    if (shown.markers.isNotEmpty() && shown.markers != plan.markers) {
                        emitter.onNext(HideSymbols(shown.markers.map { it.id }))
                    }
                    // Show what's new or changed
                    plan.polylines
                        .filter { new -> shown.polylines.none { it == new } }
                        .forEach { emitter.onNext(ShowPolyline(it.id, it.encodedPolyline, it.color, it.width)) }
                    if (plan.markers.isNotEmpty() && plan.markers != shown.markers) {
                        emitter.onNext(
                            ShowSymbols(
                                plan.markers.map {
                                    Symbol.POI(
                                        id = it.id,
                                        lat = it.lat,
                                        lng = it.lng,
                                        type = it.type,
                                        name = it.name,
                                    )
                                },
                            ),
                        )
                    }
                    shown = plan
                }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun onDestroy() {
        scope.cancel()
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        const val TAG = "ClimbSense"
    }
}
