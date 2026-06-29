package com.wildtrail.app.domain.model

data class User(
    val firebaseUid: String,
    val username: String,
    val sex: Sex?,
    val dateOfBirth: Long?,
    val country: String?,
    val level: Int,
    val xpPoints: Int,
    val totalDistanceKm: Float,
    val totalHikesCount: Int,
    val profilePictureUrl: String?,
    val bio: String?,
    val emergencyContactNumber: String,
    val createdAt: Long,
    val lastActive: Long,
    val isPublic: Boolean,
)

const val DEFAULT_EMERGENCY_NUMBER: String = "9999999"

enum class Sex { MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY }
