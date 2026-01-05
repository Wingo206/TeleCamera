package com.example.telecamera.domain.camera

import kotlinx.serialization.Serializable

@Serializable
enum class AspectRatio(val value: Float, val displayName: String) {
    RATIO_4_3(4f / 3f, "4:3"),
    RATIO_16_9(16f / 9f, "16:9"),
    RATIO_1_1(1f, "1:1")
}

@Serializable
enum class FlashMode {
    AUTO, ON, OFF
}

@Serializable
enum class CameraLens {
    BACK, FRONT
}

@Serializable
data class CameraState(
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val exposureCompensation: Int = 0,
    val minExposureCompensation: Int = 0,
    val maxExposureCompensation: Int = 0,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val flashMode: FlashMode = FlashMode.AUTO,
    val cameraLens: CameraLens = CameraLens.BACK,
    val isCameraReady: Boolean = false
) {
    val zoomProgress: Float
        get() = if (maxZoomRatio > minZoomRatio) {
            (zoomRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio)
        } else 0f

    val exposureProgress: Float
        get() = if (maxExposureCompensation > minExposureCompensation) {
            (exposureCompensation - minExposureCompensation).toFloat() /
                    (maxExposureCompensation - minExposureCompensation).toFloat()
        } else 0.5f
}

