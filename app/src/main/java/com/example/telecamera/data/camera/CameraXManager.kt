package com.example.telecamera.data.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.AspectRatio as CameraXAspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.telecamera.domain.camera.AspectRatio
import com.example.telecamera.domain.camera.CameraLens
import com.example.telecamera.domain.camera.CameraState
import com.example.telecamera.domain.camera.CaptureResult
import com.example.telecamera.domain.camera.FlashMode
import com.example.telecamera.domain.camera.ICameraManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class CameraXManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ICameraManager {

    companion object {
        private const val TAG = "TeleCamera.CameraX"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executor = Executors.newSingleThreadExecutor()

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null

    private val _cameraState = MutableStateFlow(CameraState())
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _latestPreviewFrame = MutableStateFlow<ByteArray?>(null)
    override val latestPreviewFrame: StateFlow<ByteArray?> = _latestPreviewFrame.asStateFlow()

    private val _captureResult = MutableStateFlow<CaptureResult?>(null)
    override val captureResult: StateFlow<CaptureResult?> = _captureResult.asStateFlow()

    private var previewCaptureJob: Job? = null
    private var pendingPreviewCaptureIntervalMs: Long? = null
    private var frameCount = 0

    override fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        Log.d(TAG, "bindCamera called")
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            Log.d(TAG, "Camera provider ready")
            cameraProvider = cameraProviderFuture.get()
            rebindCamera()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun rebindCamera() {
        val provider = cameraProvider ?: run {
            Log.e(TAG, "rebindCamera: cameraProvider is null")
            return
        }
        val owner = lifecycleOwner ?: run {
            Log.e(TAG, "rebindCamera: lifecycleOwner is null")
            return
        }
        val view = previewView ?: run {
            Log.e(TAG, "rebindCamera: previewView is null")
            return
        }
        val state = _cameraState.value

        Log.d(TAG, "rebindCamera: unbinding all use cases")
        provider.unbindAll()

        val cameraSelector = when (state.cameraLens) {
            CameraLens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraLens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }

        val aspectRatioInt = when (state.aspectRatio) {
            AspectRatio.RATIO_4_3 -> CameraXAspectRatio.RATIO_4_3
            AspectRatio.RATIO_16_9 -> CameraXAspectRatio.RATIO_16_9
            AspectRatio.RATIO_1_1 -> CameraXAspectRatio.RATIO_4_3 // CameraX doesn't support 1:1 directly
        }

        preview = Preview.Builder()
            .setTargetAspectRatio(aspectRatioInt)
            .build()
            .also {
                it.setSurfaceProvider(view.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(aspectRatioInt)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(
                when (state.flashMode) {
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                }
            )
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(aspectRatioInt)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        Log.d(TAG, "rebindCamera: binding use cases to lifecycle")

        try {
            camera = provider.bindToLifecycle(
                owner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )

            camera?.let { cam ->
                val zoomState = cam.cameraInfo.zoomState.value
                val exposureState = cam.cameraInfo.exposureState

                _cameraState.value = state.copy(
                    minZoomRatio = zoomState?.minZoomRatio ?: 1f,
                    maxZoomRatio = zoomState?.maxZoomRatio ?: 1f,
                    zoomRatio = zoomState?.zoomRatio ?: 1f,
                    minExposureCompensation = exposureState.exposureCompensationRange.lower,
                    maxExposureCompensation = exposureState.exposureCompensationRange.upper,
                    exposureCompensation = exposureState.exposureCompensationIndex,
                    isCameraReady = true
                )

                Log.i(TAG, "Camera bound successfully. Zoom: ${zoomState?.minZoomRatio}-${zoomState?.maxZoomRatio}x")

                // If preview capture was requested before camera was ready, start it now
                pendingPreviewCaptureIntervalMs?.let { interval ->
                    Log.d(TAG, "Starting pending preview capture with interval ${interval}ms")
                    startPreviewFrameCaptureInternal(interval)
                    pendingPreviewCaptureIntervalMs = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
            _cameraState.value = state.copy(isCameraReady = false)
        }
    }

    override fun unbindCamera() {
        Log.d(TAG, "unbindCamera called")
        stopPreviewFrameCapture()
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
        imageAnalysis = null
        preview = null
    }

    override fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
        _cameraState.value = _cameraState.value.copy(zoomRatio = ratio)
    }

    override fun setZoomByProgress(progress: Float) {
        val state = _cameraState.value
        val ratio = state.minZoomRatio + (state.maxZoomRatio - state.minZoomRatio) * progress
        setZoom(ratio)
    }

    override fun setExposureCompensation(value: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(value)
        _cameraState.value = _cameraState.value.copy(exposureCompensation = value)
    }

    override fun setExposureByProgress(progress: Float) {
        val state = _cameraState.value
        val value = (state.minExposureCompensation +
                (state.maxExposureCompensation - state.minExposureCompensation) * progress).toInt()
        setExposureCompensation(value)
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        _cameraState.value = _cameraState.value.copy(aspectRatio = ratio)
        rebindCamera()
    }

    override fun setFlashMode(mode: FlashMode) {
        _cameraState.value = _cameraState.value.copy(flashMode = mode)
        imageCapture?.flashMode = when (mode) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }

    override fun switchCamera() {
        val newLens = when (_cameraState.value.cameraLens) {
            CameraLens.BACK -> CameraLens.FRONT
            CameraLens.FRONT -> CameraLens.BACK
        }
        _cameraState.value = _cameraState.value.copy(cameraLens = newLens)
        rebindCamera()
    }

    override fun focusOnPoint(point: PointF, previewWidth: Int, previewHeight: Int) {
        val cam = camera ?: return

        val factory = SurfaceOrientedMeteringPointFactory(
            previewWidth.toFloat(),
            previewHeight.toFloat()
        )
        val meteringPoint = factory.createPoint(point.x, point.y)
        val action = FocusMeteringAction.Builder(meteringPoint)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        cam.cameraControl.startFocusAndMetering(action)
    }

    override suspend fun capturePhoto(): CaptureResult = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture ?: run {
            Log.e(TAG, "capturePhoto: imageCapture is null")
            continuation.resume(CaptureResult.Error("Camera not ready"))
            return@suspendCancellableCoroutine
        }

        val name = "TeleCamera_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TeleCamera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        Log.d(TAG, "Taking photo...")
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "Photo saved: ${output.savedUri}")
                    val result = CaptureResult.Success(output.savedUri?.toString() ?: "")
                    _captureResult.value = result
                    continuation.resume(result)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    val result = CaptureResult.Error(exception.message ?: "Capture failed")
                    _captureResult.value = result
                    continuation.resume(result)
                }
            }
        )
    }

    override fun startPreviewFrameCapture(intervalMs: Long) {
        Log.d(TAG, "startPreviewFrameCapture requested with interval ${intervalMs}ms")
        
        val analysis = imageAnalysis
        if (analysis == null) {
            Log.w(TAG, "imageAnalysis is null, deferring preview capture until camera is bound")
            pendingPreviewCaptureIntervalMs = intervalMs
            return
        }

        startPreviewFrameCaptureInternal(intervalMs)
    }

    private fun startPreviewFrameCaptureInternal(intervalMs: Long) {
        previewCaptureJob?.cancel()
        frameCount = 0

        val analysis = imageAnalysis ?: run {
            Log.e(TAG, "startPreviewFrameCaptureInternal: imageAnalysis is null")
            return
        }

        Log.i(TAG, "Setting up ImageAnalysis analyzer for preview frames")
        
        analysis.setAnalyzer(executor) { imageProxy ->
            frameCount++
            if (frameCount % 50 == 1) {
                Log.v(TAG, "Processing frame #$frameCount (${imageProxy.width}x${imageProxy.height})")
            }
            
            val jpeg = imageProxyToJpeg(imageProxy)
            if (jpeg != null) {
                _latestPreviewFrame.value = jpeg
                if (frameCount % 50 == 1) {
                    Log.v(TAG, "Frame #$frameCount converted to JPEG (${jpeg.size} bytes)")
                }
            } else {
                if (frameCount % 50 == 1) {
                    Log.w(TAG, "Frame #$frameCount failed to convert to JPEG")
                }
            }
            imageProxy.close()
        }

        Log.d(TAG, "ImageAnalysis analyzer set successfully")
    }

    override fun stopPreviewFrameCapture() {
        Log.d(TAG, "stopPreviewFrameCapture called")
        previewCaptureJob?.cancel()
        previewCaptureJob = null
        pendingPreviewCaptureIntervalMs = null
        imageAnalysis?.clearAnalyzer()
        _latestPreviewFrame.value = null
        frameCount = 0
    }

    private fun imageProxyToJpeg(imageProxy: ImageProxy): ByteArray? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )

            val outputStream = ByteArrayOutputStream()
            
            // Compress to smaller size for network transfer
            val scaleFactor = 4
            val scaledWidth = imageProxy.width / scaleFactor
            val scaledHeight = imageProxy.height / scaleFactor
            
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                50, // Quality
                outputStream
            )

            // Resize the image for network efficiency
            val fullBitmap = BitmapFactory.decodeByteArray(
                outputStream.toByteArray(),
                0,
                outputStream.size()
            )

            val scaledBitmap = Bitmap.createScaledBitmap(
                fullBitmap,
                scaledWidth,
                scaledHeight,
                true
            )

            // Apply rotation if needed
            val rotatedBitmap = if (imageProxy.imageInfo.rotationDegrees != 0) {
                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                }
                Bitmap.createBitmap(
                    scaledBitmap,
                    0, 0,
                    scaledBitmap.width,
                    scaledBitmap.height,
                    matrix,
                    true
                )
            } else {
                scaledBitmap
            }

            val finalOutputStream = ByteArrayOutputStream()
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, finalOutputStream)

            fullBitmap.recycle()
            if (scaledBitmap != rotatedBitmap) scaledBitmap.recycle()
            rotatedBitmap.recycle()

            finalOutputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert frame to JPEG", e)
            null
        }
    }
}
