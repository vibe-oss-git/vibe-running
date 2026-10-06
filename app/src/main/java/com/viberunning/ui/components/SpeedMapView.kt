package com.viberunning.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.viberunning.data.model.LocationPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

// Toggle between coloring the route by speed or by GPS altitude
private enum class MapColorMode { Speed, Elevation }

// Blue-to-red gradient for speed (slow → fast)
private val SPEED_COLOR_STOPS = listOf(
    Color(0xFF2196F3), // Blue — slowest
    Color(0xFF4CAF50), // Green
    Color(0xFFFFEB3B), // Yellow
    Color(0xFFFF9800), // Orange
    Color(0xFFF44336)  // Red — fastest
)

// Terrain-inspired gradient for elevation (low → high)
private val ELEVATION_COLOR_STOPS = listOf(
    Color(0xFF1B5E20), // Dark green — lowest
    Color(0xFF4CAF50), // Green
    Color(0xFFCDDC39), // Lime
    Color(0xFFFF9800), // Orange
    Color(0xFF795548)  // Brown — highest
)

@Composable
fun SpeedMapView(
    points: List<LocationPoint>,
    useImperial: Boolean,
    modifier: Modifier = Modifier,
    baseHeight: Dp = 360.dp,
    enlargedHeight: Dp = 560.dp
) {
    if (points.size < 2) {
        Box(
            modifier = modifier.height(baseHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Not enough GPS data to display a map",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val rawData = remember(points) { RawMapData.from(points) }

    // Color mode toggle — speed (default) or elevation
    var colorMode by remember { mutableStateOf(MapColorMode.Speed) }

    // Speed and elevation each maintain independent filter ranges so switching
    // modes doesn't lose the user's previous slider position.
    var speedRangeMin by remember(rawData) { mutableFloatStateOf(rawData.minSpeedMph.toFloat()) }
    var speedRangeMax by remember(rawData) { mutableFloatStateOf(rawData.maxSpeedMph.toFloat()) }

    // Convert raw altitude (meters) to display units for the slider and legend
    val elevDisplayMin = remember(rawData, useImperial) {
        if (useImperial) rawData.minAltitudeMeters * 3.28084 else rawData.minAltitudeMeters
    }
    val elevDisplayMax = remember(rawData, useImperial) {
        if (useImperial) rawData.maxAltitudeMeters * 3.28084 else rawData.maxAltitudeMeters
    }
    var elevRangeMin by remember(rawData, useImperial) { mutableFloatStateOf(elevDisplayMin.toFloat()) }
    var elevRangeMax by remember(rawData, useImperial) { mutableFloatStateOf(elevDisplayMax.toFloat()) }

    // Resolve which range/colors to use based on the active color mode
    val activeMin: Float
    val activeMax: Float
    val activeAbsMin: Float
    val activeAbsMax: Float
    val activeColorStops: List<Color>

    when (colorMode) {
        MapColorMode.Speed -> {
            activeMin = speedRangeMin
            activeMax = speedRangeMax
            activeAbsMin = rawData.minSpeedMph.toFloat()
            activeAbsMax = rawData.maxSpeedMph.toFloat()
            activeColorStops = SPEED_COLOR_STOPS
        }
        MapColorMode.Elevation -> {
            activeMin = elevRangeMin
            activeMax = elevRangeMax
            activeAbsMin = elevDisplayMin.toFloat()
            activeAbsMax = elevDisplayMax.toFloat()
            activeColorStops = ELEVATION_COLOR_STOPS
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isZoomed by remember { mutableStateOf(false) }
    var isEnlarged by remember { mutableStateOf(false) }
    val mapHeight by animateDpAsState(
        targetValue = if (isEnlarged) enlargedHeight else baseHeight,
        label = "mapHeight"
    )

    Column(modifier = modifier) {
        // Show Speed/Elevation toggle chips only when valid altitude data exists
        if (rawData.hasElevationData) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                FilterChip(
                    selected = colorMode == MapColorMode.Speed,
                    onClick = { colorMode = MapColorMode.Speed },
                    label = { Text("Speed") }
                )
                FilterChip(
                    selected = colorMode == MapColorMode.Elevation,
                    onClick = { colorMode = MapColorMode.Elevation },
                    label = { Text("Elevation") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .then(
                        if (isEnlarged) {
                            Modifier.pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 10f)
                                    val maxOffsetX = (newScale - 1f) * size.width / 2f
                                    val maxOffsetY = (newScale - 1f) * size.height / 2f
                                    scale = newScale
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    isZoomed = newScale > 1.05f
                                }
                            }
                        } else Modifier
                    )
                    .pointerInput(isEnlarged) {
                        detectTapGestures(
                            onTap = {
                                if (isEnlarged && isZoomed) return@detectTapGestures
                                isEnlarged = !isEnlarged
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                isZoomed = false
                            },
                            onDoubleTap = {
                                if (isEnlarged) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                    isZoomed = false
                                }
                            }
                        )
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                ) {
                    drawFilteredRoute(
                        data = rawData,
                        colorMode = colorMode,
                        useImperial = useImperial,
                        filterMin = activeMin.toDouble(),
                        filterMax = activeMax.toDouble(),
                        absoluteMin = activeAbsMin.toDouble(),
                        absoluteMax = activeAbsMax.toDouble(),
                        colorStops = activeColorStops,
                        currentScale = scale
                    )
                }

                val hint = when {
                    !isEnlarged -> "Tap to enlarge"
                    isZoomed -> "Double-tap to reset"
                    else -> "Pinch to zoom \u00b7 tap to shrink"
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val elevUnit = if (useImperial) "ft" else "m"
        // Speed ranges are kept in mph internally; convert only for display
        val speedUnit = if (useImperial) "mph" else "km/h"
        val speedFactor = if (useImperial) 1f else 1.609344f

        MapLegend(
            label = when (colorMode) {
                MapColorMode.Speed -> "Speed Range ($speedUnit)"
                MapColorMode.Elevation -> "Elevation Range ($elevUnit)"
            },
            filterHint = when (colorMode) {
                MapColorMode.Speed -> String.format(
                    Locale.US, "Filter: %.2f \u2013 %.2f $speedUnit", activeMin * speedFactor, activeMax * speedFactor
                )
                MapColorMode.Elevation -> String.format(Locale.US, "Filter: %.0f \u2013 %.0f $elevUnit", activeMin, activeMax)
            },
            colorStops = activeColorStops,
            rangeMin = activeMin,
            rangeMax = activeMax,
            absoluteMin = activeAbsMin,
            absoluteMax = activeAbsMax,
            formatValue = { value ->
                when (colorMode) {
                    MapColorMode.Speed -> String.format(Locale.US, "%.2f", value * speedFactor)
                    MapColorMode.Elevation -> String.format(Locale.US, "%.0f", value)
                }
            },
            onRangeChange = { newMin, newMax ->
                when (colorMode) {
                    MapColorMode.Speed -> {
                        speedRangeMin = newMin
                        speedRangeMax = newMax
                    }
                    MapColorMode.Elevation -> {
                        elevRangeMin = newMin
                        elevRangeMax = newMax
                    }
                }
            },
            onReset = {
                when (colorMode) {
                    MapColorMode.Speed -> {
                        speedRangeMin = rawData.minSpeedMph.toFloat()
                        speedRangeMax = rawData.maxSpeedMph.toFloat()
                    }
                    MapColorMode.Elevation -> {
                        elevRangeMin = elevDisplayMin.toFloat()
                        elevRangeMax = elevDisplayMax.toFloat()
                    }
                }
            }
        )
    }
}

// Draws the GPS route with each segment colored by either speed or elevation.
// Segments outside [filterMin, filterMax] are hidden (drawn as gaps).
// In elevation mode, segments with missing altitude data are drawn gray.
private fun DrawScope.drawFilteredRoute(
    data: RawMapData,
    colorMode: MapColorMode,
    useImperial: Boolean,
    filterMin: Double,
    filterMax: Double,
    absoluteMin: Double,
    absoluteMax: Double,
    colorStops: List<Color>,
    currentScale: Float
) {
    if (data.normalizedPoints.isEmpty()) return

    val padding = 16f
    // Square drawing region preserves aspect ratio regardless of canvas shape
    val side = min(size.width, size.height) - padding * 2
    val originX = (size.width - side) / 2f
    val originY = (size.height - side) / 2f
    // Scale stroke inversely with zoom so lines don't become huge when pinch-zoomed
    val strokeWidth = (6f / currentScale).coerceIn(1f, 6f)
    val dotRadius = (10f / currentScale).coerceIn(3f, 10f)
    // Absolute color range — colors stay fixed even when the filter narrows
    val colorSpan = (absoluteMax - absoluteMin).coerceAtLeast(0.01)
    val noDataColor = Color(0xFF9E9E9E) // Gray for segments missing elevation data

    for (seg in data.segmentData) {
        val value = when (colorMode) {
            MapColorMode.Speed -> seg.avgSpeedMph
            MapColorMode.Elevation -> {
                if (seg.avgAltitudeMeters.isNaN()) {
                    // No elevation data for this segment — draw gray
                    val startX = originX + seg.startX * side
                    val startY = originY + seg.startY * side
                    val endX = originX + seg.endX * side
                    val endY = originY + seg.endY * side
                    drawLine(
                        color = noDataColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    continue
                }
                if (useImperial) seg.avgAltitudeMeters * 3.28084 else seg.avgAltitudeMeters
            }
        }

        if (value < filterMin || value > filterMax) continue

        val fraction = ((value - absoluteMin) / colorSpan).toFloat().coerceIn(0f, 1f)
        val color = interpolateColor(fraction, colorStops)

        val startX = originX + seg.startX * side
        val startY = originY + seg.startY * side
        val endX = originX + seg.endX * side
        val endY = originY + seg.endY * side

        drawLine(
            color = color,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    if (data.normalizedPoints.isNotEmpty()) {
        val first = data.normalizedPoints.first()
        drawCircle(
            color = Color(0xFF4CAF50),
            radius = dotRadius,
            center = Offset(originX + first.first * side, originY + first.second * side)
        )
        val last = data.normalizedPoints.last()
        drawCircle(
            color = Color(0xFFF44336),
            radius = dotRadius,
            center = Offset(originX + last.first * side, originY + last.second * side)
        )
    }
}

// Reusable gradient legend with range slider, used for both speed and elevation modes.
// Shows a color bar, min/mid/max labels, and a draggable range filter.
@Composable
private fun MapLegend(
    label: String,
    filterHint: String,
    colorStops: List<Color>,
    rangeMin: Float,
    rangeMax: Float,
    absoluteMin: Float,
    absoluteMax: Float,
    formatValue: (Float) -> String,
    onRangeChange: (Float, Float) -> Unit,
    onReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val isCustom = rangeMin > absoluteMin + 0.01f || rangeMax < absoluteMax - 0.01f
            if (isCustom) {
                TextButton(onClick = onReset) {
                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(colorStops))
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatValue(absoluteMin),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatValue((absoluteMin + absoluteMax) / 2f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = formatValue(absoluteMax),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        RangeSlider(
            value = rangeMin..rangeMax,
            onValueChange = { range ->
                onRangeChange(range.start, range.endInclusive)
            },
            valueRange = absoluteMin..absoluteMax,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = filterHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Drag handles to filter range. Segments outside the range are hidden.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// Linearly interpolates between color stops for a smooth gradient mapping
private fun interpolateColor(fraction: Float, stops: List<Color>): Color {
    if (fraction <= 0f) return stops.first()
    if (fraction >= 1f) return stops.last()

    val scaledPos = fraction * (stops.size - 1)
    val index = scaledPos.toInt().coerceIn(0, stops.size - 2)
    val localFraction = scaledPos - index

    val c1 = stops[index]
    val c2 = stops[index + 1]

    return Color(
        red = c1.red + (c2.red - c1.red) * localFraction,
        green = c1.green + (c2.green - c1.green) * localFraction,
        blue = c1.blue + (c2.blue - c1.blue) * localFraction,
        alpha = 1f
    )
}

// Pre-computed segment data for efficient re-rendering when filter or color mode changes.
// avgAltitudeMeters is NaN for segments where altitude was culled (missing data).
private data class SegmentData(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val avgSpeedMph: Double,
    val avgAltitudeMeters: Double
)

private class RawMapData(
    val normalizedPoints: List<Pair<Float, Float>>,
    val segmentData: List<SegmentData>,
    val minSpeedMph: Double,
    val maxSpeedMph: Double,
    val minAltitudeMeters: Double,
    val maxAltitudeMeters: Double,
    val hasElevationData: Boolean
) {
    companion object {
        fun from(points: List<LocationPoint>): RawMapData {
            if (points.size < 2) return RawMapData(
                emptyList(), emptyList(), 0.0, 0.0, 0.0, 0.0, false
            )

            val speedsMph = points.map { it.speedMps * 2.23694 }
            val minSpeed = speedsMph.min()
            val maxSpeed = max(speedsMph.max(), minSpeed + 0.01)

            // Altitude culling: GPS reports 0.0 when it has no altitude fix.
            // If non-zero readings exist and they're all near sea level (<30m),
            // the 0.0 values are likely real → keep them. Otherwise, treat 0.0
            // as missing data and exclude from the color range.
            val allAltitudes = points.map { it.altitude }
            val nonZeroAltitudes = allAltitudes.filter { it != 0.0 }
            val cullZeros: Boolean
            val hasElevation: Boolean

            if (nonZeroAltitudes.isEmpty()) {
                // No altitude data at all
                cullZeros = false
                hasElevation = false
            } else if (nonZeroAltitudes.all { abs(it) < 30.0 }) {
                // All near sea level — 0.0 values are likely genuine readings
                cullZeros = false
                hasElevation = true
            } else {
                // Mix of real altitudes and 0.0 gaps — cull the zeros
                cullZeros = allAltitudes.any { it == 0.0 }
                hasElevation = true
            }

            val validAltitudes = if (cullZeros) nonZeroAltitudes else allAltitudes
            val minAlt = if (hasElevation) validAltitudes.min() else 0.0
            val maxAlt = if (hasElevation) max(validAltitudes.max(), minAlt + 0.1) else 0.0

            // Project lat/lon to x/y
            val centerLat = (points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2.0
            val cosLat = cos(Math.toRadians(centerLat))

            val xs = points.map { it.longitude * cosLat }
            val ys = points.map { it.latitude }

            val minX = xs.min()
            val maxX = xs.max()
            val minY = ys.min()
            val maxY = ys.max()

            val rangeX = max(maxX - minX, 0.000001)
            val rangeY = max(maxY - minY, 0.000001)
            val scale = max(rangeX, rangeY)

            val offsetX = (scale - rangeX) / 2.0
            val offsetY = (scale - rangeY) / 2.0

            val normX = xs.map { ((it - minX + offsetX) / scale).toFloat() }
            val normY = ys.map { (1.0f - ((it - minY + offsetY) / scale)).toFloat() }

            val normalizedPoints = normX.zip(normY)

            val segments = mutableListOf<SegmentData>()
            for (i in 0 until points.size - 1) {
                val avgSpeedMph = (speedsMph[i] + speedsMph[i + 1]) / 2.0

                // Mark segments with culled altitude endpoints as NaN (drawn gray)
                val alt1 = points[i].altitude
                val alt2 = points[i + 1].altitude
                val avgAlt = if (cullZeros && (alt1 == 0.0 || alt2 == 0.0)) {
                    Double.NaN
                } else {
                    (alt1 + alt2) / 2.0
                }

                segments.add(
                    SegmentData(
                        startX = normX[i],
                        startY = normY[i],
                        endX = normX[i + 1],
                        endY = normY[i + 1],
                        avgSpeedMph = avgSpeedMph,
                        avgAltitudeMeters = avgAlt
                    )
                )
            }

            return RawMapData(
                normalizedPoints, segments, minSpeed, maxSpeed,
                minAlt, maxAlt, hasElevation
            )
        }
    }
}
