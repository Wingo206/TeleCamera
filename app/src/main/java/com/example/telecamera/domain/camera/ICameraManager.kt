package com.example.telecamera.domain.camera

import android.graphics.PointF
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow

interface ICameraManager {
    val cameraState: StateFlow<CameraState>
    val latestPreviewFrame: StateFlow<ByteArray?>
    val captureResult: StateFlow<CaptureResult?>

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    )

    fun unbindCamera()

    fun setZoom(ratio: Float)
    fun setZoomByProgress(progress: Float)
    fun setExposureCompensation(value: Int)
    fun setExposureByProgress(progress: Float)
    fun setAspectRatio(ratio: AspectRatio)
    fun setFlashMode(mode: FlashMode)
    fun switchCamera()

    fun focusOnPoint(point: PointF, previewWidth: Int, previewHeight: Int)

    suspend fun capturePhoto(): CaptureResult

    fun startPreviewFrameCapture(intervalMs: Long = 150)
    fun stopPreviewFrameCapture()
}

sealed class CaptureResult {
    data class Success(val uri: String) : CaptureResult()
    data class Error(val message: String) : CaptureResult()
}

