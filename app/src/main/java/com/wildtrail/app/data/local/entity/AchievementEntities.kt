package com.wildtrail.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wildtrail.app.domain.model.AchievementCategory
import com.wildtrail.app.domain.model.AchievementDefinition
import com.wildtrail.app.domain.model.UserAchievement

@Entity(tableName = "achievement_definitions")
data class AchievementDefinitionEntity(
    @PrimaryKey val achievementId: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val xpReward: Int,
    val category: AchievementCategory,
    val thresholdValue: Float,
)

fun AchievementDefinitionEntity.toDomain(): AchievementDefinition = AchievementDefinition(
    achievementId = achievementId,
    name = name,
    description = description,
    iconUrl = iconUrl,
    xpReward = xpReward,
    category = category,
    thresholdValue = thresholdValue,
)

fun AchievementDefinition.toEntity(): AchievementDefinitionEntity = AchievementDefinitionEntity(
    achievementId = achievementId,
    name = name,
    description = description,
    iconUrl = iconUrl,
    xpReward = xpReward,
    category = category,
    thresholdValue = thresholdValue,
)

@Entity(
    tableName = "user_achievements",
    primaryKeys = ["userUid", "achievementId"],
    indices = [Index("achievementId")],
)
data class UserAchievementEntity(
    val userUid: String,
    val achievementId: String,
    val earnedAt: Long,
)

fun UserAchievementEntity.toDomain(): UserAchievement = UserAchievement(
    userUid = userUid,
    achievementId = achievementId,
    earnedAt = earnedAt,
)

fun UserAchievement.toEntity(): UserAchievementEntity = UserAchievementEntity(
    userUid = userUid,
    achievementId = achievementId,
    earnedAt = earnedAt,
)
