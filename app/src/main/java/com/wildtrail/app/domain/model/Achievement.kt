package com.wildtrail.app.domain.model

data class AchievementDefinition(
    val achievementId: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val xpReward: Int,
    val category: AchievementCategory,
    val thresholdValue: Float,
)

enum class AchievementCategory {
    DISTANCE, ELEVATION, SOCIAL, BIODIVERSITY, STREAK, OTHER
}

data class UserAchievement(
    val userUid: String,
    val achievementId: String,
    val earnedAt: Long,
)
