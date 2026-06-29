package com.wildtrail.app.domain.model

import kotlin.math.sqrt

data class SensorSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNs: Long,
) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}
