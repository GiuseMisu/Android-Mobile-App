package com.wildtrail.app.domain.usecase

import com.wildtrail.app.data.repository.SensorRepository
import com.wildtrail.app.domain.model.AccelReading
import com.wildtrail.app.domain.model.SensorSample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetectFallUseCaseTest {

    private val gravity = 9.81f

    private val testConfig = FallDetectionConfig(
        impactThresholdG = 2.5f,
        rotationWindowMs = 1_000L,
        rotationThresholdDeg = 30f,
        inactivityWindowMs = 3_000L,
        inactivityAccelMaxG = 1.2f,
        inactivityGyroMaxRadPerSec = 0.6f,
        maxStillnessWaitMs = 6_000L,
        gravity = gravity,
    )

    @Test
    fun `confirms a real fall - impact then rotation then stillness`() = runTest {
        val accel = listOf(
            accelG(3.0f, atMs = 0),
            accelG(1.0f, atMs = 100),
            accelG(1.0f, atMs = 1_000),
            accelG(1.0f, atMs = 3_100),
        )
        val gyro = listOf(
            gyro(10f, atMs = 20),
            gyro(10f, atMs = 40),
            gyro(10f, atMs = 60),
        )

        val events = collectFalls(accel, gyro, StandardTestDispatcher(testScheduler))

        assertEquals(1, events.size)
        assertTrue(events.single() is FallDetectionEvent.FallDetected)
    }

    @Test
    fun `rejects a false positive - sudden stop while running keeps moving`() = runTest {
        val accel = listOf(
            accelG(3.0f, atMs = 0),
            accelG(1.5f, atMs = 100),
            accelG(1.6f, atMs = 600),
            accelG(1.5f, atMs = 1_500),
            accelG(1.7f, atMs = 2_500),
            accelG(1.5f, atMs = 3_200),
        )
        val gyro = listOf(
            gyro(10f, atMs = 20),
            gyro(10f, atMs = 40),
            gyro(10f, atMs = 60),
        )

        val events = collectFalls(accel, gyro, StandardTestDispatcher(testScheduler))

        assertTrue("a running stop must not trigger a fall", events.isEmpty())
    }

    @Test
    fun `rejects a near miss - hard impact without an orientation change`() = runTest {
        val accel = listOf(
            accelG(3.0f, atMs = 0),
            accelG(1.0f, atMs = 1_100),
            accelG(1.0f, atMs = 4_200),
        )
        val gyro = listOf(
            gyro(0.1f, atMs = 20),
            gyro(0.1f, atMs = 40),
        )

        val events = collectFalls(accel, gyro, StandardTestDispatcher(testScheduler))

        assertTrue("an impact without a tumble must not trigger a fall", events.isEmpty())
    }

    private suspend fun collectFalls(
        accel: List<AccelReading>,
        gyro: List<SensorSample>,
        dispatcher: CoroutineDispatcher,
    ): List<FallDetectionEvent> {
        val useCase = DetectFallUseCase(
            sensorRepository = FakeSensorRepository(
                accel = timedFlow(accel) { it.timestampNs },
                gyro = timedFlow(gyro) { it.timestampNs },
            ),
            config = testConfig,
            dispatcher = dispatcher,
        )
        return useCase().toList()
    }

    private fun accelG(g: Float, atMs: Long) =
        AccelReading(
            rawMagnitude = g * gravity,
            smoothedMagnitude = g * gravity,
            timestampNs = atMs * 1_000_000L,
        )

    private fun gyro(radPerSec: Float, atMs: Long) =
        SensorSample(x = radPerSec, y = 0f, z = 0f, timestampNs = atMs * 1_000_000L)

    private fun <T> timedFlow(samples: List<T>, timeNs: (T) -> Long): Flow<T> = flow {
        var clockMs = 0L
        for (sample in samples) {
            val targetMs = timeNs(sample) / 1_000_000L
            val waitMs = targetMs - clockMs
            if (waitMs > 0) delay(waitMs)
            clockMs = targetMs
            emit(sample)
        }
    }

    private class FakeSensorRepository(
        private val accel: Flow<AccelReading>,
        private val gyro: Flow<SensorSample>,
    ) : SensorRepository(sensorManager = null) {
        override fun accelerometerSamples(): Flow<AccelReading> = accel
        override fun gyroscopeSamples(): Flow<SensorSample> = gyro
    }
}
