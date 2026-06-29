package com.wildtrail.app.util

import com.wildtrail.app.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HikeMathTest {

    @Test
    fun `haversineMeters approximates the Milan-Rome distance`() {
        val milan = GeoPoint(lat = 45.4642, lng = 9.1900)
        val rome = GeoPoint(lat = 41.9028, lng = 12.4964)

        val km = HikeMath.haversineMeters(milan, rome) / 1000.0

        assertTrue("Got $km km, expected ~477", km in 470.0..485.0)
    }

    @Test
    fun `haversine of identical points is zero`() {
        val p = GeoPoint(lat = 0.0, lng = 0.0)
        assertEquals(0.0, HikeMath.haversineMeters(p, p), 0.0001)
    }

    @Test
    fun `totalDistanceKm of an empty or single-point route is zero`() {
        assertEquals(0f, HikeMath.totalDistanceKm(emptyList()), 0f)
        assertEquals(
            0f,
            HikeMath.totalDistanceKm(listOf(GeoPoint(45.4642, 9.1900))),
            0f,
        )
    }

    @Test
    fun `elevationGainMeters counts only positive deltas`() {
        val points = listOf(
            GeoPoint(0.0, 0.0, altitudeM = 100.0),
            GeoPoint(0.0, 0.001, altitudeM = 200.0),
            GeoPoint(0.0, 0.002, altitudeM = 150.0),
            GeoPoint(0.0, 0.003, altitudeM = 200.0),
        )
        assertEquals(150, HikeMath.elevationGainMeters(points))
    }

    @Test
    fun `avgSpeedKmh computes km per hour`() {
        assertEquals(10f, HikeMath.avgSpeedKmh(distanceKm = 10f, durationSeconds = 3_600L), 0.001f)
    }

    @Test
    fun `estimateCalories scales with distance and elevation`() {
        val cals = HikeMath.estimateCalories(distanceKm = 10f, elevationGainM = 100)
        assertEquals(850, cals)
    }

    @Test
    fun `xpFromHike awards distance + elevation + completion bonus`() {
        val xp = HikeMath.xpFromHike(distanceKm = 10f, elevationGainM = 100)
        assertEquals(110, xp)
    }

    @Test
    fun `xpFromHike never returns less than ten`() {
        assertTrue(HikeMath.xpFromHike(distanceKm = 0.1f, elevationGainM = 0) >= 10)
    }
}
