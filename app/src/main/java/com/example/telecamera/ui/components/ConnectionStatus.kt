package com.example.telecamera.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material.icons.filled.SignalCellularAlt2Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.telecamera.domain.connection.ConnectionQuality
import com.example.telecamera.domain.connection.ConnectionState
import com.example.telecamera.domain.connection.QualityLevel

@Composable
fun ConnectionStatus(
    connectionState: ConnectionState,
    connectedDeviceCount: Int,
    connectionQuality: ConnectionQuality,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> Color(0xFF38ef7d)
            ConnectionState.CONNECTING -> Color(0xFFFFD700)
            ConnectionState.ADVERTISING, ConnectionState.DISCOVERING -> Color(0xFF667eea)
            ConnectionState.ERROR -> Color(0xFFFF6B6B)
            ConnectionState.IDLE -> Color.White.copy(alpha = 0.5f)
        },
        animationSpec = tween(300),
        label = "statusColor"
    )

    val pulseAlpha by animateFloatAsState(
        targetValue = when (connectionState) {
            ConnectionState.ADVERTISING, ConnectionState.DISCOVERING, ConnectionState.CONNECTING -> 0.6f
            else -> 1f
        },
        animationSpec = tween(500),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(statusColor)
        )

        // Status text
        Text(
            text = when (connectionState) {
                ConnectionState.IDLE -> "Idle"
                ConnectionState.ADVERTISING -> "Waiting..."
                ConnectionState.DISCOVERING -> "Searching..."
                ConnectionState.CONNECTING -> "Connecting..."
                ConnectionState.CONNECTED -> if (connectedDeviceCount > 1) {
                    "$connectedDeviceCount connected"
                } else {
                    "Connected"
                }
                ConnectionState.ERROR -> "Error"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )

        // Quality indicator (only when connected)
        if (connectionState == ConnectionState.CONNECTED) {
            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = when (connectionQuality.qualityLevel) {
                    QualityLevel.EXCELLENT -> Icons.Default.SignalCellular4Bar
                    QualityLevel.GOOD -> Icons.Default.SignalCellularAlt
                    QualityLevel.FAIR -> Icons.Default.SignalCellularAlt2Bar
                    QualityLevel.POOR -> Icons.Default.SignalCellularAlt1Bar
                },
                contentDescription = "Signal quality",
                modifier = Modifier.size(16.dp),
                tint = when (connectionQuality.qualityLevel) {
                    QualityLevel.EXCELLENT -> Color(0xFF38ef7d)
                    QualityLevel.GOOD -> Color(0xFF98D8AA)
                    QualityLevel.FAIR -> Color(0xFFFFD700)
                    QualityLevel.POOR -> Color(0xFFFF6B6B)
                }
            )

            Text(
                text = "${connectionQuality.latencyMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

