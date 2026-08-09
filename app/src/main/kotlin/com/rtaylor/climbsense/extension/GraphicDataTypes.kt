package com.rtaylor.climbsense.extension

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.rtaylor.climbsense.R
import com.rtaylor.climbsense.core.ClimbBoard
import com.rtaylor.climbsense.core.ElevationProfile
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Base for canvas-rendered fields: computes the ClimbBoard from RouteHub
 * snapshots and pushes a rendered bitmap on every change.
 */
abstract class CanvasDataType(
    extension: String,
    typeId: String,
    private val scope: CoroutineScope,
    private val climbHub: ClimbHub,
    private val routeHub: RouteHub,
    private val showHeader: Boolean,
) : DataTypeImpl(extension, typeId) {

    protected abstract fun render(
        width: Int,
        height: Int,
        board: ClimbBoard?,
        profile: ElevationProfile?,
        hasRoute: Boolean,
    ): Bitmap

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = showHeader, formatDataTypeId = null))
        // Suppress any stream-state text overlay: the canvas is the display
        emitter.onNext(ShowCustomStreamState(message = "", color = null))
        val (width, height) = config.viewSize
        val job = scope.launch {
            combine(climbHub.climbState, routeHub.snapshots) { _, snapshot ->
                // Position from the CLIMB stream when available, so the states
                // stay right even when route matching is confused
                val board = snapshot.route?.let { route ->
                    climbHub.positionOn(snapshot)?.let { ClimbBoard.build(route, it) }
                }
                Triple(board, snapshot.route?.profile, snapshot.route != null)
            }
                .distinctUntilChanged()
                .collect { (board, profile, hasRoute) ->
                    val views = RemoteViews(context.packageName, R.layout.field_canvas)
                    views.setImageViewBitmap(R.id.canvas, render(width, height, board, profile, hasRoute))
                    emitter.updateView(views)
                }
        }
        emitter.setCancellable { job.cancel() }
    }
}

/**
 * Half-page tile: current climb status, or a preview of the next climb.
 * Hides the OS header (which squeezes the card into the bottom half of the
 * tile) — the renderer's own label line serves as the header, so the dark
 * card fills the full tile height like native fields.
 */
class NextClimbDataType(
    extension: String,
    scope: CoroutineScope,
    climbHub: ClimbHub,
    routeHub: RouteHub,
) : CanvasDataType(extension, "next-climb", scope, climbHub, routeHub, showHeader = false) {
    override fun render(
        width: Int,
        height: Int,
        board: ClimbBoard?,
        profile: ElevationProfile?,
        hasRoute: Boolean,
    ): Bitmap = ClimbRenderer.renderNextClimb(width, height, board, profile, hasRoute)
}

/** Full-page climb list — visible any time, including mid-climb. */
class ClimbListDataType(
    extension: String,
    scope: CoroutineScope,
    climbHub: ClimbHub,
    routeHub: RouteHub,
) : CanvasDataType(extension, "climb-list", scope, climbHub, routeHub, showHeader = false) {
    override fun render(
        width: Int,
        height: Int,
        board: ClimbBoard?,
        profile: ElevationProfile?,
        hasRoute: Boolean,
    ): Bitmap = ClimbRenderer.renderClimbList(width, height, board, profile, hasRoute)
}
