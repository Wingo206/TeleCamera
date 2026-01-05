package com.example.telecamera.ui.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.telecamera.domain.camera.CaptureResult
import com.example.telecamera.ui.components.CameraControls
import com.example.telecamera.ui.components.CaptureButton
import com.example.telecamera.ui.components.ConnectionStatus
import com.example.telecamera.ui.components.FocusIndicator
import com.example.telecamera.ui.components.FocusPoint
import com.example.telecamera.ui.viewmodel.CameraViewModel
import com.example.telecamera.util.PermissionHelper
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    onBackClick: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraState by viewModel.cameraState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val connectionQuality by viewModel.connectionQuality.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val lastCaptureResult by viewModel.lastCaptureResult.collectAsState()

    var showControls by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }
    var showCaptureSuccess by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<FocusPoint?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Handle explicit back navigation - cleanup connection
    val handleBack: () -> Unit = {
        viewModel.cameraManager.unbindCamera()
        viewModel.stopAdvertising()
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
            viewModel.cameraManager.bindCamera(lifecycleOwner, previewView)
            viewModel.startAdvertising()
        }
    }

    LaunchedEffect(Unit) {
        val requiredPermissions = PermissionHelper.getCameraPermissions() +
                PermissionHelper.getNearbyPermissions() +
                PermissionHelper.getStoragePermissions()

        if (PermissionHelper.hasAllPermissions(context, requiredPermissions)) {
            hasPermissions = true
            viewModel.cameraManager.bindCamera(lifecycleOwner, previewView)
            viewModel.startAdvertising()
        } else {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    LaunchedEffect(lastCaptureResult) {
        if (lastCaptureResult is CaptureResult.Success) {
            showCaptureSuccess = true
            delay(2000)
            showCaptureSuccess = false
            viewModel.clearCaptureResult()
        }
    }

    // Only unbind camera on dispose (not connection - that's handled by back navigation)
    DisposableEffect(lifecycleOwner) {
        onDispose {
            // Don't stop advertising here - let ViewModel.onCleared() handle it
            // or explicit back navigation
            viewModel.cameraManager.unbindCamera()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermissions) {
            // Camera Preview
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val point = android.graphics.PointF(offset.x, offset.y)
                            focusPoint = FocusPoint(offset.x, offset.y)
                            viewModel.focusOnPoint(point, size.width, size.height)
                        }
                    }
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                FocusIndicator(focusPoint = focusPoint)
            }

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
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }

            // Camera Controls (collapsible)
            AnimatedVisibility(
                visible = showControls,
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
                        enabled = cameraState.isCameraReady
                    )

                    IconButton(
                        onClick = viewModel::switchCamera,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White
                        )
                    }
                }
            }

            // Capture Success Indicator
            AnimatedVisibility(
                visible = showCaptureSuccess,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38ef7d).copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Photo saved",
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
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
                    text = "Camera & Connection Permissions Required",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "TeleCamera needs camera, location, and Bluetooth permissions to function.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val requiredPermissions = PermissionHelper.getCameraPermissions() +
                                PermissionHelper.getNearbyPermissions() +
                                PermissionHelper.getStoragePermissions()
                        permissionLauncher.launch(requiredPermissions.toTypedArray())
                    }
                ) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}
