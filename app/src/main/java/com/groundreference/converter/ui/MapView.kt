package com.groundreference.converter.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * MapView.kt
 *
 * Direct Compose port of the desktop MapPanel.java. Plots up to three
 * points -- Artillery (green), Current (blue), Target (red) -- on a simple
 * radar-style canvas with N/S/E/W compass labels (North = up, East =
 * right). A solid arrow is drawn Artillery -> Target (or Current -> Target if
 * "Artillery" isn't set yet); a dashed line shows Artillery -> Current for context.
 *
 * This is purely a visual aid: it only does screen-space plotting from
 * lat/lon deltas. It performs no bearing/distance calculations of its own.
 */

private val BACKGROUND_COLOR = Color(0xFF141C26)
private val GRID_COLOR = Color(0xFF283444)
private val AXIS_COLOR = Color(0xFF465A6E)

private val ARTILLERY_COLOR = Color(0xFF66BB6A)
private val CURRENT_COLOR = Color(0xFF42A5F5)
private val TARGET_COLOR = Color(0xFFEF5350)

private val PRIMARY_LINE_COLOR = Color(0xFFFFEB3B)
private val SECONDARY_LINE_COLOR = Color(0xC8C8C8C8)

private val COMPASS_TEXT_COLOR = Color(0xFFB4C3D2)
private val LABEL_TEXT_COLOR = Color.White

data class LatLon(val latitude: Double, val longitude: Double)

@Composable
fun MapView(
    artillery: LatLon?,
    Shell: LatLon?,
    target: LatLon?,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(BACKGROUND_COLOR, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFC8C8C8), RoundedCornerShape(12.dp))
    ) {
        val width = size.width
        val height = size.height
        val margin = 46f

        val plotLeft = margin
        val plotTop = margin
        val plotWidth = max(10f, width - margin * 2)
        val plotHeight = max(10f, height - margin * 2)
        val centerX = plotLeft + plotWidth / 2
        val centerY = plotTop + plotHeight / 2

        // ---- Grid background ----
        drawRoundRect(
            color = Color(0xFF1A232E),
            topLeft = Offset(plotLeft, plotTop),
            size = androidx.compose.ui.geometry.Size(plotWidth, plotHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
        )
        var gx = plotLeft
        while (gx <= plotLeft + plotWidth) {
            drawLine(GRID_COLOR, Offset(gx, plotTop), Offset(gx, plotTop + plotHeight), strokeWidth = 1f)
            gx += 30f
        }
        var gy = plotTop
        while (gy <= plotTop + plotHeight) {
            drawLine(GRID_COLOR, Offset(plotLeft, gy), Offset(plotLeft + plotWidth, gy), strokeWidth = 1f)
            gy += 30f
        }
        // Center crosshair axes
        drawLine(AXIS_COLOR, Offset(plotLeft, centerY), Offset(plotLeft + plotWidth, centerY), strokeWidth = 1.5f)
        drawLine(AXIS_COLOR, Offset(centerX, plotTop), Offset(centerX, plotTop + plotHeight), strokeWidth = 1.5f)
        drawRoundRect(
            color = AXIS_COLOR,
            topLeft = Offset(plotLeft, plotTop),
            size = androidx.compose.ui.geometry.Size(plotWidth, plotHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
            style = Stroke(width = 1.5f)
        )

        // ---- Compass labels ----
        val compassPaint = Paint().apply {
            color = COMPASS_TEXT_COLOR.toArgb()
            textSize = 34f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText("N", centerX, 30f, compassPaint)
            drawText("S", centerX, height - 10f, compassPaint)
            compassPaint.textAlign = Paint.Align.LEFT
            drawText("W", 6f, centerY + 10f, compassPaint)
            compassPaint.textAlign = Paint.Align.RIGHT
            drawText("E", width - 6f, centerY + 10f, compassPaint)
        }

        val points = listOfNotNull(artillery, Shell, target)
        if (points.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = COMPASS_TEXT_COLOR.toArgb()
                textSize = 28f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Enter Cooridinates to see the map.",
                centerX, centerY, emptyPaint
            )
            return@Canvas
        }

        // ---- Determine scale so all present points comfortably fit ----
        val refLat = points.sumOf { it.latitude } / points.size
        val refLon = points.sumOf { it.longitude } / points.size

        var maxLatDelta = 0.0001
        var maxLonDelta = 0.0001
        for (p in points) {
            maxLatDelta = max(maxLatDelta, kotlin.math.abs(p.latitude - refLat))
            maxLonDelta = max(maxLonDelta, kotlin.math.abs(p.longitude - refLon))
        }

        val usableHalfWidth = (plotWidth / 2.0) * 0.75
        val usableHalfHeight = (plotHeight / 2.0) * 0.75
        val scaleX = usableHalfWidth / maxLonDelta
        val scaleY = usableHalfHeight / maxLatDelta
        val scale = min(scaleX, scaleY)

        fun toScreen(p: LatLon): Offset {
            // North is up -> higher latitude = smaller (upper) screen Y.
            // East is right -> higher longitude = larger (rightward) screen X.
            val x = centerX + ((p.longitude - refLon) * scale).toFloat()
            val y = centerY - ((p.latitude - refLat) * scale).toFloat()
            return Offset(x, y)
        }

        val artilleryPoint = artillery?.let { toScreen(it) }
        val currentPoint = Shell?.let { toScreen(it) }
        val targetPoint = target?.let { toScreen(it) }

        // ---- Secondary line: Artillery -> Current (dashed, context only) ----
        if (artilleryPoint != null && currentPoint != null) {
            drawLine(
                color = SECONDARY_LINE_COLOR,
                start = artilleryPoint,
                end = currentPoint,
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )
        }

        // ---- Primary line: Artillery -> Target (solid + arrowhead), fallback Current -> Target ----
        val primaryFrom = artilleryPoint ?: currentPoint
        if (primaryFrom != null && targetPoint != null) {
            drawLine(color = PRIMARY_LINE_COLOR, start = primaryFrom, end = targetPoint, strokeWidth = 5f)
            drawArrowHead(primaryFrom, targetPoint, PRIMARY_LINE_COLOR)
        }

        // ---- Draw dots (Current first, so Artillery/Target sit visually on top) ----
        currentPoint?.let { drawDot(it, CURRENT_COLOR, "Shell") }
        artilleryPoint?.let { drawDot(it, ARTILLERY_COLOR, "Artillery") }
        targetPoint?.let {
            drawDot(it, TARGET_COLOR, "Target")
        }

        
    }

}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDot(point: Offset, color: Color, label: String) {
    val radius = 16f
    drawCircle(color = color, radius = radius, center = point)
    drawCircle(color = LABEL_TEXT_COLOR, radius = radius, center = point, style = Stroke(width = 3f))

    val labelPaint = Paint().apply {
        this.color = LABEL_TEXT_COLOR.toArgb()
        textSize = 26f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    drawContext.canvas.nativeCanvas.drawText(label, point.x + radius + 6f, point.y + 8f, labelPaint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(from: Offset, to: Offset, color: Color) {
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val arrowLength = 26f
    val arrowAngle = Math.toRadians(24.0)

    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(
            (to.x - arrowLength * cos(angle - arrowAngle)).toFloat(),
            (to.y - arrowLength * sin(angle - arrowAngle)).toFloat()
        )
        lineTo(
            (to.x - arrowLength * cos(angle + arrowAngle)).toFloat(),
            (to.y - arrowLength * sin(angle + arrowAngle)).toFloat()
        )
        close()
    }
    drawPath(path, color = color)
}

private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).roundToInt(),
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt()
    )
}
