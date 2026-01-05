package com.example.telecamera.ui.viewmodel

import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.telecamera.data.connection.MessageSerializer
import com.example.telecamera.data.connection.NearbyConnectionManager
import com.example.telecamera.data.feedback.FeedbackManager
import com.example.telecamera.domain.camera.AspectRatio
import com.example.telecamera.domain.camera.CameraState
import com.example.telecamera.domain.camera.FlashMode
import com.example.telecamera.domain.connection.ConnectionQuality
import com.example.telecamera.domain.connection.ConnectionState
import com.example.telecamera.domain.connection.DiscoveredDevice
import com.example.telecamera.domain.connection.PairingEvent
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
class RemoteViewModel @Inject constructor(
    private val connectionManager: NearbyConnectionManager,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    companion object {
        private const val TAG = "TeleCamera.RemoteVM"
    }

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState
    val connectedDevices = connectionManager.connectedDevices
    val connectionQuality: StateFlow<ConnectionQuality> = connectionManager.connectionQuality

    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _previewFrame = MutableStateFlow<ByteArray?>(null)
    val previewFrame: StateFlow<ByteArray?> = _previewFrame.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _captureConfirmation = MutableStateFlow<Message.CaptureConfirmation?>(null)
    val captureConfirmation: StateFlow<Message.CaptureConfirmation?> = _captureConfirmation.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private var pingJob: Job? = null
    private var frameCount = 0

    init {
        Log.d(TAG, "RemoteViewModel initialized")
        observeIncomingMessages()
        observePairingEvents()
    }

    fun startDiscovery() {
        Log.d(TAG, "startDiscovery called")
        connectionManager.startAsRemote()
        startPingLoop()
    }

    fun stopDiscovery() {
        Log.d(TAG, "stopDiscovery called")
        connectionManager.disconnectAll()
        pingJob?.cancel()
        _previewFrame.value = null
        _discoveredDevices.value = emptyList()
        frameCount = 0
    }

    fun connectToDevice(device: DiscoveredDevice) {
        Log.d(TAG, "connectToDevice: ${device.endpointId} (${device.name})")
        connectionManager.connectToDevice(device)
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

    private fun observePairingEvents() {
        viewModelScope.launch {
            connectionManager.connectionState.collect { state ->
                Log.d(TAG, "Connection state changed: $state")
                // Clear discovered devices when we connect
                if (state == ConnectionState.CONNECTED) {
                    _discoveredDevices.value = emptyList()
                    Log.i(TAG, "Connected! Waiting for preview frames...")
                }
            }
        }
    }

    private fun observeIncomingMessages() {
        viewModelScope.launch {
            connectionManager.incomingMessages.collect { (endpointId, message) ->
                handleMessage(message)
            }
        }
    }

    private fun handleMessage(message: Message) {
        when (message) {
            is Message.PreviewFrame -> {
                frameCount++
                if (frameCount % 30 == 1) {
                    Log.d(TAG, "Received PreviewFrame #$frameCount (${message.jpegBase64.length} chars base64)")
                }
                try {
                    val bytes = MessageSerializer.decodeBase64ToImage(message.jpegBase64)
                    _previewFrame.value = bytes
                    if (frameCount % 30 == 1) {
                        Log.d(TAG, "Decoded frame to ${bytes.size} bytes")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode preview frame", e)
                }
            }
            is Message.StateSync -> {
                Log.d(TAG, "Received StateSync: zoom=${message.state.zoomRatio}, cameraReady=${message.state.isCameraReady}")
                _cameraState.value = message.state
            }
            is Message.CaptureConfirmation -> {
                Log.i(TAG, "Received CaptureConfirmation: success=${message.success}, uri=${message.photoUri}")
                _isCapturing.value = false
                _captureConfirmation.value = message
                if (message.success) {
                    feedbackManager.vibrateConfirmation()
                }
            }
            is Message.QualityUpdate -> {
                Log.v(TAG, "Received QualityUpdate: ${message.latencyMs}ms")
            }
            else -> {
                Log.d(TAG, "Received other message: ${message::class.simpleName}")
            }
        }
    }

    fun setZoom(progress: Float) {
        val state = _cameraState.value
        val ratio = state.minZoomRatio + (state.maxZoomRatio - state.minZoomRatio) * progress
        _cameraState.value = state.copy(zoomRatio = ratio)
        sendControlUpdate(zoomRatio = ratio)
    }

    fun setExposure(progress: Float) {
        val state = _cameraState.value
        val value = (state.minExposureCompensation +
                (state.maxExposureCompensation - state.minExposureCompensation) * progress).toInt()
        _cameraState.value = state.copy(exposureCompensation = value)
        sendControlUpdate(exposureCompensation = value)
    }

    fun setAspectRatio(ratio: AspectRatio) {
        _cameraState.value = _cameraState.value.copy(aspectRatio = ratio)
        sendControlUpdate(aspectRatio = ratio)
    }

    fun setFlashMode(mode: FlashMode) {
        _cameraState.value = _cameraState.value.copy(flashMode = mode)
        sendControlUpdate(flashMode = mode)
    }

    private fun sendControlUpdate(
        zoomRatio: Float? = null,
        exposureCompensation: Int? = null,
        aspectRatio: AspectRatio? = null,
        flashMode: FlashMode? = null
    ) {
        viewModelScope.launch {
            val message = Message.ControlUpdate(
                zoomRatio = zoomRatio,
                exposureCompensation = exposureCompensation,
                aspectRatio = aspectRatio,
                flashMode = flashMode
            )
            connectionManager.sendToAll(message)
        }
    }

    fun focusOnPoint(point: PointF, previewWidth: Int, previewHeight: Int) {
        // Send normalized coordinates to camera
        val normalizedX = point.x / previewWidth
        val normalizedY = point.y / previewHeight

        Log.d(TAG, "Sending FocusPoint: ($normalizedX, $normalizedY)")

        viewModelScope.launch {
            val message = Message.FocusPoint(x = normalizedX, y = normalizedY)
            connectionManager.sendToAll(message)
        }

        feedbackManager.playFocusFeedback()
    }

    fun capturePhoto() {
        if (_isCapturing.value) return

        Log.i(TAG, "Triggering remote capture")
        _isCapturing.value = true
        feedbackManager.playShutterFeedback()

        viewModelScope.launch {
            val deviceId = "remote_${System.currentTimeMillis()}"
            val message = Message.CaptureCommand(senderId = deviceId)
            connectionManager.sendToAll(message)

            // Timeout after 10 seconds if no confirmation received
            delay(10000)
            if (_isCapturing.value) {
                Log.w(TAG, "Capture confirmation timeout")
                _isCapturing.value = false
            }
        }
    }

    fun clearCaptureConfirmation() {
        _captureConfirmation.value = null
    }

    fun refreshPreview() {
        Log.d(TAG, "Refresh preview requested")
        // Request a fresh preview frame by triggering the ping
        // The camera will continue sending frames
        connectionManager.sendPing()
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }
}
