package com.wildtrail.app.data.remote.dto

import com.wildtrail.app.domain.model.GeoPoint
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.HikeMediaItem
import com.wildtrail.app.domain.model.HikeMediaType
import com.wildtrail.app.domain.model.SurfaceType

data class HikeLogDto(
    var hikeId: String = "",
    var creatorFirebaseUid: String = "",
    var creatorUsername: String = "",
    var creatorProfilePictureUrl: String? = null,
    var workoutId: String? = null,
    var title: String = "",
    var description: String? = null,
    var avgSpeedKmh: Double = 0.0,
    var stepCount: Int = 0,
    var caloriesBurned: Int = 0,
    var coverPhotoUrl: String? = null,
    var xpEarned: Int = 0,
    var likesCount: Int = 0,
    var surfaceType: String = SurfaceType.OTHER.name,
    var lengthKm: Double = 0.0,
    var durationSeconds: Long = 0L,
    var startedAt: Long = 0L,
    var endedAt: Long = 0L,
    var elevationGainMeters: Int = 0,
    var routeCoordinates: List<Map<String, Any?>> = emptyList(),
    var isPrivate: Boolean = false,
    var difficultyLevel: Int = 3,
    var mudRisk: Int = 3,
    var pathClarity: Int = 3,
    var fatigueLevel: Int = 3,
    var animalEncounterRisk: Int = 3,
    var waterAvailability: Boolean = false,
    var averageRating: Double = 0.0,
    var reviewCount: Int = 0,
    // Stored as plain maps so Firestore can (de)serialize them; filePath holds a remote
    // download URL once uploaded, so any viewer (not just the creator) can load the media.
    var mediaItems: List<Map<String, Any?>> = emptyList(),
)

private fun SurfaceType.code(): String = name
private fun String.toSurfaceType(): SurfaceType =
    runCatching { SurfaceType.valueOf(this) }.getOrDefault(SurfaceType.OTHER)

private fun Map<String, Any?>.toMediaItem(): HikeMediaItem? {
    val id = this["id"] as? String ?: return null
    val type = runCatching { HikeMediaType.valueOf(this["type"] as? String ?: "") }
        .getOrDefault(HikeMediaType.PHOTO)
    return HikeMediaItem(
        id = id,
        type = type,
        filePath = this["filePath"] as? String ?: "",
        lat = (this["lat"] as? Number)?.toDouble() ?: 0.0,
        lng = (this["lng"] as? Number)?.toDouble() ?: 0.0,
        timestamp = (this["timestamp"] as? Number)?.toLong() ?: 0L,
    )
}

private fun HikeMediaItem.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "type" to type.name,
    "filePath" to filePath,
    "lat" to lat,
    "lng" to lng,
    "timestamp" to timestamp,
)

fun HikeLogDto.toDomain(): HikeLog = HikeLog(
    hikeId = hikeId,
    creatorFirebaseUid = creatorFirebaseUid,
    creatorUsername = creatorUsername,
    creatorProfilePictureUrl = creatorProfilePictureUrl,
    workoutId = workoutId,
    title = title,
    description = description,
    avgSpeedKmh = avgSpeedKmh.toFloat(),
    stepCount = stepCount,
    caloriesBurned = caloriesBurned,
    coverPhotoUrl = coverPhotoUrl,
    xpEarned = xpEarned,
    likesCount = likesCount,
    surfaceType = surfaceType.toSurfaceType(),
    lengthKm = lengthKm.toFloat(),
    durationSeconds = durationSeconds,
    startedAt = startedAt,
    endedAt = endedAt,
    elevationGainMeters = elevationGainMeters,
    routeCoordinates = routeCoordinates.map { m ->
        GeoPoint(
            lat = (m["lat"] as? Number)?.toDouble() ?: 0.0,
            lng = (m["lng"] as? Number)?.toDouble() ?: 0.0,
            altitudeM = (m["altitudeM"] as? Number)?.toDouble(),
            timestamp = (m["timestamp"] as? Number)?.toLong() ?: 0L,
        )
    },
    isPrivate = isPrivate,
    difficultyLevel = difficultyLevel,
    mudRisk = mudRisk,
    pathClarity = pathClarity,
    fatigueLevel = fatigueLevel,
    animalEncounterRisk = animalEncounterRisk,
    waterAvailability = waterAvailability,
    averageRating = averageRating.toFloat(),
    reviewCount = reviewCount,
    mediaItems = mediaItems.mapNotNull { it.toMediaItem() },
)

fun HikeLog.toDto(): HikeLogDto = HikeLogDto(
    hikeId = hikeId,
    creatorFirebaseUid = creatorFirebaseUid,
    creatorUsername = creatorUsername,
    creatorProfilePictureUrl = creatorProfilePictureUrl,
    workoutId = workoutId,
    title = title,
    description = description,
    avgSpeedKmh = avgSpeedKmh.toDouble(),
    stepCount = stepCount,
    caloriesBurned = caloriesBurned,
    coverPhotoUrl = coverPhotoUrl,
    xpEarned = xpEarned,
    likesCount = likesCount,
    surfaceType = surfaceType.code(),
    lengthKm = lengthKm.toDouble(),
    durationSeconds = durationSeconds,
    startedAt = startedAt,
    endedAt = endedAt,
    elevationGainMeters = elevationGainMeters,
    routeCoordinates = routeCoordinates.map { gp ->
        mapOf(
            "lat" to gp.lat,
            "lng" to gp.lng,
            "altitudeM" to gp.altitudeM,
            "timestamp" to gp.timestamp,
        )
    },
    isPrivate = isPrivate,
    difficultyLevel = difficultyLevel,
    mudRisk = mudRisk,
    pathClarity = pathClarity,
    fatigueLevel = fatigueLevel,
    animalEncounterRisk = animalEncounterRisk,
    waterAvailability = waterAvailability,
    averageRating = averageRating.toDouble(),
    reviewCount = reviewCount,
    mediaItems = mediaItems.map { it.toMap() },
)
