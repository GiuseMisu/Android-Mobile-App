package com.wildtrail.app.domain.model

data class AccelReading(
    val rawMagnitude: Float,
    val smoothedMagnitude: Float,
    val timestampNs: Long,
)
