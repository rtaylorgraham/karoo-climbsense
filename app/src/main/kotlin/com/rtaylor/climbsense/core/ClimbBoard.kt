package com.rtaylor.climbsense.core

/**
 * Render model for the climb list page and next-climb tile: every climb with
 * its live state plus aggregates.
 */
data class ClimbBoard(
    val rows: List<Row>,
    val currentIndex: Int?,
    val remainingAscent: Double,
) {
    enum class State { DONE, CURRENT, UPCOMING }

    data class Row(
        val index: Int,
        val span: ClimbSpan,
        val state: State,
        val maxPitch: Double?,
        val distanceToStart: Double?,
        val distanceToTop: Double?,
        val ascentToTop: Double?,
    )

    /** The upcoming climb to preview — only when not already on a climb. */
    val nextRow: Row?
        get() = if (currentIndex != null) null else rows.firstOrNull { it.state == State.UPCOMING }

    companion object {
        fun build(route: RouteModel, progress: Double): ClimbBoard {
            val profile = route.profile
            val rows = route.climbs.mapIndexed { i, span ->
                val state = when {
                    progress >= span.end -> State.DONE
                    progress >= span.start -> State.CURRENT
                    else -> State.UPCOMING
                }
                val ascentToTop = if (state == State.CURRENT) {
                    profile?.let { it.elevationAt(span.end) - it.elevationAt(progress) }
                        ?: (span.totalElevation * (span.end - progress) / (span.end - span.start))
                } else {
                    null
                }
                Row(
                    index = i + 1,
                    span = span,
                    state = state,
                    maxPitch = profile?.maxWindowGrade(span.start, span.end),
                    distanceToStart = if (state == State.UPCOMING) span.start - progress else null,
                    distanceToTop = if (state == State.CURRENT) span.end - progress else null,
                    ascentToTop = ascentToTop,
                )
            }
            val remainingAscent = rows.sumOf { row ->
                when (row.state) {
                    State.DONE -> 0.0
                    State.CURRENT -> row.ascentToTop ?: 0.0
                    State.UPCOMING -> row.span.totalElevation
                }
            }
            return ClimbBoard(
                rows = rows,
                currentIndex = rows.firstOrNull { it.state == State.CURRENT }?.index,
                remainingAscent = remainingAscent,
            )
        }
    }
}
