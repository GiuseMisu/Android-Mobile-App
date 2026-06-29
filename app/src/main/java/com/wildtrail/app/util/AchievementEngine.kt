package com.wildtrail.app.util

import com.wildtrail.app.domain.model.AchievementCategory
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.User

object AchievementEngine {

    fun metricFor(
        category: AchievementCategory,
        user: User,
        hikes: List<HikeLog>,
    ): Float = when (category) {
        AchievementCategory.DISTANCE -> user.totalDistanceKm
        AchievementCategory.ELEVATION ->
            hikes.maxOfOrNull { it.elevationGainMeters }?.toFloat() ?: 0f
        AchievementCategory.SOCIAL ->
            hikes.count { !it.isPrivate }.toFloat()
        AchievementCategory.STREAK -> user.totalHikesCount.toFloat()
        AchievementCategory.BIODIVERSITY -> 0f
        AchievementCategory.OTHER -> 0f
    }
}
