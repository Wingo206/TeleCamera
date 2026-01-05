package com.example.telecamera.ui.viewmodel

import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.telecamera.data.camera.CameraXManager
import com.example.telecamera.data.connection.MessageSerializer
import com.example.telecamera.data.connection.NearbyConnectionManager
import com.example.telecamera.data.feedback.FeedbackManager
import com.example.telecamera.domain.camera.AspectRatio
import com.example.telecamera.domain.camera.CameraState
import com.example.telecamera.domain.camera.CaptureResult
import com.example.telecamera.domain.camera.FlashMode
import com.example.telecamera.domain.connection.ConnectionQuality
import com.example.telecamera.domain.connection.ConnectionState
import com.example.telecamera.domain.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    val cameraManager: CameraXManager,
    private val connectionManager: NearbyConnectionManager,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    companion object {
        private const val TAG = "TeleCamera.CameraVM"
    }

    val cameraState: StateFlow<CameraState> = cameraManager.cameraState
    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val connectedDevices = connectionManager.connectedDevices
    val connectionQuality: StateFlow<ConnectionQuality> = connectionManager.connectionQuality

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _lastCaptureResult = MutableStateFlow<CaptureResult?>(null)
    val lastCaptureResult: StateFlow<CaptureResult?> = _lastCaptureResult.asStateFlow()

    private var previewStreamJob: Job? = null
    private var pingJob: Job? = null
    private var frameCount = 0

    init {
        observeIncomingMessages()
        observeCameraReady()
    }

    fun startAdvertising() {
        Log.d(TAG, "startAdvertising called")
        connectionManager.startAsCamera()
        startPingLoop()
        // Note: Preview streaming will start when camera is ready (see observeCameraReady)
    }

    fun stopAdvertising() {
        Log.d(TAG, "stopAdvertising called")
        connectionManager.disconnectAll()
        stopPreviewStreaming()
        pingJob?.cancel()
    }

    private fun observeCameraReady() {
        viewModelScope.launch {
            cameraManager.cameraState.collect { state ->
                if (state.isCameraReady) {
                    Log.i(TAG, "Camera is ready, starting preview streaming")
                    startPreviewStreaming()
                }
            }
        }
    }

    private fun startPreviewStreaming() {
        if (previewStreamJob?.isActive == true) {
            Log.d(TAG, "Preview streaming already active")
            return
        }
        
        Log.d(TAG, "Starting preview frame capture")
        cameraManager.startPreviewFrameCapture(150) // ~6-7 fps

        previewStreamJob = viewModelScope.launch {
            Log.d(TAG, "Preview stream job started, collecting frames...")
            cameraManager.latestPreviewFrame.collect { frameBytes ->
                val devices = connectionManager.connectedDevices.value
                if (frameBytes != null && devices.isNotEmpty()) {
                    frameCount++
                    if (frameCount % 30 == 1) { // Log every ~5 seconds
                        Log.d(TAG, "Sending preview frame #$frameCount (${frameBytes.size} bytes) to ${devices.size} devices")
                    }
                    val base64 = MessageSerializer.encodeImageToBase64(frameBytes)
                    val message = Message.PreviewFrame(
                        jpegBase64 = base64,
                        timestamp = System.currentTimeMillis()
                    )
                    connectionManager.sendToAll(message)
                } else if (frameBytes != null && devices.isEmpty()) {
                    if (frameCount % 100 == 0) {
                        Log.v(TAG, "Frame available but no connected devices")
                    }
                    frameCount++
                }
            }
        }
    }

    private fun stopPreviewStreaming() {
        Log.d(TAG, "Stopping preview streaming")
        previewStreamJob?.cancel()
        previewStreamJob = null
        cameraManager.stopPreviewFrameCapture()
        frameCount = 0
    }

    private fun startPingLoop() {
        pingJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                if (connectionManager.connectedDevices.value.isNotEmpty()) {
                    connectionManager.sendPing()
                }
            }
        }
    }

    private fun observeIncomingMessages() {
        viewModelScope.launch {
            connectionManager.incomingMessages.collect { (_, message) ->
                handleMessage(message)
            }
        }

        // Also sync state when devices connect
        viewModelScope.launch {
            connectionManager.connectedDevices.collect { devices ->
                if (devices.isNotEmpty()) {
                    Log.i(TAG, "Device connected, syncing state and ensuring preview streaming")
                    syncStateToRemotes()
                }
            }
        }
    }

    private suspend fun handleMessage(message: Message) {
        when (message) {
            is Message.CaptureCommand -> {
                Log.d(TAG, "Received CaptureCommand from remote")
                capturePhoto()
            }
            is Message.ControlUpdate -> {
                Log.d(TAG, "Received ControlUpdate: zoom=${message.zoomRatio}, exposure=${message.exposureCompensation}")
                message.zoomRatio?.let { cameraManager.setZoom(it) }
                message.exposureCompensation?.let { cameraManager.setExposureCompensation(it) }
                message.aspectRatio?.let { cameraManager.setAspectRatio(it) }
                message.flashMode?.let { cameraManager.setFlashMode(it) }
                syncStateToRemotes()
            }
            is Message.FocusPoint -> {
                Log.d(TAG, "Received FocusPoint: (${message.x}, ${message.y})")
                val point = PointF(message.x, message.y)
                cameraManager.focusOnPoint(point, 1000, 1000)
                feedbackManager.playFocusFeedback()
            }
            else -> { /* Ignore other messages */ }
        }
    }

    private suspend fun syncStateToRemotes() {
        val stateMessage = Message.StateSync(state = cameraManager.cameraState.value)
        connectionManager.sendToAll(stateMessage)
    }

    fun setZoom(progress: Float) {
        cameraManager.setZoomByProgress(progress)
        viewModelScope.launch {
            syncStateToRemotes()
        }
    }

    fun setExposure(progress: Float) {
        cameraManager.setExposureByProgress(progress)
        viewModelScope.launch {
            syncStateToRemotes()
        }
    }

    fun setAspectRatio(ratio: AspectRatio) {
        cameraManager.setAspectRatio(ratio)
        viewModelScope.launch {
            syncStateToRemotes()
        }
    }

    fun setFlashMode(mode: FlashMode) {
        cameraManager.setFlashMode(mode)
        viewModelScope.launch {
            syncStateToRemotes()
        }
    }

    fun focusOnPoint(point: PointF, previewWidth: Int, previewHeight: Int) {
        cameraManager.focusOnPoint(point, previewWidth, previewHeight)
        feedbackManager.playFocusFeedback()
    }

    fun switchCamera() {
        cameraManager.switchCamera()
        viewModelScope.launch {
            syncStateToRemotes()
        }
    }

    fun capturePhoto() {
        if (_isCapturing.value) return

        viewModelScope.launch {
            _isCapturing.value = true
            feedbackManager.playShutterFeedback()

            val result = cameraManager.capturePhoto()
            _lastCaptureResult.value = result

            Log.d(TAG, "Photo captured: ${if (result is CaptureResult.Success) "success" else "failed"}")

            // Send confirmation to remotes
            val confirmation = Message.CaptureConfirmation(
                success = result is CaptureResult.Success,
                photoUri = (result as? CaptureResult.Success)?.uri,
                errorMessage = (result as? CaptureResult.Error)?.message
            )
            connectionManager.sendToAll(confirmation)

            // Provide feedback on remote as well
            if (result is CaptureResult.Success) {
                feedbackManager.vibrateConfirmation()
            }

            _isCapturing.value = false
        }
    }

    fun clearCaptureResult() {
        _lastCaptureResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAdvertising()
    }
}
