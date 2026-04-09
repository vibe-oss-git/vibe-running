package com.viberunning.ui.components

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
import androidx.compose.ui.unit.dp
import com.viberunning.data.model.LocationPoint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

private val SPEED_COLOR_STOPS = listOf(
    Color(0xFF2196F3), // Blue — slowest
    Color(0xFF4CAF50), // Green
    Color(0xFFFFEB3B), // Yellow
    Color(0xFFFF9800), // Orange
    Color(0xFFF44336)  // Red — fastest
)

@Composable
fun SpeedMapView(
    points: List<LocationPoint>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Not enough GPS data to display a map",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val rawData = remember(points) { RawMapData.from(points) }

    // Adjustable range — initialized to the actual min/max
    var rangeMin by remember(rawData) { mutableFloatStateOf(rawData.minSpeedMph.toFloat()) }
    var rangeMax by remember(rawData) { mutableFloatStateOf(rawData.maxSpeedMph.toFloat()) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isZoomed by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Map canvas with zoom/pan
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 10f)
                            // Adjust offset to keep the map within bounds when zooming
                            val maxOffsetX = (newScale - 1f) * size.width / 2f
                            val maxOffsetY = (newScale - 1f) * size.height / 2f
                            scale = newScale
                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            isZoomed = newScale > 1.05f
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // Reset zoom on double tap
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                isZoomed = false
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
                    drawFilteredRoute(rawData, rangeMin.toDouble(), rangeMax.toDouble(), scale)
                }

                // Zoom hint
                if (!isZoomed) {
                    Text(
                        text = "Pinch to zoom",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                    )
                } else {
                    Text(
                        text = "Double-tap to reset",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Speed legend with gradient
        SpeedLegend(
            rangeMin = rangeMin,
            rangeMax = rangeMax,
            absoluteMin = rawData.minSpeedMph.toFloat(),
            absoluteMax = rawData.maxSpeedMph.toFloat(),
            onRangeChange = { newMin, newMax ->
                rangeMin = newMin
                rangeMax = newMax
            },
            onReset = {
                rangeMin = rawData.minSpeedMph.toFloat()
                rangeMax = rawData.maxSpeedMph.toFloat()
            }
        )
    }
}

private fun DrawScope.drawFilteredRoute(
    data: RawMapData,
    filterMin: Double,
    filterMax: Double,
    currentScale: Float
) {
    if (data.normalizedPoints.isEmpty()) return

    val padding = 16f
    val drawWidth = size.width - padding * 2
    val drawHeight = size.height - padding * 2
    // Scale stroke width inversely with zoom so lines don't become huge
    val strokeWidth = (6f / currentScale).coerceIn(1f, 6f)
    val dotRadius = (10f / currentScale).coerceIn(3f, 10f)

    val effectiveMin = filterMin.coerceAtMost(filterMax - 0.01)
    val effectiveMax = filterMax

    for (seg in data.segmentData) {
        val avgSpeed = seg.avgSpeedMph
        // Skip segments outside the filter range (draw as gap)
        if (avgSpeed < effectiveMin || avgSpeed > effectiveMax) continue

        val fraction = ((avgSpeed - effectiveMin) / (effectiveMax - effectiveMin))
            .toFloat().coerceIn(0f, 1f)
        val color = interpolateColor(fraction)

        val startX = padding + seg.startX * drawWidth
        val startY = padding + seg.startY * drawHeight
        val endX = padding + seg.endX * drawWidth
        val endY = padding + seg.endY * drawHeight

        drawLine(
            color = color,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    // Start dot (green)
    if (data.normalizedPoints.isNotEmpty()) {
        val first = data.normalizedPoints.first()
        drawCircle(
            color = Color(0xFF4CAF50),
            radius = dotRadius,
            center = Offset(padding + first.first * drawWidth, padding + first.second * drawHeight)
        )
        val last = data.normalizedPoints.last()
        drawCircle(
            color = Color(0xFFF44336),
            radius = dotRadius,
            center = Offset(padding + last.first * drawWidth, padding + last.second * drawHeight)
        )
    }
}

@Composable
private fun SpeedLegend(
    rangeMin: Float,
    rangeMax: Float,
    absoluteMin: Float,
    absoluteMax: Float,
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
                text = "Speed Range (mph)",
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

        // Gradient bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(SPEED_COLOR_STOPS))
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Range labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(Locale.US, "%.2f", rangeMin),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "%.2f", (rangeMin + rangeMax) / 2f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = String.format(Locale.US, "%.2f", rangeMax),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Range slider
        RangeSlider(
            value = rangeMin..rangeMax,
            onValueChange = { range ->
                onRangeChange(range.start, range.endInclusive)
            },
            valueRange = absoluteMin..absoluteMax,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Text(
            text = "Drag handles to filter speed range. Segments outside the range are hidden.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

private fun interpolateColor(fraction: Float): Color {
    val stops = SPEED_COLOR_STOPS
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

// Pre-computed segment data for efficient re-rendering when filter changes
private data class SegmentData(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val avgSpeedMph: Double
)

private class RawMapData(
    val normalizedPoints: List<Pair<Float, Float>>,
    val segmentData: List<SegmentData>,
    val minSpeedMph: Double,
    val maxSpeedMph: Double
) {
    companion object {
        fun from(points: List<LocationPoint>): RawMapData {
            if (points.size < 2) return RawMapData(emptyList(), emptyList(), 0.0, 0.0)

            val speedsMph = points.map { it.speedMps * 2.23694 }
            val minSpeed = speedsMph.min()
            val maxSpeed = max(speedsMph.max(), minSpeed + 0.01)

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
                segments.add(
                    SegmentData(
                        startX = normX[i],
                        startY = normY[i],
                        endX = normX[i + 1],
                        endY = normY[i + 1],
                        avgSpeedMph = avgSpeedMph
                    )
                )
            }

            return RawMapData(normalizedPoints, segments, minSpeed, maxSpeed)
        }
    }
}
