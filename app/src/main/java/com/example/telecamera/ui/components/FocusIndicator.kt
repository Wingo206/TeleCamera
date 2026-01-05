package com.example.telecamera.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

data class FocusPoint(
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun FocusIndicator(
    focusPoint: FocusPoint?,
    modifier: Modifier = Modifier
) {
    if (focusPoint == null) return

    val scale = remember { Animatable(1.5f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(focusPoint.timestamp) {
        scale.snapTo(1.5f)
        alpha.snapTo(1f)

        // Animate scale down
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(200)
        )

        // Wait and then fade out
        kotlinx.coroutines.delay(800)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(300)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (focusPoint.x - 30.dp.toPx()).toInt(),
                        y = (focusPoint.y - 30.dp.toPx()).toInt()
                    )
                }
                .size(60.dp)
        ) {
            val color = Color(0xFFFFD700).copy(alpha = alpha.value)
            val strokeWidth = 2.dp.toPx()
            val cornerLength = size.width * 0.25f
            val scaledSize = size.width * scale.value
            val offset = (scaledSize - size.width) / 2

            // Draw corner brackets
            // Top-left
            drawLine(
                color = color,
                start = Offset(-offset, -offset),
                end = Offset(-offset + cornerLength, -offset),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(-offset, -offset),
                end = Offset(-offset, -offset + cornerLength),
                strokeWidth = strokeWidth
            )

            // Top-right
            drawLine(
                color = color,
                start = Offset(size.width + offset - cornerLength, -offset),
                end = Offset(size.width + offset, -offset),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(size.width + offset, -offset),
                end = Offset(size.width + offset, -offset + cornerLength),
                strokeWidth = strokeWidth
            )

            // Bottom-left
            drawLine(
                color = color,
                start = Offset(-offset, size.height + offset - cornerLength),
                end = Offset(-offset, size.height + offset),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(-offset, size.height + offset),
                end = Offset(-offset + cornerLength, size.height + offset),
                strokeWidth = strokeWidth
            )

            // Bottom-right
            drawLine(
                color = color,
                start = Offset(size.width + offset - cornerLength, size.height + offset),
                end = Offset(size.width + offset, size.height + offset),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = color,
                start = Offset(size.width + offset, size.height + offset - cornerLength),
                end = Offset(size.width + offset, size.height + offset),
                strokeWidth = strokeWidth
            )
        }
    }
}

