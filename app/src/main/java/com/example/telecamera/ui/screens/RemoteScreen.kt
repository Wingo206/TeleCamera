package com.example.telecamera.ui.screens

import android.graphics.PointF
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.telecamera.domain.connection.ConnectionState
import com.example.telecamera.ui.components.CameraControls
import com.example.telecamera.ui.components.CaptureButton
import com.example.telecamera.ui.components.ConnectionStatus
import com.example.telecamera.ui.components.RemotePreview
import com.example.telecamera.ui.viewmodel.RemoteViewModel
import com.example.telecamera.util.PermissionHelper
import kotlinx.coroutines.delay

@Composable
fun RemoteScreen(
    onBackClick: () -> Unit,
    viewModel: RemoteViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val connectionQuality by viewModel.connectionQuality.collectAsState()
    val cameraState by viewModel.cameraState.collectAsState()
    val previewFrame by viewModel.previewFrame.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val captureConfirmation by viewModel.captureConfirmation.collectAsState()

    var showControls by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }
    var showCaptureResult by remember { mutableStateOf(false) }
    var captureSuccess by remember { mutableStateOf(false) }

    val isConnected = connectionState == ConnectionState.CONNECTED

    // Handle explicit back navigation - cleanup connection
    val handleBack: () -> Unit = {
        viewModel.stopDiscovery()
        onBackClick()
    }

    // Handle system back button
    BackHandler {
        handleBack()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            viewModel.startDiscovery()
        }
    }

    LaunchedEffect(Unit) {
        val requiredPermissions = PermissionHelper.getNearbyPermissions()

        if (PermissionHelper.hasAllPermissions(context, requiredPermissions)) {
            hasPermissions = true
            viewModel.startDiscovery()
        } else {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    LaunchedEffect(captureConfirmation) {
        captureConfirmation?.let { confirmation ->
            captureSuccess = confirmation.success
            showCaptureResult = true
            delay(2000)
            showCaptureResult = false
            viewModel.clearCaptureConfirmation()
        }
    }

    // Don't stop discovery on dispose - let ViewModel.onCleared() or explicit back handle it
    // This prevents disconnection on configuration changes

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        if (hasPermissions) {
            // Remote Preview
            RemotePreview(
                previewBytes = previewFrame,
                isConnected = isConnected,
                onFocusTap = { point, width, height ->
                    viewModel.focusOnPoint(point, width, height)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = handleBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                ConnectionStatus(
                    connectionState = connectionState,
                    connectedDeviceCount = connectedDevices.size,
                    connectionQuality = connectionQuality
                )

                IconButton(
                    onClick = { showControls = !showControls },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    enabled = isConnected
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = if (isConnected) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // Camera Controls (collapsible) - only show when connected
            AnimatedVisibility(
                visible = showControls && isConnected,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .padding(horizontal = 16.dp)
            ) {
                CameraControls(
                    cameraState = cameraState,
                    onZoomChange = viewModel::setZoom,
                    onExposureChange = viewModel::setExposure,
                    onAspectRatioChange = viewModel::setAspectRatio,
                    onFlashModeChange = viewModel::setFlashMode,
                    showFlashControl = true
                )
            }

            // Bottom Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Refresh button
                if (isConnected) {
                    IconButton(
                        onClick = viewModel::refreshPreview,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Preview",
                            tint = Color.White
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Empty space for balance
                    Box(modifier = Modifier.size(48.dp))

                    CaptureButton(
                        onClick = viewModel::capturePhoto,
                        isCapturing = isCapturing,
                        enabled = isConnected
                    )

                    // Empty space for balance
                    Box(modifier = Modifier.size(48.dp))
                }
            }

            // Capture Result Indicator
            AnimatedVisibility(
                visible = showCaptureResult,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            if (captureSuccess) {
                                Color(0xFF38ef7d).copy(alpha = 0.9f)
                            } else {
                                Color(0xFFFF6B6B).copy(alpha = 0.9f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (captureSuccess) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Error
                        },
                        contentDescription = if (captureSuccess) "Photo saved" else "Capture failed",
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            // Searching overlay when not connected
            if (!isConnected && connectionState != ConnectionState.IDLE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (connectionState) {
                                ConnectionState.DISCOVERING -> "Searching for cameras..."
                                ConnectionState.CONNECTING -> "Connecting..."
                                ConnectionState.ERROR -> "Connection error"
                                else -> "Waiting..."
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (connectionState == ConnectionState.DISCOVERING) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Make sure the other phone has Camera mode open",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        } else {
            // Permission Request UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Connection Permissions Required",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "TeleCamera needs location and Bluetooth permissions to discover and connect to other devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val requiredPermissions = PermissionHelper.getNearbyPermissions()
                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                    }
                ) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}
