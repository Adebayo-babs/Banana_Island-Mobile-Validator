package com.example.cardapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun QRScannerOverlay(isDetected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")

    // Animated scanning line
    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    // Color animation when QR is detected
    val targetColor = if (isDetected) Color(0xFF4CAF50) else Color.White
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "color"
    )

    // Capture flash animation - ADDED HERE
    val captureFlash by animateFloatAsState(
        targetValue = if (isDetected) 1f else 0f,
        animationSpec = tween(400),
        label = "flash"
    )

    // Corner expansion animation - ADDED HERE
    val cornerExpansion by animateFloatAsState(
        targetValue = if (isDetected) 1.1f else 1f,
        animationSpec = tween(300),
        label = "expansion"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Scanner box dimensions with expansion - MODIFIED HERE
        val baseBoxSize = minOf(canvasWidth, canvasHeight) * 0.65f
        val boxSize = baseBoxSize * cornerExpansion
        val left = (canvasWidth - boxSize) / 2
        val top = (canvasHeight - boxSize) / 2

        // White flash effect when captured - ADDED HERE
        if (captureFlash > 0f) {
            drawRect(
                color = Color.White.copy(alpha = captureFlash * 0.6f),
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize)
            )
        }

        // Corner length and thickness
        val cornerLength = boxSize * 0.15f
        val cornerThickness = 8f

        // Draw four corners
        // Top-left
        drawLine(
            color = animatedColor,
            start = Offset(left, top),
            end = Offset(left + cornerLength, top),
            strokeWidth = cornerThickness
        )
        drawLine(
            color = animatedColor,
            start = Offset(left, top),
            end = Offset(left, top + cornerLength),
            strokeWidth = cornerThickness
        )

        // Top-right
        drawLine(
            color = animatedColor,
            start = Offset(left + boxSize, top),
            end = Offset(left + boxSize - cornerLength, top),
            strokeWidth = cornerThickness
        )
        drawLine(
            color = animatedColor,
            start = Offset(left + boxSize, top),
            end = Offset(left + boxSize, top + cornerLength),
            strokeWidth = cornerThickness
        )

        // Bottom-left
        drawLine(
            color = animatedColor,
            start = Offset(left, top + boxSize),
            end = Offset(left + cornerLength, top + boxSize),
            strokeWidth = cornerThickness
        )
        drawLine(
            color = animatedColor,
            start = Offset(left, top + boxSize),
            end = Offset(left, top + boxSize - cornerLength),
            strokeWidth = cornerThickness
        )

        // Bottom-right
        drawLine(
            color = animatedColor,
            start = Offset(left + boxSize, top + boxSize),
            end = Offset(left + boxSize - cornerLength, top + boxSize),
            strokeWidth = cornerThickness
        )
        drawLine(
            color = animatedColor,
            start = Offset(left + boxSize, top + boxSize),
            end = Offset(left + boxSize, top + boxSize - cornerLength),
            strokeWidth = cornerThickness
        )

        // Animated scanning line (only when not detected)
        if (!isDetected) {
            val lineY = top + (boxSize * scanLinePosition)
            drawLine(
                color = animatedColor.copy(alpha = 0.8f),
                start = Offset(left, lineY),
                end = Offset(left + boxSize, lineY),
                strokeWidth = 3f
            )
        } else {
            // Draw full box border when detected
            drawRoundRect(
                color = animatedColor,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 4f)
            )
        }
    }
}