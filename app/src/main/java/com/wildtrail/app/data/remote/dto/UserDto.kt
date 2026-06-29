package com.wildtrail.app.data.remote.dto

import com.wildtrail.app.domain.model.DEFAULT_EMERGENCY_NUMBER
import com.wildtrail.app.domain.model.Sex
import com.wildtrail.app.domain.model.User

data class UserDto(
    var firebaseUid: String = "",
    var username: String = "",
    var sex: String? = null,
    var dateOfBirth: Long? = null,
    var country: String? = null,
    var level: Int = 1,
    var xpPoints: Int = 0,
    var totalDistanceKm: Double = 0.0,
    var totalHikesCount: Int = 0,
    var profilePictureUrl: String? = null,
    var bio: String? = null,
    var emergencyContactNumber: String = DEFAULT_EMERGENCY_NUMBER,
    var createdAt: Long = 0L,
    var lastActive: Long = 0L,
    var isPublic: Boolean = true,
)

private fun String?.toSex(): Sex? = this?.let {
    runCatching { Sex.valueOf(it) }.getOrNull()
}

fun UserDto.toDomain(): User = User(
    firebaseUid = firebaseUid,
    username = username,
    sex = sex.toSex(),
    dateOfBirth = dateOfBirth,
    country = country,
    level = level,
    xpPoints = xpPoints,
    totalDistanceKm = totalDistanceKm.toFloat(),
    totalHikesCount = totalHikesCount,
    profilePictureUrl = profilePictureUrl,
    bio = bio,
    emergencyContactNumber = emergencyContactNumber.ifBlank { DEFAULT_EMERGENCY_NUMBER },
    createdAt = createdAt,
    lastActive = lastActive,
    isPublic = isPublic,
)

fun User.toDto(): UserDto = UserDto(
    firebaseUid = firebaseUid,
    username = username,
    sex = sex?.name,
    dateOfBirth = dateOfBirth,
    country = country,
    level = level,
    xpPoints = xpPoints,
    totalDistanceKm = totalDistanceKm.toDouble(),
    totalHikesCount = totalHikesCount,
    profilePictureUrl = profilePictureUrl,
    bio = bio,
    emergencyContactNumber = emergencyContactNumber.ifBlank { DEFAULT_EMERGENCY_NUMBER },
    createdAt = createdAt,
    lastActive = lastActive,
    isPublic = isPublic,
)
