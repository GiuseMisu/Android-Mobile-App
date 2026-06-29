package com.wildtrail.app.domain.model

data class HikeFilter(
    val minKm: Float = 0f,
    val maxKm: Float = DISTANCE_MAX_KM,
    val minElevation: Int = 0,
    val maxElevation: Int = ELEVATION_MAX_M,
    val difficulties: Set<Int> = emptySet(),
    val surfaceTypes: Set<SurfaceType> = emptySet(),
) {
    fun isActive(): Boolean =
        minKm > 0f ||
            maxKm < DISTANCE_MAX_KM ||
            minElevation > 0 ||
            maxElevation < ELEVATION_MAX_M ||
            difficulties.isNotEmpty() ||
            surfaceTypes.isNotEmpty()

    companion object {
        const val DISTANCE_MAX_KM = 50f
        const val ELEVATION_MAX_M = 2000

        val DIFFICULTY_LEVELS = 1..5
    }
}
