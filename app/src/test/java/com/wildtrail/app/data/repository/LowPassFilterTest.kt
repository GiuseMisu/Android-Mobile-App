package com.wildtrail.app.data.repository

import com.wildtrail.app.domain.model.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LowPassFilterTest {

    @Test
    fun `attenuates high-frequency noise around a constant mean`() {
        val filter = LowPassFilter(alpha = 0.1f)
        val mean = 9.81f
        val random = Random(seed = 42)

        val noisy = (0 until 300).map { i ->
            val noise = (random.nextFloat() - 0.5f) * 4f
            SensorSample(x = mean + noise, y = 0f, z = 0f, timestampNs = i * 20_000_000L)
        }

        val filtered = noisy.map { filter.apply(it) }

        val inputVariance = variance(noisy.takeLast(150).map { it.x })
        val outputVariance = variance(filtered.takeLast(150).map { it.x })
        assertTrue(
            "expected filtered variance ($outputVariance) << input variance ($inputVariance)",
            outputVariance < inputVariance / 4f,
        )

        val outputMean = filtered.takeLast(150).map { it.x }.average().toFloat()
        assertEquals(mean, outputMean, 0.5f)
    }

    @Test
    fun `first sample seeds the filter with no start-up ramp`() {
        val filter = LowPassFilter(alpha = 0.2f)

        val first = filter.apply(SensorSample(x = 1f, y = 2f, z = 3f, timestampNs = 0L))

        assertEquals(1f, first.x, 1e-6f)
        assertEquals(2f, first.y, 1e-6f)
        assertEquals(3f, first.z, 1e-6f)
    }

    @Test
    fun `converges towards a step input at the expected rate`() {
        val alpha = 0.5f
        val filter = LowPassFilter(alpha = alpha)
        filter.apply(SensorSample(x = 0f, y = 0f, z = 0f, timestampNs = 0L))

        val afterFirst = filter.apply(SensorSample(x = 10f, y = 0f, z = 0f, timestampNs = 1L))
        val afterSecond = filter.apply(SensorSample(x = 10f, y = 0f, z = 0f, timestampNs = 2L))

        assertEquals(5f, afterFirst.x, 1e-4f)
        assertEquals(7.5f, afterSecond.x, 1e-4f)
    }

    @Test
    fun `passes the timestamp through unchanged`() {
        val filter = LowPassFilter()

        val out = filter.apply(SensorSample(x = 1f, y = 0f, z = 0f, timestampNs = 123_456L))

        assertEquals(123_456L, out.timestampNs)
    }

    private fun variance(values: List<Float>): Float {
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }
}
