package com.wildtrail.app.data.repository

import com.wildtrail.app.data.remote.HikeApiService
import com.wildtrail.app.data.remote.dto.HikeFeaturesDto
import com.wildtrail.app.data.remote.dto.PredictRequestDto
import com.wildtrail.app.data.remote.dto.UserFeaturesDto
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.SurfaceType
import com.wildtrail.app.domain.model.User

class PredictRepository(
    private val hikeApiService: HikeApiService,
) {

    suspend fun predict(user: User, hike: HikeLog): Result<Double> = runCatching {
        val age = computeAge(user.dateOfBirth)
        val avgSpeed = computeAvgSpeed(user.xpPoints, age)

        val request = PredictRequestDto(
            user = UserFeaturesDto(
                xpPoints = user.xpPoints,
                eta = age,
                pastHikes = user.totalHikesCount,
                avgSpeed = avgSpeed,
            ),
            hike = HikeFeaturesDto(
                lunghezza = hike.lengthKm.toDouble(),
                elevationGain = hike.elevationGainMeters,
                surfaceType = hike.surfaceType.toApiString(),
                difficulty = hike.difficultyLevel,
            ),
        )

        hikeApiService.predict(request).durationMin
    }

    private fun computeAge(dateOfBirth: Long?): Int {
        dateOfBirth ?: return 30
        val ageMs = System.currentTimeMillis() - dateOfBirth
        return (ageMs / (1000L * 60 * 60 * 24 * 365)).toInt().coerceIn(10, 100)
    }

    private fun computeAvgSpeed(xpPoints: Int, age: Int): Double =
        (4.2 + 0.00018 * xpPoints - 0.015 * maxOf(0, age - 45)).coerceIn(2.0, 7.0)

    private fun SurfaceType.toApiString(): String = when (this) {
        SurfaceType.MOUNTAIN -> "mountain"
        SurfaceType.FOREST   -> "forest"
        SurfaceType.COASTAL  -> "mixed"
        SurfaceType.URBAN    -> "road"
        SurfaceType.DESERT   -> "mixed"
        SurfaceType.OTHER    -> "mixed"
    }
}
