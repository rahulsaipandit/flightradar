package com.deskradar.wear

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.deskradar.common.AircraftCategory
import com.deskradar.common.RadarMarkerPosition
import com.deskradar.common.RadarTarget
import com.deskradar.common.categories
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val GRID_GREEN = Color(0xFF00A000)
private val BRIGHT_GREEN = Color(0xFF00FF00)
private val LABEL_GREEN = Color(0xFF00E000)

// Aircraft icon tint, by category priority: military (any type) > airliner > everything else
// (private aircraft and non-military helicopters).
private val MILITARY_GREEN = Color(0xFF4B5320)
private val AIRLINER_YELLOW = Color(0xFFFFD700)
private val PRIVATE_RED = Color(0xFFE53935)

/** One bundled top-down aircraft silhouette (see res/drawable-nodpi/aircraft_*.png). */
private data class AircraftIconAsset(val bitmap: ImageBitmap, val baseRotationDeg: Float = 0f)

private data class AircraftIcons(
    val helicopter: AircraftIconAsset,
    val military: AircraftIconAsset,
    val airliner: AircraftIconAsset,
    val private_: AircraftIconAsset
)

// Reference-image color scheme: white for the aircraft identifier, blue for the numbers,
// orange for the climb/descent arrow — not everything green.
private val CALLSIGN_WHITE = Color(0xFFFFFFFF)
private val INFO_BLUE = Color(0xFF4FC3F7)
private val CLIMB_ORANGE = Color(0xFFFFA000)

private const val ZOOM_STEP_KM = 5.0
private const val ROTARY_KM_PER_PIXEL = 0.08

/** Extra margin added around each target's icon+label bounding box for a more forgiving tap. */
private const val HIT_AREA_PADDING_PX = 12f

/** Aviation-standard units (matches the reference image) — feet for altitude, knots for speed. */
private fun altitudeLabel(target: RadarTarget): String =
    target.aircraft.altitudeMeters?.let { "${(it * 3.28084).roundToInt()}ft" } ?: "GND"

private fun speedLabel(target: RadarTarget): String =
    target.aircraft.velocityMs?.let { "${(it * 1.94384).roundToInt()}kt" } ?: "?"

/** "↑" climbing, "↓" descending, "" level/unknown — vertical rate threshold avoids jitter near 0. */
private fun climbArrow(target: RadarTarget): String {
    val rate = target.aircraft.verticalRateMs ?: return ""
    return when {
        rate > 0.5 -> "↑"
        rate < -0.5 -> "↓"
        else -> ""
    }
}

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
    isAmbient: Boolean,
    activeFilters: Set<AircraftCategory>,
    myLocationMarker: RadarMarkerPosition?,
    onGrantPermission: () -> Unit,
    onZoom: (deltaKm: Double) -> Unit,
    onPan: (eastwardKm: Double, northwardKm: Double) -> Unit,
    onRecenter: () -> Unit,
    onToggleFilter: (AircraftCategory) -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }

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
                RadarSweep(targets = emptyList(), displayRangeKm = displayRangeKm, isAmbient = isAmbient, activeFilters = activeFilters, myLocationMarker = myLocationMarker, onZoom = onZoom, onPan = onPan, onTapTarget = {})
                Text("GETTING LOCATION...", color = BRIGHT_GREEN, style = MaterialTheme.typography.caption2)
            }
            is RadarUiState.Data -> {
                var selected by remember { mutableStateOf<RadarTarget?>(null) }
                RadarSweep(
                    targets = uiState.targets,
                    displayRangeKm = displayRangeKm,
                    isAmbient = isAmbient,
                    activeFilters = activeFilters,
                    myLocationMarker = myLocationMarker,
                    onZoom = onZoom,
                    onPan = onPan,
                    onTapTarget = { selected = it }
                )
                selected?.let {
                    DetailOverlay(target = it, onDismiss = { selected = null })
                }
            }
        }

        // No interactive chrome in ambient — matches Wear OS ambient conventions (touch is
        // typically disabled by the system there anyway) and avoids static UI elements burning in.
        if (uiState !is RadarUiState.PermissionRequired && !isAmbient) {
            ZoomStepper(
                displayRangeKm = displayRangeKm,
                onZoom = onZoom,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            )
            HamburgerButton(
                onClick = { showFilterMenu = !showFilterMenu },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp)
            )
            if (showFilterMenu) {
                FilterMenu(
                    activeFilters = activeFilters,
                    onToggleFilter = onToggleFilter,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp)
                )
            }
            CenterResetIcon(
                isAwayFromDefault = isPanned || displayRangeKm != RadarViewModel.DEFAULT_DISPLAY_RANGE_KM,
                onClick = onRecenter,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun CenterResetIcon(isAwayFromDefault: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        "⌖",
        color = if (isAwayFromDefault) CLIMB_ORANGE else Color.Gray,
        style = MaterialTheme.typography.title2,
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun HamburgerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .border(1.dp, CLIMB_ORANGE, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("☰", color = CLIMB_ORANGE, style = MaterialTheme.typography.caption1)
    }
}

@Composable
private fun FilterMenu(
    activeFilters: Set<AircraftCategory>,
    onToggleFilter: (AircraftCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        AircraftCategory.MILITARY to "Military",
        AircraftCategory.HELICOPTER to "Helicopters",
        AircraftCategory.AIRLINER to "Airliners",
        AircraftCategory.PRIVATE to "Private"
    )
    Column(
        modifier = modifier.background(Color.Black.copy(alpha = 0.85f)).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        options.forEach { (category, label) ->
            val active = category in activeFilters
            Text(
                (if (active) "✓ " else "  ") + label,
                color = if (active) CLIMB_ORANGE else GRID_GREEN,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.clickable { onToggleFilter(category) }.padding(2.dp)
            )
        }
    }
}

@Composable
private fun ZoomStepper(displayRangeKm: Double, onZoom: (Double) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("‹", color = CLIMB_ORANGE, style = MaterialTheme.typography.button, modifier = Modifier.clickable { onZoom(-ZOOM_STEP_KM) })
        Text("${displayRangeKm.roundToInt()} km", color = CLIMB_ORANGE, style = MaterialTheme.typography.caption1)
        Text("›", color = CLIMB_ORANGE, style = MaterialTheme.typography.button, modifier = Modifier.clickable { onZoom(ZOOM_STEP_KM) })
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
            Text(target.aircraft.callsign, color = CALLSIGN_WHITE, style = MaterialTheme.typography.title3)
            Text("${altitudeLabel(target)} ${climbArrow(target)}".trim(), color = INFO_BLUE, style = MaterialTheme.typography.body2)
            Text(speedLabel(target), color = INFO_BLUE, style = MaterialTheme.typography.body2)
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
    isAmbient: Boolean,
    activeFilters: Set<AircraftCategory>,
    myLocationMarker: RadarMarkerPosition?,
    onZoom: (Double) -> Unit,
    onPan: (Double, Double) -> Unit,
    onTapTarget: (RadarTarget) -> Unit
) {
    // Ambient: skip creating the InfiniteTransition/animateFloat entirely rather than just
    // ignoring its output — a merely-unused animation still ticks a Choreographer callback every
    // frame, which is exactly the "never let the SoC idle" cost ambient mode exists to avoid.
    val sweepAngle: Float = if (isAmbient) {
        0f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "sweep")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sweepAngle"
        )
        angle
    }

    val textMeasurer = rememberTextMeasurer()

    // Static text, independent of aircraft data — measure once, not per frame.
    val compassLabels = remember {
        listOf("N", "E", "S", "W").associateWith { direction ->
            textMeasurer.measure(direction, style = TextStyle(color = GRID_GREEN, fontSize = 10.sp))
        }
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val context = LocalContext.current
    val icons = remember {
        fun bitmap(id: Int) = BitmapFactory.decodeResource(context.resources, id).asImageBitmap()
        AircraftIcons(
            helicopter = AircraftIconAsset(bitmap(R.drawable.aircraft_helicopter), baseRotationDeg = 90f),
            military = AircraftIconAsset(bitmap(R.drawable.aircraft_military)),
            airliner = AircraftIconAsset(bitmap(R.drawable.aircraft_airliner)),
            private_ = AircraftIconAsset(bitmap(R.drawable.aircraft_private))
        )
    }

    // Only aircraft within the current zoom level and matching the active category filters (an
    // empty filter set means "show everything") are drawn/hit-testable — the repository
    // fetches/projects a superset (up to MAX_DISPLAY_RANGE_KM) so zooming is an instant
    // client-side re-filter, not a re-fetch.
    val visibleTargets = remember(targets, displayRangeKm, activeFilters) {
        targets.filter { target ->
            target.distanceKm <= displayRangeKm &&
                (activeFilters.isEmpty() || target.aircraft.categories().any { it in activeFilters })
        }
    }

    val measuredLabels = remember(visibleTargets) {
        visibleTargets.associateWith { target ->
            val arrow = climbArrow(target)
            textMeasurer.measure(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = CALLSIGN_WHITE)) { append(target.aircraft.callsign) }
                    append("\n")
                    withStyle(SpanStyle(color = INFO_BLUE)) { append(altitudeLabel(target)) }
                    if (arrow.isNotEmpty()) {
                        withStyle(SpanStyle(color = CLIMB_ORANGE)) { append(" $arrow") }
                    }
                    // Every span needs an explicit color — an unstyled span defaults to black,
                    // invisible against our black background (this is what ate the "|" earlier).
                    withStyle(SpanStyle(color = LABEL_GREEN)) { append(" | ") }
                    withStyle(SpanStyle(color = INFO_BLUE)) { append(speedLabel(target)) }
                },
                style = TextStyle(fontSize = 11.sp)
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

    // A generous hit box per target covering both the icon and its label (the label is drawn
    // offset up-and-right of the icon, so "distance to icon center" alone misses taps on the
    // text) — tapping the callsign/altitude text should select the aircraft just as well as
    // tapping the tiny triangle.
    val hitAreas = remember(visibleTargets, renderedPositions, measuredLabels, canvasSize) {
        val maxRadius = min(canvasSize.width, canvasSize.height) / 2f * 0.92f
        val iconRadius = maxRadius * 0.11f
        visibleTargets.associateWith { target ->
            val position = renderedPositions.getValue(target)
            val label = measuredLabels.getValue(target)
            val labelLeft = position.x + 4f
            val labelTop = position.y - 12f
            Rect(
                left = minOf(position.x - iconRadius, labelLeft) - HIT_AREA_PADDING_PX,
                top = minOf(position.y - iconRadius, labelTop) - HIT_AREA_PADDING_PX,
                right = maxOf(position.x + iconRadius, labelLeft + label.size.width) + HIT_AREA_PADDING_PX,
                bottom = maxOf(position.y + iconRadius, labelTop + label.size.height) + HIT_AREA_PADDING_PX
            )
        }
    }
    // pointerInput's keys below are (canvasSize, displayRangeKm) — not targets — so its gesture
    // coroutine does NOT restart when a new poll snapshot arrives (~every 108s), and a plain
    // captured `hitAreas` would go stale, hit-testing taps against long-gone aircraft.
    // rememberUpdatedState keeps the running gesture handler reading the latest map without
    // needing a restart (which would also cancel any drag the user is mid-gesture on).
    val currentHitAreas by rememberUpdatedState(hitAreas)

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
                // detectDragGestures only recognizes gestures that move past touch slop — a
                // stationary tap (down+up with no movement) never invokes its callbacks at all,
                // so a "was the total drag small?" check inside onDragEnd never gets a chance to
                // run for a real tap. Using the lower-level primitives here instead, so a tap
                // that never crosses the slop threshold is handled as its own case.
                val maxRadius = min(canvasSize.width, canvasSize.height) / 2f * 0.92f
                val kmPerPixel = if (maxRadius > 0f) displayRangeKm / maxRadius else 0.0
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // android.util.Log.d("DeskRadarGesture", "down at ${down.position}")
                    val drag = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (drag == null) {
                        // android.util.Log.d("DeskRadarGesture", "resolved as TAP (no slop crossed)")
                        // Never crossed the slop threshold — a tap, if the finger actually lifted.
                        if (waitForUpOrCancellation() != null) {
                            currentHitAreas.entries
                                .firstOrNull { (_, rect) -> rect.contains(down.position) }
                                ?.let { (target, _) -> onTapTarget(target) }
                        }
                    } else {
                        // android.util.Log.d("DeskRadarGesture", "resolved as DRAG (slop crossed), kmPerPixel=$kmPerPixel")
                        // Slop exceeded — a genuine pan gesture.
                        drag(drag.id) { change ->
                            val eastKm = -change.positionChange().x * kmPerPixel
                            val northKm = change.positionChange().y * kmPerPixel
                            // android.util.Log.d("DeskRadarGesture", "onPan(east=$eastKm, north=$northKm)")
                            onPan(eastKm, northKm)
                            change.consume()
                        }
                        // android.util.Log.d("DeskRadarGesture", "drag ended")
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f * 0.92f

        drawGrid(center, maxRadius, compassLabels)
        if (!isAmbient) {
            drawSweep(center, maxRadius, sweepAngle)
        }
        // Only meaningful once panned away — when centered on GPS, "my location" IS the center
        // (shown by the center reset reticle instead), so this would just duplicate it.
        if (myLocationMarker != null && myLocationMarker.distanceKm <= displayRangeKm) {
            val pixelRadius = (myLocationMarker.distanceKm / displayRangeKm).toFloat().coerceIn(0f, 1f) * maxRadius
            val dotX = center.x + pixelRadius * sin(myLocationMarker.bearingRad.toFloat())
            val dotY = center.y - pixelRadius * cos(myLocationMarker.bearingRad.toFloat())
            drawCircle(Color(0xFF2196F3), radius = maxRadius * 0.035f, center = Offset(dotX, dotY))
        }
        visibleTargets.forEach { target ->
            drawTarget(renderedPositions.getValue(target), target, measuredLabels.getValue(target), icons)
        }
    }
}

private fun targetPosition(center: Offset, maxRadius: Float, target: RadarTarget, rangeKm: Double): Offset {
    val pixelRadius = (target.distanceKm / rangeKm).toFloat().coerceIn(0f, 1f) * maxRadius
    val x = center.x + pixelRadius * sin(target.bearingRad.toFloat())
    val y = center.y - pixelRadius * cos(target.bearingRad.toFloat())
    return Offset(x, y)
}

private const val GRID_RING_COUNT = 7

private fun DrawScope.drawGrid(center: Offset, maxRadius: Float, compassLabels: Map<String, TextLayoutResult>) {
    for (ring in 1..GRID_RING_COUNT) {
        val fraction = ring / GRID_RING_COUNT.toFloat()
        drawCircle(
            color = if (ring == GRID_RING_COUNT) BRIGHT_GREEN else GRID_GREEN,
            radius = maxRadius * fraction,
            center = center,
            style = Stroke(width = 1.5f)
        )
    }
    drawLine(GRID_GREEN, Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius), 1f)
    drawLine(GRID_GREEN, Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y), 1f)

    val edgeMargin = 12f
    fun drawCompassLabel(direction: String, at: Offset) {
        val label = compassLabels.getValue(direction)
        drawText(label, topLeft = Offset(at.x - label.size.width / 2f, at.y - label.size.height / 2f))
    }
    drawCompassLabel("N", Offset(center.x, center.y - maxRadius + edgeMargin))
    drawCompassLabel("S", Offset(center.x, center.y + maxRadius - edgeMargin))
    drawCompassLabel("E", Offset(center.x + maxRadius - edgeMargin, center.y))
    drawCompassLabel("W", Offset(center.x - maxRadius + edgeMargin, center.y))
}

private const val SWEEP_TRAIL_SEGMENTS = 48
private val SWEEP_TRAIL_SPAN_RAD = (PI / 2).toFloat() // 90° fading tail behind the leading edge

private fun DrawScope.drawSweep(center: Offset, maxRadius: Float, sweepAngle: Float) {
    // A "comet tail" of many thin radial lines, each further behind the leading edge and more
    // transparent — this is what actually reads as a glowing radar wedge (a Brush.sweepGradient
    // clipped to a narrow arc doesn't work: the gradient's bright point sits at a fixed angle
    // around the *full* 360°, which usually lands nowhere near the arc slice you're drawing).
    for (i in 0 until SWEEP_TRAIL_SEGMENTS) {
        val fraction = i / SWEEP_TRAIL_SEGMENTS.toFloat()
        val trailAngle = sweepAngle - fraction * SWEEP_TRAIL_SPAN_RAD
        val alpha = (1f - fraction) * 0.55f
        val tx = center.x + maxRadius * sin(trailAngle)
        val ty = center.y - maxRadius * cos(trailAngle)
        drawLine(GRID_GREEN.copy(alpha = alpha), center, Offset(tx, ty), strokeWidth = 2f)
    }
    val endX = center.x + maxRadius * sin(sweepAngle)
    val endY = center.y - maxRadius * cos(sweepAngle)
    drawLine(BRIGHT_GREEN, center, Offset(endX, endY), strokeWidth = 2.5f)
}

/** Which bundled icon to use, by category priority — HELICOPTER's shape wins regardless of military status. */
private fun aircraftIcon(categories: Set<AircraftCategory>, icons: AircraftIcons): AircraftIconAsset = when {
    AircraftCategory.HELICOPTER in categories -> icons.helicopter
    AircraftCategory.MILITARY in categories -> icons.military
    AircraftCategory.AIRLINER in categories -> icons.airliner
    else -> icons.private_
}

/** Tint color, by category priority — independent of icon shape (e.g. a military helicopter is green). */
private fun aircraftTint(categories: Set<AircraftCategory>): Color = when {
    AircraftCategory.MILITARY in categories -> MILITARY_GREEN
    AircraftCategory.AIRLINER in categories -> AIRLINER_YELLOW
    else -> PRIVATE_RED
}

private fun DrawScope.drawTarget(position: Offset, target: RadarTarget, label: TextLayoutResult, icons: AircraftIcons) {
    val headingDeg = (target.aircraft.trueTrackDeg ?: 0.0).toFloat()
    val maxRadius = min(size.width, size.height) / 2f * 0.92f
    val iconSpan = maxRadius * 0.26f // matches the old hand-drawn icons' ~2*scale span

    val categories = target.aircraft.categories()
    val icon = aircraftIcon(categories, icons)
    val tint = aircraftTint(categories)

    val aspect = icon.bitmap.width.toFloat() / icon.bitmap.height.toFloat()
    val (dstWidth, dstHeight) = if (aspect >= 1f) iconSpan to iconSpan / aspect else iconSpan * aspect to iconSpan

    // Source art has "up" as the nose direction already, except the helicopter's front faces
    // left in its source image — an extra +90° baseline rotation aligns it to "up" like the rest.
    rotate(degrees = headingDeg + icon.baseRotationDeg, pivot = position) {
        drawImage(
            image = icon.bitmap,
            dstOffset = IntOffset((position.x - dstWidth / 2f).toInt(), (position.y - dstHeight / 2f).toInt()),
            dstSize = IntSize(dstWidth.toInt(), dstHeight.toInt()),
            colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)
        )
    }

    // No color override here — the label's per-span colors (white callsign, blue numbers,
    // orange arrow) come from the AnnotatedString it was measured with.
    drawText(label, topLeft = Offset(position.x + 4f, position.y - 12f))
}
