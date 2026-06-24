package com.iammaster.codecmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

@Composable
fun BitrateGraph(currentBitrate: Int, modifier: Modifier = Modifier) {
    val historySize = 30
    val bitrateHistory = remember { mutableStateListOf<Int>().apply { 
        for(i in 0 until historySize) add(0) 
    } }

    LaunchedEffect(currentBitrate) {
        while(true) {
            bitrateHistory.removeAt(0)
            bitrateHistory.add(currentBitrate)
            delay(1000)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val transparentColor = primaryColor.copy(alpha = 0.0f)
    val semiTransparentColor = primaryColor.copy(alpha = 0.2f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        val maxBitrate = 990 // max LDAC
        
        val xStep = width / (historySize - 1)
        
        val path = Path()
        val fillPath = Path()
        
        for (i in 0 until historySize) {
            val bitrate = bitrateHistory[i]
            val x = i * xStep
            // Scale y so maxBitrate is at top (0) and 0 is at bottom (height)
            // Keep a little padding
            val y = height - ((bitrate.toFloat() / maxBitrate) * height * 0.8f)
            
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

        // Draw gradient fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(semiTransparentColor, transparentColor),
                startY = 0f,
                endY = height
            )
        )

        // Draw line
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
    }
}
