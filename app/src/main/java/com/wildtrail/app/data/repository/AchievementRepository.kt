package com.wildtrail.app.data.repository

import android.util.Log
import com.wildtrail.app.data.local.dao.AchievementDao
import com.wildtrail.app.data.local.entity.toDomain
import com.wildtrail.app.data.local.entity.toEntity
import com.wildtrail.app.data.remote.FirestoreService
import com.wildtrail.app.data.remote.dto.toDomain
import com.wildtrail.app.domain.model.AchievementCatalog
import com.wildtrail.app.domain.model.AchievementDefinition
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.User
import com.wildtrail.app.domain.model.UserAchievement
import com.wildtrail.app.util.AchievementEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AchievementRepository(
    private val achievementDao: AchievementDao,
    private val firestore: FirestoreService,
) {

    suspend fun syncDefinitions() {
        runCatching {
            achievementDao.upsertDefinitions(AchievementCatalog.ALL.map { it.toEntity() })
        }.onFailure { Log.w(TAG, "Built-in achievement seed skipped", it) }
        runCatching {
            val defs = firestore.fetchAchievementDefinitions().map { it.toDomain().toEntity() }
            if (defs.isNotEmpty()) achievementDao.upsertDefinitions(defs)
        }.onFailure { Log.w(TAG, "Achievement catalogue sync skipped", it) }
    }

    suspend fun evaluateAndAward(user: User, hikes: List<HikeLog>) {
        AchievementCatalog.ALL.forEach { def ->
            val metric = AchievementEngine.metricFor(def.category, user, hikes)
            if (metric >= def.thresholdValue &&
                !achievementDao.hasEarned(user.firebaseUid, def.achievementId)
            ) {
                award(
                    UserAchievement(
                        userUid = user.firebaseUid,
                        achievementId = def.achievementId,
                        earnedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun observeAll(): Flow<List<AchievementDefinition>> =
        achievementDao.observeAllDefinitions().map { list -> list.map { it.toDomain() } }

    fun observeEarned(userUid: String): Flow<List<AchievementDefinition>> =
        achievementDao.observeEarnedFor(userUid).map { list -> list.map { it.toDomain() } }

    suspend fun award(award: UserAchievement) {
        achievementDao.upsertEarned(award.toEntity())
        runCatching {
            firestore.upsertEarnedAchievement(
                com.wildtrail.app.data.remote.dto.UserAchievementDto(
                    userUid = award.userUid,
                    achievementId = award.achievementId,
                    earnedAt = award.earnedAt,
                ),
            )
        }.onFailure { Log.w(TAG, "Achievement award sync skipped", it) }
    }

    private companion object { const val TAG = "AchievementRepository" }
}
