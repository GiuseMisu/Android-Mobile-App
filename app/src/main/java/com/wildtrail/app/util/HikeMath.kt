package com.wildtrail.app.util

import com.wildtrail.app.domain.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object HikeMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } +
                cos(lat1) * cos(lat2) * sin(dLng / 2).let { it * it }
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return EARTH_RADIUS_M * c
    }

    fun totalDistanceKm(points: List<GeoPoint>): Float {
        if (points.size < 2) return 0f
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1], points[i])
        }
        return (total / 1000.0).toFloat()
    }

    fun elevationGainMeters(points: List<GeoPoint>): Int {
        if (points.size < 2) return 0
        var gain = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1].altitudeM ?: continue
            val curr = points[i].altitudeM ?: continue
            val delta = curr - prev
            if (delta > 0) gain += delta
        }
        return gain.toInt()
    }

    fun avgSpeedKmh(distanceKm: Float, durationSeconds: Long): Float {
        if (durationSeconds <= 0L) return 0f
        val hours = durationSeconds / 3600.0
        return (distanceKm / hours).toFloat()
    }

    fun estimateCalories(distanceKm: Float, elevationGainM: Int): Int =
        (distanceKm * 80 + elevationGainM * 0.5f).toInt()

    fun xpFromHike(distanceKm: Float, elevationGainM: Int): Int {
        val base = (distanceKm * 10f).toInt()
        val elevationBonus = (elevationGainM / 100f * 5f).toInt()
        return max(10, base + elevationBonus + 5)
    }
}
