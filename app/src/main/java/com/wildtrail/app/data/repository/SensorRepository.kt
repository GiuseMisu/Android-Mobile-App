package com.wildtrail.app.data.repository

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.wildtrail.app.domain.model.AccelReading
import com.wildtrail.app.domain.model.SensorSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

open class SensorRepository(private val sensorManager: SensorManager?) {

    open fun accelerometerSamples(): Flow<AccelReading> {
        val filter = LowPassFilter()
        return sensorSamples(Sensor.TYPE_ACCELEROMETER).map { raw ->
            AccelReading(
                rawMagnitude = raw.magnitude,
                smoothedMagnitude = filter.apply(raw).magnitude,
                timestampNs = raw.timestampNs,
            )
        }
    }

    open fun gyroscopeSamples(): Flow<SensorSample> =
        sensorSamples(Sensor.TYPE_GYROSCOPE)

    private fun sensorSamples(sensorType: Int): Flow<SensorSample> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(sensorType)
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    SensorSample(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestampNs = event.timestamp,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }
}
