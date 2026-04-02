package com.viberunning.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.viberunning.data.model.LocationPoint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

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

    val mapData = remember(points) { MapData.from(points) }

    Column(modifier = modifier) {
        // Map canvas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                drawRoute(mapData)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Speed legend
        SpeedLegend(
            minSpeedMph = mapData.minSpeedMph,
            maxSpeedMph = mapData.maxSpeedMph
        )
    }
}

private fun DrawScope.drawRoute(mapData: MapData) {
    if (mapData.segments.isEmpty()) return

    val padding = 16f
    val drawWidth = size.width - padding * 2
    val drawHeight = size.height - padding * 2

    for (segment in mapData.segments) {
        val startX = padding + segment.startX * drawWidth
        val startY = padding + segment.startY * drawHeight
        val endX = padding + segment.endX * drawWidth
        val endY = padding + segment.endY * drawHeight

        drawLine(
            color = segment.color,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
    }

    // Start dot (green)
    val firstSeg = mapData.segments.first()
    drawCircle(
        color = Color(0xFF4CAF50),
        radius = 10f,
        center = Offset(padding + firstSeg.startX * drawWidth, padding + firstSeg.startY * drawHeight)
    )

    // End dot (red)
    val lastSeg = mapData.segments.last()
    drawCircle(
        color = Color(0xFFF44336),
        radius = 10f,
        center = Offset(padding + lastSeg.endX * drawWidth, padding + lastSeg.endY * drawHeight)
    )
}

@Composable
private fun SpeedLegend(minSpeedMph: Double, maxSpeedMph: Double) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Speed (mph)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Gradient bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(SPEED_COLOR_STOPS)
                )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Min/max labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(Locale.US, "%.2f", minSpeedMph),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "%.2f", (minSpeedMph + maxSpeedMph) / 2),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "%.2f", maxSpeedMph),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class Segment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val color: Color
)

private class MapData(
    val segments: List<Segment>,
    val minSpeedMph: Double,
    val maxSpeedMph: Double
) {
    companion object {
        fun from(points: List<LocationPoint>): MapData {
            if (points.size < 2) return MapData(emptyList(), 0.0, 0.0)

            // Convert speeds to mph
            val speedsMph = points.map { it.speedMps * 2.23694 }
            val minSpeed = speedsMph.min()
            val maxSpeed = max(speedsMph.max(), minSpeed + 0.01)

            // Project lat/lon to x/y using Mercator-like scaling
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

            // Maintain aspect ratio
            val scale = max(rangeX, rangeY)

            val offsetX = (scale - rangeX) / 2.0
            val offsetY = (scale - rangeY) / 2.0

            val normX = xs.map { ((it - minX + offsetX) / scale).toFloat() }
            // Flip Y since screen Y increases downward
            val normY = ys.map { (1.0f - ((it - minY + offsetY) / scale)).toFloat() }

            val segments = mutableListOf<Segment>()
            for (i in 0 until points.size - 1) {
                val speedFraction = ((speedsMph[i] + speedsMph[i + 1]) / 2.0 - minSpeed) / (maxSpeed - minSpeed)
                val color = interpolateColor(speedFraction.toFloat().coerceIn(0f, 1f))
                segments.add(
                    Segment(
                        startX = normX[i],
                        startY = normY[i],
                        endX = normX[i + 1],
                        endY = normY[i + 1],
                        color = color
                    )
                )
            }

            return MapData(segments, minSpeed, maxSpeed)
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
    }
}
