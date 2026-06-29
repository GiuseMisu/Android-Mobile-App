package com.wildtrail.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wildtrail.app.domain.model.Sex
import com.wildtrail.app.domain.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val firebaseUid: String,
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

fun UserEntity.toDomain(): User = User(
    firebaseUid = firebaseUid,
    username = username,
    sex = sex,
    dateOfBirth = dateOfBirth,
    country = country,
    level = level,
    xpPoints = xpPoints,
    totalDistanceKm = totalDistanceKm,
    totalHikesCount = totalHikesCount,
    profilePictureUrl = profilePictureUrl,
    bio = bio,
    emergencyContactNumber = emergencyContactNumber,
    createdAt = createdAt,
    lastActive = lastActive,
    isPublic = isPublic,
)

fun User.toEntity(): UserEntity = UserEntity(
    firebaseUid = firebaseUid,
    username = username,
    sex = sex,
    dateOfBirth = dateOfBirth,
    country = country,
    level = level,
    xpPoints = xpPoints,
    totalDistanceKm = totalDistanceKm,
    totalHikesCount = totalHikesCount,
    profilePictureUrl = profilePictureUrl,
    bio = bio,
    emergencyContactNumber = emergencyContactNumber,
    createdAt = createdAt,
    lastActive = lastActive,
    isPublic = isPublic,
)
