package com.wildtrail.app.data.repository

import com.wildtrail.app.domain.model.SensorSample

class LowPassFilter(private val alpha: Float = DEFAULT_ALPHA) {

    private var hasPrevious = false
    private var x = 0f
    private var y = 0f
    private var z = 0f

    fun apply(sample: SensorSample): SensorSample {
        if (!hasPrevious) {
            x = sample.x
            y = sample.y
            z = sample.z
            hasPrevious = true
        } else {
            x += alpha * (sample.x - x)
            y += alpha * (sample.y - y)
            z += alpha * (sample.z - z)
        }
        return sample.copy(x = x, y = y, z = z)
    }

    companion object {
        const val DEFAULT_ALPHA = 0.2f
    }
}
