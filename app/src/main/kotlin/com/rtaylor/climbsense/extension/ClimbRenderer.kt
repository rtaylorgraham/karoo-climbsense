package com.rtaylor.climbsense.extension

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.rtaylor.climbsense.core.ClimbBoard
import com.rtaylor.climbsense.core.ElevationProfile
import com.rtaylor.climbsense.core.GradientSegments
import com.rtaylor.climbsense.core.OverlayPlan
import java.util.Locale

/**
 * Canvas rendering for the Next Climb tile and the Climb List page.
 * Draws self-contained dark cards so both day and night map themes stay readable.
 */
object ClimbRenderer {

    private const val BG = 0xFF101010.toInt()
    private const val FG = Color.WHITE
    private const val MUTED = 0xFF9E9E9E.toInt()
    private const val ACCENT = 0xFF2ECC71.toInt()
    private const val CARD_RADIUS = 10f

    private fun textPaint(size: Float, color: Int = FG, bold: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Draw [text], shrinking the paint's size until it fits [maxWidth] (floor 9px). */
    private fun drawFitted(c: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, paint: Paint) {
        while (paint.measureText(text) > maxWidth && paint.textSize > 9f) {
            paint.textSize *= 0.94f
        }
        c.drawText(text, x, baseline, paint)
    }

    private fun km(m: Double): String =
        if (m >= 950) String.format(Locale.US, "%.1fkm", m / 1000.0) else String.format(Locale.US, "%.0fm", m)

    // ---------------------------------------------------------------- tile ---

    /** Half-page tile: current climb status while climbing, otherwise next-climb preview. */
    fun renderNextClimb(
        width: Int,
        height: Int,
        board: ClimbBoard?,
        profile: ElevationProfile?,
        hasRoute: Boolean = false,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), CARD_RADIUS, CARD_RADIUS, Paint().apply { color = BG })

        // Vertical rhythm with real margins: label band / big band / detail band
        val padX = width * 0.05f
        val padY = height * 0.10f
        val small = height * 0.14f
        val big = height * 0.26f
        val maxW = width - 2 * padX
        val labelY = padY + small
        val bigY = height * 0.56f
        val detailY = height - padY

        val current = board?.rows?.firstOrNull { it.state == ClimbBoard.State.CURRENT }
        val next = board?.nextRow

        // The label is ALWAYS drawn: a field with no value must still say what it
        // is, otherwise it's an unidentifiable black box on the ride screen.
        val (labelText, labelColor) = when {
            current != null -> "ON CLIMB ${current.index}/${board.rows.size}" to ACCENT
            next != null -> "NEXT CLIMB ${next.index}/${board.rows.size}" to MUTED
            board != null && board.rows.isNotEmpty() -> "CLIMBS DONE" to MUTED
            else -> "NEXT CLIMB" to MUTED
        }
        val labelWidth = if (current != null) width * 0.55f else maxW
        drawFitted(c, labelText, padX, labelY, labelWidth, textPaint(small, labelColor))

        when {
            current != null -> {
                // Sparkline sits behind the top-right, clear of the big line
                drawSparklineRect(
                    c, profile, current.span.start, current.span.end,
                    width * 0.62f, padY, width - padX, height * 0.34f,
                    current, board,
                )
                drawFitted(
                    c, "${km(current.distanceToTop ?: 0.0)} ↑${(current.ascentToTop ?: 0.0).toInt()}m",
                    padX, bigY, maxW, textPaint(big),
                )
                drawFitted(
                    c, String.format(Locale.US, "max %.0f%% to top", current.maxPitch ?: 0.0),
                    padX, detailY, maxW, textPaint(small, MUTED),
                )
            }
            next != null -> {
                drawFitted(c, "in ${km(next.distanceToStart ?: 0.0)}", padX, bigY, maxW, textPaint(big))
                drawFitted(
                    c,
                    String.format(
                        Locale.US,
                        "%s @ %.0f%% · max %.0f%%",
                        km(next.span.end - next.span.start),
                        next.span.grade,
                        next.maxPitch ?: next.span.grade,
                    ),
                    padX, detailY, maxW, textPaint(small, MUTED),
                )
            }
            board != null && board.rows.isNotEmpty() -> {
                drawFitted(c, "${board.rows.size}/${board.rows.size} ✓", padX, bigY, maxW, textPaint(big, ACCENT))
            }
            else -> {
                // No route / no climb data: label above (already drawn) + no-value marker
                drawFitted(c, "--", padX, bigY, maxW, textPaint(big, MUTED))
                val hint = if (hasRoute) "waiting for position" else "no route loaded"
                drawFitted(c, hint, padX, detailY, maxW, textPaint(small, MUTED))
            }
        }
        return bmp
    }

    // ---------------------------------------------------------------- list ---

    /** Full-page climb list. */
    fun renderClimbList(
        width: Int,
        height: Int,
        board: ClimbBoard?,
        profile: ElevationProfile?,
        hasRoute: Boolean = false,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), CARD_RADIUS, CARD_RADIUS, Paint().apply { color = BG })

        val pad = width * 0.04f
        val headerSize = height * 0.052f
        // Header is ALWAYS drawn so the page identifies itself even with no data
        if (board == null || board.rows.isEmpty()) {
            c.drawText("CLIMBS", pad, pad + headerSize, textPaint(headerSize))
            val hint = if (hasRoute) "Waiting for position…" else "No climbs — load a route"
            c.drawText(hint, pad, height * 0.5f, textPaint(width * 0.055f, MUTED))
            return bmp
        }

        val done = board.rows.count { it.state == ClimbBoard.State.DONE }
        c.drawText("CLIMBS $done/${board.rows.size}", pad, pad + headerSize, textPaint(headerSize))
        val rightText = "↑${board.remainingAscent.toInt()}m left"
        val rp = textPaint(headerSize, ACCENT)
        c.drawText(rightText, width - pad - rp.measureText(rightText), pad + headerSize, rp)

        // Rows: current + upcoming get full rows; done rows compact. Fit within height.
        val top = pad + headerSize * 1.8f
        val availing = height - top - pad
        val fullRows = board.rows.count { it.state != ClimbBoard.State.DONE }
        val compactH = availing * 0.07f
        val fullH = if (fullRows > 0) ((availing - done * compactH) / fullRows).coerceAtMost(availing * 0.30f) else 0f

        var y = top
        board.rows.forEach { row ->
            val h = if (row.state == ClimbBoard.State.DONE) compactH else fullH
            drawRow(c, row, board, profile, pad, y, width - pad, h)
            y += h
        }
        return bmp
    }

    private fun drawRow(
        c: Canvas,
        row: ClimbBoard.Row,
        board: ClimbBoard,
        profile: ElevationProfile?,
        left: Float,
        top: Float,
        right: Float,
        h: Float,
    ) {
        val muted = row.state == ClimbBoard.State.DONE
        val labelColor = when (row.state) {
            ClimbBoard.State.CURRENT -> ACCENT
            ClimbBoard.State.DONE -> MUTED
            ClimbBoard.State.UPCOMING -> FG
        }
        // Index chip
        val chipSize = if (muted) h * 0.55f else h * 0.30f
        val chip = textPaint(chipSize, labelColor)
        val prefix = when (row.state) {
            ClimbBoard.State.DONE -> "✓"
            ClimbBoard.State.CURRENT -> "▶"
            ClimbBoard.State.UPCOMING -> " "
        }
        c.drawText("$prefix C${row.index}", left, top + chipSize * 1.2f, chip)

        val span = row.span
        val statsX = left + (right - left) * 0.18f
        val statsW = right - statsX
        if (muted) {
            drawFitted(
                c,
                String.format(Locale.US, "%s @ %.0f%%", km(span.end - span.start), span.grade),
                statsX,
                top + chipSize * 1.2f,
                statsW,
                textPaint(chipSize, MUTED, bold = false),
            )
            return
        }

        // Sparkline (colored by gradient) across the middle of the row
        val sparkTop = top + h * 0.10f
        val sparkBottom = top + h * 0.46f
        drawSparklineRect(c, profile, span.start, span.end, statsX, sparkTop, right, sparkBottom, row, board)

        // Stats split across two fitted lines so nothing ever clips:
        // line1 = the climb itself, line2 = where you are relative to it + max pitch
        val line1 = String.format(
            Locale.US,
            "%s @ %.0f%% ↑%dm",
            km(span.end - span.start),
            span.grade,
            span.totalElevation.toInt(),
        )
        val line2 = when (row.state) {
            ClimbBoard.State.CURRENT ->
                "${km(row.distanceToTop ?: 0.0)} to top · ↑${(row.ascentToTop ?: 0.0).toInt()}m left"
            else -> String.format(
                Locale.US,
                "in %s · max %.0f%%",
                km(row.distanceToStart ?: 0.0),
                row.maxPitch ?: span.grade,
            )
        }
        val t1 = textPaint(h * 0.20f)
        val t2 = textPaint(h * 0.18f, labelColor, bold = row.state == ClimbBoard.State.CURRENT)
        drawFitted(c, line1, statsX, top + h * 0.72f, statsW, t1)
        drawFitted(c, line2, statsX, top + h * 0.95f, statsW, t2)
    }

    // ---------------------------------------------------------- sparklines ---

    private fun drawSparklineRect(
        c: Canvas,
        profile: ElevationProfile?,
        from: Double,
        to: Double,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        row: ClimbBoard.Row?,
        board: ClimbBoard?,
    ) {
        profile ?: return
        val cols = 40
        val span = to - from
        if (span <= 0) return
        val elevations = (0..cols).map { profile.elevationAt(from + span * it / cols) }
        val min = elevations.min()
        val max = (elevations.max()).coerceAtLeast(min + 1.0)
        val colW = (right - left) / cols
        val paint = Paint()
        for (i in 0 until cols) {
            val d0 = from + span * i / cols
            val d1 = from + span * (i + 1) / cols
            val grade = profile.avgGrade(d0 - 15, d1 + 15) ?: 0.0
            val bin = GradientSegments.binFor(grade)
            paint.color = bin?.let(OverlayPlan::colorFor) ?: 0xFF4A4A4A.toInt()
            val hNorm = ((elevations[i + 1] - min) / (max - min)).toFloat()
            val barTop = bottom - (bottom - top) * (0.15f + 0.85f * hNorm)
            c.drawRect(left + i * colW, barTop, left + (i + 1) * colW + 0.5f, bottom, paint)
        }
        // Progress cursor on the current climb
        if (row?.state == ClimbBoard.State.CURRENT && row.distanceToTop != null) {
            val progressed = ((span - row.distanceToTop) / span).toFloat().coerceIn(0f, 1f)
            val x = left + (right - left) * progressed
            c.drawRect(x - 2f, top, x + 2f, bottom, Paint().apply { color = FG })
        }
    }
}
