package com.example.telecamera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.telecamera.domain.camera.AspectRatio
import com.example.telecamera.domain.camera.CameraState
import com.example.telecamera.domain.camera.FlashMode

@Composable
fun CameraControls(
    cameraState: CameraState,
    onZoomChange: (Float) -> Unit,
    onExposureChange: (Float) -> Unit,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onFlashModeChange: (FlashMode) -> Unit,
    modifier: Modifier = Modifier,
    showFlashControl: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        // Zoom Control
        ControlRow(
            icon = Icons.Default.ZoomIn,
            label = "Zoom",
            value = String.format("%.1fx", cameraState.zoomRatio)
        ) {
            Slider(
                value = cameraState.zoomProgress,
                onValueChange = onZoomChange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF667eea),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Exposure Control
        ControlRow(
            icon = Icons.Default.BrightnessHigh,
            label = "Exposure",
            value = when {
                cameraState.exposureCompensation > 0 -> "+${cameraState.exposureCompensation}"
                else -> "${cameraState.exposureCompensation}"
            }
        ) {
            Slider(
                value = cameraState.exposureProgress,
                onValueChange = onExposureChange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF11998e),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Aspect Ratio Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AspectRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = cameraState.aspectRatio == ratio,
                    onClick = { onAspectRatioChange(ratio) },
                    label = { Text(ratio.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF667eea),
                        selectedLabelColor = Color.White,
                        containerColor = Color.Transparent,
                        labelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
            }
        }

        // Flash Control
        AnimatedVisibility(
            visible = showFlashControl,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Flash:",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))

                FlashMode.entries.forEach { mode ->
                    IconButton(
                        onClick = { onFlashModeChange(mode) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                FlashMode.AUTO -> Icons.Default.FlashAuto
                                FlashMode.ON -> Icons.Default.FlashOn
                                FlashMode.OFF -> Icons.Default.FlashOff
                            },
                            contentDescription = mode.name,
                            tint = if (cameraState.flashMode == mode) {
                                Color(0xFFFFD700)
                            } else {
                                Color.White.copy(alpha = 0.5f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp)
        )
        content()
    }
}

