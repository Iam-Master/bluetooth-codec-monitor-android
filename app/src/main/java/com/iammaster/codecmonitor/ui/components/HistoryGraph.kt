package com.iammaster.codecmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.iammaster.codecmonitor.data.local.HistoryEntity

@Composable
fun HistoryGraph(points: List<HistoryEntity>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val transparentColor = primaryColor.copy(alpha = 0.0f)
    val semiTransparentColor = primaryColor.copy(alpha = 0.2f)
    val bitrates = points.map { it.bitrateKbps ?: 0 }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (bitrates.size < 2) return@Canvas
        val width = size.width
        val height = size.height
        val maxBitrate = (bitrates.maxOrNull() ?: 990).coerceAtLeast(330)
        val xStep = width / (bitrates.size - 1)

        val path = Path()
        val fillPath = Path()

        bitrates.forEachIndexed { i, bitrate ->
            val x = i * xStep
            val y = height - ((bitrate.toFloat() / maxBitrate) * height * 0.85f)
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(width, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(listOf(semiTransparentColor, transparentColor), startY = 0f, endY = height)
        )
        drawPath(path = path, color = primaryColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}
