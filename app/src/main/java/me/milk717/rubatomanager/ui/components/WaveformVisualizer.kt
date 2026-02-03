package me.milk717.rubatomanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun WaveformVisualizer(
    audioLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 20,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    // Store recent audio levels for visualization
    var levels by remember { mutableStateOf(List(barCount) { 0f }) }

    LaunchedEffect(audioLevel) {
        levels = (levels.drop(1) + audioLevel)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val barWidth = size.width / (barCount * 2f)
        val maxHeight = size.height * 0.8f
        val centerY = size.height / 2

        levels.forEachIndexed { index, level ->
            val x = barWidth + (index * barWidth * 2)
            val barHeight = maxHeight * level.coerceIn(0.1f, 1f)

            drawLine(
                color = primaryColor.copy(alpha = 0.5f + level * 0.5f),
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth * 0.7f,
                cap = StrokeCap.Round
            )
        }
    }
}
