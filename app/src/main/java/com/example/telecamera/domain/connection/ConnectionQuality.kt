package com.example.telecamera.domain.connection

data class ConnectionQuality(
    val latencyMs: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val qualityLevel: QualityLevel
        get() = when {
            latencyMs < 100 -> QualityLevel.EXCELLENT
            latencyMs < 300 -> QualityLevel.GOOD
            latencyMs < 600 -> QualityLevel.FAIR
            else -> QualityLevel.POOR
        }
}

enum class QualityLevel {
    EXCELLENT, GOOD, FAIR, POOR
}

