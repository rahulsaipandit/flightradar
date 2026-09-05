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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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

private const val ZOOM_STEP_KM = 5.0
private const val ROTARY_KM_PER_PIXEL = 0.08
private const val DRAG_VS_TAP_THRESHOLD_PX = 28f
private const val TARGET_HIT_RADIUS_PX = 28f

private fun altitudeLabel(target: RadarTarget): String =
    target.aircraft.altitudeMeters?.let { "${it.roundToInt()}m" } ?: "GND"

private fun speedLabel(target: RadarTarget): String =
    target.aircraft.velocityMs?.let { "${(it * 3.6).roundToInt()}km/h" } ?: "?"

private fun headingCompass(trueTrackDeg: Double?): String {
    if (trueTrackDeg == null) return "?"
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((trueTrackDeg % 360 + 360) % 360) / 45.0).roundToInt() % 8
    return directions[index]
}

@Composable
fun RadarScreen(
    uiState: RadarUiState,
    displayRangeKm: Double,
    isPanned: Boolean,
    onGrantPermission: () -> Unit,
    onZoom: (deltaKm: Double) -> Unit,
    onPan: (eastwardKm: Double, northwardKm: Double) -> Unit,
    onRecenter: () -> Unit
) {
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
                RadarSweep(targets = emptyList(), displayRangeKm = displayRangeKm, onZoom = onZoom, onPan = onPan, onTapTarget = {})
                Text("GETTING LOCATION...", color = BRIGHT_GREEN, style = MaterialTheme.typography.caption2)
            }
            is RadarUiState.Data -> {
                var selected by remember { mutableStateOf<RadarTarget?>(null) }
                RadarSweep(
                    targets = uiState.snapshot.targets,
                    displayRangeKm = displayRangeKm,
                    onZoom = onZoom,
                    onPan = onPan,
                    onTapTarget = { selected = it }
                )
                selected?.let {
                    DetailOverlay(target = it, onDismiss = { selected = null })
                }
            }
        }

        if (uiState !is RadarUiState.PermissionRequired) {
            ZoomStepper(
                displayRangeKm = displayRangeKm,
                onZoom = onZoom,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
            if (isPanned) {
                Text(
                    "● recenter",
                    color = BRIGHT_GREEN,
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).clickable(onClick = onRecenter)
                )
            }
        }
    }
}

@Composable
private fun ZoomStepper(displayRangeKm: Double, onZoom: (Double) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("‹", color = BRIGHT_GREEN, style = MaterialTheme.typography.button, modifier = Modifier.clickable { onZoom(-ZOOM_STEP_KM) })
        Text("${displayRangeKm.roundToInt()} km", color = BRIGHT_GREEN, style = MaterialTheme.typography.caption1)
        Text("›", color = BRIGHT_GREEN, style = MaterialTheme.typography.button, modifier = Modifier.clickable { onZoom(ZOOM_STEP_KM) })
    }
}

@Composable
private fun DetailOverlay(target: RadarTarget, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(target.aircraft.callsign, color = BRIGHT_GREEN, style = MaterialTheme.typography.title3)
            Text(altitudeLabel(target), color = LABEL_GREEN, style = MaterialTheme.typography.body2)
            Text(speedLabel(target), color = LABEL_GREEN, style = MaterialTheme.typography.body2)
            Text("Heading ${headingCompass(target.aircraft.trueTrackDeg)}", color = LABEL_GREEN, style = MaterialTheme.typography.body2)
            Text("${target.distanceKm.roundToInt()} km away", color = LABEL_GREEN, style = MaterialTheme.typography.caption1)
            Text("Registration/model: coming soon", color = GRID_GREEN, style = MaterialTheme.typography.caption2)
        }
    }
}

@Composable
private fun RadarSweep(
    targets: List<RadarTarget>,
    displayRangeKm: Double,
    onZoom: (Double) -> Unit,
    onPan: (Double, Double) -> Unit,
    onTapTarget: (RadarTarget) -> Unit
) {
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
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Only aircraft within the current zoom level are actually drawn/hit-testable — the
    // repository fetches/projects a superset (up to MAX_DISPLAY_RANGE_KM) so zooming is an
    // instant client-side re-filter, not a re-fetch.
    val visibleTargets = remember(targets, displayRangeKm) {
        targets.filter { it.distanceKm <= displayRangeKm }
    }

    val measuredLabels = remember(visibleTargets) {
        visibleTargets.associateWith { target ->
            textMeasurer.measure(
                "${target.aircraft.callsign}\n${altitudeLabel(target)}",
                style = TextStyle(fontSize = 8.sp)
            )
        }
    }

    // Precomputed screen position per target, shared by the draw pass and tap hit-testing —
    // Canvas's draw lambda has no per-shape click targets of its own.
    val renderedPositions = remember(visibleTargets, displayRangeKm, canvasSize) {
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val maxRadius = min(canvasSize.width, canvasSize.height) / 2f * 0.92f
        visibleTargets.associateWith { target -> targetPosition(center, maxRadius, target, displayRangeKm) }
    }
    // pointerInput's keys below are (canvasSize, displayRangeKm) — not targets — so its gesture
    // coroutine does NOT restart when a new poll snapshot arrives (~every 108s), and a plain
    // captured `renderedPositions` would go stale, hit-testing taps against long-gone aircraft.
    // rememberUpdatedState keeps the running gesture handler reading the latest map without
    // needing a restart (which would also cancel any drag the user is mid-gesture on).
    val currentRenderedPositions by rememberUpdatedState(renderedPositions)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                // Direction is a guess pending on-device feedback — flip the sign if it zooms backwards.
                onZoom(event.verticalScrollPixels * ROTARY_KM_PER_PIXEL)
                true
            }
            .pointerInput(canvasSize, displayRangeKm) {
                val maxRadius = min(canvasSize.width, canvasSize.height) / 2f * 0.92f
                val kmPerPixel = if (maxRadius > 0f) displayRangeKm / maxRadius else 0.0
                var totalDrag = Offset.Zero
                var gestureStart = Offset.Zero
                detectDragGestures(
                    onDragStart = { offset -> totalDrag = Offset.Zero; gestureStart = offset },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                        onPan(-dragAmount.x * kmPerPixel, dragAmount.y * kmPerPixel)
                    },
                    onDragEnd = {
                        if (totalDrag.getDistance() < DRAG_VS_TAP_THRESHOLD_PX) {
                            currentRenderedPositions.entries
                                .minByOrNull { (_, pos) -> (pos - gestureStart).getDistance() }
                                ?.takeIf { (_, pos) -> (pos - gestureStart).getDistance() <= TARGET_HIT_RADIUS_PX }
                                ?.let { (target, _) -> onTapTarget(target) }
                        }
                    }
                )
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f * 0.92f

        drawGrid(center, maxRadius)
        drawSweep(center, maxRadius, sweepAngle)
        visibleTargets.forEach { target ->
            drawTarget(renderedPositions.getValue(target), target, measuredLabels.getValue(target))
        }
    }
}

private fun targetPosition(center: Offset, maxRadius: Float, target: RadarTarget, rangeKm: Double): Offset {
    val pixelRadius = (target.distanceKm / rangeKm).toFloat().coerceIn(0f, 1f) * maxRadius
    val x = center.x + pixelRadius * sin(target.bearingRad.toFloat())
    val y = center.y - pixelRadius * cos(target.bearingRad.toFloat())
    return Offset(x, y)
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
    // Trailing fade wedge behind the sweep line, then the bright line itself. Purely decorative
    // now — it no longer gates target visibility (see the dense always-on-label change).
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

private fun DrawScope.drawTarget(position: Offset, target: RadarTarget, label: TextLayoutResult) {
    val headingRad = ((target.aircraft.trueTrackDeg ?: 0.0) * PI / 180.0).toFloat()
    val maxRadius = min(size.width, size.height) / 2f * 0.92f
    val noseLen = maxRadius * 0.05f
    val tailLen = maxRadius * 0.035f
    val nose = Offset(position.x + noseLen * sin(headingRad), position.y - noseLen * cos(headingRad))
    val tailRight = Offset(position.x + tailLen * sin(headingRad + 2.35f), position.y - tailLen * cos(headingRad + 2.35f))
    val tailLeft = Offset(position.x + tailLen * sin(headingRad - 2.35f), position.y - tailLen * cos(headingRad - 2.35f))

    drawPath(
        path = Path().apply {
            moveTo(nose.x, nose.y)
            lineTo(tailRight.x, tailRight.y)
            lineTo(tailLeft.x, tailLeft.y)
            close()
        },
        color = Color.White
    )

    drawText(label, color = LABEL_GREEN, topLeft = Offset(position.x + 4f, position.y - 12f))
}
