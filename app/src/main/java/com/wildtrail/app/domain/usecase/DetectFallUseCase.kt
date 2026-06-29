package com.wildtrail.app.domain.usecase

import com.wildtrail.app.data.repository.SensorRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.math.abs

sealed interface FallDetectionEvent {
    data class FallDetected(val timestampNs: Long) : FallDetectionEvent
}

data class FallDetectionConfig(
    val impactThresholdG: Float = 5.5f,
    val rotationWindowMs: Long = 1_000L,
    val rotationThresholdDeg: Float = 15f,
    val inactivityWindowMs: Long = 1_000L,
    val inactivityAccelMaxG: Float = 1.3f,
    val inactivityGyroMaxRadPerSec: Float = 0.6f,
    val maxStillnessWaitMs: Long = 6_000L,
    val gravity: Float = 9.81f,
)

class DetectFallUseCase(
    private val sensorRepository: SensorRepository,
    private val config: FallDetectionConfig = FallDetectionConfig(),
    private val debugLog: ((String) -> Unit)? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    operator fun invoke(): Flow<FallDetectionEvent> {
        val accelReadings = sensorRepository.accelerometerSamples()
            .map {
                Reading.Accel(
                    rawG = it.rawMagnitude / config.gravity,
                    smoothedG = it.smoothedMagnitude / config.gravity,
                    tNs = it.timestampNs,
                )
            }
        val gyroReadings = sensorRepository.gyroscopeSamples()
            .map { Reading.Gyro(angularSpeed = it.magnitude, tNs = it.timestampNs) }

        return merge(accelReadings, gyroReadings)
            .detectFalls()
            .flowOn(dispatcher)
    }

    private sealed interface Reading {
        val tNs: Long
        data class Accel(val rawG: Float, val smoothedG: Float, override val tNs: Long) : Reading
        data class Gyro(val angularSpeed: Float, override val tNs: Long) : Reading
    }

    private fun Flow<Reading>.detectFalls(): Flow<FallDetectionEvent> =
        flow {
            val machine = FallStateMachine(config, debugLog)
            collect { reading ->
                machine.process(reading)?.let { emit(it) }
            }
        }

    private class FallStateMachine(
        private val config: FallDetectionConfig,
        private val debugLog: ((String) -> Unit)?,
    ) {

        // a real fall = hard impact, then rotation, then stillness — requiring all three cuts false positives
        private enum class Phase { MONITORING, AWAIT_ROTATION, AWAIT_INACTIVITY }

        private val rotationWindowNs = config.rotationWindowMs * NANOS_PER_MILLI
        private val inactivityWindowNs = config.inactivityWindowMs * NANOS_PER_MILLI
        private val maxStillnessWaitNs = config.maxStillnessWaitMs * NANOS_PER_MILLI

        private var phase = Phase.MONITORING
        private var impactTNs = 0L
        private var rotationAccumDeg = 0f
        private var lastGyroTNs = 0L
        private var inactivitySinceTNs = 0L
        private var awaitInactivityStartTNs = 0L

        private var peakG = 0f
        private var peakWindowStartNs = 0L

        fun process(reading: Reading): FallDetectionEvent? {
            trackPeak(reading)
            when (phase) {
                Phase.MONITORING -> {
                    if (reading is Reading.Accel && reading.rawG > config.impactThresholdG) {
                        phase = Phase.AWAIT_ROTATION
                        impactTNs = reading.tNs
                        rotationAccumDeg = 0f
                        lastGyroTNs = reading.tNs
                        log { "impact %.2fg → awaiting rotation".format(reading.rawG) }
                    }
                }

                Phase.AWAIT_ROTATION -> {
                    if (reading.tNs - impactTNs > rotationWindowNs) {
                        log { "no rotation within ${config.rotationWindowMs}ms (got %.0f°) → reset".format(rotationAccumDeg) }
                        reset()
                        return process(reading)
                    }
                    if (reading is Reading.Gyro) {
                        val dtSec = (reading.tNs - lastGyroTNs).coerceAtLeast(0L) / NANOS_PER_SECOND
                        lastGyroTNs = reading.tNs
                        rotationAccumDeg += abs(reading.angularSpeed) * dtSec * RAD_TO_DEG
                        if (rotationAccumDeg >= config.rotationThresholdDeg) {
                            phase = Phase.AWAIT_INACTIVITY
                            inactivitySinceTNs = reading.tNs
                            awaitInactivityStartTNs = reading.tNs
                            log { "rotation %.0f° confirmed → awaiting ${config.inactivityWindowMs}ms stillness".format(rotationAccumDeg) }
                        }
                    }
                }

                Phase.AWAIT_INACTIVITY -> {
                    val movedByAccel = reading is Reading.Accel &&
                        reading.smoothedG > config.inactivityAccelMaxG
                    val movedByGyro = reading is Reading.Gyro &&
                        abs(reading.angularSpeed) > config.inactivityGyroMaxRadPerSec
                    if (movedByAccel || movedByGyro) {
                        inactivitySinceTNs = reading.tNs
                        if (reading.tNs - awaitInactivityStartTNs > maxStillnessWaitNs) {
                            log { "no stillness within ${config.maxStillnessWaitMs}ms → reset" }
                            reset()
                        }
                        return null
                    }
                    if (reading.tNs - inactivitySinceTNs >= inactivityWindowNs) {
                        log { "FALL CONFIRMED" }
                        val event = FallDetectionEvent.FallDetected(reading.tNs)
                        reset()
                        return event
                    }
                }
            }
            return null
        }

        private fun trackPeak(reading: Reading) {
            if (debugLog == null || reading !is Reading.Accel) return
            if (peakWindowStartNs == 0L) peakWindowStartNs = reading.tNs
            if (reading.rawG > peakG) peakG = reading.rawG
            if (reading.tNs - peakWindowStartNs >= ONE_SECOND_NS) {
                log { "peak %.2fg (phase=$phase)".format(peakG) }
                peakG = 0f
                peakWindowStartNs = reading.tNs
            }
        }

        private fun reset() {
            phase = Phase.MONITORING
            rotationAccumDeg = 0f
        }

        private inline fun log(message: () -> String) {
            debugLog?.invoke(message())
        }

        private companion object {
            const val NANOS_PER_MILLI = 1_000_000L
            const val ONE_SECOND_NS = 1_000_000_000L
            const val NANOS_PER_SECOND = 1_000_000_000f
            const val RAD_TO_DEG = 57.29578f
        }
    }
}
