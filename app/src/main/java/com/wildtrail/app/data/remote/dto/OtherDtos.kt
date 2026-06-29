package com.wildtrail.app.data.remote.dto

import com.wildtrail.app.domain.model.AchievementCategory
import com.wildtrail.app.domain.model.AchievementDefinition
import com.wildtrail.app.domain.model.EmergencyContact
import com.wildtrail.app.domain.model.FollowedTrail
import com.wildtrail.app.domain.model.HikeComment
import com.wildtrail.app.domain.model.Like
import com.wildtrail.app.domain.model.TrailReview
import com.wildtrail.app.domain.model.UserAchievement
import com.wildtrail.app.domain.model.UserFollow

data class TrailReviewDto(
    var reviewId: String = "",
    var reviewerUid: String = "",
    var hikeId: String = "",
    var overallRating: Int = 3,
    var fatigueLevel: Int = 0,
    var pathClarity: Int = 0,
    var difficultyLevel: Int = 0,
    var mudRisk: Int = 0,
    var animalEncounterRisk: Int = 0,
    var waterAvailability: Boolean = false,
    var commentText: String? = null,
    var imageUrls: List<String> = emptyList(),
    var createdAt: Long = 0L,
)

fun TrailReviewDto.toDomain() = TrailReview(
    reviewId = reviewId,
    reviewerUid = reviewerUid,
    hikeId = hikeId,
    overallRating = overallRating,
    fatigueLevel = fatigueLevel,
    pathClarity = pathClarity,
    difficultyLevel = difficultyLevel,
    mudRisk = mudRisk,
    animalEncounterRisk = animalEncounterRisk,
    waterAvailability = waterAvailability,
    commentText = commentText,
    imageUrls = imageUrls,
    createdAt = createdAt,
)

fun TrailReview.toDto() = TrailReviewDto(
    reviewId = reviewId,
    reviewerUid = reviewerUid,
    hikeId = hikeId,
    overallRating = overallRating,
    fatigueLevel = fatigueLevel,
    pathClarity = pathClarity,
    difficultyLevel = difficultyLevel,
    mudRisk = mudRisk,
    animalEncounterRisk = animalEncounterRisk,
    waterAvailability = waterAvailability,
    commentText = commentText,
    imageUrls = imageUrls,
    createdAt = createdAt,
)

data class UserFollowDto(
    var followerUid: String = "",
    var followeeUid: String = "",
    var notifyOnNewHike: Boolean = false,
    var createdAt: Long = 0L,
)

fun UserFollowDto.toDomain() = UserFollow(followerUid, followeeUid, notifyOnNewHike, createdAt)
fun UserFollow.toDto() = UserFollowDto(followerUid, followeeUid, notifyOnNewHike, createdAt)

data class FollowedTrailDto(
    var userUid: String = "",
    var hikeId: String = "",
    var notifyOnNewReview: Boolean = false,
    var createdAt: Long = 0L,
)

fun FollowedTrailDto.toDomain() = FollowedTrail(userUid, hikeId, notifyOnNewReview, createdAt)
fun FollowedTrail.toDto() = FollowedTrailDto(userUid, hikeId, notifyOnNewReview, createdAt)

data class HikeCommentDto(
    var commentId: String = "",
    var authorUid: String = "",
    var hikeId: String = "",
    var text: String = "",
    var photoUrls: List<String> = emptyList(),
    var createdAt: Long = 0L,
)

fun HikeCommentDto.toDomain() = HikeComment(commentId, authorUid, hikeId, text, photoUrls, createdAt)
fun HikeComment.toDto() = HikeCommentDto(commentId, authorUid, hikeId, text, photoUrls, createdAt)

data class AchievementDefinitionDto(
    var achievementId: String = "",
    var name: String = "",
    var description: String = "",
    var iconUrl: String? = null,
    var xpReward: Int = 0,
    var category: String = AchievementCategory.OTHER.name,
    var thresholdValue: Double = 0.0,
)

fun AchievementDefinitionDto.toDomain() = AchievementDefinition(
    achievementId,
    name,
    description,
    iconUrl,
    xpReward,
    runCatching { AchievementCategory.valueOf(category) }.getOrDefault(AchievementCategory.OTHER),
    thresholdValue.toFloat(),
)

fun AchievementDefinition.toDto() = AchievementDefinitionDto(
    achievementId, name, description, iconUrl, xpReward, category.name, thresholdValue.toDouble(),
)

data class UserAchievementDto(
    var userUid: String = "",
    var achievementId: String = "",
    var earnedAt: Long = 0L,
)

fun UserAchievementDto.toDomain() = UserAchievement(userUid, achievementId, earnedAt)
fun UserAchievement.toDto() = UserAchievementDto(userUid, achievementId, earnedAt)

data class EmergencyContactDto(
    var contactId: String = "",
    var userUid: String = "",
    var name: String = "",
    var phoneNumber: String = "",
    var relationship: String? = null,
    var isPrimary: Boolean = false,
    var notifyOnFall: Boolean = true,
)

fun EmergencyContactDto.toDomain() = EmergencyContact(
    contactId, userUid, name, phoneNumber, relationship, isPrimary, notifyOnFall,
)

fun EmergencyContact.toDto() = EmergencyContactDto(
    contactId, userUid, name, phoneNumber, relationship, isPrimary, notifyOnFall,
)

data class LikeDto(
    var userUid: String = "",
    var hikeId: String = "",
    var createdAt: Long = 0L,
)

fun LikeDto.toDomain() = Like(userUid, hikeId, createdAt)
fun Like.toDto() = LikeDto(userUid, hikeId, createdAt)
