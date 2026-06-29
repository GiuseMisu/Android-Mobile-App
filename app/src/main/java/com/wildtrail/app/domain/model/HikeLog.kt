package com.wildtrail.app.domain.model

import kotlinx.serialization.Serializable

data class HikeLog(
    val hikeId: String,
    val creatorFirebaseUid: String,
    val creatorUsername: String,
    val creatorProfilePictureUrl: String?,
    val workoutId: String?,
    val title: String,
    val description: String?,
    val avgSpeedKmh: Float,
    val stepCount: Int,
    val caloriesBurned: Int,
    val coverPhotoUrl: String?,
    val xpEarned: Int,
    val likesCount: Int,
    val surfaceType: SurfaceType,
    val lengthKm: Float,
    val durationSeconds: Long,
    val startedAt: Long,
    val endedAt: Long,
    val elevationGainMeters: Int,
    val routeCoordinates: List<GeoPoint>,
    val isPrivate: Boolean,
    val difficultyLevel: Int,
    val mudRisk: Int,
    val pathClarity: Int,
    val fatigueLevel: Int,
    val animalEncounterRisk: Int,
    val waterAvailability: Boolean,
    val averageRating: Float,
    val reviewCount: Int,
    val mediaItems: List<HikeMediaItem> = emptyList(),
)

@Serializable
enum class HikeMediaType { PHOTO, AUDIO }

@Serializable
data class HikeMediaItem(
    val id: String,
    val type: HikeMediaType,
    val filePath: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
)

enum class SurfaceType {
    MOUNTAIN, FOREST, COASTAL, URBAN, DESERT, OTHER
}

@Serializable
data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val altitudeM: Double? = null,
    val timestamp: Long = 0L,
)
