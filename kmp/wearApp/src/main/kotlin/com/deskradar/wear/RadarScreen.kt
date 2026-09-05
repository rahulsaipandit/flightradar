package com.deskradar.wear

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.deskradar.common.RadarTarget
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val GRID_GREEN = Color(0xFF00A000)
private val BRIGHT_GREEN = Color(0xFF00FF00)
private val LABEL_GREEN = Color(0xFF00E000)

private fun altitudeLabel(target: RadarTarget): String =
    target.aircraft.altitudeMeters?.let { "${it.roundToInt()}m" } ?: "GND"

@Composable
fun RadarScreen(uiState: RadarUiState, rangeKm: Double, onGrantPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .let { if (uiState is RadarUiState.PermissionRequired) it.clickable(onClick = onGrantPermission) else it },
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            RadarUiState.PermissionRequired -> {
                Text(
                    "Tap to grant\nlocation access",
                    color = BRIGHT_GREEN,
                    style = MaterialTheme.typography.body2
                )
            }
            RadarUiState.Loading -> {
                RadarSweep(targets = emptyList(), rangeKm = rangeKm)
                Text("GETTING LOCATION...", color = BRIGHT_GREEN, style = MaterialTheme.typography.caption2)
            }
            is RadarUiState.Data -> {
                RadarSweep(targets = uiState.snapshot.targets, rangeKm = rangeKm)
            }
        }
    }
}

@Composable
private fun RadarSweep(targets: List<RadarTarget>, rangeKm: Double) {
    val infiniteTransition = rememberInfiniteTransition(label = "sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val textMeasurer = rememberTextMeasurer()

    // Measuring text layout is comparatively expensive — do it once per new snapshot (~every
    // 108s), not on every animation frame of the continuously-rotating sweep (~60fps). Color/
    // alpha still animate per frame via drawText's color override, no re-measure needed for that.
    val measuredLabels = remember(targets) {
        targets.associateWith { target ->
            textMeasurer.measure(
                "${target.aircraft.callsign}\n${altitudeLabel(target)}",
                style = TextStyle(fontSize = 8.sp)
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f * 0.92f

        drawGrid(center, maxRadius)
        drawSweep(center, maxRadius, sweepAngle)
        targets.forEach { target ->
            drawTarget(center, maxRadius, target, sweepAngle, rangeKm, measuredLabels.getValue(target))
        }
    }
}

private fun DrawScope.drawGrid(center: Offset, maxRadius: Float) {
    listOf(0.3f, 0.6f, 1.0f).forEachIndexed { index, fraction ->
        drawCircle(
            color = if (index == 2) BRIGHT_GREEN else GRID_GREEN,
            radius = maxRadius * fraction,
            center = center,
            style = Stroke(width = 1.5f)
        )
    }
    drawLine(GRID_GREEN, Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius), 1f)
    drawLine(GRID_GREEN, Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y), 1f)
}

private fun DrawScope.drawSweep(center: Offset, maxRadius: Float, sweepAngle: Float) {
    // Trailing fade wedge behind the sweep line, then the bright line itself.
    rotate(degrees = (sweepAngle * 180.0 / PI).toFloat() - 90f, pivot = center) {
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(Color.Transparent, GRID_GREEN.copy(alpha = 0.35f), Color.Transparent),
                center = center
            ),
            startAngle = -60f,
            sweepAngle = 60f,
            useCenter = true,
            topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
            size = Size(maxRadius * 2, maxRadius * 2)
        )
    }
    val endX = center.x + maxRadius * sin(sweepAngle)
    val endY = center.y - maxRadius * cos(sweepAngle)
    drawLine(BRIGHT_GREEN, center, Offset(endX, endY), strokeWidth = 2f)
}

/** Ports renderRadarFrame()'s sweep-fade + triangle-heading marker from the ESP32 firmware. */
private fun DrawScope.drawTarget(
    center: Offset,
    maxRadius: Float,
    target: RadarTarget,
    sweepAngle: Float,
    rangeKm: Double,
    label: TextLayoutResult
) {
    var angleDiff = sweepAngle - target.bearingRad.toFloat()
    if (angleDiff < 0) angleDiff += (2 * PI).toFloat()
    if (angleDiff >= PI.toFloat()) return // faded out — not swept in the last half rotation

    val brightness = (1f - angleDiff / PI.toFloat()).coerceIn(0f, 1f)
    val planeColor = Color(brightness, brightness, brightness)
    val textColor = LABEL_GREEN.copy(alpha = brightness)

    val pixelRadius = (target.distanceKm / rangeKm).toFloat().coerceIn(0f, 1f) * maxRadius
    val x = center.x + pixelRadius * sin(target.bearingRad.toFloat())
    val y = center.y - pixelRadius * cos(target.bearingRad.toFloat())

    val headingRad = ((target.aircraft.trueTrackDeg ?: 0.0) * PI / 180.0).toFloat()
    val noseLen = maxRadius * 0.05f
    val tailLen = maxRadius * 0.035f
    val nose = Offset(x + noseLen * sin(headingRad), y - noseLen * cos(headingRad))
    val tailRight = Offset(x + tailLen * sin(headingRad + 2.35f), y - tailLen * cos(headingRad + 2.35f))
    val tailLeft = Offset(x + tailLen * sin(headingRad - 2.35f), y - tailLen * cos(headingRad - 2.35f))

    drawPath(
        path = Path().apply {
            moveTo(nose.x, nose.y)
            lineTo(tailRight.x, tailRight.y)
            lineTo(tailLeft.x, tailLeft.y)
            close()
        },
        color = planeColor
    )

    drawText(label, color = textColor, topLeft = Offset(x + 4f, y - 12f))
}
