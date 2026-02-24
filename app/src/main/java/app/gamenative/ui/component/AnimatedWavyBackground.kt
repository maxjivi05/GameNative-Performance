package app.gamenative.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin

@Composable
fun AnimatedWavyBackground(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxSize().graphicsLayer(alpha = 0.6f)) {
        val width = size.width
        val height = size.height
        
        // Draw 3 layers of waves with different properties
        drawWave(width, height, phase, color, 0.4f, 50f, 1f)
        drawWave(width, height, phase * 0.7f, color.copy(alpha = 0.1f), 0.6f, 70f, 1.2f)
        drawWave(width, height, phase * 1.3f, color.copy(alpha = 0.05f), 0.3f, 40f, 0.8f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWave(
    width: Float,
    height: Float,
    phase: Float,
    color: Color,
    yOffsetPercent: Float,
    amplitude: Float,
    frequency: Float
) {
    val path = Path()
    val midY = height * yOffsetPercent
    
    path.moveTo(0f, midY)
    
    for (x in 0..width.toInt() step 5) {
        val relativeX = x / width
        val sineValue = sin(relativeX * 2 * Math.PI * frequency + phase).toFloat()
        val y = midY + sineValue * amplitude
        path.lineTo(x.toFloat(), y)
    }
    
    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()
    
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(color, Color.Transparent),
            startY = midY - amplitude,
            endY = height
        )
    )
    
    // Draw the line on top
    val linePath = Path()
    linePath.moveTo(0f, midY + sin(phase).toFloat() * amplitude)
    for (x in 0..width.toInt() step 5) {
        val relativeX = x / width
        val sineValue = sin(relativeX * 2 * Math.PI * frequency + phase).toFloat()
        val y = midY + sineValue * amplitude
        linePath.lineTo(x.toFloat(), y)
    }
    
    drawPath(
        path = linePath,
        color = color.copy(alpha = 0.3f),
        style = Stroke(width = 2f)
    )
}
