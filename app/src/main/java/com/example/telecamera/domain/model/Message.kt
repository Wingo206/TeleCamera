package com.example.telecamera.domain.model

import com.example.telecamera.domain.camera.AspectRatio
import com.example.telecamera.domain.camera.CameraState
import com.example.telecamera.domain.camera.FlashMode
import kotlinx.serialization.Serializable

@Serializable
sealed class Message {
    // Camera -> Remote(s)
    @Serializable
    data class PreviewFrame(
        val jpegBase64: String,
        val timestamp: Long
    ) : Message()

    @Serializable
    data class CaptureConfirmation(
        val success: Boolean,
        val photoUri: String?,
        val errorMessage: String? = null
    ) : Message()

    @Serializable
    data class StateSync(
        val state: CameraState
    ) : Message()

    @Serializable
    data class QualityUpdate(
        val latencyMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : Message()

    // Either -> Camera (or Remote for ping)
    @Serializable
    data class CaptureCommand(
        val senderId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : Message()

    @Serializable
    data class ControlUpdate(
        val zoomRatio: Float? = null,
        val exposureCompensation: Int? = null,
        val aspectRatio: AspectRatio? = null,
        val flashMode: FlashMode? = null
    ) : Message()

    @Serializable
    data class FocusPoint(
        val x: Float,
        val y: Float
    ) : Message()

    // Ping/Pong for latency measurement
    @Serializable
    data class Ping(
        val timestamp: Long = System.currentTimeMillis()
    ) : Message()

    @Serializable
    data class Pong(
        val originalTimestamp: Long,
        val responseTimestamp: Long = System.currentTimeMillis()
    ) : Message()
}

