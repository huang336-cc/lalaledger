package com.lightledger.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 柔和配色的分类占比饼图（环形）。
 * - 只展示金额最高的前 [maxSlices] 类，其余合并为"其他"
 * - 中心显示总额
 */
@Composable
fun CategoryPieChart(
    slices: List<PieSlice>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    maxSlices: Int = 6,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 30.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            if (slices.isEmpty()) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                return@Canvas
            }

            // 取前 maxSlices，剩余合并
            val shown = slices.take(maxSlices)
            val rest = slices.drop(maxSlices)
            val restRatio = rest.sumOf { it.ratio.toDouble() }.toFloat()
            val total = shown.map { it.ratio } + if (restRatio > 0f) listOf(restRatio) else emptyList()
            val colors = shown.map { it.color } + if (restRatio > 0f) listOf(trackColor) else emptyList()

            var startAngle = -90f
            total.forEachIndexed { index, ratio ->
                val sweep = ratio * 360f
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweep - 1.5f, // 留缝隙更轻盈
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = centerValue,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

data class PieSlice(
    val label: String,
    val ratio: Float,
    val color: Color,
)
