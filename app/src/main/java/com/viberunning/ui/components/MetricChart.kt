package com.viberunning.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.viberunning.data.model.Activity
import com.viberunning.util.FormatUtils

private enum class ChartKind { BARS, LINE }

private data class MetricSpec(
    val label: String,
    val kind: ChartKind,
    val extract: (Activity) -> Double,
    val format: (Double, Boolean) -> String
)

@Composable
fun MetricChart(
    activities: List<Activity>,
    useImperial: Boolean,
    modifier: Modifier = Modifier
) {
    val metrics = remember(useImperial) {
        listOf(
            MetricSpec("Distance", ChartKind.BARS,
                { it.distanceMeters },
                { v, imp -> FormatUtils.formatDistance(v, imp) }),
            MetricSpec("Duration", ChartKind.BARS,
                { it.durationMillis.toDouble() },
                { v, _ -> FormatUtils.formatDuration(v.toLong()) }),
            MetricSpec("Calories", ChartKind.BARS,
                { it.caloriesBurned.toDouble() },
                { v, _ -> "%,d kcal".format(v.toInt()) }),
            MetricSpec("Avg Speed", ChartKind.LINE,
                { it.avgSpeedMps },
                { v, imp -> FormatUtils.formatSpeed(v, imp) }),
            MetricSpec("Top Speed", ChartKind.LINE,
                { it.maxSpeedMps },
                { v, imp -> FormatUtils.formatSpeed(v, imp) }),
            MetricSpec("Pace", ChartKind.LINE,
                { it.avgSpeedMps },
                { v, imp -> FormatUtils.formatPace(v, imp) })
        )
    }

    var selectedIndex by remember { mutableStateOf(0) }
    val selected = metrics[selectedIndex]

    // Activities arrive newest-first; reverse for chronological left-to-right
    val chronological = remember(activities) { activities.asReversed() }
    val values = chronological.map { selected.extract(it) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            metrics.forEachIndexed { i, m ->
                FilterChip(
                    selected = i == selectedIndex,
                    onClick = { selectedIndex = i },
                    label = { Text(m.label) }
                )
            }
        }

        if (values.isEmpty() || values.all { it == 0.0 }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not enough data yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        val maxV = values.max()
        val minV = if (selected.label == "Pace") {
            values.filter { it > 0 }.minOrNull() ?: 0.0
        } else 0.0

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Max: ${selected.format(maxV, useImperial)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "n=${values.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val barColor = MaterialTheme.colorScheme.primary
                val lineColor = MaterialTheme.colorScheme.primary
                val axisColor = MaterialTheme.colorScheme.outline

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 8.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    // baseline
                    drawLine(
                        color = axisColor,
                        start = Offset(0f, h),
                        end = Offset(w, h),
                        strokeWidth = 2f
                    )

                    if (selected.kind == ChartKind.BARS) {
                        val n = values.size
                        val slot = w / n
                        val barW = (slot * 0.7f).coerceAtLeast(2f)
                        values.forEachIndexed { i, v ->
                            val ratio = if (maxV > 0) (v / maxV).toFloat() else 0f
                            val barH = h * ratio
                            val x = i * slot + (slot - barW) / 2f
                            drawRect(
                                color = barColor,
                                topLeft = Offset(x, h - barH),
                                size = androidx.compose.ui.geometry.Size(barW, barH)
                            )
                        }
                    } else {
                        val n = values.size
                        if (n == 1) {
                            drawCircle(color = lineColor, radius = 5f, center = Offset(w / 2f, h / 2f))
                        } else {
                            val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
                            val path = Path()
                            values.forEachIndexed { i, v ->
                                val x = i * (w / (n - 1))
                                val ratio = ((v - minV) / span).toFloat().coerceIn(0f, 1f)
                                val y = h - h * ratio
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 3f)
                            )
                            values.forEachIndexed { i, v ->
                                val x = i * (w / (n - 1))
                                val ratio = ((v - minV) / span).toFloat().coerceIn(0f, 1f)
                                val y = h - h * ratio
                                drawCircle(color = lineColor, radius = 4f, center = Offset(x, y))
                            }
                        }
                    }
                }

                Text(
                    text = "Oldest → Newest",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
